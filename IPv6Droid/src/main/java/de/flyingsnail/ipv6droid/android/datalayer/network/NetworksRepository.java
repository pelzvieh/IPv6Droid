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

import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.datalayer.network.event.Event;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventAvailable;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventBlockingChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventCapabilitiesChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnected;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnecting;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventLinkPropertiesChanged;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observables.ConnectableObservable;

/**
 * This class provides the single source of truth concerning the device's
 * networks and their respective state. It is consuming a number of sources
 * for network enumeration and to actively retreive changes.
 * <p>
 * This is a Repository class in the Data Layer according to the
 * <a href="https://developer.android.com/topic/architecture/data-layer">Android developer reference
 * architecture</a>.
 */
public class NetworksRepository {
    private static final Logger logger = Logger.getLogger(NetworksRepository.class.getName());
    private @NonNull ConnectableObservable<NetworksInformationContainer> networksProperties;
    private @NonNull Observable<Network> currentNetworkObservable;
    private @NonNull Observable<Long> onlineNetwork;
    private @NonNull Observable<Boolean> deviceOnline;

    @NonNull
    public Observable<NetworksInformationContainer> getNetworksProperties() {
        logger.fine("Returning networksProperties observable");
        return networksProperties;
    }

    @NonNull
    public Observable<Network> getCurrentNetworkObservable() {
        logger.fine("Returning currentNetworkObservable observable");
        return currentNetworkObservable;
    }

    @NonNull
    public Observable<Long> getOnlineNetwork() {
        logger.fine("Returning onlineNetwork observable");
        return onlineNetwork;
    }

    @NonNull
    public Observable<Boolean> getDeviceOnline() {
        logger.fine("Returning deviceOnline observable");
        return deviceOnline;
    }

    /**
     * Information container for the NetworkProperty observable.
     */
    public static class NetworksInformationContainer {
        public static long NONE = -1;
        /**
         * The id of the Network that was subject of the last connectivity event.
         */
        long id;

        /**
         * Current properties of all known networks.
         */
        Map<Long, NetworkProperty> networkProperties;

        public NetworksInformationContainer(
                @NonNull Long id, @NonNull Map<Long, NetworkProperty> networkProperties) {
            this.id = id;
            this.networkProperties = networkProperties;
        }

        public NetworksInformationContainer() {
            this.id = NONE;
            this.networkProperties = new HashMap<>(3);
        }

        public Map<Long, NetworkProperty> getNetworkProperties() {
            if (id == NONE) {
                throw new IllegalStateException("Information container empty");
            }
            return networkProperties;
        }

        public long getId() {
            return id;
        }
    }

    private final NetworkLocalDataSource networkLocalDataSource;
    private final ConnectivityLocalDataSource connectivityLocalDataSource;

    /**
     * Constructor with dependencies to inject.
     * @param networkLocalDataSource the NetworkLocalDataSource to use.
     * @param connectivityLocalDataSource the ConnectivityLocalDataSource to use.
     */
    NetworksRepository (final NetworkLocalDataSource networkLocalDataSource,
                        final ConnectivityLocalDataSource connectivityLocalDataSource) {
        this.networkLocalDataSource = networkLocalDataSource;
        this.connectivityLocalDataSource = connectivityLocalDataSource;
        startConnectivityListening();
        logger.info("Constructed");
    }

    /**
     * Propagate a pair of network id and map with network properties for each known id
     * @param networks the NetworksInformationContainer about all networks
     * @param e the Event to handle
     * @return the NetworksInformationContainer about all networks, considering the last event.
     */
    private @NonNull
    NetworksInformationContainer applyNetworksEvent(
            @NonNull NetworksInformationContainer networks,
            @NonNull Event e) {
        Long id = e.getAffectedNetwork().getNetworkHandle();
        logger.info(String.format(Locale.ENGLISH, "event for network %d", id));
        Map<Long, NetworkProperty> nextMap = new HashMap<>(networks.networkProperties);
        if (!nextMap.containsKey(id)) {
            logger.info("New network handle reported - creating entry");
            nextMap.put(
                    id,
                    applyNetworkEvent(
                            new NetworkProperty(e.getAffectedNetwork()),
                            e));
        } else {
            logger.info("This network is not actually new, but it's newly available");
            nextMap.put(
                    id,
                    applyNetworkEvent(Objects.requireNonNull(nextMap.get(id)), e));
        }
        return new NetworksInformationContainer(id, nextMap);
    }


    private NetworkProperty applyNetworkEvent(NetworkProperty networkProperty, Event event) {
        NetworkProperty retval;
        if (networkProperty.getNetwork().getNetworkHandle() == event.getAffectedNetwork().getNetworkHandle()) {
            retval = (NetworkProperty) networkProperty.clone();
        } else {
            retval = new NetworkProperty(event.getAffectedNetwork());
        }
        boolean handled
                 = applyAvailableEvent(networkProperty, event)
                || applyBlockingEvent(networkProperty, event)
                || applyCapabilitiesEvent(networkProperty, event)
                || applyDisconnectingEvent(networkProperty, event)
                || applyDisconnectedEvent(networkProperty, event)
                || applyLinkPropertiesEvent(networkProperty, event);
        if (!handled)
            logger.warning(String.format(
                    "Received event %s that was not handled by any apply function",
                    event));
        return retval;
    }

    private boolean applyAvailableEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventAvailable) {
            networkProperty.network = event.getAffectedNetwork();
            networkProperty.properties = null;
            networkProperty.capabilities = null;
            networkProperty.blocked = null;
            networkProperty.invalidAfter = null;
            return true;
        } else {
            return false;
        }
    }

    private boolean applyBlockingEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventBlockingChanged) {
            networkProperty.blocked = ((EventBlockingChanged)event).isBlocked();
            return true;
        } else {
            return false;
        }
    }

    private boolean applyCapabilitiesEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventCapabilitiesChanged) {
            networkProperty.capabilities = ((EventCapabilitiesChanged)event).getNetworkCapabilities();
            return true;
        } else {
            return false;
        }
    }

    private boolean applyDisconnectingEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventDisconnecting) {
            networkProperty.invalidAfter = ((EventDisconnecting)event).getEndOfLife();
            return true;
        } else {
            return false;
        }
    }

    private boolean applyDisconnectedEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventDisconnected) {
            networkProperty.invalidAfter = new Date();
            networkProperty.blocked = null;
            networkProperty.capabilities = null;
            networkProperty.properties = null;
            return true;
        } else {
            return false;
        }
    }

    private boolean applyLinkPropertiesEvent(NetworkProperty networkProperty, Event event) {
        if (event instanceof EventLinkPropertiesChanged) {
            networkProperty.properties = ((EventLinkPropertiesChanged)event).getLinkProperties();
            return true;
        } else {
            return false;
        }
    }

    private void startConnectivityListening() {
        logger.info("Building the Observable functional chains");
        /*
        Connectivity	  -acbp--g-l-ab----acpb--l
		                    1111  1 1 33    1111  3
        CurrentNetwork?	 1         3     1
        NetProp	    	  -0123--4-5-02----0132--5
		                     1111  1 1 33    1111  3
        OnlineNet	      ----1---------------1---
        OnlineDev	      -0--1----0----------1---
        CloseRemote	    -------1----------------
        StartRemote	    ----1---------------1---
         */
        this.networksProperties =
                connectivityLocalDataSource.getConnectivityEventObservable()
                        .scan(new NetworksInformationContainer(), this::applyNetworksEvent)
                        .filter((nic)-> nic.id != NetworksInformationContainer.NONE)
                        .replay(10);
        this.onlineNetwork =
                networksProperties
                        .filter((nic) -> {
                            NetworkProperty networkProperty = Objects.requireNonNull(
                                    nic.getNetworkProperties().get(nic.id));
                            return !networkProperty.isBlocked()
                                    && networkProperty.getProperties() != null
                                    && capabilityMeansOnline(networkProperty.getCapabilities())
                                    && (networkProperty.getInvalidAfter() == null
                                    || networkProperty.getInvalidAfter().before(new Date()));
                        })
                        .map((nic)->nic.id)
                        .replay(1)
                        .autoConnect(0);
        this.deviceOnline =
                onlineNetwork
                        .map((e)->TRUE)
                        .mergeWith(
                                networksProperties
                                        .filter(this::isAllNetworksOffline)
                                        .map((e) -> FALSE)
                        )
                        .startWithItem(FALSE)
                        .replay(1)
                        .autoConnect(0);
        this.currentNetworkObservable =
                connectivityLocalDataSource.getConnectivityEventObservable()
                        .filter(e->e instanceof EventAvailable)
                        .cast(EventAvailable.class)
                        .map(EventAvailable::getAffectedNetwork)
                        .replay(1)
                        .autoConnect(0);
        // let's start
        networksProperties.connect();
    }

    private boolean isAllNetworksOffline(NetworksInformationContainer networksInformationContainer) {
        for (NetworkProperty property: networksInformationContainer.networkProperties.values() ) {
            if (!isNetworkOffline(property)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNetworkOffline(NetworkProperty property) {
        if (property.isBlocked())
            return true;
        NetworkCapabilities capas = property.getCapabilities();
        if (capas == null)
            return true;
        return !(capas.hasCapability(NET_CAPABILITY_INTERNET)
                        && capas.hasCapability(NET_CAPABILITY_NOT_VPN)
                );

    }

    private boolean capabilityMeansOnline(@Nullable NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NET_CAPABILITY_NOT_VPN)
                && capabilities.hasCapability(NET_CAPABILITY_VALIDATED)
                && ((Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    || capabilities.hasCapability(NET_CAPABILITY_FOREGROUND));

    }
}
