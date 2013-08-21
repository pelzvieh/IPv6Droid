package de.flyingsnail.jaiccu;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by pelzi on 15.08.13.
 * (c) Dr. Andreas Feldner, see license file.
 */
public class AiccuVpnService extends VpnService {

    private static final String TAG = AiccuVpnService.class.getName();
    private static final String SESSION_NAME = AiccuVpnService.class.getSimpleName();

    /*private String serverAddress;
    private String serverPort;
    private byte[] sharedSecret;*/
    private PendingIntent configureIntent;

    private Handler handler;
    private Thread thread;

    private String parameters;
    private ParcelFileDescriptor vpnFD;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start a new session by creating a new thread.
        thread = new Thread(new VpnThread(), SESSION_NAME);
        thread.start();

        return START_STICKY;
    }

    private class VpnThread extends Thread {
        protected VpnThread () {};

        private Thread inThread = null;
        private Thread outThread = null;

        @Override
        public void run() {
            try {
                // Read the tunnel specification from Tic
                TicConfiguration ticConfig = loadTicConfiguration();
                Tic tic = new Tic (ticConfig);
                TicTunnel tunnelSpecification = null;
                try {
                    tic.connect();
                    List<String> tunnelIds = tic.listTunnels();
                    tunnelSpecification = selectFirstSuitable(tunnelIds, tic);
                } finally {
                    tic.close();
                }

                // build vpn device on local machine
                Builder builder = new Builder();
                configureBuilderFromTunnelSpecification(builder, tunnelSpecification);

                vpnFD = builder.establish();

                // Packets to be sent are queued in this input stream.
                FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                // Packets received need to be written to this output stream.
                FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                // Perpare the tunnel to PoP
                Ayiya ayiya = new Ayiya (tunnelSpecification);

                try {
                    // setup tunnel to PoP
                    ayiya.connect();
                    Socket popSocket = ayiya.getSocket();
                    protect(popSocket);
                    InputStream popIn = popSocket.getInputStream();
                    OutputStream popOut = popSocket.getOutputStream();
                    // start the copying threads
                    inThread = startStreamCopy (localIn, popOut);
                    outThread = startStreamCopy (popIn, localOut);
                    // wait until interrupted
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        Log.i(TAG, "Tunnel thread received interrupt, closing tunnel");
                    }
                } finally {
                    if (inThread != null)
                        inThread.interrupt();
                    if (outThread != null)
                        outThread.interrupt();
                    ayiya.close();
                }
            } catch (ConnectionFailedException e) {
                Log.e(TAG, "This confiugration will not work on this device", e);
                // @todo inform the human user
                // if this thread fails, the service per se is out of order
                stopSelf();
            } catch (Throwable t) {
                Log.e(TAG, "Failed to run tunnel", t);
                // @todo this might be a temporary problem, some work is required with ConnectionManager yet :-(
                // if this thread fails, the service per se is out of order
                stopSelf();
            }
        }

        private TicTunnel selectFirstSuitable(List<String> tunnelIds, Tic tic) throws IOException, ConnectionFailedException {
            for (String id: tunnelIds) {
                TicTunnel desc = null;
                try {
                    desc = tic.describeTunnel(id);
                } catch (TunnelNotAcceptedException e) {
                    continue;
                }
                if (desc.isEnabled() && "6in4".equals(desc.getType())){
                    Log.i(TAG, "Tunnel " + id + " is suitable");
                    return desc;
                }
            }
            throw new ConnectionFailedException("No suitable tunnels found", null);
        }

        private void configureBuilderFromTunnelSpecification(Builder builder, TicTunnel tunnelSpecification) {
            builder.setMtu(tunnelSpecification.getMtu());
            builder.setSession(tunnelSpecification.getPopName());
            try {
                builder.addRoute(Inet6Address.getByName("::"), 0);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Could not add IPv6 default route to builder");
            }
            builder.addAddress(tunnelSpecification.getIpv4Endpoint(), tunnelSpecification.getPrefixLength());
            // @todo add the configure intent?
            Log.i(TAG, "Builder is configured");
        }

        /**
         * Create and run a thread that copies from in to out until interrupted.
         * @param in The stream to copy from.
         * @param out The stream to copy to.
         * @return The thread that does so until interrupted.
         */
        private Thread startStreamCopy(final InputStream in, final OutputStream out) {
            Thread thread = new Thread (new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.i(TAG, "Copy thread started");
                        // Allocate the buffer for a single packet.
                        ByteBuffer packet = ByteBuffer.allocate(32767);

                        // @TODO there *must* be a suitable utility class for that...?
                        while (true) {
                            packet.clear();
                            int len = in.read (packet.array());
                            if (len > 0) {
                                String s = null;
                                try {
                                    s = new String (packet.array(), 0, len, "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    Log.e(TAG, "UTF-8 not supported");
                                }
                                Log.d(TAG, "read string: " + s);
                                out.write(packet.array(), 0, len);
                            } else {
                                Thread.sleep(100);
                            }
                        }
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Log.i(TAG, "Copy thread interrupted, will end gracefully");
                        } else {
                            Log.e(TAG, "Copy thread got exception", e);
                        }
                    } finally {
                        try {
                            in.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Copy thread could not gracefully close input", e);
                        }
                        try {
                            out.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Copy thread could not gracefully close output", e);
                        }
                    }
                }
            });
            return thread;
        }
    }

    private TicConfiguration loadTicConfiguration() {
        return new TicConfiguration("XYZ0-SIXXS", "geheim", "tic.sixxs.net");
    }

    @Override
    public void onDestroy() {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
