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

package de.flyingsnail.ipv6droid.simplecert4ipv6droid;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This service implements the certificate request protocol between
 * IPv6Droid core app and this SimpleCertification compagnion app.
 */
public class SimpleCertificationService extends Service {
    final static Logger logger = AndroidLoggingHandler.getLogger(SimpleCertificationService.class);

    /**
     * A String used to identify the Intent action when binding to a certificate issuing service.
     * TODO this should be a single constant shared via interface defining classes.
     */
    public static final String ACTION_CSR = "de.flyingsnail.ipv6droid.REQUEST_TUNNEL";

    /**
     * A String giving the key of a Bundle, where to find the List&lt;String&gt; with the elements of
     * the cert path.
     * TODO this should be a single constant shared via interface defining classes.
     */
    public static final String CERTPATH_KEY="CERT";

    /**
     * A String used to identify the Intent action when UI binding to this service's state.
     */
    public static final String ACTION_UI = Objects.requireNonNull(SimpleCertificationService.class.getPackage()).getName() + "BIND_UI";

    /**
     * A Messenger to receive messages from a bound service client.
     */
    private final Messenger messenger;
    /**
     * A Messenger to send answers to.
     */
    private Messenger replyMessenger = null;

    private String csr;

    private ArrayList<String> cert;

    public SimpleCertificationService() {
        messenger = new Messenger(new IncomingHandler(this));
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionCertRequest(String csr, Messenger replyTo) {
        this.csr = csr;
        this.cert = null;
        this.replyMessenger = replyTo;
        Intent csrActionIntent = new Intent(this, CertSetup.class);
        csrActionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(csrActionIntent);
    }

    /**
     * Query the csr string that was posted by the core app.
     * @return a String representing a PEM encoded CSR.
     */
    public String getCsr() {
        return csr;
    }

    /**
     * Set the certificate signed for the previously queried CSR.
     * @param cert a String representing a PEM encoded X509 certificate.
     */
    public void setCertChain(List<String> cert) {
        logger.info("Received cert path");
        this.cert = new ArrayList<>(cert.size());
        this.cert.addAll(cert);
        if (replyMessenger != null) {
            postCertificate();
        }
    }

    /**
     * Send the certificate string as a Bundle over to the main app.
     */
    private void postCertificate() {
        Bundle certBundle = new Bundle(cert.size());
        certBundle.putStringArrayList(CERTPATH_KEY, cert);
        Message message = Message.obtain();
        message.setData(certBundle);
        try {
            replyMessenger.send(message);
            logger.info("Sent cert path to bound external services");
        } catch (RemoteException e) {
            Toast.makeText(getApplicationContext(), "Unable to send message with cert path", Toast.LENGTH_LONG).show();
            logger.log(Level.WARNING, "Unable to send message with cert path", e);
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends Handler {
        private static final int MSG_WHAT_CERT_REQUEST = 1;
        private final SimpleCertificationService simpleCertificationService;

        IncomingHandler(SimpleCertificationService context) {
            super(Looper.getMainLooper());
            simpleCertificationService = context;
        }

        @Override
        public void handleMessage(Message msg) {
            logger.info("Received message");
            switch (msg.what) {
                case MSG_WHAT_CERT_REQUEST:
                    String csr = msg.getData().getString("csr");
                    if (Objects.nonNull(csr)) {
                        logger.info("Handling received CSR: " + csr);
                        simpleCertificationService.handleActionCertRequest(csr, msg.replyTo);
                    } else {
                        logger.log(Level.WARNING, "Received message with illegal data content");
                    }
                    break;
                default:
                    logger.warning("Message is unkown: " + msg.what);
                    super.handleMessage(msg);
            }
        }
    }

    public class LocalBinder extends Binder {
        SimpleCertificationService getService() {
            return SimpleCertificationService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        logger.info("Received binding request for " + intent.getAction());
        if (ACTION_CSR.equals(intent.getAction())) {
            return messenger.getBinder();
        } else if (ACTION_UI.equals(intent.getAction())) {
            return new LocalBinder();
        } else {
            logger.log(Level.WARNING, "Unsupported action: " + intent.getAction());
            return null;
        }
    }
}