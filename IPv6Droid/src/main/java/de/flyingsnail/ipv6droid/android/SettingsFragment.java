/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
 *  *
 *  *     This program is free software; you can redistribute it and/or modify
 *  *     it under the terms of the GNU General Public License as published by
 *  *     the Free Software Foundation; either version 2 of the License, or
 *  *     (at your option) any later version.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU General Public License along
 *  *     with this program; if not, write to the Free Software Foundation, Inc.,
 *  *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *  *
 *  * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 *
 *
 */

package de.flyingsnail.ipv6droid.android;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import de.flyingsnail.ipv6droid.R;

/**
 * A {@link PreferenceFragmentCompat} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // set special input types
        final EditTextPreference username = findPreference("tic_username");
        username.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                        editText.setSingleLine(true);
                        editText.setSelectAllOnFocus(true);
                    }
                }
        );

        final EditTextPreference password = findPreference("tic_password");
        password.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        editText.setSingleLine(true);
                        editText.setSelectAllOnFocus(true);
                    }
                }
        );

        final EditTextPreference host = findPreference("tic_host");
        host.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                        editText.setSingleLine(true);
                        editText.setSelectAllOnFocus(true);
                    }
                }
        );

        final EditTextPreference routesSpecific = findPreference("routes_specific");
        routesSpecific.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
                        editText.setSingleLine(true);
                        editText.setSelectAllOnFocus(false);
                    }
                }
        );

        final EditTextPreference dtlsKey = findPreference("dtls_key");
        dtlsKey.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
                        editText.setSingleLine(false);
                        editText.setSelectAllOnFocus(false);
                    }
                }
        );
        final EditTextPreference dtlsCerts = findPreference("dtls_certs");
        dtlsCerts.setOnBindEditTextListener(
                new EditTextPreference.OnBindEditTextListener() {
                    @Override
                    public void onBindEditText(@NonNull EditText editText) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
                        editText.setSingleLine(false);
                        editText.setSelectAllOnFocus(false);
                    }
                }
        );
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceSummaryToValueListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    Preference preference = findPreference(key);
                    if (preference == null)
                        return;
                    if (preference instanceof TwoStatePreference)
                        return;
                    if (preference instanceof EditTextPreference && key.contains("password")) {
                        String value = sharedPreferences.getString(key, "");
                        if (!value.isEmpty())
                            preference.setSummary (R.string.password_set);
                        else
                            preference.setSummary (R.string.password_unset);
                    } else if (preference instanceof EditTextPreference){
                        preference.setSummary(sharedPreferences.getString(key, ""));
                    }
                }
            };

    /**
     * Fragment is shown to the user.
     */
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = getPreferenceScreen().getSharedPreferences();
        for (String key: preferences.getAll().keySet()) {
            preferenceSummaryToValueListener.onSharedPreferenceChanged(preferences, key);
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceSummaryToValueListener);
        Preference autoStart = findPreference("autostart");
        if (autoStart != null)
            autoStart.setEnabled(Build.VERSION.SDK_INT < Build.VERSION_CODES.N);
        Preference alwaysOn = findPreference("always-on-vpn");
        if (alwaysOn != null)
            alwaysOn.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
    }

    /**
     * Fragment temporarily hidden from the user.
     */
    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(preferenceSummaryToValueListener);
    }

}
