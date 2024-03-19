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

import static de.flyingsnail.ipv6droid.android.signinginterface.IPv6DroidCertRequest.CERTPATH_KEY;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.List;

class MessageHandler extends Handler {
    final static String TAG = MessageHandler.class.getSimpleName();
    List<String> certPath;
    @Override
    public void handleMessage(@NonNull Message msg) {
        Log.i(TAG, "Received message from remote");
        Bundle answer = msg.getData();
        certPath = answer.getStringArrayList(CERTPATH_KEY);
        if (certPath == null) {
            Log.e(TAG, "Received message from signing app does not contain key " + CERTPATH_KEY);
        } else {
            Log.d(TAG, "Received cert path: " + certPath);
        }
    }
}

/**
 * This class implements a simple protocol client that can request a certificate from a
 * separate purchasing app. Certificates with specific attributes, together with their
 * corresponding private key on the local device, can be translated into tunnel specifications.
 */
class SigningServiceConnection implements ServiceConnection {
    private static final String TAG = SigningServiceConnection.class.getSimpleName();
    private boolean damaged;
    private Messenger serviceMessenger;


    private final Messenger myMessenger;
    private final MessageHandler myHandler;

    public SigningServiceConnection () {
        serviceMessenger = null;
        myHandler = new MessageHandler();
        myMessenger = new Messenger(myHandler);
        damaged = false;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        Log.i(TAG, "We have a connection, creating Messenger");
        serviceMessenger = new Messenger(iBinder);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        serviceMessenger = null;
        Log.i(TAG, "Lost connection");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        damaged = true;
        Log.w(TAG, "Certification app died: " + name);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        damaged = true;
        Log.w(TAG, "Certification app refused binding: " + name);
    }

    public boolean isFinished() {
        return serviceMessenger != null || damaged;
    }

    public void requestCertificate(final String signingRequest) throws IOException {
        final Message message = Message.obtain(null, 1, signingRequest);
        message.replyTo = myMessenger;
        myHandler.certPath = null; // we want to know if the answer to _this_ request has arrived
        try {
            serviceMessenger.send(message);
        } catch (RemoteException e) {
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

    /**
     * Get the certificate chain as received from the provider app. May be null, if the provider
     * app did not yet send a message back. May be empty, if the provider app does not have
     * an active "usage right" for this device.
     * @return a List&lt;String&gt; representing the PEM encoded certificates forming this
     * device's certificate chain; or null.
     */
    @Nullable public List<String> getCertPath() {
        return myHandler.certPath;
    }
}
