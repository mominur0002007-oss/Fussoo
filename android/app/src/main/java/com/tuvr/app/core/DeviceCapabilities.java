package com.tuvr.app.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the on-device capability report. It is generated locally and shown in
 * the Diagnostics screen; nothing is uploaded anywhere.
 */
public final class DeviceCapabilities {

    private DeviceCapabilities() {
    }

    public static String versionInfo(Context context) {
        String version = "1.0.0";
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            version = info.versionName;
        } catch (Exception ignored) {
        }
        return "TU VR - The Unlimited Virtual Reality\nVersion " + version
                + "\nA standalone, Cardboard-compatible VR app.\n"
                + "No accounts, no analytics, no advertising.";
    }

    public static String storageSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long free = stat.getAvailableBytes();
            long total = stat.getTotalBytes();
            sb.append("Internal storage free: ").append(Formats.size(free))
                    .append(" of ").append(Formats.size(total)).append('\n');
        } catch (Exception ignored) {
        }
        sb.append("Videos are streamed from their original location.\n");
        sb.append("TU VR never copies video files into app storage.\n");
        sb.append("Thumbnail cache: ").append(com.tuvr.app.media.ThumbLoader.get()
                .cacheSizeKb() / 1024).append(" MB in memory\n");
        sb.append("On-device database: playback positions and favourites only.");
        return sb.toString();
    }

    public static String batteryAdvice(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("What to expect on a TECNO Spark 10C:\n\n");
        sb.append("- VR rendering is the heaviest thing this app does. Expect roughly "
                + "2.5 to 4 hours of video playback on a full battery.\n");
        sb.append("- The phone gets warm. Unisoc T606 will reduce clocks when hot; "
                + "TU VR lowers its render resolution automatically when it detects "
                + "thermal throttling (Android 10+).\n");
        sb.append("- 'Low' graphics quality and a 30 fps frame limit halve GPU load.\n");
        sb.append("- Stop other apps before long sessions; 4 GB RAM is shared.\n");
        sb.append("- Charging while watching adds heat. Charge between sessions when possible.\n\n");
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null && Build.VERSION.SDK_INT >= 29) {
                sb.append("Current thermal status: ")
                        .append(thermalName(power.getCurrentThermalStatus())).append('\n');
                sb.append("Battery saver: ")
                        .append(power.isPowerSaveMode() ? "on" : "off").append('\n');
            }
            sb.append("Android battery optimisation may delay background work; "
                    + "TU VR deliberately has no background service, so this does not "
                    + "affect playback while the app is open.");
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    public static String thermalName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                return "none";
            case PowerManager.THERMAL_STATUS_LIGHT:
                return "light";
            case PowerManager.THERMAL_STATUS_MODERATE:
                return "moderate";
            case PowerManager.THERMAL_STATUS_SEVERE:
                return "severe";
            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "shutdown";
            default:
                return "unknown";
        }
    }

    public static String fullReport(Context context) {
        StringBuilder sb = new StringBuilder();
        AppSettings settings = AppSettings.get(context);

        sb.append("== DEVICE ==\n");
        sb.append("Model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Device: ").append(Build.DEVICE).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Build: ").append(Build.DISPLAY).append('\n');
        sb.append("CPU: ").append(socName())
                .append(" · ").append(Runtime.getRuntime().availableProcessors())
                .append(" cores\n");
        sb.append("ABIs: ").append(String.join(", ", Build.SUPPORTED_ABIS)).append('\n');

        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                sb.append("RAM: ").append(Formats.size(info.totalMem))
                        .append(" (available ").append(Formats.size(info.availMem)).append(")\n");
                sb.append("Low memory state: ").append(info.lowMemory ? "yes" : "no").append('\n');
                sb.append("Heap limit: ").append(Formats.size(Runtime.getRuntime().maxMemory()))
                        .append('\n');
                sb.append("GLES version: ")
                        .append(am.getDeviceConfigurationInfo().getGlEsVersion()).append('\n');
            }
        } catch (Exception ignored) {
        }

        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                sb.append("Display: ").append(metrics.widthPixels).append(" x ")
                        .append(metrics.heightPixels).append(" @ ")
                        .append(Math.round(metrics.densityDpi)).append(" dpi\n");
                sb.append("Refresh rate: ")
                        .append(String.format(Locale.US, "%.0f Hz",
                                wm.getDefaultDisplay().getRefreshRate())).append('\n');
            }
        } catch (Exception ignored) {
        }

        sb.append("\n== SENSORS ==\n");
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        boolean hasRotation = false;
        boolean hasGameRotation = false;
        boolean hasGyro = false;
        boolean hasAccel = false;
        if (sm != null) {
            List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
            for (Sensor sensor : sensors) {
                int type = sensor.getType();
                if (type == Sensor.TYPE_ROTATION_VECTOR) {
                    hasRotation = true;
                } else if (type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                    hasGameRotation = true;
                } else if (type == Sensor.TYPE_GYROSCOPE) {
                    hasGyro = true;
                } else if (type == Sensor.TYPE_ACCELEROMETER) {
                    hasAccel = true;
                }
                if (type == Sensor.TYPE_ROTATION_VECTOR
                        || type == Sensor.TYPE_GAME_ROTATION_VECTOR
                        || type == Sensor.TYPE_GYROSCOPE
                        || type == Sensor.TYPE_ACCELEROMETER
                        || type == Sensor.TYPE_MAGNETIC_FIELD
                        || type == Sensor.TYPE_GRAVITY
                        || type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    sb.append(String.format(Locale.US, "%-26s %-22s %5.1f Hz\n",
                            sensor.getName(), vendor(sensor), sensor.getMaxDelay() > 0
                                    ? 1_000_000f / sensor.getMaxDelay() : 0f));
                }
            }
        }
        sb.append("Rotation vector: ").append(hasRotation ? "yes" : "no").append('\n');
        sb.append("Game rotation vector: ").append(hasGameRotation ? "yes" : "no").append('\n');
        sb.append("Gyroscope: ").append(hasGyro ? "yes" : "no").append('\n');
        sb.append("Accelerometer: ").append(hasAccel ? "yes" : "no").append('\n');

        sb.append("\n== VIDEO DECODERS ==\n");
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            List<String> hardware = new ArrayList<>();
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    for (String type : info.getSupportedTypes()) {
                        if (type.startsWith("video/")) {
                            hardware.add(info.getName() + " (" + type + ")");
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < hardware.size() && i < 12; i++) {
                sb.append("- ").append(hardware.get(i)).append('\n');
            }
            if (hardware.isEmpty()) {
                sb.append("none detected\n");
            }
            sb.append("H.264 1080p: ").append(canDecode(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
                    ? "supported" : "not advertised").append('\n');
            sb.append("H.265 1080p: ").append(canDecode(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080)
                    ? "supported" : "not advertised").append('\n');
            sb.append("VP9 1080p: ").append(canDecode(MediaFormat.MIMETYPE_VIDEO_VP9, 1920, 1080)
                    ? "supported" : "not advertised").append('\n');
            sb.append("AV1 1080p: ").append(canDecode(MediaFormat.MIMETYPE_VIDEO_AV1, 1920, 1080)
                    ? "supported" : "not advertised").append('\n');
        } catch (Exception e) {
            sb.append("codec query failed: ").append(e.getClass().getSimpleName()).append('\n');
        }

        sb.append("\n== TU VR SETTINGS ==\n");
        sb.append("Viewer: ").append(ViewerProfile.displayName(settings.viewerProfileId()))
                .append('\n');
        sb.append(String.format(Locale.US, "FOV %.0f deg · lens %.0f mm · screen-lens %.0f mm\n",
                settings.fovDeg(), settings.lensSeparationMm(), settings.screenLensDistanceMm()));
        sb.append(String.format(Locale.US, "IPD %.0f mm · distortion %.2f/%.2f · chroma %.3f\n",
                settings.ipdMm(), settings.distortionK1(), settings.distortionK2(),
                settings.chromaticShift()));
        sb.append("Quality: ").append(settings.quality())
                .append(" · adaptive ").append(settings.adaptiveQuality())
                .append(" · limit ").append(settings.frameLimit()).append(" fps\n");
        sb.append("Tracking: smoothing ").append(Math.round(settings.trackingSmoothing() * 100))
                .append("% · sensitivity ")
                .append(Math.round(settings.trackingSensitivity() * 100)).append("%\n");

        sb.append("\n== UNSUPPORTED / LIMITED ==\n");
        for (String line : unsupportedFeatures(context)) {
            sb.append("- ").append(line).append('\n');
        }

        String crash = CrashLog.read();
        if (crash != null) {
            sb.append("\n== LAST CRASH ==\n");
            String[] lines = crash.split("\n");
            for (int i = 0; i < Math.min(lines.length, 18); i++) {
                sb.append(lines[i]).append('\n');
            }
        }
        return sb.toString();
    }

    private static String socName() {
        if (Build.VERSION.SDK_INT >= 31) {
            String soc = Build.SOC_MODEL;
            if (soc != null && !soc.isEmpty() && !"unknown".equalsIgnoreCase(soc)) {
                return soc;
            }
        }
        return Build.HARDWARE;
    }

    private static String vendor(Sensor sensor) {
        String vendor = sensor.getVendor();
        return vendor == null ? "" : (vendor.length() > 20 ? vendor.substring(0, 20) : vendor);
    }

    public static boolean canDecode(String mime, int width, int height) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            String codec = list.findDecoderForFormat(format);
            return codec != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> unsupportedFeatures(Context context) {
        List<String> out = new ArrayList<>();
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        boolean hasRotation = sm != null
                && sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
        boolean hasGameRotation = sm != null
                && sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null;
        boolean hasGyro = sm != null && sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
        if (!hasRotation && !hasGameRotation && !hasGyro) {
            out.add("No rotation sensor: VR head tracking is unavailable. "
                    + "Touch drag look-around is used instead.");
        }
        if (!Hardware.hasGyroscope(context)) {
            out.add("No gyroscope: rotational tracking falls back to the fused rotation "
                    + "vector only.");
        }
        if (!canDecode(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080)) {
            out.add("HEVC/H.265 1080p is not advertised by a decoder; use H.264 files.");
        }
        out.add("6DoF (positional) tracking is not supported by this hardware and is not "
                + "attempted. Tracking is 3DoF rotation only.");
        out.add("The VR browser copies the rendered page into a texture a few times per "
                + "second; it is meant for reading, not for video playback inside VR.");
        out.add("Regular Android apps are shown on a flat virtual screen only. They are not "
                + "converted into stereoscopic VR.");
        return out;
    }
}
