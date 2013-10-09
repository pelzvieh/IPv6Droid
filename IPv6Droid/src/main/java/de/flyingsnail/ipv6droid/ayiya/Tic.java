/*
 * Copyright (c) 2013 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.ayiya;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * This represents the Tunnel Information Control Protocol as published by Sixxs (http://www.sixxs.net/tools/tic).
 * It supports (when finished) STARTTLS, MD5 Authentication, but none of the other variants.
 * Created by pelzi on 19.08.13.
 */
public class Tic {
    /** A String used to identify this class in logs */
    private static final String TAG = Tic.class.getName();

    /**
     * The official port number, not configurable.
     */
    public static final int TIC_PORT = 3874;

    /**
     * A String representing the protocol version supported by this implementation.
     */
    public static final String TIC_VERSION = "draft-00";

    /**
     * The maximum allowed deviation of TIC and local clocks.
     */
    public static final long MAX_TIME_OFFSET = 120;

    /**
     * the bundled information where we're running in.
     */
    private final ContextInfo contextInfo;

    /**
     * Our copy of the eventually user-accessible configuration object for the Tic.
     */
    private TicConfiguration config;

    /** The socket connected to the TIC */
    Socket socket = null;
    /** The line reader */
    private BufferedReader in = null;
    /** The line writer */
    private BufferedWriter out = null;

    /**
     * A class collecting all OS and build specific properties, to be injected here, because this
     * class should not have dependencies on android specifics.
     */
    public static class ContextInfo {
        /**
         * A String representing this client's name.
         */
        private final String clientName;

        /** A String representing this client's version. */
        private final String clientVersion;

        /** A String giving the name of the operating system. */
        private final String os;

        /** A String giving the version of the operating system. */
        private final String osVersion;

        /**
         * Constructor.
         */
        public ContextInfo(String clientName, String clientVersion, String os, String osVersion) {
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.os = os;
            this.osVersion = osVersion;
        }

        public String getClientName() {
            return clientName;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public String getOs() {
            return os;
        }

        public String getOsVersion() {
            return osVersion;
        }
    }

    /** Constructor with configuration */
    public Tic(TicConfiguration ticConfig, ContextInfo contextInfo) {
        config = (TicConfiguration)ticConfig.clone();
        this.contextInfo = contextInfo;
    }

    /**
     * Connect to configured tic server and log in using user id and password.
     * Once connected, close must be called when done.
     * @throws ConnectionFailedException in case of a permanent functional problem with this configuration on this device.
     * @throws IOException in case of a (presumably) temporary technical problem, e.g. a connection breakdown.
     * @param sslContext the SSLContext to use or null to disable SSL
     */
    public synchronized void connect(SSLContext sslContext) throws ConnectionFailedException, IOException {
        try {
            if (socket != null) {
                throw new IllegalStateException("This Tic is already connected.");
            }
            // unencrypted TCP connection
            socket = new Socket(config.getServer(), TIC_PORT);

            initLineReaderAndWriter();

            // handle protocol until login
            protocolStepWelcome ();
            protocolStepClientIdentification ();
            protocolStepTimeComparison ();
            if (sslContext != null)
                protocolStepStartTLS(sslContext);
            protocolStepSendUsername();
            String challenge = protocolStepRequestChallenge();
            protocolStepSendAuthentication (challenge);
            Log.i(TAG, "Logged in to TIC server " + config.getServer() + " as user " + config.getUsername());
        } catch (ConnectionFailedException e) {
            close();
            throw e;
        } catch (Exception e){
            close();
            throw new IOException ("Error reading/writing to TIC", e);
        }

    }

    /**
     * Initialize the fields in and out as buffered reader/writer on the current socket.
     * @throws IOException
     */
    private void initLineReaderAndWriter() throws IOException {
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * Performs log-off and closes connections.
     * Any exceptions are eaten carefully to leave a usable object under any circumstances.
     */
    public synchronized void close() {
        if (socket != null && !socket.isClosed()) {
            try {
                try {
                    requestResponse("QUIT no lyrics here at this time");
                } catch (ConnectionFailedException e) {
                    Log.e (TAG, "TIC server did not accept our good-bye", e);
                    // we'll close connection anyway!
                }
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Couldn't close socket", e);
            }
            socket = null; // it's useless anyway
            in = null;
            out = null;
        }
    }

    /**
     * Gets a list of tunnels for the user ID associated with this Tic's configuration.
     * This Tic must be "connected", i.e. connect() must have been run successfully, and close()
     * must not have called yet.
     * @return a List of Strings with the IDs of tunnels.
     * @throws IOException in case of a temporary problem in communication. Closing and reconnecting is probably required.
     * @throws IllegalStateException in case of invalid usage of this object.
     */
    public synchronized List<String> listTunnels () throws IOException {
        if (socket == null || !socket.isConnected())
            throw new IllegalStateException("Tic object not connected");
        List<String> list = new ArrayList<String>(10);
        // we start with a request-response pair
        try {
            requestResponse("tunnel list");
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "Server did not accept the tunnel list command. This is probaly a client bug, please file a report.", e);
            throw new IllegalStateException("TIC doesn't accept tunnel list command; this is unexpected.", e);
        }
        // now we read lines with tunnel ids until we receive a 202 code.
        while (true) {
            String line = in.readLine();
            if (line.startsWith("202"))
                break; // we're done!
            else {
                StringTokenizer tok = new StringTokenizer(line, " ");
                String tid = tok.nextToken();
                list.add (tid);
            }
        }
        Log.i(TAG, "TIC listed " + list.size() + " elements");
        return list;
    }

    /**
     * Get information about the specific tunnel identified by id. Id must have been read by
     * listTunnels. The id remains constant, though, so can be saved from an earlier call to
     * listTunnels. This Tic must be connected, i.e. connect has been called successfully, and
     * close() not yet.
     * @param id A String representing the queried tunnel's ID.
     * @throws IOException in case of temporary communication problems. Close and connect is probably required.
     */
    public synchronized TicTunnel describeTunnel (String id) throws IOException, TunnelNotAcceptedException {
        if (socket == null || !socket.isConnected())
            throw new IllegalStateException("Tic object not connected");
        TicTunnel tunnelDesc = new TicTunnel(id);
        // we start with a request-response pair
        try {
            requestResponse("tunnel show " + id);
        } catch (ConnectionFailedException e) {
            throw new TunnelNotAcceptedException ("TIC doesn't accept tunnel list command, check listTunnels", e);
        }
        // now we read lines with tunnel ids until we receive a 202 code.
        while (true) {
            String line = in.readLine();
            if (line.startsWith("202"))
                break; // we're done!
            else {
                int delimPos = line.indexOf(": ");
                if (delimPos < 0) {
                    Log.e(TAG, "Unparsable line, no delimiter found");
                } else {
                    String key = line.substring(0, delimPos);
                    String value = line.substring(delimPos + 2);
                    if (!tunnelDesc.parseKeyValue (key, value))
                        Log.e(TAG, "unsupported tunnel description key found: " + key);
                }
            }
        }
        Log.i(TAG, "TIC described tunnel with id " + id);

        return tunnelDesc;
    }

    private String requestResponse (String request) throws IOException, ConnectionFailedException {
        assert (in != null && out != null);
        out.write(request);
        out.newLine();
        out.flush();
        String answer = in.readLine();
        if (!answer.startsWith("2"))
            throw new ConnectionFailedException ("No success with challenge response " + answer, null);
        return answer.substring(4); // strip the 3 digit response code and following space
    }

    /**
     * First step of the TIC protocol, welcome message.
     * @throws IOException
     * @throws ConnectionFailedException
     */
    private void protocolStepWelcome() throws IOException, ConnectionFailedException {
        assert (in != null);
        // fetch the welcome
        String welcome = in.readLine();
        if (!welcome.startsWith("2"))
            throw new ConnectionFailedException ("No success code on welcome: " + welcome, null);
        Log.i(TAG, "TIC accepted our welcome");
    }

    /**
     * 2nd step of the TIC protocol
     */
    private void protocolStepClientIdentification() throws ConnectionFailedException, IOException {
        // write client ident
        requestResponse("client TIC/" + TIC_VERSION + " " + contextInfo.getClientName() + "/" +
                contextInfo.getClientVersion() + " " + contextInfo.getOs() + "/" + contextInfo.getOsVersion());
        Log.i(TAG, "TIC accepted the client identification");
    }

    private void protocolStepTimeComparison() throws IOException, ConnectionFailedException {
        String answer = requestResponse("get unixtime");
        long ticTimeSecs = Long.parseLong(answer);
        if (ticTimeSecs < 0)
            ticTimeSecs += 2*((long)Integer.MAX_VALUE);
        Date localTime = new Date();
        long localTimeSecs = (int)(localTime.getTime() / 1000l);
        long offset = localTimeSecs - ticTimeSecs;
        if (Math.abs(offset) > MAX_TIME_OFFSET) {
            throw new ConnectionFailedException("Time differs more than allowed, set correct time. Offset: " + offset, null);
        }
        Log.i(TAG, "Clock comparison succeeded, current difference is " + offset);
    }

    /**
     * Send STARTTLS command. On success message from server, replace active socket and reader/writer
     * by SSLSocket wrapped around the TCP socket.
     * @param sslContext the SSLContext to use for the SSLSocket.
     */
    private void protocolStepStartTLS(SSLContext sslContext) throws IOException, ConnectionFailedException {
        String answer;
        try {
            answer = requestResponse("STARTTLS");
        } catch (ConnectionFailedException e) {
            // @todo is this really a good idea? Are all TIC servers around guaranteed to offer TLS?
            Log.i(TAG, "Server did not accept TLS encryption, going on with plain socket");
            return;
        }
        Log.i(TAG, "Switching to SSL encrypted connection");

        SSLSocketFactory socketFactory = sslContext.getSocketFactory();
        socket = (SSLSocket)socketFactory.createSocket(socket,
                config.getServer(),
                TIC_PORT,
                true);
        initLineReaderAndWriter();
    }

    /**
     * Send username and check for 2xx response
     * @throws IOException
     * @throws ConnectionFailedException
     */
    private void protocolStepSendUsername() throws IOException, ConnectionFailedException {
        requestResponse("username " + config.getUsername());
        Log.i(TAG, "TIC accepted our username");
    }


    /**
     * Protocol step to request the challenge from TIC
     * @return the challenge as a String
     * @throws IOException
     * @throws ConnectionFailedException
     */
    private String protocolStepRequestChallenge() throws IOException, ConnectionFailedException {
        String challenge = requestResponse("challenge md5");
        Log.i(TAG, "TIC provided a challenge for MD5 authentication");
        return challenge;
    }

    /**
     * Send authentication and verify server response
     * @param challenge a String representing the server's challenge. I'm confident that it's plain
     *                  ASCII, so no worries about the encoding.
     */
    private void protocolStepSendAuthentication(String challenge) throws ConnectionFailedException, IOException {
        String signature;
        try {
            // actually, the algorithm is a bit strange...
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] pwDigest = md5.digest(config.getPassword().getBytes("UTF-8"));
            String pwDigestString = String.format("%032x", new BigInteger(1, pwDigest));
            // ... as we don't use this as a digest, but the bytes from its hexdump string =8-O
            pwDigest = pwDigestString.getBytes("UTF-8");

            // now let's calculate the response to the challenge
            md5.reset();
            md5.update(challenge.getBytes("UTF-8")); // probably the challenge is already hexdumped by the server
            md5.update(pwDigest);
            byte[] authDigest = md5.digest();
            // the auth response is just the hex representation of the digest
            signature = String.format("%032x", new BigInteger(1, authDigest));
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("MD5 algorithm not available on this device", e);
        } catch (UnsupportedEncodingException e) {
            throw new ConnectionFailedException("UTF-8 encoding not available on this device", e);
        }

        // finally, let's see what the server says about it :-)
        try {
            requestResponse("authenticate md5 " + signature);
        } catch (ConnectionFailedException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
        Log.i (TAG, "TIC accepted authentication with MD5");
    }
}
