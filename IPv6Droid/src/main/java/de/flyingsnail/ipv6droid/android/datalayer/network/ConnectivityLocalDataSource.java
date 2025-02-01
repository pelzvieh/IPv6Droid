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

package de.flyingsnail.ipv6droid.android.datalayer.network;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.datalayer.network.event.Event;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventAvailable;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventBlockingChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventCapabilitiesChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnected;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnecting;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventLinkPropertiesChanged;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;


/**
 * The data source for network information from the Android connectivity manager system service.
 */
public class ConnectivityLocalDataSource  {
    final static Logger logger = Logger.getLogger(ConnectivityLocalDataSource.class.getName());

    private final Observable<Event> connectivityEvent;

    public Observable<Event> getConnectivityEventObservable() {
        return connectivityEvent;
    }

    private class DataSourceNetworkCallback extends ConnectivityManager.NetworkCallback
        implements AutoCloseable
    {
        private final @NonNull ObservableEmitter<Event> emitter;
        DataSourceNetworkCallback(@NonNull ObservableEmitter<Event> emitter) {
            super();
            this.emitter = emitter;
        }

        @Override
        public void onAvailable(final @NonNull Network network) {
            logger.info(String.format("New network %d became available", network.getNetworkHandle()));
            emitter.onNext(new EventAvailable(network));
        }

        @Override
        public void onLinkPropertiesChanged(final @NonNull Network network,
                                            final @NonNull LinkProperties linkProperties) {
            logger.info(String.format("Link properties changed for network %d", network.getNetworkHandle()));
            emitter.onNext(new EventLinkPropertiesChanged(network, linkProperties));
        }

        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
            logger.info(String.format("Network %d is %s", network.getNetworkHandle(),
                    blocked?"blocked":"unblocked"));
            emitter.onNext(new EventBlockingChanged(network, blocked));
        }

        @Override
        public void onLosing(@NonNull Network network, int maxMsToLive) {
            logger.info(String.format("Network %d is going down in %d ms",
                    network.getNetworkHandle(), maxMsToLive));
            emitter.onNext(new EventDisconnecting(network, maxMsToLive));
        }

        @Override
        public void onLost(Network network) {
            logger.info(String.format("Network %d lost connection", network.getNetworkHandle()));
            emitter.onNext(new EventDisconnected(network));
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            logger.info(String.format("Network capabilites changed for network %d", network.getNetworkHandle()));
            emitter.onNext(new EventCapabilitiesChanged(network, networkCapabilities));
        }

        @Override
        public void onUnavailable() {
            logger.info("No networks available, restarting");
            startCallback(this);
        }

        @Override
        public void close() {
            emitter.onComplete();
        }
    }

    private final ConnectivityManager connectivityManager;

    public ConnectivityLocalDataSource(final ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
        //this.networkCallback = new DataSourceNetworkCallback();
        connectivityEvent = Observable.create(emitter -> {
                    logger.info("Emitter is up");
                    DataSourceNetworkCallback callback = new DataSourceNetworkCallback(emitter);
                    startCallback(callback);
                    emitter.setCancellable(()->connectivityManager.unregisterNetworkCallback(callback));
                });
        logger.info("Constructed");

    }

    /**
     * Starts creating ConnectivityManager callbacks by "requesting" an internet capable network.
     * @param callback a NetworkCallback that receives the ConnectivityManager events.
     */
    private void startCallback(ConnectivityManager.NetworkCallback callback) {
        logger.fine("Starting new network request");
        NetworkRequest.Builder builder = new NetworkRequest.Builder().
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        NetworkRequest request = builder.build();

        connectivityManager.registerNetworkCallback(request, callback);
    }
}
