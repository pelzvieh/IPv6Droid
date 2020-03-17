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

package de.flyingsnail.ipv6droid.transport;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Date;

/**
 * Interface to interact with a tunnel specification, not knowing which kind of tunnel.
 */
public interface TunnelSpec extends Serializable {
    String getTunnelName();

    void setTunnelName(String tunnelName);

    Inet4Address getIPv4Pop();

    void setIPv4Pop(Inet4Address ipv4Pop);

    String getTunnelId();

    void setTunnelId(String tunnelId);

    String getType();

    Inet6Address getIpv6Endpoint();

    void setIpv6Endpoint(Inet6Address ipv6Endpoint);

    //Inet6Address getIpv6Pop();

    //void setIpv6Pop(Inet6Address ipv6Pop);

    //int getPrefixLength();

    //void setPrefixLength(int prefixLength);

    String getPopName();

    void setPopName(String popName);

    int getHeartbeatInterval();

    void setHeartbeatInterval(int heartbeatInterval);

    int getMtu();

    void setMtu(int mtu);

    Date getExpiryDate();

    boolean isEnabled();
}
