package com.tuvr.app.vr;

/**
 * Small, allocation-free math helpers for 3DoF VR rendering.
 * Quaternions are stored as {x, y, z, w}. Matrices are column-major float[16]
 * to match OpenGL conventions.
 */
public final class MathUtil {

    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    private MathUtil() {
    }

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = 0f;
        }
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
    }

    public static float[] newIdentity() {
        float[] m = new float[16];
        identity(m);
        return m;
    }

    /** out = a * b (both column-major 4x4). out must not alias a or b. */
    public static void multiply(float[] a, float[] b, float[] out) {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
    }

    /** Returns a new translation matrix. */
    public static void translation(float x, float y, float z, float[] out) {
        identity(out);
        out[12] = x;
        out[13] = y;
        out[14] = z;
    }

    /** Rotation matrix from a quaternion (x, y, z, w). */
    public static void quatToMatrix(float qx, float qy, float qz, float qw, float[] out) {
        float xx = qx * qx;
        float yy = qy * qy;
        float zz = qz * qz;
        float xy = qx * qy;
        float xz = qx * qz;
        float yz = qy * qz;
        float wx = qw * qx;
        float wy = qw * qy;
        float wz = qw * qz;

        out[0] = 1f - 2f * (yy + zz);
        out[1] = 2f * (xy + wz);
        out[2] = 2f * (xz - wy);
        out[3] = 0f;

        out[4] = 2f * (xy - wz);
        out[5] = 1f - 2f * (xx + zz);
        out[6] = 2f * (yz + wx);
        out[7] = 0f;

        out[8] = 2f * (xz + wy);
        out[9] = 2f * (yz - wx);
        out[10] = 1f - 2f * (xx + yy);
        out[11] = 0f;

        out[12] = 0f;
        out[13] = 0f;
        out[14] = 0f;
        out[15] = 1f;
    }

    /** Transposed rotation matrix (world -> camera) from a quaternion. */
    public static void quatToViewMatrix(float qx, float qy, float qz, float qw, float[] out) {
        quatToMatrix(qx, qy, qz, qw, out);
        // transpose the rotation part
        swap(out, 1, 4);
        swap(out, 2, 8);
        swap(out, 6, 9);
    }

    private static void swap(float[] m, int a, int b) {
        float t = m[a];
        m[a] = m[b];
        m[b] = t;
    }

    public static void perspective(float fovYDeg, float aspect, float near, float far, float[] out) {
        float fovY = fovYDeg * DEG_TO_RAD;
        float f = 1f / (float) Math.tan(fovY * 0.5);
        for (int i = 0; i < 16; i++) {
            out[i] = 0f;
        }
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1f;
        out[14] = (2f * far * near) / (near - far);
    }

    public static void normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1e-6f) {
            v[0] = 0f;
            v[1] = 0f;
            v[2] = -1f;
            return;
        }
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
    }

    public static float[] cross(float[] a, float[] b, float[] out) {
        out[0] = a[1] * b[2] - a[2] * b[1];
        out[1] = a[2] * b[0] - a[0] * b[2];
        out[2] = a[0] * b[1] - a[1] * b[0];
        return out;
    }

    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Rotates a 3-vector by a quaternion (x,y,z,w). */
    public static void rotateVector(float[] q, float vx, float vy, float vz, float[] out) {
        float qx = q[0];
        float qy = q[1];
        float qz = q[2];
        float qw = q[3];
        // t = 2 * cross(q.xyz, v)
        float tx = 2f * (qy * vz - qz * vy);
        float ty = 2f * (qz * vx - qx * vz);
        float tz = 2f * (qx * vy - qy * vx);
        out[0] = vx + qw * tx + (qy * tz - qz * ty);
        out[1] = vy + qw * ty + (qz * tx - qx * tz);
        out[2] = vz + qw * tz + (qx * ty - qy * tx);
    }

    public static void quatNormalize(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len < 1e-6f) {
            q[0] = 0f;
            q[1] = 0f;
            q[2] = 0f;
            q[3] = 1f;
            return;
        }
        q[0] /= len;
        q[1] /= len;
        q[2] /= len;
        q[3] /= len;
    }

    /** out = a * b using the Hamilton product, order a then b. */
    public static void quatMultiply(float[] a, float[] b, float[] out) {
        float ax = a[0];
        float ay = a[1];
        float az = a[2];
        float aw = a[3];
        float bx = b[0];
        float by = b[1];
        float bz = b[2];
        float bw = b[3];
        out[0] = aw * bx + ax * bw + ay * bz - az * by;
        out[1] = aw * by - ax * bz + ay * bw + az * bx;
        out[2] = aw * bz + ax * by - ay * bx + az * bw;
        out[3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    public static void quatConjugate(float[] q, float[] out) {
        out[0] = -q[0];
        out[1] = -q[1];
        out[2] = -q[2];
        out[3] = q[3];
    }

    public static void quatSlerp(float[] a, float[] b, float t, float[] out) {
        float ax = a[0];
        float ay = a[1];
        float az = a[2];
        float aw = a[3];
        float bx = b[0];
        float by = b[1];
        float bz = b[2];
        float bw = b[3];

        float cos = ax * bx + ay * by + az * bz + aw * bw;
        if (cos < 0f) {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
            cos = -cos;
        }
        float k0;
        float k1;
        if (cos > 0.9995f) {
            k0 = 1f - t;
            k1 = t;
        } else {
            float theta = (float) Math.acos(Math.min(1f, Math.max(-1f, cos)));
            float sinTheta = (float) Math.sin(theta);
            k0 = (float) Math.sin((1f - t) * theta) / sinTheta;
            k1 = (float) Math.sin(t * theta) / sinTheta;
        }
        out[0] = ax * k0 + bx * k1;
        out[1] = ay * k0 + by * k1;
        out[2] = az * k0 + bz * k1;
        out[3] = aw * k0 + bw * k1;
        quatNormalize(out);
    }

    /** Yaw-only (heading) component of a quaternion, as a quaternion. */
    public static void quatYawOnly(float[] q, float[] out) {
        float qx = q[0];
        float qy = q[1];
        float qz = q[2];
        float qw = q[3];
        // forward vector rotated by q, projected on the ground plane
        float fwdX = 2f * (qx * qz + qw * qy);
        float fwdZ = 1f - 2f * (qx * qx + qy * qy);
        float yaw = (float) Math.atan2(fwdX, -fwdZ);
        out[0] = 0f;
        out[1] = (float) Math.sin(yaw * 0.5f);
        out[2] = 0f;
        out[3] = (float) Math.cos(yaw * 0.5f);
        quatNormalize(out);
    }

    /** Euler angles in degrees: {yaw, pitch, roll} for diagnostics. */
    public static void quatToEuler(float[] q, float[] outDeg) {
        float qx = q[0];
        float qy = q[1];
        float qz = q[2];
        float qw = q[3];

        float sinp = 2f * (qw * qy - qz * qx);
        float pitch;
        if (Math.abs(sinp) >= 1f) {
            pitch = (float) Math.copySign(Math.PI / 2.0, sinp);
        } else {
            pitch = (float) Math.asin(sinp);
        }

        float siny = 2f * (qw * qz + qx * qy);
        float cosy = 1f - 2f * (qy * qy + qz * qz);
        float yaw = (float) Math.atan2(siny, cosy);

        float sinr = 2f * (qw * qx + qy * qz);
        float cosr = 1f - 2f * (qx * qx + qy * qy);
        float roll = (float) Math.atan2(sinr, cosr);

        outDeg[0] = yaw * RAD_TO_DEG;
        outDeg[1] = pitch * RAD_TO_DEG;
        outDeg[2] = roll * RAD_TO_DEG;
    }

    /** Quaternion from Euler angles in radians (yaw around Y, pitch X, roll Z). */
    public static void quatFromEuler(float yaw, float pitch, float roll, float[] out) {
        float cy = (float) Math.cos(yaw * 0.5f);
        float sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f);
        float sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f);
        float sr = (float) Math.sin(roll * 0.5f);

        out[0] = sr * cp * cy - cr * sp * sy;
        out[1] = cr * sp * cy + sr * cp * sy;
        out[2] = cr * cp * sy - sr * sp * cy;
        out[3] = cr * cp * cy + sr * sp * sy;
    }

    public static void quatFromAxisAngle(float ax, float ay, float az, float angleRad, float[] out) {
        float half = angleRad * 0.5f;
        float s = (float) Math.sin(half);
        out[0] = ax * s;
        out[1] = ay * s;
        out[2] = az * s;
        out[3] = (float) Math.cos(half);
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
