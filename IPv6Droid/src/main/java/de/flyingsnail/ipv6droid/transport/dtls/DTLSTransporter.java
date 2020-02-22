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

import android.util.Log;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.TlsClient;
import org.bouncycastle.tls.UDPTransport;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Date;

import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterInputStream;
import de.flyingsnail.ipv6droid.transport.TransporterOutputStream;
import de.flyingsnail.ipv6droid.transport.TunnelBrokenException;

public class DTLSTransporter implements Transporter {
  public static final String TUNNEL_TYPE = TransporterParams.TUNNEL_TYPE;
  private final static String TAG = DTLSTransporter.class.getName();
  private Date lastPacketReceivedTime;
  private Date lastPacketSentTime;
  private DatagramSocket socket;
  /** The port number for dtls transport */
  private int port = 5073;
  private DTLSTransport dtls = null;
  private int maxPacketSize = 0;
  private boolean validPacketReceived = false;

  private Inet4Address ipv4Pop;
  private final int mtu;
    /**
     * The size of our receive buffers. We do not want to limit the transmission by our buffers...
     */
  static final int MAX_MTU = 64*1024;
  private final int heartbeat;


  private final Certificate certChain;

  private final AsymmetricKeyParameter privateKey;

  private final TlsCrypto crypto;


  public DTLSTransporter (TransporterParams params) {
    crypto = new BcTlsCrypto(new SecureRandom());

    ipv4Pop = params.getIPv4Pop();
    port = params.getPortPop();
    mtu = params.getMtu();
    heartbeat = params.getHeartbeatInterval();

    try {
      this.certChain = DTLSUtils.parseCertificateChain(crypto, params.getCertChain());
    } catch (IOException e) {
      throw new IllegalStateException("Incorrectly bundled, failure to read certificates", e);
    }
    try {
      this.privateKey = DTLSUtils.parseBcPrivateKeyString(params.getPrivateKey());
    } catch (IOException e) {
      throw new IllegalStateException("Incorrectly bundled, failure to read private key", e);
    }
    Log.i(TAG, "DTLS transporter constructed");
  }

  /**
   * Yield the time when the last packet was <b>received</b>. This gives an indication if the
   * tunnel is still alive.
   *
   * @return a Date denoting the time of last packet received.
   */
  @Override
  public Date getLastPacketReceivedTime() {
    return lastPacketReceivedTime;
  }

  /**
   * Yield the time when the last packet was <b>sent</b>. This gives an indication if we should
   * send an heartbeat packet.
   *
   * @return a Date denoting the time of last packet sent.
   */
  @Override
  public Date getLastPacketSentTime() {
    return lastPacketSentTime;
  }

  /**
   * Check if this object is in a functional state
   *
   * @return a boolean, true if socket is still connected
   */
  @Override
  public boolean isAlive() {
    return socket != null && socket.isConnected();
  }

  /**
   * Connect the tunnel.
   */
  @Override
  public void connect() throws IOException, ConnectionFailedException {
    if (socket != null) {
      throw new IllegalStateException("This AYIYA is already connected.");
    }

    validPacketReceived = false;

    // UDP connection
    socket = new DatagramSocket();
    socket.connect(ipv4Pop, port);
    socket.setSoTimeout(10000); // no read timeout

    DatagramTransport transport = new UDPTransport(socket, mtu) {
        @Override
        public int getReceiveLimit() {
            // we do not want to limit incoming packages
            return MAX_MTU;
        }
    };
    TlsClient client = new IPv6DTlsClient(crypto, heartbeat, certChain, privateKey);
    DTLSClientProtocol protocol = new DTLSClientProtocol();
    dtls = protocol.connect(client, transport);

    Log.i(TAG, "DTLS tunnel to POP IP " + ipv4Pop + " created.");
  }

  /**
   * Re-Connect the tunnel, closing the existing socket
   */
  @Override
  public void reconnect() throws IOException, ConnectionFailedException {
    if (socket == null)
      throw new IllegalStateException("Ayiya object is closed or not initialized");
    close();
    connect();
  }

  /**
   * Tell if a valid response has already been received by this instance.
   *
   * @return true if any valid response was already received.
   */
  @Override
  public boolean isValidPacketReceived() {
    return validPacketReceived;
  }

  /**
   * Return the number of invalid packages received yet.
   *
   * @return an int representing the number.
   */
  @Override
  public int getInvalidPacketCounter() {
    return 0; // invalid packets are handled by lower levels
  }

  /**
   * Get the maximum transmission unit (MTU) associated with this DTLS instance.
   *
   * @return the MTU in bytes
   */
  @Override
  public int getMtu() {
    try {
      return dtls == null ? 0 : dtls.getSendLimit();
    } catch (IOException e) {
      Log.e(TAG, "Exception reading send limit from dtls", e);
      return 0;
    }
  }

  /**
   * Send a heartbeat to the PoP. In DTLS, this is just check connection validity, as heartbeat
   * is handled entirely different.
   */
  @Override
  public void beat() throws IOException, TunnelBrokenException {
    if (socket == null)
      throw new IOException("beat() called on unconnected DTLS");
    if (!socket.isConnected())
      throw new TunnelBrokenException("Socket to PoP is not connected", null);
  }

  /**
   * Read next packet from the tunnel.
   *
   * @param bb
   * @return a ByteBuffer representing a read packets, with current position set to beginning of the <b>payload</b> and end set to end of payload.
   * @throws IOException           in case of network problems (probably temporary in nature)
   * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
   */
  @Override
  public ByteBuffer read(ByteBuffer bb) throws IOException, TunnelBrokenException {
    Log.d(TAG, "DTLS Transport reading");
    if (socket == null || dtls == null)
      throw new IllegalStateException("read() called on unconnected DTLSTransporter");
    if (!socket.isConnected())
      throw new TunnelBrokenException("Socket to PoP is closed", null);

    boolean validResult = false;
    while (!validResult) {
      // read from socket
      int bytecount = dtls.receive(bb.array(), bb.arrayOffset(), bb.capacity(), 10000);


      if (bytecount > maxPacketSize)
        maxPacketSize = bytecount;

      // first check some pathological results for stability reasons
      if (bytecount < 0) {
        Log.i(TAG, "DTLS transporter ends: connection terminated");
        throw new TunnelBrokenException("Input stream disrupted", null);
      } else if (bytecount == 0) {
        Log.d(TAG, "Received 0 bytes within timeout");
        try {
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          throw new TunnelBrokenException ("Received interrupt", e);
        }
        continue;
      } else if (bytecount == bb.capacity()) {
        Log.e(TAG, "WARNING: maximum size of buffer reached - indication of a MTU problem");
      }

      // update timestamp of last packet received
      lastPacketReceivedTime = new Date();
      validResult = true;
      validPacketReceived = true;

      // prepare and fill the ByteBuffer
      bb.limit(bytecount);
      bb.position(0);
    }

    return bb;
  }

  /**
   * Writes a packet to the tunnel.
   *
   * @param payload the payload to send (an IP packet itself...)
   * @throws IOException           in case of network problems (probably temporary in nature)
   * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
   */
  @Override
  public void write(ByteBuffer payload) throws IOException, TunnelBrokenException {
    if (socket == null || dtls == null)
      throw new IllegalStateException("write(byte[]) called on unconnected DTLSTransporter");
    if (!socket.isConnected())
      throw new TunnelBrokenException("Socket to PoP is closed", null);

    dtls.send(payload.array(), payload.arrayOffset()+payload.position(), payload.remaining());

    lastPacketSentTime = new Date();
  }

  /**
   * Provides an InputStream on the tunnel's payload. Only sensible use is to provide enough
   * buffer to read one datagram at a time. In this case, each call will receive one packet
   * send by the tunnel.
   *
   * @return the InputStream.
   */
  @Override
  public InputStream getInputStream() {
    return new TransporterInputStream(this);  }

  /**
   * Provides an OutputStream on the tunnel. Any write should give a whole tcp package to transmit.
   *
   * @return the OutputStream
   */
  @Override
  public OutputStream getOutputStream() {
    return new TransporterOutputStream(this);
  }

  /**
   * This can be used by friendly classes to protect this socket from tunneling, query its state, etc.
   */
  @Override
  public DatagramSocket getSocket() {
    return socket;
  }

  /**
   * Return the number of bytes of overhead required by this transport on each packet.
   *
   * @return an int giving the number of bytes of overhead
   */
  @Override
  public int getOverhead() {
    return 0;
  }

  /**
   * Close our socket. Basically that's about it.
   */
  @Override
  public void close() {
    if (dtls != null) {
      try {
        dtls.close();
      } catch (IOException e) {
        Log.e(TAG, "Unable to close dtls connection cleanly", e);
      }
    }
    if (socket != null && !socket.isClosed()) {
      socket.close();
    }
    socket = null; // it's useless anyway
    dtls = null;
    Log.i(TAG, "DTLS tunnel closed");
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "#" + socket.getLocalAddress().getHostAddress() + ":"+ socket.getLocalPort();
  }

  /**
   * Configure this DTLSTransporter to use a different UDP port on IPv4.
   *
   * @param port an int giving the port number to use.
   * @todo this should eventually become an attribute of TicTunnel
   */
  @Override
  public void setPort(int port) {
    this.port = port;
  }

}
