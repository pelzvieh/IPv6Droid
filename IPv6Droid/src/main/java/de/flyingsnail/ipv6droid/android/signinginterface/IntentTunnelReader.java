/*
 *
 *  * Copyright (c) 2024 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.signinginterface;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.android.TunnelReader;
import de.flyingsnail.ipv6droid.android.dtlsrequest.CertificateToTunnel;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * A TunnelReader that reads tunnels from a partner app.
 */
public class IntentTunnelReader implements TunnelReader {
    /** A String used to identify the Intent action when binding to a certificat issuing service */
    public static final String ACTION = "de.flyingsnail.ipv6droid.REQUEST_TUNNEL";
    final private static String TAG = IntentTunnelReader.class.getSimpleName();
    private final CertificateToTunnel certHelper;
    private final Context context;
    private SigningServiceConnection serviceConnection;

    public IntentTunnelReader(final Context context) {
        this.context = context;
        serviceConnection = new SigningServiceConnection();
        certHelper = new CertificateToTunnel();
        Intent queryCertificateIntent = new Intent(ACTION);
        if (!context.bindService(queryCertificateIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("Cannot bind to certificate issuer - companion app seems to be missing");
        }
    }

    /**
     * Query tunnels from external application by intent
     * @return List&lt;TicTunnel&gt; with all tunnel definition associated with the Google acccount
     * @throws ConnectionFailedException in case of a permanent problem, e.g. no subscription active
     * @throws IOException in case of a temporary problem to query.
     */
    @Override
    public synchronized List<TunnelSpec> queryTunnels() throws ConnectionFailedException, IOException {
        serviceConnection.requestCertificate(certHelper.getCsr());
        List<String> certPath = serviceConnection.getCertPath();
        if (certPath == null) {
            Log.d(TAG, "No certificate available yet");
            try {
                if (serviceConnection.isDamaged()) {
                    Log.i(TAG, "First attempt to query certificates lead to broken connection");
                    serviceConnection = new SigningServiceConnection();
                    serviceConnection.requestCertificate(certHelper.getCsr());
                }
                wait(10000L);
                certPath = serviceConnection.getCertPath();
            } catch (InterruptedException e) {
                throw new IOException (e);
            }
        }
        if (certPath == null) {
            throw new IOException("Timeout, subscription query not finalised after 10 secs.");
        }
        if (certPath.isEmpty()) {
            throw new ConnectionFailedException("No active certificates available for this device", null);
        }

        TunnelSpec spec = certHelper.createTunnelSpec(certPath);
        Log.d(TAG, "Success creating a tunnel spec: " + spec);
        List<TunnelSpec> retVal = new ArrayList<>(1);
        retVal.add(spec);
        return retVal;
    }

    /**
     * Cleanup connections and resources.
     */
    @Override
    public void destroy() {
        final ServiceConnection toDestroy = serviceConnection;
        if (toDestroy != null) {
            serviceConnection = null;
            context.unbindService(toDestroy);
        }
    }
}
