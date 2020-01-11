/*
 *
 *  * Copyright (c) 2017 Dr. Andreas Feldner.
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
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.ayiya.Tic;
import de.flyingsnail.ipv6droid.transport.ayiya.TicConfiguration;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;
import de.flyingsnail.ipv6droid.transport.TunnelNotAcceptedException;

import static de.flyingsnail.ipv6droid.android.googlesubscription.Subscription.GOOGLESUBSCRIPTION;

/**
 * Read tunnel information via the TIC protocol. Return true if anything changed on the current
 * tunnel.
 */
public class TicTunnelReader implements TunnelReader {
    private final static String TAG = TicTunnelReader.class.getSimpleName();
    private final Context context;
    private final TicConfiguration ticConfiguration;

    /**
     * The constructor.
     * @param context The Context that originated creation of this helper class
     */
    public TicTunnelReader (final Context context) throws ConnectionFailedException {
        this.context = context;
        // load configuration
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        ticConfiguration = loadTicConfiguration(myPreferences);
        // todo implement proper handling of googlesubscription re-check
        if (GOOGLESUBSCRIPTION.equals(ticConfiguration.getServer())) {
            throw new ConnectionFailedException("Google subscription managed tunnels cannot be verified by TIC", null);
        }
    }

    private TicConfiguration loadTicConfiguration(SharedPreferences myPreferences) {
        return new TicConfiguration(myPreferences.getString("tic_username", ""),
                myPreferences.getString("tic_password", ""),
                myPreferences.getString("tic_host", ""));
    }


    /**
     * Read tunnel information via the TIC protocol. Return true if anything changed on the current
     * tunnel.
     * @return true if something changed
     * @throws ConnectionFailedException if some permanent problem exists with TIC and the current config
     * @throws IOException if some (hopefully transient) technical problem came up.
     */
    public List<TicTunnel> queryTunnels() throws ConnectionFailedException, IOException {
        // gather some client information for the nosy TIC
        Tic.ContextInfo contextInfo;
        try {
            contextInfo = new Tic.ContextInfo(
                    context.getPackageName(),
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName,
                    "Android",
                    Build.VERSION.RELEASE);
        } catch (PackageManager.NameNotFoundException e) {
            throw new ConnectionFailedException("Unable to read version name", e);
        }

        // Initialize new Tic object
        // @todo extract as public method that allows the Android Activity to actively query tunnels
        // @todo generalize to cope with Google Subscriptions token alternatively to ticConfig
        Tic tic = new Tic(ticConfiguration, contextInfo);
        try {
            tic.connect();
            List<String> tunnelIds = tic.listTunnels();
            List<TicTunnel> availableTunnels = expandSuitables(tunnelIds, tic);
            return availableTunnels;
        } finally {
            tic.close();
        }
    }

    /**
     * Return the tunnel descriptions from available tunnels that are suitable for this VpnThread.
     * @param tunnelIds the List of Strings containing tunnel IDs each
     * @param tic the connected Tic object
     * @return a List&lt;TicTunnel&gt; containing suitable tunnel specifications from the ID list
     * @throws IOException in case of a communication problem
     */
    private @NonNull
    List<TicTunnel> expandSuitables(@NonNull List<String> tunnelIds, @NonNull Tic tic) throws IOException {
        List<TicTunnel> retval = new ArrayList<TicTunnel>(tunnelIds.size());
        for (String id: tunnelIds) {
            TicTunnel desc;
            try {
                desc = tic.describeTunnel(id);
            } catch (TunnelNotAcceptedException e) {
                Log.e(TAG, "Tunnel not accepted", e);
                continue;
            }
            if (desc.isValid() && desc.isEnabled() && "ayiya".equals(desc.getType())){
                Log.i(TAG, "Tunnel " + id + " is suitable");
                retval.add(desc);
            }
        }
        return retval;
    }
}
