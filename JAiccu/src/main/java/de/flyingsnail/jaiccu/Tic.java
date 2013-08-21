package de.flyingsnail.jaiccu;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.net.SocketFactory;

/**
 * Created by pelzi on 19.08.13.
 */
public class Tic {
    public static final String TAG = "Tic";
    private static final int TIC_PORT = 3874;
    private TicConfiguration config;

    /** The socket connected to the TIC */
    Socket socket = null;
    private BufferedReader in = null;
    private BufferedWriter out = null;

    /** Constructor with configuration */
    public Tic(TicConfiguration ticConfig) {
        config = (TicConfiguration)ticConfig.clone();
    }

    /**
     * Connect to configured tic server and log in using user id and password.
     * Once connected, close must be called when done.
     */
    public synchronized void connect() throws ConnectionFailedException {
        try {
            // unencrypted TCP connection
            socket = new Socket(config.getServer(), TIC_PORT);

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // handle protocol until login
            protocolStepWelcome ();
            protocolStepClientIdentification ();
            protocolStepTimeComparison ();
            // @todo here TLS should start
            // protocolStepStartTLS();
            protocolStepSendUsername();
            String challenge = protocolStepRequestChallenge();
            protocolStepSendAuthentication (challenge);

        } catch (ConnectionFailedException e) {
            close();
            throw e;
        } catch (Exception e){
            close();
            throw new ConnectionFailedException ("Error reading/writing to TIC", e);
        }

    }

    public void close() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Couldn't close socket", e);
            }
            socket = null; // it's useless anyway
            in = null;
            out = null;
        }
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
    }

    /**
     * 2nd step of the TIC protocol
     */
    private void protocolStepClientIdentification() throws ConnectionFailedException, IOException {
        // write client ident
        // @todo read out actual system version
        // @todo link client version to android build versioning system
        requestResponse("client TIC/" + TIC_VERSION + " " + TIC_CLIENT_NAME + "/" +
                TIC_CLIENT_VERSION + " Android/4.00");
    }

    private void protocolStepTimeComparison() throws IOException, ConnectionFailedException {
        String answer = requestResponse("get unixtime");
        long ticTimeSecs = Long.parseLong(answer);
        if (ticTimeSecs < 0)
            ticTimeSecs += 2*((long)Integer.MAX_VALUE);
        Date localTime = new Date();
        // @todo check if c epoch really starts at 1st January 1970
        Calendar cEpoch = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cEpoch.set(1970, Calendar.JANUARY, 0);
        long localTimeSecs = (localTime.getTime() - cEpoch.getTimeInMillis()) / 1000;
        long offset = localTimeSecs - ticTimeSecs;
        if (Math.abs(offset) > MAX_TIME_OFFSET) {
            throw new ConnectionFailedException("Time differs more than allowed, set correct time. Offset: " + offset, null);
        }
    }

    /**
     * Send username and check for 2xx response
     * @throws IOException
     * @throws ConnectionFailedException
     */
    private void protocolStepSendUsername() throws IOException, ConnectionFailedException {
        requestResponse("username " + config.getUsername());
    }


    /**
     * Protocol step to request the challenge from TIC
     * @return the challenge as a String
     * @throws IOException
     * @throws ConnectionFailedException
     */
    private String protocolStepRequestChallenge() throws IOException, ConnectionFailedException {
        return requestResponse("challenge md5");
    }

    /**
     * Send authentication and verify server response
     * @param challenge
     */
    private void protocolStepSendAuthentication(String challenge) throws ConnectionFailedException, IOException {
        String signature;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            // @todo verify the result of MD5String in C
            byte[] pwDigest = md5.digest(config.getPassword().getBytes("UTF-8"));
            md5.reset();
            md5.update(challenge.getBytes("UTF-8"));
            md5.update(pwDigest);
            // @todo verify the result of MD5String in C
            signature = new String(md5.digest(), "UTF-8");
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("MD5 algorithm not availale on this device", e);
        } catch (UnsupportedEncodingException e) {
            throw new ConnectionFailedException("UTF-8 encoding not availale on this device", e);
        }
        requestResponse("authenticate md5 " + signature);
    }

}
