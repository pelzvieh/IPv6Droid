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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;

/**
 * This provides a callback function, asking IPv6DroidVpnService to start the VPN tunnel when it receives a boot completed
 * message.
 */
public class BootReceiver extends BroadcastReceiver {
    private final String TAG = BootReceiver.class.getName();
    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "BootReceiver received intent: " + intent.getAction());
        SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (myPrefs.getBoolean("autostart", false)) {
            TunnelPersisting tunnelPersisting = new TunnelPersistingFile(context);
            try {
                Tunnels tunnels = tunnelPersisting.readTunnels();
                if (tunnels.isTunnelActive()) {
                    Log.i(TAG, "Starting last used tunnel \"on boot\"");
                    Log.d(TAG, "Preparing to use VpnService");
                    // Start system-managed intent for VPN
                    Intent systemVpnIntent = VpnService.prepare(context);
                    if (systemVpnIntent == null) {
                        Log.d(TAG, "No explicit user consent required - going ahead!");
                        Intent i = new Intent(context, IPv6DroidVpnService.class);
                        // Android's Parcel system doesn't handle subclasses well, so...
                        i.putExtra(IPv6DroidVpnService.EXTRA_CACHED_TUNNELS, tunnels.getAndroidSerializable());

                        if (Build.VERSION.SDK_INT >= 26) {
                            context.startForegroundService(i);
                        } else {
                            context.startService(i);
                        }
                        Log.d(TAG, "Sent service start intent");
                    } else
                        Log.i(TAG, "User must consent to starting this VPN - no autostart possible");

                } else {
                    Log.i(TAG, "Autostart \"on boot\" is configured, but no tunnel persisted to be working.");
                }

            } catch (IOException e) {
                Log.e(TAG, "Unable to load list of persisted tunnels - no autostart possible", e);
            }
        }
    }
}
