/*
 * Copyright (c) 2013 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.android;

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

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.Ayiya;
import de.flyingsnail.ipv6droid.ayiya.ConnectionFailedException;
import de.flyingsnail.ipv6droid.ayiya.Tic;
import de.flyingsnail.ipv6droid.ayiya.TicConfiguration;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;
import de.flyingsnail.ipv6droid.ayiya.TunnelNotAcceptedException;

/**
 * This class does the actual work, i.e. logs in to TIC, reads available tunnels and starts
 * a copy thread for each direction.
 */
class VpnThread extends Thread {
    /**
     * The Action name for a status broadcast intent.
     */
    public static final String BC_STATUS = AyiyaVpnService.class.getName() + ".STATUS";

    /**
     * The extended data name for the status in a status broadcast intent.
     */
    public static final String EDATA_STATUS = AyiyaVpnService.class.getName() + ".STATUS";
    public static final String EDATA_ACTIVITY = AyiyaVpnService.class.getName() + ".ACTIVITY";
    public static final String EDATA_PROGRESS = AyiyaVpnService.class.getName() + ".PROGRESS";
    public static final String EDATA_ACTIVE_TUNNEL = AyiyaVpnService.class.getName() + ".ACTIVE_TUNNEL";
    public static final String EDATA_TUNNEL_PROVEN = AyiyaVpnService.class.getName() + ".TUNNEL_PROVEN";

    /**
     * The tag for logging.
     */
    private static final String TAG = VpnThread.class.getName();

    /**
     * The service that created this thread.
     */
    private AyiyaVpnService ayiyaVpnService;
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
    private TicTunnel tunnelSpecification;

    /**
     * The constructor setting all required fields.
     * @param ayiyaVpnService the Service that created this thread
     * @param cachedTunnel
     * @param config the tic configuration
     * @param routingConfiguration the routing configuration
     * @param sessionName the name of this thread
     */
    VpnThread(AyiyaVpnService ayiyaVpnService,
              TicTunnel cachedTunnel, TicConfiguration config,
              RoutingConfiguration routingConfiguration,
              String sessionName) {
        setName(sessionName);
        this.ayiyaVpnService = ayiyaVpnService;
        this.ticConfig = (TicConfiguration)config.clone();
        this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
        this.tunnelSpecification = cachedTunnel;
    };


    @Override
    public void run() {
        try {
            handler = new Handler(ayiyaVpnService.getApplicationContext().getMainLooper());

            if (tunnelSpecification == null) {
                // Read the tunnel specification from Tic
                Tic tic = new Tic (ticConfig);
                try {
                    reportStatus(0, Status.Connecting, "Query TIC", tunnelSpecification, false);
                    tic.connect();
                    List<String> tunnelIds = tic.listTunnels();
                    tunnelSpecification = selectFirstSuitable(tunnelIds, tic);
                    reportStatus(25, Status.Connecting, "Selected Tunnel", tunnelSpecification, false);
                } finally {
                    tic.close();
                }
            } else {
                Log.i(TAG, "Using cached TicTunnel instead of contacting TIC");
            }
            // build vpn device on local machine
            VpnService.Builder builder = ayiyaVpnService.createBuilder();
            configureBuilderFromTunnelSpecification(builder, tunnelSpecification);

            // Perpare the tunnel to PoP
            Ayiya ayiya = new Ayiya (tunnelSpecification);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // setup local tun and routing
                    vpnFD = builder.establish();
                    reportStatus(50, Status.Connecting, "Configured local network", tunnelSpecification, false);

                    // Packets to be sent are queued in this input stream.
                    FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                    // Packets received need to be written to this output stream.
                    FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                    // setup tunnel to PoP
                    ayiya.connect();
                    reportStatus(75, Status.Connecting, "Pinged POP", tunnelSpecification, ayiya.isValidPacketReceived());


                    DatagramSocket popSocket = ayiya.getSocket();
                    ayiyaVpnService.protect(popSocket);
                    InputStream popIn = ayiya.getInputStream();
                    OutputStream popOut = ayiya.getOutputStream();
                    // start the copying threads
                    inThread = startStreamCopy (localIn, popOut);
                    inThread.setName("AYIYA from local to POP");
                    outThread = startStreamCopy (popIn, localOut);
                    outThread.setName("AYIYA from POP to local");

                    if (tunnelSpecification.getIpv6Pop().isReachable(10000)) {
                        postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_tunnel_up, Toast.LENGTH_SHORT);
                    } else {
                        Log.e(TAG, "Warning: couldn't ping pop via ipv6!");
                    };

                    // wait until interrupted
                    try {
                        while (!Thread.currentThread().isInterrupted() && inThread.isAlive() && outThread.isAlive()) {
                            reportStatus(100,
                                    Status.Connected,
                                    "Transmitting",
                                    tunnelSpecification,
                                    ayiya.isValidPacketReceived());
                            // wait for half the heartbeat interval or until inThread dies.
                            // Note: the inThread is reading fromt the network socket to the POP
                            // in case of network changes, this socket breaks immediately, so
                            // inThread crashes on external network changes even if no transfer
                            // is active.
                            inThread.join(tunnelSpecification.getHeartbeatInterval()*1000/2);
                            if (inThread.isAlive())
                                ayiya.beat();
                            // @todo here, some validation of tunnel should happen. No packet received yet -> revalidation of tunnel config with TIC!
                            // ...but make sure this later happens only once (no TIC hammering...)
                            Log.i(TAG, "Sent heartbeat.");
                        }
                    } catch (InterruptedException ie) {
                        Log.i(TAG, "Tunnel thread received interrupt, closing tunnel");
                        throw ie;
                    }
                } catch (IOException e) {
                    Log.i (TAG, "Tunnel connection broke down, closing and reconnecting ayiya", e);
                    reportStatus(50, Status.Disturbed, "Disturbed", tunnelSpecification, ayiya.isValidPacketReceived());
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
                    postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_tunnel_down, Toast.LENGTH_SHORT);
                }
            }
            reportStatus(0, Status.Idle, "Tearing down", tunnelSpecification, ayiya.isValidPacketReceived());
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This configuration will not work on this device", e);
            // @todo inform the human user
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_invalid_configuration, Toast.LENGTH_LONG);
        } catch (InterruptedException e) {
            // controlled behaviour, no logging or treatment required
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // if this thread fails, the service per se is out of order
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_unexpected_problem, Toast.LENGTH_LONG);
        }
        // if this thread fails, the service per se is out of order
        ayiyaVpnService.stopSelf();
        reportStatus(0, Status.Idle, "Down", null, false);
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
                               TicTunnel activeTunnel,
                               boolean tunnelProvedWorking) {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS, status.toString())
                .putExtra(EDATA_ACTIVITY, activity)
                .putExtra(EDATA_PROGRESS, progressPerCent)
                .putExtra(EDATA_TUNNEL_PROVEN, tunnelProvedWorking);
        if (activeTunnel != null)
                statusBroadcast.putExtra(EDATA_ACTIVE_TUNNEL, activeTunnel);
        // Broadcast locally
        LocalBroadcastManager.getInstance(ayiyaVpnService).sendBroadcast(statusBroadcast);
    }

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }
}
