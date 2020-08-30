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

import androidx.annotation.NonNull;

import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.DTLSClientProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.SignatureAlgorithm;
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

import de.flyingsnail.ipv6droid.android.dtlsrequest.AndroidBackedKeyPair;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterInputStream;
import de.flyingsnail.ipv6droid.transport.TransporterOutputStream;
import de.flyingsnail.ipv6droid.transport.TunnelBrokenException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class DTLSTransporter implements Transporter {
  public static final String TUNNEL_TYPE = TransporterParams.TUNNEL_TYPE;
  private final static String TAG = DTLSTransporter.class.getName();
  private final TransporterParams params;
  private final AndroidBackedKeyPair keyPair;
  private final String dnsName;
  private Date lastPacketReceivedTime;
  private Date lastPacketSentTime;
  private DatagramSocket socket;
  private int port;
  private DTLSTransport dtls = null;
  private int maxPacketSize = 0;
  private boolean validPacketReceived = false;

  private final static int OVERHEAD = 92;

  private Inet4Address ipv4Pop;
  private final int mtu;
    /**
     * The size of our receive buffers. We do not want to limit the transmission by our buffers...
     */
  private static final int MAX_MTU = 64*1024;
  private final int heartbeat;


  private final Certificate certChain;

  private final TlsCrypto crypto;


  public DTLSTransporter (@NonNull TransporterParams params) {
    crypto = new BcTlsCrypto(new SecureRandom()) {
      public boolean hasSignatureAlgorithm (short signatureAlgorithm) {
        return signatureAlgorithm == SignatureAlgorithm.rsa;
      }
    };

    this.params = params;
    // IPv4Pop needs network to be resolvable, so we postpone reading it until connect()
    port = params.getPortPop();
    mtu = params.getMtu();
    heartbeat = params.getHeartbeatInterval();
    certChain = params.getCertChain();
    keyPair = params.getKeyPair();
    dnsName = params.getDnsPop();

    Log.i(TAG, "DTLS transporter constructed");
  }

  /**
   * Get the specification of the tunnel that this transporter runs.
   *
   * @return TunnelSpec the TunnelSpec of this transporter
   */
  @Override
  public TunnelSpec getTunnelSpec() {
    return params;
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
   * Prepare for connection, esp. create an unconnected DatagramSocket. This enables the parent
   * object to bind the socket to a network.
   *
   * @return the DatagramSocket that is going to be used for native traffic
   * @throws IOException in case of trouble preparing the socket
   */
  @Override
  public DatagramSocket prepare() throws IOException {
    if (socket != null) {
      throw new IllegalStateException("This DTLSTransporter is already prepared.");
    }

    validPacketReceived = false;

    // UDP connection
    socket = new DatagramSocket();
    return socket;
  }

  /**
   * Connect the tunnel.
   */
  @Override
  public void connect() throws IOException {
    if (socket == null) {
      throw new IllegalStateException("This DTLSTransporter is not prepared.");
    }

    if (socket.isConnected()){
      throw new IllegalStateException("This DTLSTransporter is already connected.");
    }
    if (ipv4Pop == null) {
      ipv4Pop = params.getIPv4Pop();
    }
    if (ipv4Pop == null) {
      throw new IOException("No PoP address resolvable");
    }

    socket.connect(ipv4Pop, port);
    // we need a timeout for the connect phase, otherwise we're facing infinite hangs
    socket.setSoTimeout(10000);

    DatagramTransport transport = new UDPTransport(socket, mtu + 2*OVERHEAD) {
        @Override
        public int getReceiveLimit() {
            // we do not want to limit incoming packages
            return MAX_MTU - OVERHEAD;
        }
    };
    TlsClient client = new IPv6DTlsClient(crypto, heartbeat, certChain, keyPair, dnsName);
    DTLSClientProtocol protocol = new DTLSClientProtocol();
    dtls = protocol.connect(client, transport);

    // after the connect, we do not want a timeout
    socket.setSoTimeout(heartbeat); // with every heartbeat interval, there should be communication

    Log.i(TAG, "DTLS tunnel to POP IP " + ipv4Pop + " created.");
  }

  /**
   * Re-Connect the tunnel, closing the existing socket
   */
  @Override
  public void reconnect() throws IOException {
    if (socket == null)
      throw new IllegalStateException("DTLSTransporter is closed or not initialized");
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
   * @param bb a ByteBuffer to receive a read packet
   * @return the same ByteBuffer representing a read packet, with current position set to beginning of the <b>payload</b> and end set to end of payload.
   * @throws IOException           in case of network problems (probably temporary in nature)
   * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
   */
  @Override
  public ByteBuffer read(ByteBuffer bb) throws IOException, TunnelBrokenException {
    if (socket == null || dtls == null)
      throw new IllegalStateException("read() called on unconnected DTLSTransporter");
    if (!socket.isConnected())
      throw new TunnelBrokenException("Socket to PoP is closed", null);

    boolean validResult = false;
    while (!validResult) {
      // read from socket, no sensible timeout required as dtls will handle alive messages
      int bytecount = dtls.receive(bb.array(), bb.arrayOffset(), bb.capacity(), Integer.MAX_VALUE);

      if (bytecount > maxPacketSize)
        maxPacketSize = bytecount;

      if (bytecount <= 0) {
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
    if (payload.remaining() > mtu)
      throw new IOException("Too big packet received: " + payload.remaining() + " (MTU: " + mtu + ")");

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
  public @NonNull String toString() {
    return getClass().getSimpleName() + "#" + socket.getLocalAddress().getHostAddress() + ":"+ socket.getLocalPort();
  }

  /**
   * Configure this DTLSTransporter to use a different UDP port on IPv4.
   *
   * @param port an int giving the port number to use.
   * todo this should eventually become an attribute of TicTunnel
   */
  @Override
  public void setPort(int port) {
    this.port = port;
  }

}
