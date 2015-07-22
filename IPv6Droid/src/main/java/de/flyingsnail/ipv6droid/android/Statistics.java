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

    protected Statistics(long bytesTransmitted, long bytesReceived, long packetsTransmitted, long packetsReceived, Inet4Address brokerIPv4, Inet4Address myIPv4, Inet6Address brokerIPv6, Inet6Address myIPv6, int mtu, List<RouteInfo> routing, List<InetAddress> dnsSetting) {
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
}
