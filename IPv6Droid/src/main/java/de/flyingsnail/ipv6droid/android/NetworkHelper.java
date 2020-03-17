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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.RouteInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

public class NetworkHelper  {
    /**
     * The tag for logging.
     */
    private static final String TAG = NetworkHelper.class.getName();

    /**
     * The object to call back on new or lost connectivity.
     */
    private final NetworkChangeListener networkChangeListener;

    /** Old style (pre Android 23) global connectivity receiver */
    private ConnectivityReceiver connectivityReceiver;
    /** New style (post Android 23) local connectivity receiver */
    private ConnectivityManager.NetworkCallback networkCallback;


    /**
     * The native routing, VPN routing, native DNS and VPN DNS information of current network setting.
     */
    private final NetworkDetails networkDetails = new NetworkDetails();

    private final Context notificationContext;


    /**
     * The system service ConnectivityManager
     */
    private ConnectivityManager connectivityManager;

    NetworkHelper(NetworkChangeListener networkChangeListener, Context notificationContext) {
        this.networkChangeListener = networkChangeListener;
        this.notificationContext = notificationContext;
        // resolve system service "ConnectivityManager"
        connectivityManager = (ConnectivityManager) notificationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        updateNetworkDetails(null);
        registerConnectivityReceiver();
    }


    void destroy() {
        unregisterConnectivityReceiver();
    }

    /**
     * Inner class to handle connectivity changes.
     * Generally obsolete, will only be used on Android versions prior to 23.
     */
    public class ConnectivityReceiver extends BroadcastReceiver {
        private ConnectivityReceiver() {
        }

        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            if (Objects.equals(action, ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.i(TAG, "Received connectivity action");
                onConnectivityChange(
                        !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false),
                        null);
            }
        }
    }

    ConnectivityManager getConnectivityManager() {
        return connectivityManager;
    }

    /**
     * Register to be called in event of internet available.
     */
    private void registerConnectivityReceiver() {
        if (Build.VERSION.SDK_INT >= 23) {
            ConnectivityManager cm = notificationContext.getSystemService(ConnectivityManager.class);
            NetworkRequest.Builder builder = new NetworkRequest.Builder().
                    addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).
                    addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
            NetworkRequest request = builder.build();

            networkCallback = new ConnectivityManager.NetworkCallback () {
                @Override
                public void onAvailable(Network network) {
                    if (cm != null) {
                        onConnectivityChange(true,
                                cm.getLinkProperties(network));
                    }
                }

                @Override
                public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                    onConnectivityChange(true, linkProperties);
                }

                @Override
                public void onLost(Network network) {
                    onConnectivityChange(false, null);
                }
            };
            if (cm != null) {
                cm.registerNetworkCallback(request, networkCallback);
            }
        }
        // anyway, register for callback on connectivity change
        registerGlobalConnectivityReceiver();
    }

    private void unregisterConnectivityReceiver() {
        if (Build.VERSION.SDK_INT >= 23) {
            connectivityManager.unregisterNetworkCallback(
                    networkCallback
            );
        }
        unregisterGlobalConnectivityReceiver();
    }

    /**
     * Register an instance of connectivityReceiver for global CONNECTIVITY_ACTIONs.
     * This was once documented to be obsolete, but in 2019 again to be usable.
     */
    private void registerGlobalConnectivityReceiver() {
        connectivityReceiver = new ConnectivityReceiver();
        final IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

        notificationContext.registerReceiver(connectivityReceiver, intentFilter);
        Log.d(TAG, "registered CommandReceiver for global broadcasts");
    }

    /**
     * Revert registerGlobalConnectivityReceiver().
     */
    private void unregisterGlobalConnectivityReceiver() {
        try {
            notificationContext.unregisterReceiver(connectivityReceiver);
            Log.d(TAG, "un-registered CommandReceiver for global broadcasts");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not unregister global connectivity receiver");
        }
    }


    /**
     * Read route and DNS info of the currently active and the VPN network. Weird code, but the best
     * I could imagine out of the ConnectivityManager API. In API versions 28, network changes
     * are no longer handled by CONNECTIVITY_ACTION broadcast, but by requestNetwork callback methods.
     * These provide the required details as parameters.
     *
     * <p>So when running in version 28 following,
     * we only use this method to</p>
     * <ul>
     * <li>store the supplied value of native network properties</li>
     * <li>enumerate the network properties of our VPN network</li>
     * </ul>
     * <p>In result, the networkDetails field will be updated.</p>
     */
    void updateNetworkDetails(@Nullable final LinkProperties newLinkProperties) {
        boolean foundNative = false; // we need to try different approaches to get native network depending on API version

        // force-set native link properties to supplied information
        if (newLinkProperties != null) {
            networkDetails.setNativeProperties(newLinkProperties);
            foundNative = true;
        }

        Log.d(TAG, "updateNetworkDetails trying to read native link properties");
        ConnectivityManager cm = getConnectivityManager();

        // direct way to read network available from API 23
        if (!foundNative && Build.VERSION.SDK_INT >= 23) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                if (ConnectivityManager.TYPE_VPN != cm.getNetworkInfo(activeNetwork).getType()) {
                    LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
                    networkDetails.setNativeProperties(linkProperties);
                    foundNative = true;
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
    private void onConnectivityChange(final boolean connected, @Nullable final LinkProperties newLinkProperties) {
        Log.i(TAG, "Connectivity changed");
        if (connected) {
            // update cached information
            updateNetworkDetails(newLinkProperties);

            // notify caller on network connected
            networkChangeListener.onNewConnection();

        } // we have connectivity
        else {
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