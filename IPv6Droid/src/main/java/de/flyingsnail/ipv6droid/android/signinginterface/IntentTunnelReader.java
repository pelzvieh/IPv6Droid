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
import android.content.pm.ResolveInfo;
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
    /** A String used to identify the Intent action when binding to a certificate issuing service */
    public static final String ACTION = "de.flyingsnail.ipv6droid.REQUEST_TUNNEL";
    final private static String TAG = IntentTunnelReader.class.getSimpleName();
    private final CertificateToTunnel certHelper;
    private final Context context;
    private SigningServiceConnection serviceConnection;
    private Thread queryThread;

    public IntentTunnelReader(final Context context) throws IOException {
        this.context = context;
        serviceConnection = new SigningServiceConnection();
        certHelper = new CertificateToTunnel();
        Intent queryCertificateIntent = new Intent(ACTION);
        // query matching services and explicitly set package name to one of them
        // todo this needs refactoring into user-selectable list; here using the selected package
        for (ResolveInfo resolveInfo: context.getPackageManager().queryIntentServices(queryCertificateIntent, 0) ) {
            Log.i(TAG, "bind candidate " + resolveInfo);
            if (resolveInfo.serviceInfo != null) {
                queryCertificateIntent.setPackage(resolveInfo.serviceInfo.packageName);
            }
        }
        if (!context.bindService(queryCertificateIntent, serviceConnection, Context.BIND_AUTO_CREATE|Context.BIND_ALLOW_ACTIVITY_STARTS)) {
            context.unbindService(serviceConnection);
            throw new IOException("Cannot bind to certificate issuer - companion app seems to be missing");
        }
        // send CSR to service
        serviceConnection.requestCertificate(certHelper.getCsr());
    }


    /**
     * Query tunnels from external application by intent
     * @return List&lt;TicTunnel&gt; with all tunnel definition associated with the Google acccount
     * @throws ConnectionFailedException in case of a permanent problem, e.g. no subscription active
     * @throws IOException in case of a temporary problem to query.
     */
    @Override
    public synchronized List<TunnelSpec> queryTunnels() throws ConnectionFailedException, IOException {
        List<String> certPath = null;
        do {
            SigningServiceConnection localServiceConnection = serviceConnection;
            if (localServiceConnection == null) {
                throw new IOException("lost serviceConnection while querying tunnels");
            }
            if (localServiceConnection.isDamaged()) {
                Log.i(TAG, "First attempt to query certificates lead to broken connection");
                serviceConnection = new SigningServiceConnection();
                serviceConnection.requestCertificate(certHelper.getCsr());
                localServiceConnection = serviceConnection;
            }
            synchronized (localServiceConnection) {
                certPath = localServiceConnection.getCertPath();
                if (certPath == null) {
                    Log.d(TAG, "No certificate available yet");
                    try {
                        localServiceConnection.wait();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Wait on service connection interrupted, no longer trying to read tunnels");
                        return new ArrayList<>(0);
                    }
                }
            }
        } while (certPath == null);
        if (certPath.isEmpty()) {
            // empty array indicates that there's positively no tunnel
            return new ArrayList<>(0);
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
    public void close() {
        final ServiceConnection toDestroy = serviceConnection;
        if (toDestroy != null) {
            synchronized (toDestroy) {
                serviceConnection = null;
                context.unbindService(toDestroy);
                toDestroy.notifyAll();
            }
        }
    }
}
