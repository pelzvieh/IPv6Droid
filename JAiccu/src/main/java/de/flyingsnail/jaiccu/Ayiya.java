package de.flyingsnail.jaiccu;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;

/**
 * Created by pelzi on 17.08.13.
 *
 * AYIYA - Anything In Anything
 *
 * This realises the tunnel setup protocol with the PoP in the SixXS network.
 *
 * See http://www.sixxs.net/tools/ayiya for specification.
 *
 * Copyright 2003-2005 SixXS - http://www.sixxs.net
 * Copyright 2013 Dr. Andreas Feldner
 * @todo clarify to avoid copyright issues with SixXS
 * See license.
 */
public class Ayiya {
    /**
     *  Anything In Anything - AYIYA (uses UDP in our case)
     */
    public static final int PORT = 5072;

    /**
     * AYIYA version (which document this should conform to)
     * Per draft-massar-v6ops-ayiya-02 (July 2004)
     */
    public static final String VERSION = "draft-02";

    /** Tag for Logger */
    private static final String TAG = Ayiya.class.getName();

    /** The port number for AYIYA */
    public static final int AYIYA_PORT = 5072;

    // @todo I'm afraid I missed an official source for this kind of constants
    private static final byte IPPROTO_IPv6 = 41;
    private static final byte IPPROTO_NONE = 59;
    private static final int AYIYA_OVERHEAD = 44;

    /** The IPv6 address of the PoP, used as identiy in the protocol. */
    private final Inet6Address ipv6Pop;

    /** the maximum transmission unit in bytes */
    private final int mtu;

    /** The IPv4 address of the PoP - the only address we can send packets to. */
    private Inet4Address ipv4Pop;

    /** Our IPv6 address, in other words, the IPv6 endpoint of the tunnel. */
    private Inet6Address ipv6Local;

    /** The tunnel password as clear text */
    private String password;

    /** The sha1 hash of the tunnel password */
    private byte[] hashedPassword;

    /** The socket to the PoP */
    private DatagramSocket socket = null;

    // @TODO Prüfen, worfür diese genutzt werden. Enum oder Hashmap??
    enum Identity
    {
        NONE,  /* None */
        INTEGER,	/* Integer */
        STRING	/* ASCII String */
    };

    enum HashAlgorithm
    {
        NONE,	/* No hash */
        MD5,	/* MD5 Signature */
        SHA1,	/* SHA1 Signature */
        UMAC	/* UMAC Signature (UMAC: Message Authentication Code using Universal Hashing / draft-krovetz-umac-04.txt */
    };

    enum AuthType
    {
        NONE,	/* No authentication */
        SHAREDSECRED,	/* Shared Secret */
        PGP	/* Public/Private Key */
    };

    enum OpCode
    {
        NOOP,	/* No Operation */
        FORWARD,	/* Forward */
        ECHO_REQUEST,	/* Echo Request */
        ECHO_REQUEST_FORWARD,	/* Echo Request and Forward */
        ECHO_RESPONSE,	/* Echo Response */
        MOTD,	/* MOTD */
        QUERY_REQUEST,	/* Query Request */
        QUERY_RESPONSE	/* Query Response */
    };


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
        // @todo check actual requirements later: if we don't have to remember clear text pw, remove the field and calculate hash here.
        // otherwise, calculate hash whereever it is needed from remembered clear text pw.
        password = tunnel.getPassword();
        try {
            hashedPassword = ayiyaHash (password);
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("Cannot hash password", e);
        } catch (UnsupportedEncodingException e) {
            throw new ConnectionFailedException("Cannot hash password", e);
        }
    }

    private static byte[] ayiyaHash (String s) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // compute the SHA1 hash of the password
        return ayiyaHash(s.getBytes("UTF-8"));
    }

    private static byte[] ayiyaHash (byte[] in) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        // compute the SHA1 hash of the password
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        return sha1.digest(in);
    }

    /**
     * Connect the tunnel.
     */
    public void connect() throws IOException, ConnectionFailedException {
        if (socket != null) {
            throw new IllegalStateException("This Tic is already connected.");
        }

        // UDP connection
        socket = new DatagramSocket();
        socket.connect(ipv4Pop, AYIYA_PORT);

        // beat it!
        try {
            beat();
        } catch (TunnelBrokenException e) {
            throw new ConnectionFailedException("Tunnel broken right from scratch", e);
        }

        Log.i(TAG, "Ayiya tunnel to POP IP " + ipv4Pop + "created.");
    }

    public boolean isAlive() {
        return (socket != null && socket.isConnected());
    }

    /**
     * Send a heartbeat to the PoP
     */
    public void beat() throws IOException, TunnelBrokenException {
        if (socket == null)
            throw new IllegalStateException("beat() called on unconnected Ayiya");
        if (!socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is not connected", null);
        byte[] ayiyaPacket;
        try {
            ayiyaPacket = buildAyiyaStruct(new byte[0], OpCode.NOOP,  IPPROTO_NONE);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "SHA1 no longer available???", e);
            throw new TunnelBrokenException("Cannot build ayiya struct", e);
        }
        DatagramPacket dgPacket = new DatagramPacket(ayiyaPacket, ayiyaPacket.length, socket.getRemoteSocketAddress());
        socket.send(dgPacket);
    }

    /**
     * Create a byte from to 4 bit values.
     */
    private static byte buildByte(int val1, int val2) {
        byte retval = (byte)((val1 & 0xF) + ((val2 & 0xF) << 4));
        return retval;
    }

    private byte[] buildAyiyaStruct(byte[] payload, OpCode opcode, byte nextHeader) throws NoSuchAlgorithmException {
        byte[] retval = new byte[payload.length + AYIYA_OVERHEAD];
        ByteBuffer bb = ByteBuffer.wrap (retval);
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
                        putInt((int) ((new Date().getTime()) / 1000l)).
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
        sha1.update(payload);
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
     * @return a byte array representing a read packets <b>payload</b>.
     * @throws IOException in case of network problems (probably temporary in nature)
     * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
     */
    public byte[] read() throws IOException, TunnelBrokenException {
        if (socket == null)
            throw new IllegalStateException("read() called on unconnected Ayiya");
        if (!socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is closed", null);

        // @todo insanely inefficient! Probably allocation strategy is required + refactoring of this methods parameters and returns.
        byte[] packet = new byte[AYIYA_OVERHEAD + mtu];
        int bytecount = 0;
        DatagramPacket dgPacket = new DatagramPacket(packet, packet.length);

        boolean validResult = false;
        while (!validResult) {
            // read from socket
            socket.receive(dgPacket);
            bytecount = dgPacket.getLength();
            if (bytecount < 0)
                throw new TunnelBrokenException("Input stream disrupted", null);
            else if (bytecount == 0) {
                Log.e(TAG, "Received 0 bytes from blocking read..?");
                try {
                    Thread.sleep(100l);
                } catch (InterruptedException e) {
                    throw new TunnelBrokenException ("Received interrupt", e);
                }
            }

            // @todo refactor these checks.
            // check if the size includes at least a full ayiya header
            if (bytecount < AYIYA_OVERHEAD) {
                Log.e(TAG, "Received too short package, skipping");
                continue;
            }

            // check if correct AYIYA packet
            if (buildByte(4, Identity.INTEGER.ordinal()) != packet[0] ||
                    buildByte(5, HashAlgorithm.SHA1.ordinal()) != packet[1] ||
                    AuthType.SHAREDSECRED.ordinal() != (packet[2] & 0xF) ||
                    ((packet[2] >> 4 != OpCode.FORWARD.ordinal()) &&
                            (packet[2] >> 4 != OpCode.ECHO_REQUEST.ordinal()) &&
                            (packet[2] >> 4 != OpCode.ECHO_REQUEST_FORWARD.ordinal())) ||
                    ((packet[3] != IPPROTO_IPv6) && (packet[3] != IPPROTO_NONE))
                    ) {
                Log.e(TAG, "Received packet with invalid ayiya header, skipping");
                continue;
            }

            // check if correct sender id
            Inet6Address sender = (Inet6Address)Inet6Address.getByAddress(Arrays.copyOfRange(packet, 8, 23));
            if (!sender.equals(ipv6Pop)) {
                Log.e(TAG, "Received packet from invalid sender id " + sender);
            }

            // check time
            ByteBuffer bb = ByteBuffer.wrap(packet, 4, 4);
            int epochTimeRemote = bb.getInt();
            int epochTimeLocal = (int) (new Date().getTime() / 1000);
            if (Math.abs(epochTimeLocal - epochTimeRemote) > Tic.MAX_TIME_OFFSET) {
                Log.e(TAG, "Received packet from " + (epochTimeLocal-epochTimeRemote) + " in the past");
                continue;
            }

            // check signature
            byte[] theirHash = Arrays.copyOfRange(packet, 24, 43);
            bb = ByteBuffer.wrap(packet, 24, 43);
            bb.put (hashedPassword); // note that this changes retval
            MessageDigest sha1 = null;
            try {
                sha1 = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                throw new TunnelBrokenException("Unable to do sha1 hashes", e);
            }
            sha1.update(packet, 0, bytecount);
            byte[] myHash = sha1.digest();
            if (!myHash.equals(theirHash)) {
                Log.e(TAG, "Received packet with failed hash comparison");
                continue;
            }

            // check ipv6
            if (packet[3] == IPPROTO_IPv6 && (packet[AYIYA_OVERHEAD] >> 4) != 6) {
                Log.e(TAG, "Payload should be an IPv6 packet, but isn't");
                continue;
            }

            // this packet appears to be valid!
            validResult = true;

        }

        return Arrays.copyOfRange(packet, AYIYA_OVERHEAD, bytecount - 1);
    }

    /**
     * Writes a packet to the tunel.
     * @param payload the payload to send (an IP packet itself...)
     * @throws IOException in case of network problems (probably temporary in nature)
     * @throws TunnelBrokenException in case that this tunnel is no longer usable and must be restarted
     */
    public void write(byte[] payload) throws IOException, TunnelBrokenException {
        if (socket == null)
            throw new IllegalStateException("write(byte[]) called on unconnected Ayiya");
        if (!socket.isConnected())
            throw new TunnelBrokenException("Socket to PoP is closed", null);

        byte[] ayiyaPacket;
        try {
            ayiyaPacket = buildAyiyaStruct(payload, OpCode.FORWARD, IPPROTO_IPv6);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "SHA1 no longer available???", e);
            throw new TunnelBrokenException("Cannot build ayiya struct", e);
        }
        DatagramPacket dgPacket = new DatagramPacket(ayiyaPacket, ayiyaPacket.length, socket.getRemoteSocketAddress());
        socket.send(dgPacket);
    }

    private class AyiyaInputStream extends InputStream {
        private byte[] streamBuffer = new byte[0];
        private int bufferReadPointer = 0;

        private void ensureBuffer() throws IOException {
            synchronized (streamBuffer) {
                while (streamBuffer.length == bufferReadPointer) {
                    try {
                        streamBuffer = Ayiya.this.read();
                    } catch (TunnelBrokenException e) {
                        throw new IOException(e);
                    }
                    bufferReadPointer = 0;
                }
            }
        }

        @Override
        public int read() throws IOException {
            // complicated, hopefully nobody uses this
            synchronized (streamBuffer) {
                ensureBuffer();
                return streamBuffer[bufferReadPointer++];
            }
        }

        private int read (ByteBuffer bb) throws IOException {
            synchronized (streamBuffer) {
                ensureBuffer();
                int byteCount = Math.max(streamBuffer.length - bufferReadPointer, bb.capacity());
                bb.put(streamBuffer, bufferReadPointer, byteCount);
                bufferReadPointer += byteCount;
                return byteCount;
            }
        }
        @Override
        public int read(byte[] buffer) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buffer);
            return read(bb);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            ByteBuffer bb = ByteBuffer.wrap(buffer, offset, length);
            return read(bb);
        }

        @Override
        public void close() throws IOException {
            synchronized (streamBuffer) {
                super.close();
                streamBuffer = new byte[0];
                bufferReadPointer = 0;
            }
        }
    }

    /**
     * Provides an InputStream on the tunnel's payload. Only sensible use is to provide enough
     * buffer to read one datagram at a time. In this case, each call will receive one packet
     * send by the tunnel.
     * @return the InputStream.
     */
    public InputStream getInputStream() {
        return new AyiyaInputStream();
    }

    private class AyiyaOutputStream extends OutputStream {

        @Override
        public void write(byte[] buffer) throws IOException {
            try {
                Ayiya.this.write(buffer);
            } catch (TunnelBrokenException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            this.write(Arrays.copyOfRange(buffer, offset, offset + count - 1));
        }

        @Override
        public void write(int i) throws IOException {
            this.write(new byte[] {(byte)i});
        }
    }

    /**
     * Provides an OutputStream on the tunnel. Any write should give a whole tcp package to transmit.
     * @return the OutputStream
     */
    public OutputStream getOutputStream() {
        return new AyiyaOutputStream();
    }

    /** This can be used by friendly classes to protect this socket from tunneling, query its state, etc. */
    protected DatagramSocket getSocket () {
        return socket;
    }
    /**
     * Close our socket. Basically that's about it.
     */
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null; // it's useless anyway
        }
    }

}
