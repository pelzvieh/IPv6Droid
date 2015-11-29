package de.flyingsnail.ipv6droid.android;

import android.annotation.TargetApi;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.Build;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * A container for the relevant network information in the context of a running VPN tunnel.
 */
class NetworkDetails {
    private LinkProperties nativeProperties;
    private LinkProperties vpnProperties;

    public LinkProperties getNativeProperties() {
        return nativeProperties;
    }

    public void setNativeProperties(LinkProperties nativeProperties) {
        this.nativeProperties = nativeProperties;
    }

    public LinkProperties getVpnProperties() {
        return vpnProperties;
    }

    public void setVpnProperties(LinkProperties vpnProperties) {
        this.vpnProperties = vpnProperties;
    }

    /**
     * Query the routing table of the network underlying the VPN.
     * @return List&lt;RouteInfo&gt; containing route definitions. May be empty, if no network
     * is connected or no information is available.
     */
    @TargetApi(21)
    public List<RouteInfo> getNativeRouteInfos() {
        List<RouteInfo> routeInfos = (nativeProperties != null && Build.VERSION.SDK_INT >= 21)
                ? nativeProperties.getRoutes()
                : null;
        // read information out of the cached linkProperties
        if (routeInfos == null)
            routeInfos = new ArrayList<RouteInfo>(0);

        return routeInfos;
    }

    /**
     * Query the routing table of the VPN network.
     * @return List&lt;RouteInfo&gt; containing route definitions. May be empty, if no network
     * is connected or no information is available.
     */
    @TargetApi(21)
    public List<RouteInfo> getVpnRouteInfos() {
        List<RouteInfo> routeInfos = (vpnProperties != null && Build.VERSION.SDK_INT >= 21)
                ? vpnProperties.getRoutes()
                : null;
        // read information out of the cached linkProperties
        if (routeInfos == null)
            routeInfos = new ArrayList<RouteInfo>(0);

        return routeInfos;
    }

    /**
     * Query the DNS servers of the network underlying the VPN.
     * @return List&lt;InetAddress&gt; containing the DNS server's addresses. May be null, if no
     * network is connected, or no such information is available.
     */
    @TargetApi(21)
    public List<InetAddress> getNativeDnsServers() {
        List<InetAddress> dnsServers = (nativeProperties != null && Build.VERSION.SDK_INT >= 21)
                ? nativeProperties.getDnsServers()
                : null;

        if (dnsServers == null)
            dnsServers = new ArrayList<InetAddress>(0);

        return dnsServers;
    }

    /**
     * Query the DNS servers of the VPN network.
     * @return List&lt;InetAddress&gt; containing the DNS server's addresses. May be null, if no
     * network is connected, or no such information is available.
     */
    @TargetApi(21)
    public List<InetAddress> getVpnDnsServers() {
        List<InetAddress> dnsServers = (vpnProperties != null && Build.VERSION.SDK_INT >= 21)
                ? vpnProperties.getDnsServers()
                : null;

        if (dnsServers == null)
            dnsServers = new ArrayList<InetAddress>(0);

        return dnsServers;
    }

}
