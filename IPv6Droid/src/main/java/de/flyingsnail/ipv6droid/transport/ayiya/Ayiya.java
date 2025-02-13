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

package de.flyingsnail.ipv6droid.transport.ayiya;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.AndroidLoggingHandler;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterInputStream;
import de.flyingsnail.ipv6droid.transport.TransporterOutputStream;
import de.flyingsnail.ipv6droid.transport.TunnelBrokenException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * AYIYA - Anything In Anything
 * <p>
 * This realises the tunnel protocol with the PoP in the SixXS network.
 * <p>
 * Based on specifications published by SixXS, see
 * <a href="http://www.sixxs.net/tools/ayiya">Ayiya</a>
 *
 */
public class Ayiya implements Transporter {

    /**
     * The maximum allowed deviation of TIC and local clocks.
     */
    public static final long MAX_TIME_OFFSET = 120;

    /** Tag for Logger */
    private static final Logger logger = AndroidLoggingHandler.getLogger(Ayiya.class);

    private final TicTunnel tunnel;

    /** The port number for AYIYA */
    private final int port = 5072;

    // @todo I'm afraid I missed an official source for this kind of constants
    private static final byte IPPROTO_IPv6 = 41;
    private static final byte IPPROTO_NONE = 59;

    /** size of the AYIYA header */
    public static final int OVERHEAD = 44;

    /** The IPv6 address of the PoP, used as identity in the protocol. */
    private final Inet6Address ipv6Pop;

    /** the maximum transmission unit in bytes */
    private final int mtu;

    /** the maximum packet size */
    private int maxPacketSize = 0;

    /** The IPv4 address of the PoP - the only address we can send packets to. */
    private final Inet4Address ipv4Pop;

    /** Our IPv6 address, in other words, the IPv6 endpoint of the tunnel. */
    private final Inet6Address ipv6Local;

    /** The sha1 hash of the tunnel password */
    private final byte[] hashedPassword;

    /** Expiration time of supplied tunnel */
    private final @Nullable Date expiry;

    /** The socket to the PoP */
    private DatagramSocket socket = null;

    /** keep track if a valid packet has been received yet. This is the final proof that the tunnel
     * is working.
     */
    private boolean validPacketReceived = false;

    private Date lastPacketReceivedTime = new Date();
    private Date lastPacketSentTime = new Date();

    /**
     * Get the specification of the tunnel that this transporter runs.
     *
     * @return TunnelSpec the TunnelSpec of this transporter
     */
    @Override
    public TunnelSpec getTunnelSpec() {
        return tunnel;
    }

    /**
     * Yield the time when the last packet was <b>received</b>. This gives an indication if the
     * tunnel is still alive.
     * @return a Date denoting the time of last packet received.
     */
    @Override
    public Date getLastPacketReceivedTime() {
        return lastPacketReceivedTime;
    }

    /**
     * Yield the time when the last packet was <b>sent</b>. This gives an indication if we should
     * send an heartbeat packet.
     * @return a Date denoting the time of last packet sent.
     */
    @Override
    public Date getLastPacketSentTime() {
        return lastPacketSentTime;
    }

    /**
     * The representation of our identity. This code supports INTEGER only.
     */
    enum Identity
    {
        NONE,  /* None */
        INTEGER,	/* Integer */
        STRING	/* ASCII String */
    }

    /**
     * The algorithm to calculate the hashes of datagrams. This code supports SHA1 only.
     */
    enum HashAlgorithm
    {
        NONE,	/* No hash */
        MD5,	/* MD5 Signature */
        SHA1,	/* SHA1 Signature */
        UMAC	/* UMAC Signature (UMAC: Message Authentication Code using Universal Hashing / draft-krovetz-umac-04.txt */
    }

    /**
     * The authentication type for datagrams. This code supports SHAREDSECRET only.
     */
    enum AuthType
    {
        NONE,	/* No authentication */
        SHAREDSECRED,	/* Shared Secret */
        PGP	/* Public/Private Key */
    }

    /**
     * The code of AYIYA operation. This code supports NOOP and FORWARD only.
     */
    enum OpCode
    {
        NOOP,	/* No Operation */
        FORWARD,	/* Forward */
        ECHO_REQUEST,	/* Echo Request */
        ECHO_REQUEST_FORWARD,	/* Echo Request and Forward */
        ECHO_RESPONSE,	/* Echo Response */
        MOTD,	/* MOTD */
        QUERY_REQUEST,	/* Query Request */
        QUERY_RESPONSE,	/* Query Response */
        FORWARD_RESPONSE /* Resonse to a forward request - flyingsnail extension not present in original protocol */
    }

    /**
     * The error codes sent as responses of unsuccessful packages
     * @author pelzi
     *
     */
    enum ErrorCode {
        INVALID_OPERATION, /* no valid opcode was supplied */
        INVALID_PACKET, /* sent packet does not meet the ayiya specs */
        AUTHENTICATION_FAILED, /* MAC did not macth package content */
        TIMED_OUT, /* tunnel not refreshed in time */
        TIMELAPSE /* server rejects packets because of excessive time difference */
    }


    /**
     * Constructor.
     * @param tunnel the specification of the tunnel to be dealt by this.
     */
    public Ayiya (TicTunnel tunnel) throws ConnectionFailedException {
        if (!tunnel.isValid() || !tunnel.isEnabled())
            throw new IllegalStateException("Invalid or disabled tunnel specification supplied to Ayiya");
        // copy the information relevant for us in local fields
        ipv4Pop = tunnel.getIPv4Pop();
        ipv6Local = tunnel.getIpv6Endpoint();
        ipv6Pop = tunnel.getIpv6Pop();
        mtu = tunnel.getMtu();
        expiry = tunnel.getExpiryDate();
        this.tunnel = tunnel;

        // we only need the hash of the password
        try {
            hashedPassword = ayiyaHash (tunnel.getPassword());
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("Cannot hash password", e);
        }
    }

    private static byte[] ayiyaHash (String s) throws NoSuchAlgorithmException {
        // compute the SHA1 hash of the password
        return ayiyaHash(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] ayiyaHash (byte[] in) throws NoSuchAlgorithmException {
        // compute the SHA1 hash of the password
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        sha1.update(in);
        return sha1.digest();
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
            throw new IllegalStateException("This AYIYA is already connected.");
        }
        // UDP connection
        socket = new DatagramSocket();
        return socket;
    }


    /**
     * Connect the tunnel.
     */
    @Override
    public synchronized void connect() throws IOException, ConnectionFailedException {
        if (socket == null) {
            throw new IllegalStateException("This AYIYA is not prepared for connect.");
        }

        socket.connect(ipv4Pop, port);
        socket.setSoTimeout(0); // no read timeout
        //socket.setSoTimeout(10000); // 10 secs. read timeout

        // beat it!
        try {
            beat();
        } catch (TunnelBrokenException e) {
            throw new ConnectionFailedException("Tunnel broken right from scratch", e);
        }

        logger.info("Ayiya tunnel to POP IP " + ipv4Pop + " created.");
    }

    /**
     * Tell if a valid response has already been received by this instance.
     * @return true if any valid response was already received.
     */
    @Override
    public boolean isValidPacketReceived() {
        // special situation: a packet was received, but is not yet read out - not the sender's
        // fault, really! Here, we ignore this situation, i.e. a tunnel might be classified
        // as troublemaker even if just the receiver thread died.
        return validPacketReceived;
    }

    /**
     * Get the maximum transmission unit (MTU) associated with this Ayiya instance.
     * @return the MTU in bytes
     */
    @Override
    public int getMtu() {
        return mtu;
    }

    /**
     * Send a heartbeat to the PoP
     */
    @Override
    public void beat() throws IOException, TunnelBrokenException {
        if (socket == null)
            throw new IOException("beat() called on unconnected Ayiya");
        if (!socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is not connected", null);
        if (expiry != null && expiry.before(new Date())) {
            throw new TunnelBrokenException("Tunnel expiry date reached", null);
        }
        byte[] ayiyaPacket;
        try {
            ayiyaPacket = buildAyiyaStruct(ByteBuffer.wrap(new byte[0]), OpCode.NOOP,  IPPROTO_NONE);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "SHA1 no longer available???", e);
            throw new TunnelBrokenException("Cannot build ayiya struct", e);
        }
        DatagramPacket dgPacket = new DatagramPacket(ayiyaPacket, ayiyaPacket.length, new InetSocketAddress(ipv4Pop, port));
        socket.send(dgPacket);
        lastPacketSentTime = new Date();
    }

    /**
     * Create a byte from to 4 bit values.
     */
    private static byte buildByte(int val1, int val2) {
        return (byte)((val2 & 0xF) + ((val1 & 0xF) << 4));
             // this should be equiv. to C bitfield behaviour in big-endian machines
    }

    @SuppressLint("Assert")
    private byte[] buildAyiyaStruct(ByteBuffer payload, OpCode opcode, byte nextHeader) throws NoSuchAlgorithmException {
        byte[] retval = new byte[payload.remaining() + OVERHEAD];
        ByteBuffer bb = ByteBuffer.wrap (retval);
        bb.order(ByteOrder.BIG_ENDIAN);
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        // first byte: idlen (=4, 2^4 = length of IPv6 address) and idtype
        bb.put(buildByte(4, Identity.INTEGER.ordinal())).
                // 2nd byte: signature length (5*4 bytes = SHA1) and hash method
                        put(buildByte(5, HashAlgorithm.SHA1.ordinal())).
                // 3rd byte: authmeth and opcode
                        put(buildByte(AuthType.SHAREDSECRED.ordinal(), opcode.ordinal())).
                // 4th byte: next header
                        put(nextHeader).
                // 5th-8th byte: epoch time
                        putInt((int) ((new Date().getTime()) / 1000L)).
                // 9th-24th byte: Identity
                        put(ipv6Local.getAddress())
        ;

        // update the message digest with the bytes so far
        int hashStart = bb.position();
        sha1.update(bb.array(), 0, hashStart);

        // standard ayiya header now finished

        // 25th byte - 44th byte sha1 hash

        // now hash and buffer content diverge. We need to calculate the hash first, because it goes here
        sha1.update(hashedPassword);
        payload.mark();
        sha1.update(payload);
        payload.reset();
        byte[] hash = sha1.digest();
        assert(hash.length == 20);

        // now complete the buffer, with hash...
        bb.put(hash);
        // ...and payload
        bb.put(payload);

        return retval;
    }

    /**
     * Read next packet from the tunnel.
     * @return a ByteBuffer representing a read packets, with current position set to beginning of the <b>payload</b> and end set to end of payload.
     * @throws IOException in case of network problems (probably temporary in nature)
     * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
     */
    @Override
    public ByteBuffer read(ByteBuffer bb) throws IOException, TunnelBrokenException {
        if (socket == null)
            throw new IllegalStateException("read() called on unconnected Ayiya");
        if (!socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is closed", null);


        DatagramPacket dgPacket = new DatagramPacket(bb.array(), bb.arrayOffset(), bb.capacity());

        boolean validResult = false;
        while (!validResult) {
            // read from socket
            socket.receive(dgPacket);
            int bytecount = dgPacket.getLength();

            // first check some pathological results for stability reasons
            if (bytecount > maxPacketSize)
                maxPacketSize = bytecount;
            if (bytecount < 0)
                throw new TunnelBrokenException("Input stream disrupted", null);
            else if (bytecount == 0) {
                logger.log(Level.WARNING, "Received 0 bytes from blocking read..?");
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    throw new TunnelBrokenException ("Received interrupt", e);
                }
                continue;
            } else if (bytecount == bb.capacity()) {
                logger.log(Level.WARNING, "WARNING: maximum size of buffer reached - indication of a MTU problem");
            }

            // update timestamp of last packet received
            lastPacketReceivedTime = new Date();

            // prepare and fill the ByteBuffer
            bb.limit(bytecount);
            bb.position(OVERHEAD);
            if (checkValidity(bb.array(), bb.arrayOffset(), bb.limit())) {
                OpCode opCode = getSupportedOpCode(bb.array(), bb.arrayOffset(), bb.limit());
                validPacketReceived = validPacketReceived || (opCode != null);
                     // note: this flag must never be reset to false!
                validResult =
                        (opCode == OpCode.FORWARD) || (opCode == OpCode.ECHO_REQUEST_FORWARD);
                if (opCode == OpCode.ECHO_RESPONSE) {
                    logger.info("Received valid echo response");
                }
                if (opCode == OpCode.FORWARD_RESPONSE) {
                    logger.warning("Received high level error code from peer");
                    ErrorCode error = getErrorCode(bb.array(), bb.arrayOffset(), bb.limit());
                    if (error == null) {
                        logger.warning("Unknown error code");
                    } else {
                        switch (error) {
                            case AUTHENTICATION_FAILED:
                                throw new TunnelBrokenException("Received error code authentication failed from peer", null);
                            case TIMELAPSE:
                                throw new TunnelBrokenException("Please check clock and timezone setting", null);
                            default:
                        }
                    }
                }
            } else {
                ErrorCode errorCode = checkErrorPacket(bb.array(), bb.arrayOffset(), bb.limit());
                if (errorCode != null) {
                    logger.info("Received low-level error packet, aborting tunnel");
                    throw new TunnelBrokenException(
                            errorCode == ErrorCode.TIMELAPSE
                                    ? "Please check clock and timezone setting"
                                    : "Server unwilling to serve us", null);
                }
            }
        }

        return bb;
    }

    private OpCode getSupportedOpCode (byte[] packet, int offset, int bytecount) {
        if (bytecount < 3) {
            logger.log(Level.WARNING, "Received too short package");
            return null;
        }

        try {
            int opCodeOrdinal = packet[2+offset] &0xF;
            return OpCode.values()[opCodeOrdinal];
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private ErrorCode getErrorCode (byte[] packet, int offset, int bytecount) {
        if (bytecount < 3) {
            logger.log(Level.WARNING, "Received too short package");
            return null;
        }

        try {
            int opCodeOrdinal = packet[OVERHEAD+offset];
            return ErrorCode.values()[opCodeOrdinal];
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private boolean checkValidity(byte[] packet, int offset, int bytecount) throws UnknownHostException, TunnelBrokenException {
        // @todo refactor these checks, they look awful and are co-variant with buildAyiyaStruct.
        // @todo never tested with offset > 0, if this part is ever going to be a library, you have to.
        // check if the size includes at least a full ayiya header
        if (bytecount < OVERHEAD) {
            logger.log(Level.WARNING, "Received too short package, skipping");
            return false;
        }

        // check if correct AYIYA packet
        if (buildByte(4, Identity.INTEGER.ordinal()) != packet[offset] ||
                buildByte(5, HashAlgorithm.SHA1.ordinal()) != packet[1+offset] ||
                AuthType.SHAREDSECRED.ordinal() != (packet[2+offset] >> 4) ||
                (getSupportedOpCode(packet, offset, bytecount) == null) ||
                ((packet[3+offset] != IPPROTO_IPv6) && (packet[3+offset] != IPPROTO_NONE))
                ) {
            logger.log(Level.WARNING, "Received packet with invalid ayiya header, skipping");
            return false;
        }

        // check if correct sender id. Strictly speaking not correct, as the sender could use our
        // id. This is considered valid here because in assertion mode we're using this method for
        // our own packets as well.
        Inet6Address sender = (Inet6Address)Inet6Address.getByAddress(
                Arrays.copyOfRange(packet, 8+offset, 24+offset));
        if (!sender.equals(ipv6Pop) && !sender.equals(ipv6Local)) {
            logger.log(Level.WARNING, "Received packet from invalid sender id " + sender);
            return false;
        }

        // check time
        ByteBuffer bb = ByteBuffer.wrap(packet, 4+offset, 4);
        int epochTimeRemote = bb.getInt();
        int epochTimeLocal = (int) (new Date().getTime() / 1000);
        if (Math.abs(epochTimeLocal - epochTimeRemote) > MAX_TIME_OFFSET) {
            logger.log(Level.WARNING, "Received packet from " + (epochTimeLocal-epochTimeRemote) + " in the past");
            return false;
        }

        // check signature
        byte[] theirHash = Arrays.copyOfRange(packet, 24+offset, 44+offset);

        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new TunnelBrokenException("Unable to do sha1 hashes", e);
        }
        sha1.update(packet, offset, 24);
        sha1.update(hashedPassword);
        sha1.update(packet, 44+offset, bytecount-44);
        byte[] myHash = sha1.digest();
        if (!Arrays.equals(myHash, theirHash)) {
            logger.log(Level.WARNING, "Received packet with failed hash comparison");
            return false;
        }

        // check ipv6
        if (packet[3+offset] == IPPROTO_IPv6 && (packet[OVERHEAD +offset] >> 4) != 6) {
            logger.log(Level.WARNING, "Payload should be an IPv6 packet, but isn't");
            return false;
        }

        // this packet appears to be valid!
        return true;
    }

    /**
     * Check if a packet is a low-level error message from the server.
     *
     * @param packet the byte[] to check
     * @param offset the int giving the offset into the array to start
     * @param bytecount the int giving the number of bytes to consider
     * @return the reported ErrorCode if the packet is a low-level error messsage, null otherwise.
     */
    private @Nullable ErrorCode checkErrorPacket(byte[] packet, int offset, int bytecount) {
        // check if the size includes at least a full ayiya header
        if (bytecount != 4) {
            logger.warning("Received strange packet, not a low-level error packet (wrong length)");
            return null;
        }

        // check "magic" bytes
        if (packet[offset] == 0 && packet[offset + 2] == 0 && packet [offset + 3] == 0) {
            ErrorCode errorCode = getErrorCode(packet, offset + 1, 3);
            if (errorCode == null) {
                logger.warning("Received strange packet, correct length and magic bytes, but unkown error code");
                return null;
            } else {
                logger.log(Level.WARNING, "Received low-level error message from server, error code is " + errorCode);
                return errorCode;
            }
        } else {
            logger.warning("Received strange packet, correct length but no magic bytes");
            return null;
        }
    }
    /**
     * Writes a packet to the tunnel.
     * @param payload the payload to send (an IP packet itself...)
     * @throws IOException in case of network problems (probably temporary in nature)
     * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
     */
    @Override
    @SuppressLint("Assert")
    public void write(ByteBuffer payload) throws IOException, TunnelBrokenException {
        if (socket == null || !socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is closed", null);

        byte[] ayiyaPacket;
        try {
            ayiyaPacket = buildAyiyaStruct(payload, OpCode.FORWARD, IPPROTO_IPv6);
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "SHA1 no longer available???", e);
            throw new TunnelBrokenException("Cannot build ayiya struct", e);
        }
        if (!checkValidity(ayiyaPacket, 0, ayiyaPacket.length)) {
            throw new AssertionError();
        }
        DatagramPacket dgPacket = new DatagramPacket(ayiyaPacket, ayiyaPacket.length, socket.getRemoteSocketAddress());
        socket.send(dgPacket);
        lastPacketSentTime = new Date();
    }

  /**
     * Provides an InputStream on the tunnel's payload. Only sensible use is to provide enough
     * buffer to read one datagram at a time. In this case, each call will receive one packet
     * send by the tunnel.
     * @return the InputStream.
     */
    @Override
    public InputStream getInputStream() {
        return new TransporterInputStream(this);
    }

  /**
     * Provides an OutputStream on the tunnel. Any write should give a whole tcp package to transmit.
     * @return the OutputStream
     */
    @Override
    public OutputStream getOutputStream() {
        return new TransporterOutputStream(this);
    }

    /**
     * Close our socket. Basically that's about it.
     */
    @Override
    public synchronized void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null; // it's useless anyway
        logger.info("Ayiya tunnel closed");
    }
}
