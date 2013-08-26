package de.flyingsnail.jaiccu;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.List;
import java.util.StringTokenizer;

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

    /*private String serverAddress;
    private String serverPort;
    private byte[] sharedSecret;*/
    private PendingIntent configureIntent;

    private Thread thread;

    private String parameters;
    private ParcelFileDescriptor vpnFD;
    private TextView statusText;
    private ProgressBar progress;
    private SharedPreferences myPreferences;

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }
    /**
     * The extended data name for the status in a status broadcast intent.
     */
    public static final String EDATA_STATUS = AiccuVpnService.class.getName() + ".STATUS";
    public static final String EDATA_ACTIVITY = AiccuVpnService.class.getName() + ".ACTIVITY";
    public static final String EDATA_PROGRESS = AiccuVpnService.class.getName() + ".PROGRESS";


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Build the configuration object from the extras at the intent.
        TicConfiguration ticConfiguration = loadTicConfiguration();

        // setup the intent filter for status broadcasts
        // The filter's action is BROADCAST_ACTION
        IntentFilter statusIntentFilter = new IntentFilter(MainActivity.BC_STOP);
        CommandReceiver commandReceiver = new CommandReceiver();

        // Registers the CommandReceiver and its intent filter
        LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver,
                statusIntentFilter);

        // Start a new session by creating a new thread.
        thread = new Thread(new VpnThread(ticConfiguration), SESSION_NAME);
        thread.start();

        return START_STICKY;
    }

    /**
     * This class does the actual work, i.e. logs in to TIC, reads available tunnels and starts
     * a copy thread for each direction.
     */
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
                    reportStatus(0, Status.Connecting, "Query TIC");
                    tic.connect();
                    List<String> tunnelIds = tic.listTunnels();
                    tunnelSpecification = selectFirstSuitable(tunnelIds, tic);
                    reportStatus(25, Status.Connecting, "Selected Tunnel");
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
                        reportStatus(50, Status.Connecting, "Configured local network");

                        // Packets to be sent are queued in this input stream.
                        FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                        // Packets received need to be written to this output stream.
                        FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                        // setup tunnel to PoP
                        ayiya.connect();
                        reportStatus(75, Status.Connecting, "Pinged POP");

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
                                reportStatus(100, Status.Connected, "Transmitting");
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
                        reportStatus(50, Status.Disturbed, "Disturbed");
                        Thread.sleep(5000l); // @todo we should check with ConnectivityManager
                    } finally {
                        if (inThread != null && inThread.isAlive())
                            inThread.interrupt();
                        if (outThread != null && outThread.isAlive())
                            outThread.interrupt();
                        ayiya.close();
                        try {
                            vpnFD.close();
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot close local socket", e);
                        }
                    }
                }
                reportStatus(0, Status.Disturbed, "Tearing down");
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
            reportStatus(0, Status.Idle, "Down");
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
                if (myPreferences.getBoolean("routes_default", true))
                    builder.addRoute(Inet6Address.getByName("::"), 0);
                else {
                    String routeDefinition = myPreferences.getString("routes_specific", "::/0");
                    StringTokenizer tok = new StringTokenizer(routeDefinition, "/");
                    Inet6Address address = (Inet6Address) Inet6Address.getByName(tok.nextToken());
                    int prefixLen = 64;
                    if (tok.hasMoreTokens())
                        prefixLen = Integer.parseInt(tok.nextToken());
                    builder.addRoute(address, prefixLen);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Could not add requested IPv6 route to builder", e);
                // @todo notification to user required
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


    /** Inner class to handle status updates */
    private class CommandReceiver extends BroadcastReceiver {
        private CommandReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MainActivity.BC_STOP)) {
                Log.i(TAG, "Received explicit stop brodcast, will stop VPN Tread");
                AiccuVpnService.this.thread.interrupt();
            }
        }
    }


    private void reportStatus (int progressPerCent, Status status, String activity) {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS, status.toString())
                .putExtra(EDATA_ACTIVITY, activity)
                .putExtra(EDATA_PROGRESS, progressPerCent);
        // Broadcast locally
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusBroadcast);
    }

    private TicConfiguration loadTicConfiguration() {
        return new TicConfiguration(myPreferences.getString("tic_username", ""),
                myPreferences.getString("tic_password", ""),
                myPreferences.getString("tic_host", "tic.sixxs.net"));
    }

    @Override
    public void onDestroy() {
        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }
    }
}
