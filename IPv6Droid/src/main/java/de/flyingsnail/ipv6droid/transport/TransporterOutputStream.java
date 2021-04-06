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

package de.flyingsnail.ipv6droid.transport;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class TransporterOutputStream extends OutputStream {

  private final Transporter transporter;

  public TransporterOutputStream(Transporter transporter) {
    this.transporter = transporter;
  }

  @Override
    public void write(@NonNull byte[] buffer) throws IOException {
        try {
            transporter.write(ByteBuffer.wrap(buffer));
        } catch (TunnelBrokenException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void write(@NonNull byte[] buffer, int offset, int count) throws IOException {
      try {
        transporter.write(ByteBuffer.wrap(buffer, offset, count));
      } catch (TunnelBrokenException e) {
        throw new IOException(e);
      }
    }

    @Override
    public void write(int i) throws IOException {
        this.write(new byte[] {(byte)i});
    }
}
