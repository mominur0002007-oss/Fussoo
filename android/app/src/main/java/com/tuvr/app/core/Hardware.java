package com.tuvr.app.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

/** Runtime sensor availability checks (no assumptions about the device). */
public final class Hardware {

    private Hardware() {
    }

    private static Sensor sensor(Context context, int type) {
        SensorManager manager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return manager == null ? null : manager.getDefaultSensor(type);
    }

    public static boolean hasGyroscope(Context context) {
        return sensor(context, Sensor.TYPE_GYROSCOPE) != null;
    }

    public static boolean hasAccelerometer(Context context) {
        return sensor(context, Sensor.TYPE_ACCELEROMETER) != null;
    }

    public static boolean hasRotationVector(Context context) {
        return sensor(context, Sensor.TYPE_ROTATION_VECTOR) != null;
    }

    public static boolean hasGameRotationVector(Context context) {
        return sensor(context, Sensor.TYPE_GAME_ROTATION_VECTOR) != null;
    }

    public static boolean hasCompass(Context context) {
        return sensor(context, Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    public static String bestTrackingSource(Context context) {
        if (hasRotationVector(context)) {
            return "rotation vector";
        }
        if (hasGameRotationVector(context)) {
            return "game rotation vector";
        }
        if (hasGyroscope(context) && hasAccelerometer(context)) {
            return "gyro + accelerometer fusion";
        }
        return "none";
    }
}
