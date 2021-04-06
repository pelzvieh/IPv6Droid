/*
 *
 *  * Copyright (c) 2021 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.vpnrun;

import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * A container for the relevant network information in the context of a running VPN tunnel.
 */
class NetworkDetails {
    private LinkProperties currentNativeProperties = null;
    private Network currentNativeNetwork;
    private LinkProperties vpnProperties;

    public LinkProperties getNativeProperties() {
        return currentNativeNetwork == null ? null : currentNativeProperties;
    }

    /**
     * Remove the given network's properties. If it is the currently active network, choose
     * another one or set information that we have not current network.
     * @param network the Network to remove from properties store.
     */
    public void unsetNetwork (@NonNull Network network) {
        if (network.equals(currentNativeNetwork)) {
            currentNativeNetwork = null;
            currentNativeProperties = null;
        }
    }

    /**
     * Remembers the given link properties to the map of active native properties.
     * Also sets the currentNativeNetwork to network if currently not set.
     * @param network the Network that is now available
     * @param nativeProperties the LinkProperties of the Link associated with the given Network.
     */
    public void setNativeProperties(@NonNull Network network, @Nullable LinkProperties nativeProperties) {
        currentNativeNetwork = network;
        currentNativeProperties = nativeProperties;
    }

    public LinkProperties getVpnProperties() {
        return vpnProperties;
    }

    /**
     * Set the LinkProperties for VPN. It is the caller's responsibility to ensure this is called
     * for a VPN network only.
     * @param vpnProperties the LinkProperties for the current VPN
     */
    public void setVpnProperties(LinkProperties vpnProperties) {
        this.vpnProperties = vpnProperties;
    }

    /**
     * Query the routing table of the currently recommended native network(s) underlying the VPN.
     * @return List&lt;RouteInfo&gt; containing route definitions. May be empty, if no network
     * is connected or no information is available.
     */
    public List<RouteInfo> getNativeRouteInfos() {
        List<RouteInfo> routeInfos = new ArrayList<>();
        if (currentNativeProperties != null) {
            routeInfos.addAll(currentNativeProperties.getRoutes());
        }
        return routeInfos;
    }

    /**
     * Query the routing table of the VPN network.
     * @return List&lt;RouteInfo&gt; containing route definitions. May be empty, if no network
     * is connected or no information is available.
     */
    public List<RouteInfo> getVpnRouteInfos() {
        List<RouteInfo> routeInfos = (vpnProperties != null)
                ? vpnProperties.getRoutes()
                : null;
        // read information out of the cached linkProperties
        if (routeInfos == null)
            routeInfos = new ArrayList<>(0);

        return routeInfos;
    }

    /**
     * Query the DNS servers of all native network(s) underlying the VPN.
     * @return List&lt;InetAddress&gt; containing the DNS server's addresses. May be null, if no
     * network is connected, or no such information is available.
     */
    public List<InetAddress> getNativeDnsServers() {
        List<InetAddress> dnsServers = new ArrayList<>(0);
        if (currentNativeProperties != null) {
            dnsServers.addAll(currentNativeProperties.getDnsServers());
        }

        return dnsServers;
    }

    /**
     * Query the DNS servers of the VPN network.
     * @return List&lt;InetAddress&gt; containing the DNS server's addresses. May be null, if no
     * network is connected, or no such information is available.
     */
    public List<InetAddress> getVpnDnsServers() {
        List<InetAddress> dnsServers = (vpnProperties != null)
                ? vpnProperties.getDnsServers()
                : null;

        if (dnsServers == null)
            dnsServers = new ArrayList<>(0);

        return dnsServers;
    }

    public Network getNativeNetwork() {
        return currentNativeNetwork;
    }
}
