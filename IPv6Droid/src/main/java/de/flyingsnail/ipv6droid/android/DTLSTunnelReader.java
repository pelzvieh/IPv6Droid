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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.dtls.TransporterParams;

/**
 * Reads available DTLS tunnels based on configuration.
 */
public class DTLSTunnelReader implements TunnelReader {

    private final static String TAG = DTLSTunnelReader.class.getSimpleName();

    private final TransporterParams params;

    /**
     * Initialise the TunnelReader. In contrast to TicTunnel implementation, all work is already
     * done when successfully initialized from the App configuration.
     * @param context the Android Context, used to access shared preferences.
     * @throws ConnectionFailedException in case of parse errors of the configuration.
     */
    public DTLSTunnelReader (Context context) throws ConnectionFailedException {

        // load DTLS Configuration
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String keyConfig = myPreferences.getString("dtls_key", "");
        String concattedCerts = myPreferences.getString("dtls_certs", "");

        if (keyConfig.isEmpty() || concattedCerts.isEmpty())
            throw new ConnectionFailedException("No DTLS credentials configured", null);
        String[] certStrings = concattedCerts.split("-----BEGIN CERTIFICATE-----");
        List<String> certConfig = new ArrayList<>(certStrings.length);
        for (String certString: certStrings) {
            certString = certString.trim();
            if (!certString.isEmpty()) {
                certConfig.add("-----BEGIN CERTIFICATE-----\n" + certString);
            }
        }
        params = new TransporterParams();
        // build params and perform parsing
        try {
            params.setCertChainEncoded(certConfig);
            params.setPrivateKeyEncoded(keyConfig);
        } catch (IllegalArgumentException illegal) {
            throw new ConnectionFailedException("Invalid certificate configuration", illegal);
        }
        params.setHeartbeatInterval(10*60*1000); // 10 Minutes
        params.setMtu(1300);

        Log.i(TAG, "DTLSTunnelReader initialized");
    }

    @Override
    public List<? extends TunnelSpec> queryTunnels() {
        List<TunnelSpec> myTunnels = new ArrayList<>(1);
        myTunnels.add(params);
        return myTunnels;
    }
}
