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
import android.util.Log;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import java.io.IOException;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.dtlsrequest.AndroidBackedKeyPair;

/**
 * A {@link PreferenceFragmentCompat} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private final static String TAG = SettingsFragment.class.getName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // set special input types
        final EditTextPreference username = findPreference("tic_username");
        username.setOnBindEditTextListener(
                editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
                    editText.setSingleLine(true);
                    editText.setSelectAllOnFocus(true);
                }
        );

        final EditTextPreference password = findPreference("tic_password");
        password.setOnBindEditTextListener(
                editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editText.setSingleLine(true);
                    editText.setSelectAllOnFocus(true);
                }
        );

        final EditTextPreference host = findPreference("tic_host");
        host.setOnBindEditTextListener(
                editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
                    editText.setSingleLine(true);
                    editText.setSelectAllOnFocus(true);
                }
        );

        final EditTextPreference routesSpecific = findPreference("routes_specific");
        routesSpecific.setOnBindEditTextListener(
                editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
                    editText.setSingleLine(true);
                    editText.setSelectAllOnFocus(false);
                }
        );

        final ListPreference dtlsKeyAlias = findPreference("dtls_key_alias");
        CharSequence[] keys = new CharSequence[0];
        try {
            keys = AndroidBackedKeyPair.listAliases().toArray(keys);
        } catch (IOException e) {
            Log.i(TAG, "No key pair in Android Key Store");
        }
        dtlsKeyAlias.setEntries(keys);
        dtlsKeyAlias.setEntryValues(keys);

        final EditTextPreference dtlsCerts = findPreference("dtls_certs");
        dtlsCerts.setOnBindEditTextListener(
                editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE);
                    editText.setSingleLine(false);
                    editText.setSelectAllOnFocus(false);
                }
        );
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceSummaryToValueListener =
            (sharedPreferences, key) -> {
                Preference preference = findPreference(key);
                if (preference == null)
                    return;
                if (preference instanceof TwoStatePreference)
                    return;
                if ("dtls_key_alias".equals(key)) {
                    preference.setSummary(sharedPreferences.getString(key, ""));
                } else if (preference instanceof EditTextPreference && key.contains("password")) {
                    String value = sharedPreferences.getString(key, "");
                    if (!value.isEmpty())
                        preference.setSummary (R.string.password_set);
                    else
                        preference.setSummary (R.string.password_unset);
                } else if (preference instanceof EditTextPreference) {
                    preference.setSummary(sharedPreferences.getString(key, ""));
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
