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

import static de.flyingsnail.ipv6droid.android.signinginterface.SigningServiceConnection.CERTPATH_KEY;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class MessageHandler extends Handler {
    final static Logger logger = Logger.getLogger(MessageHandler.class.getName());
    final ObservableList<String> certPathReceiver;

    public MessageHandler(@NonNull Looper looper, @NonNull ObservableList<String> receiver) {
        super(looper);
        this.certPathReceiver = receiver;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        logger.info("Received message from remote");
        Bundle answer = msg.getData();
        final List<String> certPath = answer.getStringArrayList(CERTPATH_KEY);
        if (certPath == null) {
            logger.log(Level.WARNING, "Received message from signing app does not contain key " + CERTPATH_KEY);
        } else {
            logger.fine("Received cert path: " + certPath);
            certPathReceiver.clear();
            certPathReceiver.addAll(certPath);
        }
    }
}

/**
 * This class implements a simple protocol client that can request a certificate from a
 * separate purchasing app. Certificates with specific attributes, together with their
 * corresponding private key on the local device, can be translated into tunnel specifications.
 */
class SigningServiceConnection implements ServiceConnection {
    /**
     * A String giving the key of a Bundle, where to find the List&lt;String&gt; with the elements of
     * the cert path.
     */
    public static final String CERTPATH_KEY="CERT";
    private static final Logger logger = Logger.getLogger(SigningServiceConnection.class.getName());
    private boolean damaged;
    private Messenger serviceMessenger;

    private final Messenger myMessenger;
    private final MessageHandler myHandler;
    private String queuedSigningRequest;

    /**
     *
     * @param certPathReceiver an ObservableList&lt;String&gt; that will be set to the
     *                         cert path when it is received from the supplier app.
     */
    public SigningServiceConnection (@NonNull ObservableList<String> certPathReceiver) {
        serviceMessenger = null;
        myHandler = new MessageHandler(Looper.getMainLooper(), certPathReceiver);
        myMessenger = new Messenger(myHandler);
        queuedSigningRequest = null;
        damaged = false;
    }

    @Override
    public synchronized void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        logger.info("We have a connection, creating Messenger");
        serviceMessenger = new Messenger(iBinder);
        if (queuedSigningRequest != null) {
            try {
                requestCertificate(queuedSigningRequest);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to post queued CSR", e);
            }
        }
    }

    @Override
    public synchronized void onServiceDisconnected(ComponentName componentName) {
        serviceMessenger = null;
        this.notifyAll();
        logger.info("Lost connection");
    }

    @Override
    public synchronized void onBindingDied(ComponentName name) {
        damaged = true;
        this.notifyAll();
        logger.warning("Certification app died: " + name);
    }

    @Override
    public synchronized void onNullBinding(ComponentName name) {
        damaged = true;
        this.notifyAll();
        logger.warning("Certification app refused binding: " + name);
    }

    public void requestCertificate(final String signingRequest) throws IOException {
        Messenger localMessenger = serviceMessenger;
        if (localMessenger == null) {
            queuedSigningRequest = signingRequest;
            return;
        }

        final Message message = Message.obtain(null, 1);
        final Bundle data = new Bundle();
        data.putString("csr", signingRequest);
        message.setData(data);
        message.replyTo = myMessenger;
        try {
            localMessenger.send(message);
        } catch (RemoteException | RuntimeException e) {
            throw new IOException("Failed to send message to remote app", e);
        }
    }

    /**
     * Indicate if this ServiceConnection is damaged, i. e. will not receive any certificates in
     * future.
     * @return a boolean indicating if this ServiceConnection is damaged.
     */
    public boolean isDamaged() {
        return damaged;
    }
}
