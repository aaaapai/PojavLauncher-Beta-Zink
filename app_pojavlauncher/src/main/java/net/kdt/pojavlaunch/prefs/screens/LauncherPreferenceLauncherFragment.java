package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.Preference;

import com.firefly.feature.UpdateLauncher;

import net.kdt.pojavlaunch.R;

public class LauncherPreferenceLauncherFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_launcher);

        Preference updatePreference = requirePreference("update_launcher");
        updatePreference.setOnPreferenceClickListener(preference -> {
            UpdateLauncher updateLauncher = new UpdateLauncher(getContext());
            updateLauncher.checkForUpdates(false);
            return true;
        });

    }

}
