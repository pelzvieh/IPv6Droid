package de.flyingsnail.jaiccu;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

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
 * This class does the actual work, i.e. logs in to TIC, reads available tunnels and starts
 * a copy thread for each direction.
 */
class VpnThread extends Thread {
    /**
     * The Action name for a status broadcast intent.
     */
    public static final String BC_STATUS = AiccuVpnService.class.getName() + ".STATUS";

    /**
     * The extended data name for the status in a status broadcast intent.
     */
    public static final String EDATA_STATUS = AiccuVpnService.class.getName() + ".STATUS";
    public static final String EDATA_ACTIVITY = AiccuVpnService.class.getName() + ".ACTIVITY";
    public static final String EDATA_PROGRESS = AiccuVpnService.class.getName() + ".PROGRESS";
    public static final String EDATA_ACTIVE_TUNNEL = AiccuVpnService.class.getName() + ".ACTIVE_TUNNEL";

    /**
     * The tag for logging.
     */
    private static final String TAG = VpnThread.class.getName();

    /**
     * The service that created this thread.
     */
    private AiccuVpnService aiccuVpnService;
    /**
     * The configuration for the tic protocol.
     */
    private TicConfiguration ticConfig;
    /**
     * The configuration of the intended routing.
     */
    private RoutingConfiguration routingConfiguration;
    /**
     * Android thing to post stuff to the GUI thread.
     */
    private Handler handler;
    /**
     * The file descriptor representing the local tun socket.
     */
    private ParcelFileDescriptor vpnFD;

    /**
     * The thread that copies from PoP to local.
     */
    private Thread inThread = null;
    /**
     * The thread that copies from local to PoP.
     */
    private Thread outThread = null;
    /**
     * The constructor setting all required fields.
     * @param aiccuVpnService the Service that created this thread
     * @param config the tic configuration
     * @param routingConfiguration the routing configuration
     * @param sessionName the name of this thread
     */
    VpnThread (AiccuVpnService aiccuVpnService,
               TicConfiguration config,
               RoutingConfiguration routingConfiguration,
               String sessionName) {
        setName(sessionName);
        this.aiccuVpnService = aiccuVpnService;
        this.ticConfig = (TicConfiguration)config.clone();
        this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
    };


    @Override
    public void run() {
        try {
            handler = new Handler(aiccuVpnService.getApplicationContext().getMainLooper());

            // Read the tunnel specification from Tic
            Tic tic = new Tic (ticConfig);
            TicTunnel tunnelSpecification = null;
            try {
                reportStatus(0, Status.Connecting, "Query TIC", tunnelSpecification);
                tic.connect();
                List<String> tunnelIds = tic.listTunnels();
                tunnelSpecification = selectFirstSuitable(tunnelIds, tic);
                reportStatus(25, Status.Connecting, "Selected Tunnel", tunnelSpecification);
            } finally {
                tic.close();
            }

            // build vpn device on local machine
            VpnService.Builder builder = aiccuVpnService.createBuilder();
            configureBuilderFromTunnelSpecification(builder, tunnelSpecification);

            // Perpare the tunnel to PoP
            Ayiya ayiya = new Ayiya (tunnelSpecification);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // setup local tun and routing
                    vpnFD = builder.establish();
                    reportStatus(50, Status.Connecting, "Configured local network", tunnelSpecification);

                    // Packets to be sent are queued in this input stream.
                    FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                    // Packets received need to be written to this output stream.
                    FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                    // setup tunnel to PoP
                    ayiya.connect();
                    reportStatus(75, Status.Connecting, "Pinged POP", tunnelSpecification);


                    DatagramSocket popSocket = ayiya.getSocket();
                    aiccuVpnService.protect(popSocket);
                    InputStream popIn = ayiya.getInputStream();
                    OutputStream popOut = ayiya.getOutputStream();
                    // start the copying threads
                    inThread = startStreamCopy (localIn, popOut);
                    inThread.setName("AYIYA from local to POP");
                    outThread = startStreamCopy (popIn, localOut);
                    outThread.setName("AYIYA from POP to local");

                    postToast(aiccuVpnService.getApplicationContext(), R.id.vpnservice_tunnel_up, Toast.LENGTH_SHORT);

                    // wait until interrupted
                    try {
                        while (!Thread.currentThread().isInterrupted() && inThread.isAlive() && outThread.isAlive()) {
                            reportStatus(100, Status.Connected, "Transmitting", tunnelSpecification);
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
                    reportStatus(50, Status.Disturbed, "Disturbed", tunnelSpecification);
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
                    postToast(aiccuVpnService.getApplicationContext(), R.id.vpnservice_tunnel_down, Toast.LENGTH_SHORT);
                }
            }
            reportStatus(0, Status.Idle, "Tearing down", tunnelSpecification);
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This confiugration will not work on this device", e);
            // @todo inform the human user
            postToast(aiccuVpnService.getApplicationContext(), R.id.vpnservice_invalid_configuration, Toast.LENGTH_LONG);
        } catch (InterruptedException e) {
            // controlled behaviour, no logging or treatment required
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // @todo this might be a temporary problem, some work is required with ConnectionManager yet :-(
            // if this thread fails, the service per se is out of order
            postToast(aiccuVpnService.getApplicationContext(), R.id.vpnservice_unexpected_problem, Toast.LENGTH_LONG);
        }
        // if this thread fails, the service per se is out of order
        aiccuVpnService.stopSelf();
        reportStatus(0, Status.Idle, "Down", null);
    }

    /**
     * Return the first tunnel from available tunnels that is suitable for this VpnThread.
     * @param tunnelIds the List of Strings containing tunnel IDs each
     * @param tic the connected Tic object
     * @return a TicTunnel specifying the tunnel to build up
     * @throws IOException in case of a communication problem
     * @throws ConnectionFailedException in case of a logical problem with the setup
     * @todo Extend to first try the last used/a configured tunnel.
     */
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

    /**
     * Setup VpnService.Builder object (in effect, the local tun device)
     * @param builder the Builder to configure
     * @param tunnelSpecification the TicTunnel specification of the tunnel to set up.
     */
    private void configureBuilderFromTunnelSpecification(VpnService.Builder builder,
                                                   TicTunnel tunnelSpecification) {
        builder.setMtu(tunnelSpecification.getMtu());
        builder.setSession(tunnelSpecification.getPopName());
        try {
            if (routingConfiguration.isSetDefaultRoute())
                builder.addRoute(Inet6Address.getByName("::"), 0);
            else {
                String routeDefinition = routingConfiguration.getSpecificRoute();
                StringTokenizer tok = new StringTokenizer(routeDefinition, "/");
                Inet6Address address = (Inet6Address) Inet6Address.getByName(tok.nextToken());
                int prefixLen = 128;
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
        }

        );
        thread.start();
        return thread;
    }

    private void postToast (final Context ctx, final int resId, final int duration) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, resId, duration).show();
            }
        });
    }
    private void reportStatus (int progressPerCent,
                               Status status,
                               String activity,
                               TicTunnel activeTunnel) {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS, status.toString())
                .putExtra(EDATA_ACTIVITY, activity)
                .putExtra(EDATA_PROGRESS, progressPerCent);
        if (activeTunnel != null)
                statusBroadcast.putExtra(EDATA_ACTIVE_TUNNEL, activeTunnel);
        // Broadcast locally
        LocalBroadcastManager.getInstance(aiccuVpnService).sendBroadcast(statusBroadcast);
    }

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }
}
