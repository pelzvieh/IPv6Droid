/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ObservableList;

import java.io.IOException;

import de.flyingsnail.ipv6droid.android.dtlsrequest.CertificateToTunnel;

/**
 * A manager for the CSR/certificate exchange protocol with a partner app.
 *
 * This manager is enumerating suitable partner apps, binding and handling
 * to their service on behalf of the supplied Context.
 */
public class CSRIntentManager implements AutoCloseable {
    /** A String used to identify the Intent action when binding to a certificate issuing service */
    public static final String ACTION = "de.flyingsnail.ipv6droid.REQUEST_TUNNEL";
    final private static String TAG = CSRIntentManager.class.getSimpleName();
    private final CertificateToTunnel certHelper;
    private final Context context;
    private final ObservableList<String> supplierPackageReceiver;
    @Nullable private SigningServiceConnection serviceConnection;

    /**
     * Constructor.
     * @param context the Android Context (Activity or Service) that we're acting for
     * @param supplierPackageReceiver the ObservableList&lt;String&gt; that will receive packages
     *                                offering the required service.
     * @throws IOException in case of failure to bind to the supplier app's service.
     */
    public CSRIntentManager(@NonNull final Context context,
                            @NonNull final ObservableList<String> supplierPackageReceiver)  {
        this.context = context;
        certHelper = new CertificateToTunnel();
        this.supplierPackageReceiver = supplierPackageReceiver;
        supplierPackageReceiver.clear();
        new Thread(this::querySupplierPackages, "CertPath supplier enumerator").start();
    }

    private void querySupplierPackages() {
        // query matching services and explicitly set package name to one of them
        final Intent queryCertificateIntent = new Intent(ACTION);
        for (ResolveInfo resolveInfo: context.getPackageManager().queryIntentServices(queryCertificateIntent, 0) ) {
            Log.i(TAG, "bind candidate " + resolveInfo);
            if (resolveInfo.serviceInfo != null) {
                Log.d(TAG, " - bind candidate has service info with packageName" + resolveInfo.serviceInfo.packageName);
                supplierPackageReceiver.add(resolveInfo.serviceInfo.packageName);
            }
        }
    }

    /**
     * Binds to the certificate service of the given package (= app) and requests
     * a certificate.
     *
     * @param supplierPackage a String giving the package name of the service to use. This
     *                        identifies the providing app.
     * @param certPathReceiver an ObservableList&lt;String&gt; to which a received cert path will be
     *                         written.
     * @throws IOException in case of failed binding.
     */
    public void requestCertificate(@NonNull final String supplierPackage,
                                   @NonNull final ObservableList<String> certPathReceiver) throws IOException {

        Intent queryCertificateIntent = new Intent(ACTION);
        queryCertificateIntent.setPackage(supplierPackage);
        synchronized (this) {
            if (serviceConnection != null) {
                throw new IllegalStateException("A CSR request has already been issued with this manager");
            }
            serviceConnection = new SigningServiceConnection(certPathReceiver);
        }

        if (!context.bindService(queryCertificateIntent, serviceConnection,
                Context.BIND_AUTO_CREATE|Context.BIND_ALLOW_ACTIVITY_STARTS)) {
            context.unbindService(serviceConnection);
            serviceConnection = null;
            throw new IOException("Cannot bind to certificate issuer");
        }
        // send CSR to service
        serviceConnection.requestCertificate(certHelper.getCsr());
    }


    /**
     * Cleanup connections and resources.
     */
    public void close() {
        final ServiceConnection toDestroy = serviceConnection;
        if (toDestroy != null) {
            synchronized (toDestroy) {
                serviceConnection = null;
                context.unbindService(toDestroy);
            }
        }
    }

    /**
     * Query the instance of CertificateToTunnel that was used to issue the CSR.
     * This is might be required to create the TunnelSpec instance from the cert chain
     * received.
     * @return the CertificateToTunnel instance that was used to create the CSR.
     */
    public CertificateToTunnel getCertHelper() {
        return certHelper;
    }
}
