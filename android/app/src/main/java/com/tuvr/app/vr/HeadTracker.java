package com.tuvr.app.vr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.tuvr.app.core.AppSettings;

/**
 * 3DoF head tracking.
 *
 * Sensor priority:
 *   1. TYPE_GAME_ROTATION_VECTOR (gyro + accelerometer, no magnetometer)
 *   2. TYPE_ROTATION_VECTOR (fused rotation, includes magnetometer heading)
 *   3. Gyroscope + accelerometer complementary fusion implemented here
 *   4. Nothing (touch / drag look-around fallback)
 *
 * The tracker never claims 6DoF: only orientation is tracked, and no artificial
 * translation is invented.
 */
public class HeadTracker implements SensorEventListener {

    public static final int STATE_OK = 0;
    public static final int STATE_DEGRADED = 1;
    public static final int STATE_UNAVAILABLE = 2;

    public interface Listener {
        void onTrackingStateChanged(int state, String source);
    }

    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_ROTATION = 1;
    private static final int SOURCE_GAME_ROTATION = 2;
    private static final int SOURCE_FUSION = 3;

    private final SensorManager sensorManager;
    private final AppSettings settings;

    private Sensor rotationSensor;
    private Sensor gameRotationSensor;
    private Sensor gyroSensor;
    private Sensor accelSensor;

    private int source = SOURCE_NONE;
    private int state = STATE_UNAVAILABLE;
    private boolean running;

    /* raw orientation, protected by itself */
    private final float[] qRaw = {0f, 0f, 0f, 1f};
    private final Object rawLock = new Object();
    private boolean hasSample;

    /* fusion state (only used for SOURCE_FUSION) */
    private final float[] qFusion = {0f, 0f, 0f, 1f};
    private long lastGyroTimestamp;
    private boolean gyroInitialized;
    private final float[] gravityDevice = {0f, 1f, 0f};
    private boolean hasGravity;

    /* calibration */
    private final float[] gyroBias = new float[3];
    private boolean calibrating;
    private long calibrateStart;
    private int calibrateSamples;
    private final float[] biasAccumulator = new float[3];
    private Runnable calibrateCallback;

    /* output pipeline */
    private final float[] qSmooth = {0f, 0f, 0f, 1f};
    private final float[] qRecentered = {0f, 0f, 0f, 1f};
    private final float[] qRecenterOffset = {0f, 0f, 0f, 1f};
    private final float[] qScratch = {0f, 0f, 0f, 1f};
    private final float[] qTouch = {0f, 0f, 0f, 1f};
    /** Rotation from the UI (screen) frame to the device frame. */
    private final float[] qDisplay = {0f, 0f, 0f, 1f};
    /**
     * Android's rotation vector reports orientation in an east-north-up world
     * (Z up). The renderer works in a Y-up world, so every sample is rotated by
     * -90 degrees around X. Without this the horizon would be rolled by 90 deg.
     */
    private final float[] qWorldFix = {0f, 0f, 0f, 1f};
    private final float[] scratchA = new float[4];
    private final float[] scratchB = new float[4];
    private final float[] scratchEuler = new float[3];
    private final float[] eulerDeg = new float[3];
    private boolean smoothInitialized;
    private long lastOutputNanos;

    private float sensitivity = 1f;
    private float smoothing = 0.35f;
    private boolean recenterPending = true;

    /* statistics */
    private int sampleCount;
    private long sampleWindowStartNanos;
    private float sampleRate;
    private long lastSampleNanos;
    private String sensorName = "none";

    private Listener listener;

    public HeadTracker(Context context) {
        this.sensorManager = (SensorManager) context.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        this.settings = AppSettings.get(context);
        this.sensitivity = settings.trackingSensitivity();
        this.smoothing = settings.trackingSmoothing();
        gyroBias[0] = settings.gyroBias(0);
        gyroBias[1] = settings.gyroBias(1);
        gyroBias[2] = settings.gyroBias(2);
        MathUtil.quatFromAxisAngle(1f, 0f, 0f, -MathUtil.DEG_TO_RAD * 90f, qWorldFix);
        probeSensors();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void probeSensors() {
        if (sensorManager == null) {
            state = STATE_UNAVAILABLE;
            return;
        }
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // Inside a viewer the compass heading is unreliable: the box, the phone's
        // own magnets and any nearby metal make TYPE_ROTATION_VECTOR yaw jump or
        // crawl. The game rotation vector (gyro + accelerometer, no magnetometer)
        // is what phone VR viewers use, so it is tried first.
    }

    public boolean hasRotationSensor() {
        return rotationSensor != null || gameRotationSensor != null;
    }

    public boolean hasGyro() {
        return gyroSensor != null;
    }

    public boolean hasAccelerometer() {
        return accelSensor != null;
    }

    public String sensorName() {
        return sensorName;
    }

    public int state() {
        return state;
    }

    public float sampleRateHz() {
        return sampleRate;
    }

    /** Estimated yaw drift of the gyro path in degrees per minute. */
    public float driftDegPerMinute() {
        double bias = Math.sqrt(gyroBias[0] * gyroBias[0]
                + gyroBias[1] * gyroBias[1] + gyroBias[2] * gyroBias[2]);
        return (float) Math.toDegrees(bias) * 60f;
    }

    public float[] gyroBiasCopy() {
        return new float[]{gyroBias[0], gyroBias[1], gyroBias[2]};
    }

    /* ------------------------------------------------------------------ */

    public void start() {
        if (running || sensorManager == null) {
            return;
        }
        running = true;
        gyroInitialized = false;
        hasSample = false;
        smoothInitialized = false;
        sampleWindowStartNanos = System.nanoTime();
        sampleCount = 0;

        boolean registered = false;
        // Gyro-based first: no compass, so no yaw drift from nearby metal.
        if (gameRotationSensor != null) {
            registered = sensorManager.registerListener(this, gameRotationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            if (registered) {
                source = SOURCE_GAME_ROTATION;
                sensorName = "GAME_ROTATION_VECTOR";
            }
        }
        if (!registered && rotationSensor != null) {
            registered = sensorManager.registerListener(this, rotationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            if (registered) {
                source = SOURCE_ROTATION;
                sensorName = "ROTATION_VECTOR";
            }
        }
        if (!registered && gyroSensor != null && accelSensor != null) {
            boolean gyroOk = sensorManager.registerListener(this, gyroSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            boolean accelOk = sensorManager.registerListener(this, accelSensor,
                    SensorManager.SENSOR_DELAY_GAME);
            if (gyroOk && accelOk) {
                source = SOURCE_FUSION;
                sensorName = "GYRO + ACCEL fusion";
                registered = true;
            }
        }
        if (!registered) {
            source = SOURCE_NONE;
            sensorName = "none";
            state = STATE_UNAVAILABLE;
        } else {
            state = STATE_OK;
        }
        recenterPending = settings.recenterOnStart();
        notifyState();
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        calibrating = false;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Corrects for the current screen rotation (landscape lock). Without this the
     * horizon would appear rolled by 90 degrees inside the headset.
     *
     * @param surfaceRotation one of Surface.ROTATION_0/90/180/270
     */
    public void setDisplayRotation(int surfaceRotation) {
        float angle = -90f * surfaceRotation * MathUtil.DEG_TO_RAD;
        MathUtil.quatFromAxisAngle(0f, 0f, 1f, angle, qDisplay);
    }

    public void setSensitivity(float value) {
        sensitivity = MathUtil.clamp(value, 0.4f, 2f);
    }

    public void setSmoothing(float value) {
        smoothing = MathUtil.clamp(value, 0f, 0.9f);
    }

    public void recenter() {
        recenterPending = true;
    }

    public void clearTouchOffset() {
        qTouch[0] = 0f;
        qTouch[1] = 0f;
        qTouch[2] = 0f;
        qTouch[3] = 1f;
    }

    /** Adds a manual look delta (degrees) for the no-sensor / touch fallback. */
    public void addTouchDelta(float dyawDeg, float dpitchDeg) {
        float[] qy = new float[4];
        float[] qp = new float[4];
        MathUtil.quatFromAxisAngle(0f, 1f, 0f, -dyawDeg * MathUtil.DEG_TO_RAD, qy);
        MathUtil.quatFromAxisAngle(1f, 0f, 0f, -dpitchDeg * MathUtil.DEG_TO_RAD, qp);
        MathUtil.quatMultiply(qy, qTouch, qScratch);
        float[] copy = new float[4];
        MathUtil.quatMultiply(qScratch, qp, copy);
        System.arraycopy(copy, 0, qTouch, 0, 4);
        MathUtil.quatNormalize(qTouch);
    }

    /**
     * Averages gyroscope samples while the phone is still, then stores the result
     * as a bias that is subtracted from all future gyro readings.
     */
    public void calibrateGyro(final Runnable done) {
        calibrateStart = System.nanoTime();
        biasAccumulator[0] = 0f;
        biasAccumulator[1] = 0f;
        biasAccumulator[2] = 0f;
        calibrateSamples = 0;
        calibrating = true;
        calibrateCallback = done;
        state = STATE_DEGRADED;
        notifyState();
    }

    public boolean isCalibrating() {
        return calibrating;
    }

    /* ------------------------------------------------------------------ */

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = event.timestamp;
        lastSampleNanos = System.nanoTime();
        sampleCount++;
        long elapsed = lastSampleNanos - sampleWindowStartNanos;
        if (elapsed > 1_000_000_000L) {
            sampleRate = sampleCount * 1_000_000_000f / elapsed;
            sampleCount = 0;
            sampleWindowStartNanos = lastSampleNanos;
        }

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR: {
                float[] q = new float[4];
                SensorManager.getQuaternionFromVector(q, event.values);
                // getQuaternionFromVector returns {w, x, y, z}
                synchronized (rawLock) {
                    qRaw[0] = q[1];
                    qRaw[1] = q[2];
                    qRaw[2] = q[3];
                    qRaw[3] = q[0];
                    MathUtil.quatNormalize(qRaw);
                    hasSample = true;
                }
                break;
            }
            case Sensor.TYPE_GYROSCOPE: {
                if (source != SOURCE_FUSION) {
                    break;
                }
                if (calibrating) {
                    biasAccumulator[0] += event.values[0];
                    biasAccumulator[1] += event.values[1];
                    biasAccumulator[2] += event.values[2];
                    calibrateSamples++;
                    if (calibrateSamples >= 100
                            || now - calibrateStart > 2_500_000_000L) {
                        finishCalibration();
                    }
                    break;
                }
                integrateGyro(event.values, now);
                break;
            }
            case Sensor.TYPE_ACCELEROMETER: {
                if (source != SOURCE_FUSION) {
                    break;
                }
                correctWithGravity(event.values);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR
                || sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                state = STATE_DEGRADED;
            } else if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                    || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                state = STATE_OK;
            }
            notifyState();
        }
    }

    private void finishCalibration() {
        if (calibrateSamples > 0) {
            gyroBias[0] = biasAccumulator[0] / calibrateSamples;
            gyroBias[1] = biasAccumulator[1] / calibrateSamples;
            gyroBias[2] = biasAccumulator[2] / calibrateSamples;
            settings.setGyroBias(gyroBias[0], gyroBias[1], gyroBias[2]);
        }
        calibrating = false;
        state = STATE_OK;
        notifyState();
        if (calibrateCallback != null) {
            Runnable callback = calibrateCallback;
            calibrateCallback = null;
            callback.run();
        }
    }

    private void integrateGyro(float[] rates, long timestampNanos) {
        if (!gyroInitialized) {
            lastGyroTimestamp = timestampNanos;
            gyroInitialized = true;
            return;
        }
        float dt = (timestampNanos - lastGyroTimestamp) * 1e-9f;
        lastGyroTimestamp = timestampNanos;
        if (dt <= 0f || dt > 0.1f) {
            return;
        }
        float wx = rates[0] - gyroBias[0];
        float wy = rates[1] - gyroBias[1];
        float wz = rates[2] - gyroBias[2];
        if (Math.abs(wx) < 1e-4f && Math.abs(wy) < 1e-4f && Math.abs(wz) < 1e-4f) {
            return;
        }
        float[] omega = {wx, wy, wz, 0f};
        MathUtil.quatMultiply(qFusion, omega, qScratch);
        qFusion[0] += 0.5f * qScratch[0] * dt;
        qFusion[1] += 0.5f * qScratch[1] * dt;
        qFusion[2] += 0.5f * qScratch[2] * dt;
        qFusion[3] += 0.5f * qScratch[3] * dt;
        MathUtil.quatNormalize(qFusion);

        synchronized (rawLock) {
            System.arraycopy(qFusion, 0, qRaw, 0, 4);
            hasSample = true;
        }
    }

    private void correctWithGravity(float[] accel) {
        float len = (float) Math.sqrt(accel[0] * accel[0] + accel[1] * accel[1]
                + accel[2] * accel[2]);
        if (len < 1e-3f) {
            return;
        }
        gravityDevice[0] = accel[0] / len;
        gravityDevice[1] = accel[1] / len;
        gravityDevice[2] = accel[2] / len;
        hasGravity = true;

        // Rotate the measured "up" into the world frame and compare with +Y.
        float[] upWorld = new float[3];
        float[] q;
        synchronized (rawLock) {
            q = new float[]{qRaw[0], qRaw[1], qRaw[2], qRaw[3]};
        }
        MathUtil.rotateVector(q, gravityDevice[0], gravityDevice[1], gravityDevice[2], upWorld);

        float[] target = {0f, 1f, 0f};
        float[] axis = new float[3];
        MathUtil.cross(upWorld, target, axis);
        float sinAngle = (float) Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1]
                + axis[2] * axis[2]);
        float cosAngle = MathUtil.dot(upWorld, target);
        if (sinAngle < 1e-5f) {
            return;
        }
        float angle = (float) Math.atan2(sinAngle, cosAngle);
        axis[0] /= sinAngle;
        axis[1] /= sinAngle;
        axis[2] /= sinAngle;
        float gain = 0.06f;
        float[] correction = new float[4];
        MathUtil.quatFromAxisAngle(axis[0], axis[1], axis[2], angle * gain, correction);
        MathUtil.quatMultiply(correction, qFusion, qScratch);
        System.arraycopy(qScratch, 0, qFusion, 0, 4);
        MathUtil.quatNormalize(qFusion);
    }

    private void notifyState() {
        if (listener != null) {
            listener.onTrackingStateChanged(state, sensorName);
        }
    }

    /* ------------------------------------------------------------------ */

    /**
     * Called once per rendered frame on the GL thread. Produces the final
     * orientation including smoothing, sensitivity, recentering and the manual
     * touch offset. Writes into {@code out} as {x, y, z, w}.
     */
    public void updateOutput(float[] out) {
        if (!hasSample) {
            if (recenterPending) {
                qRecentered[0] = 0f;
                qRecentered[1] = 0f;
                qRecentered[2] = 0f;
                qRecentered[3] = 1f;
                recenterPending = false;
            }
            MathUtil.quatMultiply(qTouch, qRecentered, out);
            MathUtil.quatNormalize(out);
            return;
        }

        synchronized (rawLock) {
            System.arraycopy(qRaw, 0, scratchA, 0, 4);
        }
        // Device frame -> UI (screen) frame -> Y-up world.
        MathUtil.quatMultiply(scratchA, qDisplay, scratchB);
        MathUtil.quatMultiply(qWorldFix, scratchB, qScratch);

        // Sensitivity: scale yaw/pitch angles without touching roll.
        if (Math.abs(sensitivity - 1f) > 0.001f) {
            MathUtil.quatToEuler(qScratch, scratchEuler);
            float yaw = scratchEuler[0] * MathUtil.DEG_TO_RAD * sensitivity;
            float pitch = scratchEuler[1] * MathUtil.DEG_TO_RAD * sensitivity;
            float roll = scratchEuler[2] * MathUtil.DEG_TO_RAD;
            MathUtil.quatFromEuler(yaw, pitch, roll, qScratch);
        }

        if (recenterPending) {
            MathUtil.quatYawOnly(qScratch, scratchB);
            MathUtil.quatConjugate(scratchB, qRecenterOffset);
            recenterPending = false;
            smoothInitialized = false;
        }

        // Low-pass slerp for jitter reduction.
        if (!smoothInitialized) {
            System.arraycopy(qScratch, 0, qSmooth, 0, 4);
            smoothInitialized = true;
        } else {
            // Time-based low pass: a fixed per-frame factor would feel sluggish on
            // a device that renders at 30 fps and twitchy at 60. tau maps the
            // "smoothing" setting to 8..40 ms of lag.
            long nowNs = System.nanoTime();
            float dt = lastOutputNanos == 0L ? 0.016f
                    : Math.min(0.1f, (nowNs - lastOutputNanos) / 1e9f);
            lastOutputNanos = nowNs;
            float tau = 0.008f + MathUtil.clamp(smoothing, 0f, 1f) * 0.032f;
            float alpha = MathUtil.clamp(1f - (float) Math.exp(-dt / tau), 0.05f, 1f);
            MathUtil.quatSlerp(qSmooth, qScratch, alpha, qSmooth);
        }

        MathUtil.quatMultiply(qRecenterOffset, qSmooth, qRecentered);
        MathUtil.quatNormalize(qRecentered);

        MathUtil.quatMultiply(qTouch, qRecentered, out);
        MathUtil.quatNormalize(out);
    }

    /** Diagnostic helper: current euler angles in degrees. */
    public void euler(float[] outDeg) {
        float[] q = new float[4];
        updateOutput(q);
        MathUtil.quatToEuler(q, outDeg);
    }

    public boolean hasRecentSample() {
        return lastSampleNanos > 0
                && (System.nanoTime() - lastSampleNanos) < 500_000_000L;
    }

    public String sourceSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(sensorName).append('\n');
        sb.append("Rate: ").append(Math.round(sampleRate)).append(" Hz\n");
        sb.append("Rotation vector: ").append(rotationSensor != null ? "yes" : "no").append('\n');
        sb.append("Game rotation: ").append(gameRotationSensor != null ? "yes" : "no").append('\n');
        sb.append("Gyroscope: ").append(gyroSensor != null ? "yes" : "no").append('\n');
        sb.append("Accelerometer: ").append(accelSensor != null ? "yes" : "no").append('\n');
        return sb.toString();
    }
}
