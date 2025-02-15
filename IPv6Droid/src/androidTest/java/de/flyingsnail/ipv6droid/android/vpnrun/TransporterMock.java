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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Date;

import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class TransporterMock implements Transporter {
    private final TunnelSpecMock spec;

    public TransporterMock(TunnelSpecMock spec) {
        this.spec = spec;
    }

    @Override
    public TunnelSpec getTunnelSpec() {
        return spec;
    }

    @Override
    public Date getLastPacketReceivedTime() {
        return new Date();
    }

    @Override
    public Date getLastPacketSentTime() {
        return new Date();
    }

    @Override
    public DatagramSocket prepare() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void connect() {

    }

    @Override
    public boolean isValidPacketReceived() {
        return false;
    }

    @Override
    public int getMtu() {
        return spec.getMtu();
    }

    @Override
    public void beat() {

    }

    @Override
    public ByteBuffer read(ByteBuffer bb) {
        return ByteBuffer.allocate(0);
    }

    @Override
    public void write(ByteBuffer bb) {

    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public OutputStream getOutputStream() {
        return new ByteArrayOutputStream(1000);
    }

    @Override
    public void close() {

    }
}
