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

package de.flyingsnail.ipv6droid.transport.dtls;

import android.util.Log;

import androidx.annotation.Nullable;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.UDPTransport;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Date;

/**
 * This is an @ref {org.bouncycastle.tls.UDPTransport} performing self-checks
 * regarding silent session loss. Such session loss occurs with Android's
 * Doze mode disturbing the heartbeat mechanism implemented by BC. To compensate,
 * this UDPTransport maintains timestamps of last received package on low-level and
 * will force-close a stale socket.
 */
class SelfCheckingUDPTransport extends UDPTransport {

  private final String TAG = SelfCheckingUDPTransport.class.getName();

  /**
   * The Date when the currently active read operation was started or null if it is
   * completed.
   */
  @Nullable
  private Date lastReadTimeout;

  public SelfCheckingUDPTransport(DatagramSocket socket, int mtu) throws IOException {
    super(socket, mtu);
  }

  @Override
  public int getReceiveLimit() {
      // we do not want to limit incoming packages
      return DTLSTransporter.MAX_MTU - DTLSTransporter.OVERHEAD;
  }

  @Override
  public void send(byte[] buf, int off, int len) throws IOException {
    forceAbortOnTimeoutExcess();
    super.send(buf, off, len);
  }

  /**
   * If we're well over the intended read timeout, flag out an TLS Exception.
   */
  private void forceAbortOnTimeoutExcess() throws IOException {
    final Date currentLimit = lastReadTimeout; // do not use field, race-condition
    if (currentLimit != null && new Date().after(currentLimit)) {
      Log.w(TAG, "Aborting TLS connection because of stale read");
      throw new TlsFatalAlert(AlertDescription.internal_error, "Socket read overdue");
    }
  }

  /**
   * Receive a package into the supplied buffer, waiting for a given time maximum.
   * This overridden method is installing checks regarding the given timing.
   * It is not trying to be precise towards multithreaded overlapping calls,
   * as this probably wouldn't work anyway and doesn't make a difference concerning
   * the socket's state.
   * @param buf a byte[] going to receive the packet
   * @param off an int giving the offset within the buffer
   * @param len an int giving the maximum length to be read
   * @param waitMillis an int giving the number of milliseconds to block read maximum
   * @return an int giving the number of bytes read
   * @throws IOException in case of network problems, socket closure or detected
   *            Doziness of the socket.
   */
  @Override
  public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
    lastReadTimeout = new Date();
    lastReadTimeout.setTime(lastReadTimeout.getTime() + waitMillis + 1000L);
    try {
      // todo remove logging code if re-connecting is fixed
      if (Log.isLoggable(TAG, Log.VERBOSE))
        Log.v(TAG, String.format("Receive called with deadline at %s", lastReadTimeout.toString()));
      int read = super.receive(buf, off, len, waitMillis);
      if (Log.isLoggable(TAG, Log.VERBOSE))
        Log.v(TAG, String.format("Receive finished at %s", new Date().toString()));
      return read;
    } finally {
      lastReadTimeout = null;
    }
  }
}
