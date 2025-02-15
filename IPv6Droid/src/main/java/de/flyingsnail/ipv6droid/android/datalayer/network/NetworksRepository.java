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

import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.AndroidLoggingHandler;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.Event;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventAvailable;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventBlockingChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventCapabilitiesChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnected;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnecting;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventLinkPropertiesChanged;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * This class provides the single source of truth concerning the device's
 * networks and their respective state. It is consuming a number of sources
 * for network enumeration and to actively retrieve changes.
 * <p>
 * This is a Repository class in the Data Layer according to the
 * <a href="https://developer.android.com/topic/architecture/data-layer">Android developer reference
 * architecture</a>.
 */
public class NetworksRepository {
    private static final Logger logger = AndroidLoggingHandler.getLogger(NetworksRepository.class);
    private final @NonNull ConnectableObservable<NetworksInformationContainer> networksProperties;
    private final @NonNull Observable<NetworkProperty> currentNetworkObservable;
    private final @NonNull Observable<NetworkProperty> onlineNetworkProperty;
    private final @NonNull Observable<Boolean> deviceOnline;
    private final Subject<NetworkProperty> currentNetworkSource;

    /**
     * Get an ObservableSource streaming the networks properties evolving over time.
     * Each emitted NetworksInformationContainer aggregates the latest connectivity event as an
     * update to the affected network's NetworkProperty. The current NetworkProperty for each
     * known network is contained, along with the id of the network that changed with the emitted
     * instance.
     * @return an Observable&lt;NetworksInformationContainer&gt;
     */
    @NonNull
    public Observable<NetworksInformationContainer> getNetworksProperties() {
        logger.fine("Returning networksProperties observable");
        return networksProperties;
    }

    /**
     * Get an ObservableSource streaming the NetworkProperty of the network that <em>changed</em>
     * last with a connectivity event.
     * @return an ObservableSource&lt;NetworkProperty&gt;
     */
    @NonNull
    public Observable<NetworkProperty> getCurrentNetworkObservable() {
        logger.fine("Returning currentNetworkObservable");
        return currentNetworkObservable;
    }

    /**
     * Get an Observable streaming the NetworkProperty of the network that is currently
     * online.
     * @return an Observable&lt;NetworkProperty&gt;
     */
    @NonNull
    public Observable<NetworkProperty> getOnlineNetworkProperty() {
        logger.fine("Returning onlineNetwork observable");
        return onlineNetworkProperty;
    }

    /**
     * Get an Observable streaming the device's online state.
     * @return an Observable&lt;Boolean&gt;
     */
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

    /**
     * Constructor with dependencies to inject.
     * @param connectivityLocalDataSource the ConnectivityLocalDataSource to use.
     */
    public NetworksRepository(final ConnectivityLocalDataSource connectivityLocalDataSource) {
        logger.fine("Building the Observable functional chains");
        /*
        Connectivity	  -acbp--g-l-ab----acpb--l
		                     1111  1 1 33    1111  3
        CurrentNetwork?	 1         3     1
        NetProp	    	  -0123--4-5-02----0132--5
		                     1111  1 1 33    1111  3
        OnlineNet	      ----1--1------------1---
        OnlineDev	      -0--1----0----------1---
        CloseRemote	    -------1----------------
        StartRemote	    ----1---------------1---
         */
        currentNetworkSource = ReplaySubject.createWithSize(5); // todo this gets filled by applyEventAvailable; looks dirty
        this.networksProperties =
                connectivityLocalDataSource.getConnectivityEventObservable()
                        .scan(new NetworksInformationContainer(), this::applyNetworksEvent)
                        .filter((nic)-> nic.id != NetworksInformationContainer.NONE)
                        .doOnComplete(currentNetworkSource::onComplete)
                        .replay(25);
        this.onlineNetworkProperty =
                networksProperties
                        .map((nic)->nic.getNetworkProperties().get(nic.id))
                        .filter((networkProperty) -> !networkProperty.isBlocked()
                                && networkProperty.getProperties() != null
                                && capabilityMeansOnline(networkProperty.getCapabilities())
                                && (networkProperty.getInvalidAfter() == null
                                || networkProperty.getInvalidAfter().after(new Date())))
                        .replay(1)
                        .autoConnect(1);
        this.deviceOnline =
                onlineNetworkProperty
                        .map((e)-> {
                            logger.info("Device is online with network " + e.getNetwork());
                            return TRUE;
                        })
                        .mergeWith(
                                networksProperties
                                        .filter(this::isAllNetworksOffline)
                                        .map((e) -> FALSE)
                        )
                        .startWithItem(FALSE)
                        .distinctUntilChanged()
                        .doOnNext((e) -> logger.info("Device online: " + e))
                        .replay(1)
                        .autoConnect(1);
        this.currentNetworkObservable = currentNetworkSource
                .replay(1)
                .autoConnect(1);

        // let's start
        networksProperties.connect();
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
        logger.info(String.format(Locale.GERMAN, "event for network %s", e.getAffectedNetwork()));
        Map<Long, NetworkProperty> nextMap = new HashMap<>(networks.networkProperties);
        if (!nextMap.containsKey(id)) {
            NetworkProperty newProp = applyNetworkEvent(
                    new NetworkProperty(e.getAffectedNetwork()),
                    e);
            logger.log(Level.FINE, String.format("New network handle reported - creating entry %s", newProp));
            nextMap.put(id, newProp);
        } else {
            NetworkProperty updatable = applyNetworkEvent(Objects.requireNonNull(nextMap.get(id)), e);
            logger.log(Level.FINE, String.format("Update for existing network: %s", updatable));
            nextMap.put(id, updatable);
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
                 = applyAvailableEvent(retval, event)
                || applyBlockingEvent(retval, event)
                || applyCapabilitiesEvent(retval, event)
                || applyDisconnectingEvent(retval, event)
                || applyDisconnectedEvent(retval, event)
                || applyLinkPropertiesEvent(retval, event);
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
            logger.fine("Writing networkProperty to currentNetworkSource: " + networkProperty);
            currentNetworkSource.onNext(networkProperty);
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
        if (property.getProperties() == null)
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
