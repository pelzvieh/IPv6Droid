/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
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
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;

public class NetworkHelper  {
    /**
     * The tag for logging.
     */
    private static final String TAG = NetworkHelper.class.getName();

    /**
     * The object to call back on new or lost connectivity.
     */
    private final NetworkChangeListener networkChangeListener;

    /** New style local connectivity receiver */
    private ConnectivityManager.NetworkCallback networkCallback;


    /**
     * The native routing, VPN routing, native DNS and VPN DNS information of current network setting.
     */
    private final NetworkDetails networkDetails = new NetworkDetails();

    /**
     * The system service ConnectivityManager
     */
    private ConnectivityManager connectivityManager;

    NetworkHelper(NetworkChangeListener networkChangeListener, Context notificationContext) {
        this.networkChangeListener = networkChangeListener;
        // resolve system service "ConnectivityManager"
        connectivityManager = (ConnectivityManager) notificationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Register as listener, i.e. start dispatching network events
     */
    synchronized void start() {
        Network currentlyActiveNetwork = connectivityManager.getActiveNetwork();

        // get current link properties associated with network
        LinkProperties currentLinkProperties = connectivityManager.getLinkProperties(currentlyActiveNetwork);
        updateNetworkDetails(currentlyActiveNetwork, currentLinkProperties);

        if (networkCallback == null)
            registerConnectivityReceiver();
    }

    /**
     * Stop dispatching network events. It is possible to use this object by a later call to #start().
     */
    synchronized void stop() {
        if (networkCallback != null)
            unregisterConnectivityReceiver();
    }

    ConnectivityManager getConnectivityManager() {
        return connectivityManager;
    }

    /**
     * Register to be called in event of internet available.
     */
    private synchronized void registerConnectivityReceiver() {
        // register specific connectivity callback
        if (connectivityManager != null && networkCallback == null) {
            networkCallback = new ConnectivityManager.NetworkCallback () {

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    // update cached information so that our listener can query them immediately
                    updateNetworkDetails(network, linkProperties);

                    // notify caller on network connected
                    networkChangeListener.onNewConnection();
                }

                @Override
                public void onLost(Network network) {
                    networkDetails.unsetNetwork(network);
                    networkChangeListener.onDisconnected();
                }
            };

            NetworkRequest.Builder builder = new NetworkRequest.Builder().
                    addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
            NetworkRequest request = builder.build();

            connectivityManager.requestNetwork(request, networkCallback);
        }
    }

    /**
     * Unregister from being called at network changes.
     */
    private synchronized void unregisterConnectivityReceiver() {
        try {
            if (connectivityManager != null && networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Unable to unregister network callback", e);
        } finally {
            networkCallback = null;
        }
    }

    /**
     * Network changes are no longer handled by CONNECTIVITY_ACTION broadcast, but by requestNetwork
     * callback methods. These provide the required details as parameters.
     *
     * <p>So we only use this method to</p>
     * <ul>
     * <li>store the supplied value of native network properties</li>
     * <li>enumerate the network properties of our VPN network</li>
     * </ul>
     * <p>In result, the networkDetails field will be updated.</p>
     *
     * Note: this is called in a context where connectivityManager is not yet usable for the
     * updated network!
     *
     * @param network the currently active Network
     * @param newLinkProperties the new link properties if this call is the resulat of a network
     *                          change callback.
     */
    void updateNetworkDetails(@NonNull final Network network, @Nullable final LinkProperties newLinkProperties) {
        Log.i(TAG, "Updating cached network details");
        // force-set native link properties to supplied information
        networkDetails.setNativeProperties(network, newLinkProperties);
        for (Network n : connectivityManager.getAllNetworks()) {
            NetworkInfo ni = connectivityManager.getNetworkInfo(n);
            LinkProperties linkProperties = connectivityManager.getLinkProperties(n);
            if (ni != null) {
                if (ni.getType() == ConnectivityManager.TYPE_VPN) {
                    networkDetails.setVpnProperties(linkProperties);
                }
            }
        }
    }

    /**
     * Check if the local address of current Ayiya is still valid considering the cached network
     * details. A useful call to this method therefore should be precedet by a validation or update
     * of the networkDetails cache.
     * <p>This method constantly returns false when run on Android versions prior to 21.</p>
     *
     * @return true if the current local address still matches one of the link addresses
     * @deprecated keep track of network instead
     */
    boolean isCurrentSocketAdressStillValid(DatagramSocket socket) {
        boolean addressValid = false;
        final LinkProperties myNativeProperties = networkDetails.getNativeProperties();
        Log.i(TAG, "Explicit address validity check requested");
        if (myNativeProperties != null) {
            if (socket != null) {
                InetAddress currentLocalAddress = socket.getLocalAddress();
                Log.d(TAG, "Comparing current socket local address " + currentLocalAddress);
                for (LinkAddress linkAdress : myNativeProperties.getLinkAddresses()) {
                    InetAddress newAdress = linkAdress.getAddress();
                    Log.d(TAG, "- with link address " + newAdress);
                    if (newAdress.equals(currentLocalAddress)) {
                        Log.d(TAG, "--> old socket address matches new link local address");
                        addressValid = true;
                    } else {
                        Log.d(TAG, "--- No match");
                    }
                }
            }
        }
        if (addressValid)
            Log.i(TAG, "Current socket address still matches the new native local address");
        else
            Log.i(TAG, "Current socket address cannot be verified to be valid");
        return addressValid;
    }

    public List<RouteInfo> getNativeRouteInfos() {
        return networkDetails.getNativeRouteInfos();
    }

    public List<RouteInfo> getVpnRouteInfos() {
        return networkDetails.getVpnRouteInfos();
    }

    public List<InetAddress> getNativeDnsServers() {
        return networkDetails.getNativeDnsServers();
    }

    public List<InetAddress> getVpnDnsServers() {
        return networkDetails.getVpnDnsServers();
    }

    public Network getNativeNetwork() {
        return networkDetails.getNativeNetwork();
    }
}