/*
 * Copyright (c) 2015 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.android;

import android.net.RouteInfo;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Date;
import java.util.List;

/**
 * Created by pelzi on 01.05.15.
 */
public class Statistics {
    final long bytesTransmitted;
    final long bytesReceived;
    final long packetsTransmitted;
    final long packetsReceived;
    final Inet4Address brokerIPv4;
    final Inet4Address myIPv4;
    final Inet6Address brokerIPv6;
    final Inet6Address myIPv6;
    final int mtu;
    final List<RouteInfo> routing;
    final List<InetAddress> dnsSetting;
    final private Date timestamp;
    final private boolean tunnelRouted;

    protected Statistics(long bytesTransmitted, long bytesReceived, long packetsTransmitted, long packetsReceived, Inet4Address brokerIPv4, Inet4Address myIPv4, Inet6Address brokerIPv6, Inet6Address myIPv6, int mtu, List<RouteInfo> routing, List<InetAddress> dnsSetting, boolean tunnelRouted) {
        this.bytesTransmitted = bytesTransmitted;
        this.bytesReceived = bytesReceived;
        this.packetsTransmitted = packetsTransmitted;
        this.packetsReceived = packetsReceived;
        this.brokerIPv4 = brokerIPv4;
        this.myIPv4 = myIPv4;
        this.brokerIPv6 = brokerIPv6;
        this.myIPv6 = myIPv6;
        this.mtu = mtu;
        this.routing = routing;
        this.dnsSetting = dnsSetting;
        this.tunnelRouted = tunnelRouted;
        timestamp = new Date();
    }

    public long getBytesTransmitted() {
        return bytesTransmitted;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getPacketsTransmitted() {
        return packetsTransmitted;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public Inet4Address getBrokerIPv4() {
        return brokerIPv4;
    }

    public Inet4Address getMyIPv4() {
        return myIPv4;
    }

    public Inet6Address getBrokerIPv6() {
        return brokerIPv6;
    }

    public Inet6Address getMyIPv6() {
        return myIPv6;
    }

    public int getMtu() {
        return mtu;
    }

    public List<RouteInfo> getRouting() {
        return routing;
    }

    public List<InetAddress> getDnsSetting() {
        return dnsSetting;
    }

    public Date getTimestamp() { return timestamp; }

    public boolean isTunnelRouted() {
        return tunnelRouted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Statistics)) return false;

        Statistics that = (Statistics) o;

        if (bytesTransmitted != that.bytesTransmitted) return false;
        if (bytesReceived != that.bytesReceived) return false;
        if (packetsTransmitted != that.packetsTransmitted) return false;
        if (packetsReceived != that.packetsReceived) return false;
        if (mtu != that.mtu) return false;
        if (tunnelRouted != that.tunnelRouted) return false;
        if (!brokerIPv4.equals(that.brokerIPv4)) return false;
        if (myIPv4 != null ? !myIPv4.equals(that.myIPv4) : that.myIPv4 != null) return false;
        if (!brokerIPv6.equals(that.brokerIPv6)) return false;
        if (!myIPv6.equals(that.myIPv6)) return false;
        if (routing != null ? !routing.equals(that.routing) : that.routing != null) return false;
        return !(dnsSetting != null ? !dnsSetting.equals(that.dnsSetting) : that.dnsSetting != null);

    }

    @Override
    public int hashCode() {
        int result = (int) (bytesTransmitted ^ (bytesTransmitted >>> 32));
        result = 31 * result + (int) (bytesReceived ^ (bytesReceived >>> 32));
        result = 31 * result + (int) (packetsTransmitted ^ (packetsTransmitted >>> 32));
        result = 31 * result + (int) (packetsReceived ^ (packetsReceived >>> 32));
        result = 31 * result + brokerIPv4.hashCode();
        result = 31 * result + myIPv4.hashCode();
        result = 31 * result + brokerIPv6.hashCode();
        result = 31 * result + myIPv6.hashCode();
        result = 31 * result + mtu;
        result = 31 * result + (routing != null ? routing.hashCode() : 0);
        result = 31 * result + (dnsSetting != null ? dnsSetting.hashCode() : 0);
        result = 31 * result + (tunnelRouted ? 1 : 0);
        return result;
    }
}
