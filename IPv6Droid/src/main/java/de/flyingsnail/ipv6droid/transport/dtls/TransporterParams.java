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

package de.flyingsnail.ipv6droid.transport.dtls;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Date;
import java.util.List;

import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class TransporterParams implements TunnelSpec {
    public static final String TUNNEL_TYPE = "DTLSTunnel";

    private Inet4Address ipv4Pop;
    private int portPop;
    private int mtu;
    private int heartbeat;
    private String privateKey;
    private List<String> certChain;
    private String tunnelName;
    private String tunnelId;
    private Inet6Address ipv6Endpoint;
    private Inet6Address ipv6Pop;
    private int prefixLength;
    private String popName;

    @Override
    public Inet4Address getIPv4Pop() {
        return ipv4Pop;
    }

    @Override
    public String getTunnelName() {
        return tunnelName;
    }

    @Override
    public void setTunnelName(String tunnelName) {
        this.tunnelName = tunnelName;
    }

    @Override
    public String getTunnelId() {
        return tunnelId;
    }

    @Override
    public void setTunnelId(String tunnelId) {
        this.tunnelId = tunnelId;
    }

    @Override
    public String getType() {
        return TUNNEL_TYPE;
    }

    @Override
    public Inet6Address getIpv6Endpoint() {
        return ipv6Endpoint;
    }

    @Override
    public void setIpv6Endpoint(Inet6Address ipv6Endpoint) {
        this.ipv6Endpoint = ipv6Endpoint;
    }

    @Override
    public Inet6Address getIpv6Pop() {
        return ipv6Pop;
    }

    @Override
    public void setIpv6Pop(Inet6Address ipv6Pop) {
        this.ipv6Pop = ipv6Pop;
    }

    @Override
    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    @Override
    public String getPopName() {
        return popName;
    }

    @Override
    public void setPopName(String popName) {
        this.popName = popName;
    }

    @Override
    public Date getExpiryDate() {
        return new Date(new Date().getTime() + 1000L*60*60*24*365);
    }

    @Override
    public boolean isEnabled() {
        // todo check valid until date of own certificate
        return true;
    }

    @Override
    public void setIPv4Pop(Inet4Address ipv4Pop) {
        this.ipv4Pop = ipv4Pop;
    }

    public int getPortPop() {
        return portPop;
    }

    public void setPortPop(int portPop) {
        this.portPop = portPop;
    }

    @Override
    public int getMtu() {
        return mtu;
    }

    @Override
    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    @Override
    public int getHeartbeatInterval() {
        return heartbeat;
    }

    @Override
    public void setHeartbeatInterval(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public List<String> getCertChain() {
        return certChain;
    }

    public void setCertChain(List<String> certChain) {
        this.certChain = certChain;
    }
}
