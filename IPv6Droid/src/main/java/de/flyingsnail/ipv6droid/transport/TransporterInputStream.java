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

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import de.flyingsnail.ipv6droid.transport.ayiya.Ayiya;

public class TransporterInputStream extends InputStream {
  private final static String TAG = TransporterInputStream.class.getName();
  private final Transporter transporter;
  private final ThreadLocal<ByteBuffer> streamBuffer = new ThreadLocal<>();

  public TransporterInputStream(Transporter transporter) {
    this.transporter = transporter;
  }

  private void ensureBuffer() throws IOException {
        ByteBuffer buffer = streamBuffer.get();
        if (buffer == null) {
            // a new Thread, a new buffer.
            // a byte buffer which keeps track of position and length ("limit")
            buffer = ByteBuffer
                    .allocate(2* Ayiya.OVERHEAD + transporter.getMtu());
            buffer.limit(0); // initially no bytes inside
            buffer.order(ByteOrder.BIG_ENDIAN);
            streamBuffer.set (buffer);
        }
        while (!buffer.hasRemaining()) {
            try {
                transporter.read(buffer);
            } catch (TunnelBrokenException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public int read() throws IOException {
        ensureBuffer();
        return Objects.requireNonNull(streamBuffer.get()).get();
    }

    @Override
    public int read(@NonNull byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
        ensureBuffer();
        ByteBuffer byteBuffer = Objects.requireNonNull(streamBuffer.get());
        int byteCount = Math.min(byteBuffer.remaining(), length);
        byteBuffer.get(buffer, offset, byteCount);
        if (byteBuffer.hasRemaining())
            Log.e(TAG, "Warning: InputStream.read supplied with a buffer too small to read a full Datagram");
        return byteCount;
    }

    @Override
    public void close() throws IOException {
        super.close();
        streamBuffer.remove(); // @todo in principle, there may be more buffers of other Threads. Hm.
    }
}
