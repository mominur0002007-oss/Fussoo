package com.fussoo.vr.core;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

/**
 * 3DoF head tracking.
 *
 * <p>Uses the platform's rotation vector when it exists (it does on the Spark 10C) and
 * falls back to gyro integration with gravity levelling. The output is a column-major
 * 4x4 view rotation, already compensated for the phone's screen rotation.</p>
 *
 * <p>Stability rules, in order of importance:</p>
 * <ol>
 *   <li>Never reset orientation except on an explicit user recentre.</li>
 *   <li>Reject absurd sensor samples instead of integrating them.</li>
 *   <li>Smooth in frame time, not in frames, and never add latency when frames are slow.</li>
 * </ol>
 */
public final class Tracker implements SensorEventListener {

    private static final float NS2S = 1.0f / 1_000_000_000.0f;
    private static final float MAX_DT = 0.05f;
    private static final float MAX_RATE = 12f;

    private final SensorManager sm;
    private final Sensor vec;
    private final Sensor gyro;
    private final Sensor accel;

    private final float[] q = {0f, 0f, 0f, 1f};       // gyro path: x y z w
    private final float[] qVec = {0f, 0f, 0f, 1f};    // rotation-vector path
    private final float[] gravity = {0f, -1f, 0f};
    private boolean gravityReady;
    private long lastGyroNs;

    private boolean useVec;
    private boolean started;
    private int displayRotation = Surface.ROTATION_0;

    private final float[] m9 = new float[9];
    private final float[] rot9 = new float[9];
    private final float[] out = new float[16];
    private final float[] smooth = new float[16];
    private boolean haveSmooth;
    private final float[] tmp = new float[3];

    public Tracker(Context ctx) {
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor v = null, g = null, a = null;
        if (sm != null) {
            v = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            g = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        vec = v;
        gyro = g;
        accel = a;
    }

    public boolean hasRotationVector() {
        return vec != null;
    }

    public boolean hasGyro() {
        return gyro != null;
    }

    public boolean usingRotationVector() {
        return useVec;
    }

    /** 0 = natural portrait, 90/180/270 as reported by the display. */
    public void setDisplayRotation(int degrees) {
        displayRotation = ((degrees % 360) + 360) % 360;
    }

    public void start(Activity activity) {
        if (started || sm == null) {
            return;
        }
        started = true;
        WindowManager wm = activity.getWindowManager();
        if (wm != null) {
            try {
                int d = wm.getDefaultDisplay().getRotation();
                setDisplayRotation(d == Surface.ROTATION_90 ? 90
                        : d == Surface.ROTATION_180 ? 180
                        : d == Surface.ROTATION_270 ? 270 : 0);
            } catch (Exception ignored) {
            }
        }
        lastGyroNs = 0L;
        gravityReady = false;
        useVec = false;
        if (vec != null) {
            useVec = sm.registerListener(this, vec, SensorManager.SENSOR_DELAY_GAME);
        }
        if (!useVec) {
            if (gyro != null) {
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
            }
            if (accel != null && accel != gyro) {
                sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    public void stop() {
        if (sm != null && started) {
            sm.unregisterListener(this);
        }
        started = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int t = e.sensor.getType();
        if (t == Sensor.TYPE_ROTATION_VECTOR && useVec) {
            SensorManager.getQuaternionFromVector(qVec, e.values);
            norm(qVec);
        } else if (t == Sensor.TYPE_GYROSCOPE && !useVec) {
            onGyro(e);
        } else if (t == Sensor.TYPE_ACCELEROMETER && !useVec) {
            onAccel(e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing: the gravity correction handles a drifting bias.
    }

    private void onGyro(SensorEvent e) {
        float gx = e.values[0], gy = e.values[1], gz = e.values[2];
        if (Math.abs(gx) > MAX_RATE || Math.abs(gy) > MAX_RATE || Math.abs(gz) > MAX_RATE) {
            return;
        }
        if (lastGyroNs == 0L) {
            lastGyroNs = e.timestamp;
            return;
        }
        float dt = (e.timestamp - lastGyroNs) * NS2S;
        lastGyroNs = e.timestamp;
        if (dt <= 0f || dt > MAX_DT) {
            return;
        }
        rotate(q, gx, gy, gz, tmp);
        float wl = (float) Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1] + tmp[2] * tmp[2]);
        float angle = wl * dt;
        if (angle < 1e-7f) {
            return;
        }
        float s = (float) Math.sin(angle * 0.5f);
        float dx = tmp[0] / wl * s, dy = tmp[1] / wl * s, dz = tmp[2] / wl * s;
        float dw = (float) Math.cos(angle * 0.5f);
        mul(q, dx, dy, dz, dw);
        norm(q);
    }

    private void onAccel(SensorEvent e) {
        float ax = e.values[0], ay = e.values[1], az = e.values[2];
        float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 1e-3f) {
            return;
        }
        ax /= n;
        ay /= n;
        az /= n;
        if (!gravityReady) {
            gravity[0] = ax;
            gravity[1] = ay;
            gravity[2] = az;
            gravityReady = true;
            return;
        }
        float k = 0.5f;
        gravity[0] += k * (ax - gravity[0]);
        gravity[1] += k * (ay - gravity[1]);
        gravity[2] += k * (az - gravity[2]);
        float gl = (float) Math.sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1]
                + gravity[2] * gravity[2]);
        if (gl < 1e-3f) {
            return;
        }
        gravity[0] /= gl;
        gravity[1] /= gl;
        gravity[2] /= gl;
        // Error between measured gravity and world "down"; correct pitch/roll only.
        rotate(q, gravity[0], gravity[1], gravity[2], tmp);
        float ex = -tmp[2];
        float ez = tmp[0];
        float el = (float) Math.sqrt(ex * ex + ez * ez);
        if (el < 1e-6f || el > 0.4f) {
            return;
        }
        float angle = 0.004f * el;
        float s = (float) Math.sin(angle * 0.5f) / el;
        mul(q, ex * s, 0f, ez * s, (float) Math.cos(angle * 0.5f));
        norm(q);
    }

    /**
     * Returns a column-major 4x4 view rotation. Pass the time since the previous call so
     * the smoothing can adapt; pass 0 for "no smoothing".
     */
    public float[] view(float frameSeconds) {
        quatToMat3(useVec ? qVec : q, m9);
        float[] r = rotationFix(displayRotation);
        // m9 = R_display * M
        float m0 = m9[0], m1 = m9[1], m2 = m9[2];
        float m3 = m9[3], m4 = m9[4], m5 = m9[5];
        float m6 = m9[6], m7 = m9[7], m8 = m9[8];
        rot9[0] = r[0] * m0 + r[3] * m1 + r[6] * m2;
        rot9[1] = r[1] * m0 + r[4] * m1 + r[7] * m2;
        rot9[2] = r[2] * m0 + r[5] * m1 + r[8] * m2;
        rot9[3] = r[0] * m3 + r[3] * m4 + r[6] * m5;
        rot9[4] = r[1] * m3 + r[4] * m4 + r[7] * m5;
        rot9[5] = r[2] * m3 + r[5] * m4 + r[8] * m5;
        rot9[6] = r[0] * m6 + r[3] * m7 + r[6] * m8;
        rot9[7] = r[1] * m6 + r[4] * m7 + r[7] * m8;
        rot9[8] = r[2] * m6 + r[5] * m7 + r[8] * m8;

        out[0] = rot9[0];
        out[1] = rot9[1];
        out[2] = rot9[2];
        out[3] = 0f;
        out[4] = rot9[3];
        out[5] = rot9[4];
        out[6] = rot9[5];
        out[7] = 0f;
        out[8] = rot9[6];
        out[9] = rot9[7];
        out[10] = rot9[8];
        out[11] = 0f;
        out[12] = 0f;
        out[13] = 0f;
        out[14] = 0f;
        out[15] = 1f;

        if (!haveSmooth) {
            System.arraycopy(out, 0, smooth, 0, 16);
            haveSmooth = true;
            return smooth;
        }
        float k;
        if (frameSeconds <= 0f || frameSeconds > 0.034f) {
            k = 1f;
        } else if (frameSeconds < 0.019f) {
            k = 0.68f;
        } else {
            k = 0.55f;
        }
        for (int i = 0; i < 16; i++) {
            smooth[i] += k * (out[i] - smooth[i]);
        }
        ortho(smooth);
        return smooth;
    }

    /**
     * Screen-rotation fix. The activity is landscape-locked, the phone sits in the box
     * upright, so the world has to be counter-rotated by the display rotation.
     */
    private static float[] rotationFix(int deg) {
        float c, s;
        switch (deg) {
            case 90:
                c = 0f;
                s = -1f;
                break;
            case 180:
                c = -1f;
                s = 0f;
                break;
            case 270:
                c = 0f;
                s = 1f;
                break;
            default:
                c = 1f;
                s = 0f;
                break;
        }
        return new float[]{c, -s, 0f, s, c, 0f, 0f, 0f, 1f};
    }

    // ------------------------------------------------------------ quaternion helpers

    private static void norm(float[] v) {
        float n = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2] + v[3] * v[3]);
        if (n < 1e-6f) {
            v[0] = 0f;
            v[1] = 0f;
            v[2] = 0f;
            v[3] = 1f;
            return;
        }
        v[0] /= n;
        v[1] /= n;
        v[2] /= n;
        v[3] /= n;
    }

    private static void rotate(float[] q, float x, float y, float z, float[] out) {
        float qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        float ix = qw * x + qy * z - qz * y;
        float iy = qw * y + qz * x - qx * z;
        float iz = qw * z + qx * y - qy * x;
        float iw = -qx * x - qy * y - qz * z;
        out[0] = ix * qw + iw * -qx + iy * -qz - iz * -qy;
        out[1] = iy * qw + iw * -qy + iz * -qx - ix * -qz;
        out[2] = iz * qw + iw * -qz + ix * -qy - iy * -qx;
    }

    private static void mul(float[] q, float dx, float dy, float dz, float dw) {
        float ox = q[0], oy = q[1], oz = q[2], ow = q[3];
        q[0] = ow * dx + ox * dw + oy * dz - oz * dy;
        q[1] = ow * dy + oy * dw + oz * dx - ox * dz;
        q[2] = ow * dz + oz * dw + ox * dy - oy * dx;
        q[3] = ow * dw - ox * dx - oy * dy - oz * dz;
    }

    private static void quatToMat3(float[] q, float[] m) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        m[0] = 1f - 2f * (y * y + z * z);
        m[1] = 2f * (x * y + w * z);
        m[2] = 2f * (x * z - w * y);
        m[3] = 2f * (x * y - w * z);
        m[4] = 1f - 2f * (x * x + z * z);
        m[5] = 2f * (y * z + w * x);
        m[6] = 2f * (x * z + w * y);
        m[7] = 2f * (y * z - w * x);
        m[8] = 1f - 2f * (x * x + y * y);
    }

    private static void ortho(float[] m) {
        float x = m[0], y = m[1], z = m[2];
        float n = (float) Math.sqrt(x * x + y * y + z * z);
        if (n > 1e-6f) {
            m[0] /= n;
            m[1] /= n;
            m[2] /= n;
        }
        float ax = m[4], ay = m[5], az = m[6];
        float an = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (an > 1e-6f) {
            m[4] /= an;
            m[5] /= an;
            m[6] /= an;
        }
        m[8] = m[1] * m[6] - m[2] * m[5];
        m[9] = m[2] * m[4] - m[0] * m[6];
        m[10] = m[0] * m[5] - m[1] * m[4];
        float cn = (float) Math.sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]);
        if (cn > 1e-6f) {
            m[8] /= cn;
            m[9] /= cn;
            m[10] /= cn;
        }
    }

    public String describe() {
        return "Rotation vector: " + (vec != null ? "yes" : "no")
                + "\nGyroscope: " + (gyro != null ? "yes" : "no")
                + "\nAccelerometer: " + (accel != null ? "yes" : "no")
                + "\nMode in use: " + (useVec ? "fused (system)" : "gyro + gravity")
                + "\nDisplay rotation: " + displayRotation + "°";
    }
}
