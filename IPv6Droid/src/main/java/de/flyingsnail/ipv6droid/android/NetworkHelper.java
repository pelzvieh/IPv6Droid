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

    private final Context notificationContext;

    /**
     * Are we registered as connectivity receiver?
     */
    private boolean receiverRegistered = false;

    /**
     * Are we registered as a global connectivity receiver?
     */
    private boolean globalReceiverRegistered = false;

    /**
     * The system service ConnectivityManager
     */
    private ConnectivityManager connectivityManager;

    NetworkHelper(NetworkChangeListener networkChangeListener, Context notificationContext) {
        this.networkChangeListener = networkChangeListener;
        // resolve system service "ConnectivityManager"
        connectivityManager = (ConnectivityManager) notificationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network currentlyActiveNetwork = connectivityManager.getActiveNetwork();
        updateNetworkDetails(currentlyActiveNetwork, connectivityManager.getLinkProperties(currentlyActiveNetwork));
        registerConnectivityReceiver();
    }


    void destroy() {
        unregisterConnectivityReceiver();
    }

    ConnectivityManager getConnectivityManager() {
        return connectivityManager;
    }

    /**
     * Register to be called in event of internet available.
     */
    private synchronized void registerConnectivityReceiver() {
        if (receiverRegistered)
            return; // already done
        // register specific connectivity callback
        if (connectivityManager != null && networkCallback == null) {
            NetworkRequest.Builder builder = new NetworkRequest.Builder().
                    addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).
                    addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            NetworkRequest request = builder.build();

            networkCallback = new ConnectivityManager.NetworkCallback () {
                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    onConnectivityChange(true, network, linkProperties);
                }

                @Override
                public void onLost(Network network) {
                    onConnectivityChange(false, network, null);
                }
            };
            connectivityManager.registerNetworkCallback(request, networkCallback);
            receiverRegistered = true;
        }
    }

    private void unregisterConnectivityReceiver() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (receiverRegistered) {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
                receiverRegistered = false;
            }
        }
        unregisterGlobalConnectivityReceiver();
    }

    /**
     * Register an instance of connectivityReceiver for global CONNECTIVITY_ACTIONs.
     * This was once documented to be obsolete, but in 2019 again to be usable.
     */
    private void registerGlobalConnectivityReceiver() {
        if (globalReceiverRegistered)
            return; // already done
        connectivityReceiver = new ConnectivityReceiver();
        final IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

        notificationContext.registerReceiver(connectivityReceiver, intentFilter);
        globalReceiverRegistered = true;
        Log.d(TAG, "registered CommandReceiver for global broadcasts");
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
     * Revert registerGlobalConnectivityReceiver().
     */
    private void unregisterGlobalConnectivityReceiver() {
        if (globalReceiverRegistered) {
            notificationContext.unregisterReceiver(connectivityReceiver);
            globalReceiverRegistered = false;
        }
        Log.d(TAG, "un-registered CommandReceiver for global broadcasts");
    }


    /**
     * Read route and DNS info of the currently active and the VPN network. Weird code, but the best
     * I could imagine out of the ConnectivityManager API. In API versions 28, network changes
     * are no longer handled by CONNECTIVITY_ACTION broadcast, but by requestNetwork callback methods.
     * These provide the required details as parameters.
     *
     * <p>So we only use this method to</p>
     * <ul>
     * <li>store the supplied value of native network properties</li>
     * <li>enumerate the network properties of our VPN network</li>
     * </ul>
     * <p>In result, the networkDetails field will be updated.</p>
     */
    void updateNetworkDetails(@NonNull final Network network, @Nullable final LinkProperties newLinkProperties) {
        Log.i(TAG, "Updating cached network details");
        // force-set native link properties to supplied information
        if (newLinkProperties != null) {
            networkDetails.setNativeProperties(network, newLinkProperties);
        }
        boolean foundNative = false; // we need to try different approaches to get native network depending on API version
        ConnectivityManager cm = getConnectivityManager();

        // direct way to read network available from API 23
        if (!foundNative && Build.VERSION.SDK_INT >= 23) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkInfo networkInfo = cm.getNetworkInfo(activeNetwork);
                if (networkInfo != null && networkInfo.getType() != ConnectivityManager.TYPE_VPN) {
                    LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
                    if (linkProperties != null) {
                        networkDetails.setNativeProperties(linkProperties);
                        foundNative = true;
                    }
                } else {
                    Log.w(TAG, "ConnectivityManager.getActiveNetwork returned our VPN");
                }
            }
        }

        // reconstruct link properties for VPN network, and, prior to API 23, attempt to
        // find the native network's link properties.
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkInfo ni = cm.getNetworkInfo(n);
                if (ni != null) {
                    LinkProperties linkProperties = cm.getLinkProperties(n);
                    if (!foundNative
                            && ni.getType() == activeNetworkInfo.getType()
                            && ni.getSubtype() == activeNetworkInfo.getSubtype()) {
                        networkDetails.setNativeProperties(linkProperties);
                        foundNative = true;
                    } else if (ni.getType() == ConnectivityManager.TYPE_VPN) {
                        networkDetails.setVpnProperties(linkProperties);
                    }
                }
            }
        }
    }

    /**
     * Notify all threads waiting on a status change.
     * This is safe to call from the main thread.
     *
     * @param connected the boolean indicating if the new network situation has connectivity
     */
    private void onConnectivityChange(final boolean connected,
                                      final @NonNull Network network,
                                      @Nullable final LinkProperties newLinkProperties) {
        Log.i(TAG, "Connectivity changed");
        if (connected) {
            // update cached information
            updateNetworkDetails(network, newLinkProperties);

            // notify caller on network connected
            networkChangeListener.onNewConnection();
        } // we have connectivity
        else {
            networkDetails.unsetNetwork(network);
            networkChangeListener.onDisconnected();
        }
    }

    /**
     * Check if the local address of current Ayiya is still valid considering the cached network
     * details. A useful call to this method therefore should be precedet by a validation or update
     * of the networkDetails cache.
     * <p>This method constantly returns false when run on Android versions prior to 21.</p>
     *
     * @return true if the current local address still matches one of the link addresses
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
}