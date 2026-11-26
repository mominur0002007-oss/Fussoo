package com.tuvr.app.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.DeviceCapabilities;
import com.tuvr.app.core.ViewerProfile;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.core.ToastMessage;
import com.tuvr.app.media.ThumbLoader;

/** All preferences, grouped the same way as the settings screen in VR. */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(AppSettings.FILE);
        setPreferencesFromResource(R.xml.pref_root, rootKey);

        bindClick("calibrate_viewer", () -> startActivity(Intents.calibration(requireContext())));
        bindClick("sensor_diagnostics", () -> startActivity(Intents.diagnostics(requireContext())));
        bindClick("clear_resume", this::confirmClearResume);
        bindClick("clear_thumbs", this::clearThumbnails);
        bindClick("storage_info", this::showStorageInfo);
        bindClick("battery_info", this::showBatteryInfo);
        bindClick("device_report", () -> startActivity(Intents.diagnostics(requireContext())));
        bindClick("licenses", this::showLicenses);
        bindClick("version_info", this::showVersion);

        Preference profile = findPreference(AppSettings.K_VIEWER_PROFILE);
        if (profile instanceof ListPreference) {
            ListPreference list = (ListPreference) profile;
            updateProfileSummary(list, list.getValue());
            list.setOnPreferenceChangeListener((preference, newValue) -> {
                applyViewerPreset(String.valueOf(newValue));
                updateProfileSummary(list, String.valueOf(newValue));
                return true;
            });
        }

        bindSummary(AppSettings.K_QUALITY, R.array.quality_entries, R.array.quality_values);
        bindSummary(AppSettings.K_FRAME_LIMIT, R.array.frame_limit_entries,
                R.array.frame_limit_values);
        bindSummary(AppSettings.K_ENVIRONMENT, R.array.environment_entries,
                R.array.environment_values);
        bindSummary(AppSettings.K_DEFAULT_PROJECTION, R.array.projection_entries,
                R.array.projection_values);
        bindSummary(AppSettings.K_DEFAULT_STEREO, R.array.stereo_entries, R.array.stereo_values);
        bindSummary(AppSettings.K_SELECTION_MODE, R.array.selection_entries,
                R.array.selection_values);

        updateComfortSummary();
    }

    private void bindClick(String key, Runnable action) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                action.run();
                return true;
            });
        }
    }

    private void bindSummary(String key, int entriesRes, int valuesRes) {
        Preference preference = findPreference(key);
        if (!(preference instanceof ListPreference)) {
            return;
        }
        ListPreference list = (ListPreference) preference;
        list.setSummary(list.getEntry() == null ? "" : list.getEntry());
        list.setOnPreferenceChangeListener((p, newValue) -> {
            String[] entries = getResources().getStringArray(entriesRes);
            String[] values = getResources().getStringArray(valuesRes);
            String value = String.valueOf(newValue);
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(value)) {
                    p.setSummary(entries[i]);
                    break;
                }
            }
            return true;
        });
    }

    private void updateProfileSummary(ListPreference list, String value) {
        AppSettings settings = AppSettings.get(requireContext());
        ViewerProfile profile = settings.viewerProfile();
        list.setSummary(String.format(java.util.Locale.US,
                "%.0f deg FOV · %.0f mm lenses · k1 %.2f", profile.fovDeg,
                profile.lensSeparationMm, profile.distortionK1));
    }

    /** Applies the numeric defaults of a preset to the individual sliders. */
    private void applyViewerPreset(String id) {
        if ("custom".equals(id)) {
            return;
        }
        ViewerProfile preset = ViewerProfile.preset(id);
        AppSettings settings = AppSettings.get(requireContext());
        settings.setLensSeparationMm(preset.lensSeparationMm);
        settings.setFovDeg(preset.fovDeg);
        settings.setScreenLensDistanceMm(preset.screenLensDistanceMm);
        settings.setDistortionK1(preset.distortionK1);
        settings.setDistortionK2(preset.distortionK2);
        settings.setEyeOffsetXMm(preset.eyeOffsetXMm);
        settings.setEyeOffsetYMm(preset.eyeOffsetYMm);
        settings.setIpdMm(preset.ipdMm);
        refreshValues();
    }

    private void refreshValues() {
        AppSettings settings = AppSettings.get(requireContext());
        setSeekBarValue(AppSettings.K_LENS_SEPARATION,
                Math.round(settings.lensSeparationMm()));
        setSeekBarValue(AppSettings.K_FOV, Math.round(settings.fovDeg()));
        setSeekBarValue(AppSettings.K_SCREEN_LENS,
                Math.round(settings.screenLensDistanceMm()));
        setSeekBarValue(AppSettings.K_EYE_OFFSET_X, Math.round(settings.eyeOffsetXMm()));
        setSeekBarValue(AppSettings.K_EYE_OFFSET_Y, Math.round(settings.eyeOffsetYMm()));
        setSeekBarValue(AppSettings.K_DISTORTION_K1,
                Math.round(settings.distortionK1() * 100f));
        setSeekBarValue(AppSettings.K_DISTORTION_K2,
                Math.round(settings.distortionK2() * 100f));
        setSeekBarValue(AppSettings.K_IPD, Math.round(settings.ipdMm()));
    }

    private void setSeekBarValue(String key, int value) {
        Preference preference = findPreference(key);
        if (preference instanceof SeekBarPreference) {
            ((SeekBarPreference) preference).setValue(value);
        }
    }

    private void updateComfortSummary() {
        Preference preference = findPreference(AppSettings.K_COMFORT_FOV);
        if (preference instanceof SwitchPreferenceCompat) {
            SwitchPreferenceCompat toggle = (SwitchPreferenceCompat) preference;
            toggle.setSummary(toggle.isChecked()
                    ? "Comfort FOV is on (narrower view)"
                    : "Narrower field of view reduces motion discomfort");
        }
    }

    private void confirmClearResume() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_clear_resume)
                .setMessage("Forget stored playback positions?")
                .setPositiveButton(R.string.action_clear_confirm, (d, w) -> {
                    MediaStateDb.get(requireContext()).clearResume();
                    AppSettings.get(requireContext()).clearResumePositions();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void clearThumbnails() {
        ThumbLoader.get().clear();
        ToastMessage.show(requireContext(), "Thumbnail cache cleared");
    }

    private void showStorageInfo() {
        String message = DeviceCapabilities.storageSummary(requireContext());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_storage_info)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private void showBatteryInfo() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_battery_info)
                .setMessage(DeviceCapabilities.batteryAdvice(requireContext()))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    private void showLicenses() {
        startActivity(new Intent(requireContext(), LicenseActivity.class));
    }

    private void showVersion() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(DeviceCapabilities.versionInfo(requireContext()))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(AppSettings.FILE, android.content.Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        updateComfortSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().getSharedPreferences(AppSettings.FILE,
                android.content.Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @NonNull String key) {
        if (AppSettings.K_COMFORT_FOV.equals(key)) {
            updateComfortSummary();
        }
    }
}
