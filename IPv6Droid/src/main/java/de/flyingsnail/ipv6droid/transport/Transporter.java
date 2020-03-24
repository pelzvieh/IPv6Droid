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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Date;

public interface Transporter {
  /**
   * Get the specification of the tunnel that this transporter runs.
   * @return TunnelSpec the TunnelSpec of this transporter
   */
  TunnelSpec getTunnelSpec();

  /**
   * Yield the time when the last packet was <b>received</b>. This gives an indication if the
   * tunnel is still alive.
   * @return a Date denoting the time of last packet received.
   */
  Date getLastPacketReceivedTime();

  /**
   * Yield the time when the last packet was <b>sent</b>. This gives an indication if we should
   * send an heartbeat packet.
   * @return a Date denoting the time of last packet sent.
   */
  Date getLastPacketSentTime();

  /**
   * Check if this object is in a functional state
   * @return a boolean, true if socket is still connected
   */
  boolean isAlive();

  /**
   * Connect the tunnel.
   */
  void connect() throws IOException, ConnectionFailedException;

  /**
   * Re-Connect the tunnel, closing the existing socket
   */
  void reconnect() throws IOException, ConnectionFailedException;

  /**
   * Tell if a valid response has already been received by this instance.
   * @return true if any valid response was already received.
   */
  boolean isValidPacketReceived();

  /**
   * Return the number of invalid packages received yet.
   * @return an int representing the number.
   */
  int getInvalidPacketCounter();

  /**
   * Get the maximum transmission unit (MTU) associated with this Ayiya instance.
   * @return the MTU in bytes
   */
  int getMtu();

  /**
   * Send a heartbeat to the PoP
   */
  void beat() throws IOException, TunnelBrokenException;

  /**
   * Read next packet from the tunnel.
   * @return a ByteBuffer representing a read packets, with current position set to beginning of the <b>payload</b> and end set to end of payload.
   * @throws IOException in case of network problems (probably temporary in nature)
   * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
   */
  ByteBuffer read(ByteBuffer bb) throws IOException, TunnelBrokenException;

  /**
   * Writes a packet to the tunnel.
   * @param bb the payload to send (an IP packet itself...), defined by position and limit
   * @throws IOException in case of network problems (probably temporary in nature)
   * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
   */
  void write(ByteBuffer bb) throws IOException, TunnelBrokenException;

  /**
   * Provides an InputStream on the tunnel's payload. Only sensible use is to provide enough
   * buffer to read one datagram at a time. In this case, each call will receive one packet
   * send by the tunnel.
   * @return the InputStream.
   */
  InputStream getInputStream();

  /**
   * Provides an OutputStream on the tunnel. Any write should give a whole tcp package to transmit.
   * @return the OutputStream
   */
  OutputStream getOutputStream();

  /** This can be used by friendly classes to protect this socket from tunneling, query its state, etc. */
  DatagramSocket getSocket();

  /**
   * Close our socket. Basically that's about it.
   */
  void close();

  /**
   * Configure this AYIYA to use a different UDP port on IPv4.
   * @todo this should eventually become an attribute of TicTunnel
   * @param port an int giving the port number to use.
   */
  void setPort(int port);

  /**
   * Return the number of bytes of overhead required by this transport on each packet.
   * @return an int giving the number of bytes of overhead
   */
  int getOverhead();
}
