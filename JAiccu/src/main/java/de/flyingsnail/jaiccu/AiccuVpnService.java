package de.flyingsnail.jaiccu;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramSocket;
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

    /**
     * The Action name for a status broadcast intent.
     */
    public static final String BC_STATUS = AiccuVpnService.class.getName() + ".STATUS";

    /**
     * The extended data name for the status in a status broadcast intent.
     */
    public static final String EDATA_STATUS = AiccuVpnService.class.getName() + ".STATUS";
    public static final String EDATA_PROGRESS = AiccuVpnService.class.getName() + ".PROGRESS";

    /*private String serverAddress;
    private String serverPort;
    private byte[] sharedSecret;*/
    private PendingIntent configureIntent;

    private Thread thread;

    private String parameters;
    private ParcelFileDescriptor vpnFD;
    private TextView statusText;
    private ProgressBar progress;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Build the configuration object from the extras at the intent.
        TicConfiguration ticConfiguration = new TicConfiguration (
                intent.getStringExtra(MainActivity.EXTRA_USER_NAME),
                intent.getStringExtra(MainActivity.EXTRA_PASSWORD),
                intent.getStringExtra(MainActivity.EXTRA_TIC_URL));

        // Start a new session by creating a new thread.
        thread = new Thread(new VpnThread(ticConfiguration), SESSION_NAME);
        thread.start();

        return START_STICKY;
    }

    private class VpnThread extends Thread {

        private TicConfiguration ticConfig;

        protected VpnThread (TicConfiguration config) {
            ticConfig = config;
        };

        private Thread inThread = null;
        private Thread outThread = null;

        @Override
        public void run() {
            try {
                // Read the tunnel specification from Tic
                Tic tic = new Tic (ticConfig);
                TicTunnel tunnelSpecification = null;
                try {
                    reportStatus(0, "Query TIC");
                    tic.connect();
                    List<String> tunnelIds = tic.listTunnels();
                    tunnelSpecification = selectFirstSuitable(tunnelIds, tic);
                    reportStatus(25, "Selected Tunnel");
                } finally {
                    tic.close();
                }

                // build vpn device on local machine
                Builder builder = new Builder();
                configureBuilderFromTunnelSpecification(builder, tunnelSpecification);

                // Perpare the tunnel to PoP
                Ayiya ayiya = new Ayiya (tunnelSpecification);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // setup local tun and routing
                        vpnFD = builder.establish();
                        reportStatus(50, "Configured local network");

                        // Packets to be sent are queued in this input stream.
                        FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                        // Packets received need to be written to this output stream.
                        FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                        // setup tunnel to PoP
                        ayiya.connect();
                        reportStatus(75, "Pinged POP");

                        DatagramSocket popSocket = ayiya.getSocket();
                        protect(popSocket);
                        InputStream popIn = ayiya.getInputStream();
                        OutputStream popOut = ayiya.getOutputStream();
                        // start the copying threads
                        inThread = startStreamCopy (localIn, popOut);
                        inThread.setName("AYIYA from local to POP");
                        outThread = startStreamCopy (popIn, localOut);
                        outThread.setName("AYIYA from POP to local");
                        // wait until interrupted
                        try {
                            while (!Thread.currentThread().isInterrupted() && inThread.isAlive() && outThread.isAlive()) {
                                reportStatus(100, "Transmitting");
                                // wait for half the heartbeat interval or until inThread dies.
                                // Note: the inThread is reading fromt the network socket to the POP
                                // in case of network changes, this socket breaks immediately, so
                                // inThread crashes on external network changes even if no transfer
                                // is active.
                                inThread.join(tunnelSpecification.getHeartbeatInterval()*1000/2);
                                if (inThread.isAlive())
                                    ayiya.beat();
                                Log.i(TAG, "Sent heartbeat.");
                            }
                        } catch (InterruptedException ie) {
                            Log.i(TAG, "Tunnel thread received interrupt, closing tunnel");
                            throw ie;
                        }
                    } catch (IOException e) {
                        Log.i (TAG, "Tunnel connection broke down, closing and reconnecting ayiya", e);
                        reportStatus(50, "Disturbed");
                        Thread.sleep(5000l); // @todo we should check with ConnectivityManager
                    } finally {
                        if (inThread != null && inThread.isAlive())
                            inThread.interrupt();
                        if (outThread != null && outThread.isAlive())
                            outThread.interrupt();
                        ayiya.close();
                    }
                }
                reportStatus(0, "Tearing down");
            } catch (ConnectionFailedException e) {
                Log.e(TAG, "This confiugration will not work on this device", e);
                // @todo inform the human user
                // if this thread fails, the service per se is out of order
            } catch (InterruptedException e) {
                // controlled behaviour, no logging or treatment required
            } catch (Throwable t) {
                Log.e(TAG, "Failed to run tunnel", t);
                // @todo this might be a temporary problem, some work is required with ConnectionManager yet :-(
                // if this thread fails, the service per se is out of order
            }
            stopSelf();
            reportStatus(0, "Down");
        }

        private TicTunnel selectFirstSuitable(List<String> tunnelIds, Tic tic) throws IOException, ConnectionFailedException {
            for (String id: tunnelIds) {
                TicTunnel desc = null;
                try {
                    desc = tic.describeTunnel(id);
                } catch (TunnelNotAcceptedException e) {
                    continue;
                }
                if (desc.isValid() && desc.isEnabled() && "ayiya".equals(desc.getType())){
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
            builder.addAddress(tunnelSpecification.getIpv6Endpoint(), tunnelSpecification.getPrefixLength());
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
                        byte[] packet = new byte[32767];

                        // @TODO there *must* be a suitable utility class for that...?
                        while (!Thread.currentThread().isInterrupted()) {
                            int len = in.read (packet);
                            if (len > 0) {
                                out.write(packet, 0, len);
                                Log.d(TAG, Thread.currentThread().getName() + " copied package of size: " + len);
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
            thread.start();
            return thread;
        }
    }


    private void reportStatus (int progressPerCent, String status) {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS, status)
                .putExtra(EDATA_PROGRESS, progressPerCent);
        // Broadcast locally
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusBroadcast);
    }

/*    private TicConfiguration loadTicConfiguration() {
        return new TicConfiguration("XYZ0-SIXXS", "1234", "tic.sixxs.net");
    }
*/
    @Override
    public void onDestroy() {
        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }
    }
}
