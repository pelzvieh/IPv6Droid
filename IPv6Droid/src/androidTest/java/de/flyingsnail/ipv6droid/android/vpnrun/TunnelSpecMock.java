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

package de.flyingsnail.ipv6droid.android.vpnrun;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Date;

import de.flyingsnail.ipv6droid.transport.TransporterBuilder;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class TunnelSpecMock implements TunnelSpec {
    public TunnelSpecMock() {
        TransporterBuilder.register(TunnelSpecMock.class, TransporterMock.class);
    }

    @Override
    public String getTunnelName() {
        return "MockTunnel";
    }

    @Override
    public void setTunnelName(String tunnelName) {

    }

    @Override
    public Inet4Address getIPv4Pop() {
        try {
            return (Inet4Address) Inet4Address.getByAddress(new byte[]{10, 10, 10, 127});
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setIPv4Pop(Inet4Address ipv4Pop) {

    }

    @Override
    public String getTunnelId() {
        return "Mocked tunnel #0";
    }

    @Override
    public void setTunnelId(String tunnelId) {

    }

    @Override
    public String getType() {
        return "Mock";
    }

    @Override
    public Inet6Address getIpv6Endpoint() {
        try {
            return (Inet6Address) Inet6Address.getByAddress(new byte[]{
                    (byte) 0xfe, (byte) 0x80, 0x08, (byte) 0xe6,
                    0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00,
                    (byte) 0xde, (byte) 0xad, (byte) 0xaf, (byte) 0xfe
            });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setIpv6Endpoint(Inet6Address ipv6Endpoint) {

    }

    @Override
    public String getPopName() {
        return "MockPop";
    }

    @Override
    public void setPopName(String popName) {

    }

    @Override
    public int getHeartbeatInterval() {
        return 300;
    }

    @Override
    public void setHeartbeatInterval(int heartbeatInterval) {

    }

    @Override
    public int getMtu() {
        return 1000;
    }

    @Override
    public void setMtu(int mtu) {

    }

    @Override
    public Date getExpiryDate() {
        return new Date(new Date().getTime() + 100000L);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
