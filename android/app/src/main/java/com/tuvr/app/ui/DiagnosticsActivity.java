package com.tuvr.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.CrashLog;
import com.tuvr.app.core.DeviceCapabilities;
import com.tuvr.app.core.Problems;
import com.tuvr.app.core.ViewerProfile;
import com.tuvr.app.vr.HeadTracker;

import java.util.Locale;

/**
 * Sensor diagnostics: live orientation, sample rate, drift estimate and a full
 * device capability report that can be copied or shared.
 */
public class DiagnosticsActivity extends AppCompatActivity implements HeadTracker.Listener {

    private TextView reportView;
    private HeadTracker tracker;
    private String staticReport = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] euler = new float[3];
    private boolean calibrating;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateLiveSection();
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        reportView = findViewById(R.id.txt_report);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_recenter).setOnClickListener(v -> {
            if (tracker != null) {
                tracker.recenter();
                Toast.makeText(this, R.string.vr_recenter_done, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_calibrate).setOnClickListener(v -> startGyroCalibration());
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyReport());
        findViewById(R.id.btn_share).setOnClickListener(v -> shareReport());

        tracker = new HeadTracker(this);
        tracker.setListener(this);
        staticReport = buildStaticReport();
    }

    private String buildStaticReport() {
        AppSettings settings = AppSettings.get(this);
        ViewerProfile profile = settings.viewerProfile();
        StringBuilder sb = new StringBuilder();
        sb.append(DeviceCapabilities.fullReport(this));
        sb.append("\n== TRACKER ==\n");
        sb.append(tracker.sourceSummary());
        sb.append("Best source: ").append(com.tuvr.app.core.Hardware.bestTrackingSource(this))
                .append('\n');
        sb.append("Gyro bias (deg/s): ")
                .append(String.format(Locale.US, "%.4f / %.4f / %.4f",
                        Math.toDegrees(tracker.gyroBiasCopy()[0]),
                        Math.toDegrees(tracker.gyroBiasCopy()[1]),
                        Math.toDegrees(tracker.gyroBiasCopy()[2]))).append('\n');
        sb.append("Drift estimate: ")
                .append(String.format(Locale.US, "%.2f deg/min", tracker.driftDegPerMinute()))
                .append('\n');
        sb.append("\n== VIEWER ==\n");
        sb.append(ViewerProfile.displayName(profile.id)).append('\n');
        sb.append(String.format(Locale.US,
                "eye FOV: %.1f deg horizontal, %.1f deg vertical (per eye)\n",
                profile.eyeFovXDeg(), profile.eyeFovYDeg(1.1f)));
        sb.append(String.format(Locale.US, "stereo baseline: %.1f mm\n",
                profile.stereoBaselineMm()));
        sb.append("\n== LAST CRASH ==\n");
        String crash = CrashLog.read();
        sb.append(crash == null || crash.isEmpty() ? "none recorded\n" : crash);
        sb.append("\n== RECOVERED PROBLEMS ==\n");
        String problems = Problems.report();
        sb.append(problems.isEmpty() ? "none recorded\n" : problems);
        sb.append("\n== LIVE ==\n");
        return sb.toString();
    }

    private void updateLiveSection() {
        if (tracker == null) {
            return;
        }
        tracker.euler(euler);
        String live = String.format(Locale.US,
                "%s\nstate: %s\nsource: %s\nrate: %.0f Hz\nyaw %6.1f  pitch %6.1f  roll %6.1f\n%s",
                getString(R.string.diag_live),
                stateName(tracker.state()),
                tracker.sensorName(),
                tracker.sampleRateHz(),
                euler[0], euler[1], euler[2],
                calibrating ? getString(R.string.diag_calibrating) : "");
        reportView.setText(staticReport + live);
    }

    private String stateName(int state) {
        switch (state) {
            case HeadTracker.STATE_OK:
                return "ok";
            case HeadTracker.STATE_DEGRADED:
                return "degraded";
            default:
                return "unavailable";
        }
    }

    private void startGyroCalibration() {
        if (tracker == null) {
            return;
        }
        calibrating = true;
        Toast.makeText(this, R.string.diag_calibrating, Toast.LENGTH_SHORT).show();
        tracker.calibrateGyro(() -> runOnUiThread(() -> {
            calibrating = false;
            staticReport = buildStaticReport();
            Toast.makeText(this, R.string.diag_calibrated, Toast.LENGTH_SHORT).show();
        }));
    }

    private void copyReport() {
        String text = reportView.getText().toString();
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("TU VR report", text));
            Toast.makeText(this, R.string.diag_report_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "TU VR diagnostics");
        intent.putExtra(Intent.EXTRA_TEXT, reportView.getText().toString());
        startActivity(Intent.createChooser(intent, getString(R.string.diag_share_report)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tracker != null) {
            tracker.start();
            tracker.recenter();
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (tracker != null) {
            tracker.stop();
        }
    }

    @Override
    public void onTrackingStateChanged(int state, String source) {
        // The live section picks this up on the next tick.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tracker != null) {
            tracker.setListener(null);
            tracker.stop();
            tracker = null;
        }
    }
}
