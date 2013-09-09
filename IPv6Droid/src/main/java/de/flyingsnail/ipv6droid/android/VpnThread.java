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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Build;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.net.ssl.SSLContext;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.Ayiya;
import de.flyingsnail.ipv6droid.ayiya.ConnectionFailedException;
import de.flyingsnail.ipv6droid.ayiya.Tic;
import de.flyingsnail.ipv6droid.ayiya.TicConfiguration;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;
import de.flyingsnail.ipv6droid.ayiya.TunnelBrokenException;
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
    public static final String EDATA_STATUS_REPORT = AyiyaVpnService.class.getName() + ".STATUS_REPORT";

    /**
     * The tag for logging.
     */
    private static final String TAG = VpnThread.class.getName();

    /**
     * Time that we must wait before contacting TIC again. This applies to cached tunnels even!
     */
    private static final int TIC_RECHECK_BLOCKED_MILLISECONDS = 5 * 60 * 1000; // 5 minutes

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

    /**
     * The cached TicTunnel containing the previosly working configuration.
     */
    private TicTunnel tunnelSpecification;


    /**
     * The specific SSLContext, required as SixXS choose a cert provider not shipped with
     * Android trust stores.
     */
    private SSLContext sslContext;

    /**
     * An instance of StatusReport that continously gets updated during the lifecycle of this
     * VpnThread. Also, this object is (mis-) used as the synchronization object between threads
     * waiting for connectivity changes and the thread announcing such a change.
     */
    private VpnStatusReport vpnStatus;

    /**
     * The constructor setting all required fields.
     * @param ayiyaVpnService the Service that created this thread
     * @param cachedTunnel the previously working tunnel spec, or null if none
     * @param config the tic configuration
     * @param routingConfiguration the routing configuration
     * @param sessionName the name of this thread
     * @param sslContext the SSLContext to use for TLS
     */
    VpnThread(AyiyaVpnService ayiyaVpnService,
              TicTunnel cachedTunnel,
              TicConfiguration config,
              RoutingConfiguration routingConfiguration,
              String sessionName,
              SSLContext sslContext) {
        setName(sessionName);
        this.vpnStatus = new VpnStatusReport();
        this.ayiyaVpnService = ayiyaVpnService;
        this.ticConfig = (TicConfiguration)config.clone();
        this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
        this.tunnelSpecification = cachedTunnel;
        this.sslContext = sslContext;
    };


    @Override
    public void run() {
        try {
            handler = new Handler(ayiyaVpnService.getApplicationContext().getMainLooper());

            waitOnConnectivity();

            if (tunnelSpecification == null) {
                readTunnelFromTIC();
            } else {
                Log.i(TAG, "Using cached TicTunnel instead of contacting TIC");
            }
            vpnStatus.setActiveTunnel(tunnelSpecification);
            vpnStatus.setProgressPerCent(25);
            reportStatus();

            // build vpn device on local machine
            VpnService.Builder builder = ayiyaVpnService.createBuilder();
            configureBuilderFromTunnelSpecification(builder, tunnelSpecification);
            refreshTunnelLoop(builder);

            // important status change
            vpnStatus.setProgressPerCent(0);
            vpnStatus.setStatus(VpnStatusReport.Status.Idle);
            vpnStatus.setActivity(R.id.vpnservice_activity_closing);
            reportStatus();
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This configuration will not work on this device", e);
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_invalid_configuration, Toast.LENGTH_LONG);
        } catch (IOException e) {
            Log.e(TAG, "IOException caught before reading in tunnel data", e);
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_io_during_startup, Toast.LENGTH_LONG);
        } catch (InterruptedException e) {
            Log.i(TAG, "VpnThread interrupted outside of control loops", e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // if this thread fails, the service per se is out of order
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_unexpected_problem, Toast.LENGTH_LONG);
        }
        // if this thread fails, the service per se is out of order
        ayiyaVpnService.stopSelf();
        vpnStatus = new VpnStatusReport(); // back at zero
        reportStatus();
    }

    /**
     * Run the tunnel as long as it should be running. This method ends via one of its declared
     * exceptions, or in case that this thread is interrupted.
     * @param builder
     * @throws ConnectionFailedException in case that the current configuration seems permanently defective.

     */
    private void refreshTunnelLoop(VpnService.Builder builder) throws ConnectionFailedException {
        // Perpare the tunnel to PoP
        Ayiya ayiya = new Ayiya (tunnelSpecification);
        boolean caughtInterruptedException = false;

        while (!(Thread.currentThread().isInterrupted() || caughtInterruptedException)) {
            Log.i(TAG, "Building new local TUN and new AYIYA object");
            try {
                // setup local tun and routing
                vpnFD = builder.establish();
                vpnStatus.setActivity(R.id.vpnservice_activity_localnet);
                vpnStatus.setProgressPerCent(50);

                // Packets to be sent are queued in this input stream.
                FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                // Packets received need to be written to this output stream.
                FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                // setup tunnel to PoP
                ayiya.connect();
                vpnStatus.setProgressPerCent(75);
                vpnStatus.setActivity(R.id.vpnservice_activity_ping_pop);
                reportStatus();

                // Initialize the input and output streams from the ayiya socket
                DatagramSocket popSocket = ayiya.getSocket();
                ayiyaVpnService.protect(popSocket);
                InputStream popIn = ayiya.getInputStream();
                OutputStream popOut = ayiya.getOutputStream();

                // start the copying threads
                inThread = startStreamCopy (localIn, popOut);
                inThread.setName("AYIYA from local to POP");
                outThread = startStreamCopy (popIn, localOut);
                outThread.setName("AYIYA from POP to local");

                // now do a ping on IPv6 level. This should involve receiving one packet
                if (tunnelSpecification.getIpv6Pop().isReachable(10000)) {
                    postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_tunnel_up, Toast.LENGTH_SHORT);
                    /* by laws of logic, a successful ping on IPv6 *must* already have set the flag
                       validPacketReceived in the Ayiya instance.
                     */
                } else {
                    Log.e(TAG, "Warning: couldn't ping pop via ipv6!");
                };

                vpnStatus.setActivity(R.id.vpnservice_activity_online);

                // loop until interrupted or tunnel defective
                monitoredHeartbeatLoop(ayiya);

            } catch (IOException e) {
                Log.i (TAG, "Tunnel connection broke down, closing and reconnecting ayiya", e);
                vpnStatus.setProgressPerCent(50);
                vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
                vpnStatus.setActivity(R.id.vpnservice_activity_reconnect);
                reportStatus();
                try {
                    waitOnConnectivity();
                } catch (InterruptedException e1) {
                    caughtInterruptedException = true;
                }
            } catch (InterruptedException e) {
                caughtInterruptedException = true;
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
        Log.i(TAG, "Tunnel thread received interrupt, closing tunnel");
    }

    /**
     * Waits until the device's active connection is connected.
     * @throws InterruptedException in case of an interrupt during waiting.
     */
    private void waitOnConnectivity() throws InterruptedException {
        while (!isDeviceConnected()) {
            vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            vpnStatus.setActivity(R.id.vpnservice_activity_reconnect);
            reportStatus();
            synchronized (vpnStatus) {
                vpnStatus.wait();
            }
        }
    }

    private boolean isDeviceConnected() {
        ConnectivityManager cm =
                (ConnectivityManager)ayiyaVpnService.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * This loop monitors the two copy threads and generates heartbeats in half the heartbeat
     * interval. It detects tunnel defects by a number of means and exits by one of its
     * declared exceptions when either it is no longer intended to run or the given ayiya doesn't
     * seem to work any more. It just exits if one of the copy threads terminated (see there).
     *
     * @param ayiya the ayiya instance with which this is operating
     * @throws InterruptedException if someone (e.g. the user...) doesn't want us to go on.
     * @throws IOException in case of a (usually temporary) technical problem with the current ayiya.
     *   Often, this means that our IP address did change.
     * @throws ConnectionFailedException in case of a more fundamental problem, e.g. if the tunnel
     *   is not enabled any more in TIC, or the given and up-to-date TIC information in the tunnel
     *   repeatedly doesn't lead to a working tunnel.
     */
    private void monitoredHeartbeatLoop(Ayiya ayiya) throws InterruptedException, IOException, ConnectionFailedException {
        boolean timeoutSuspected = false;
        while (!Thread.currentThread().isInterrupted() && inThread.isAlive() && outThread.isAlive()) {
            if (ayiya.isValidPacketReceived()) {
                // major status update, just once per session
                vpnStatus.setTunnelProvedWorking(true);
                vpnStatus.setStatus(VpnStatusReport.Status.Connected);
                vpnStatus.setProgressPerCent(100);
            }

            reportStatus();

            // wait for half the heartbeat interval or until inThread dies.
            // Note: the inThread is reading from the network socket to the POP
            // in case of network changes, this socket breaks immediately, so
            // inThread crashes on external network changes even if no transfer
            // is active.
            inThread.join(tunnelSpecification.getHeartbeatInterval()*1000/2);
            if (inThread.isAlive()) {
                try {
                    ayiya.beat();
                } catch (TunnelBrokenException e) {
                    throw new IOException ("Ayiya object claims it is broken", e);
                }

                /* See if we're receiving packets:
                   no valid packet after one heartbeat - definitely not working
                   no new packets for more than heartbeat interval? Might be device sleep!
                   but if not pingable, probably broken.
                   In the latter case we give it another heartbeat interval time to recover. */
                if (!ayiya.isValidPacketReceived() // no valid packet after one heartbeat is annoying
                        || (checkExpiry (ayiya.getLastPacketReceivedTime(),
                                         tunnelSpecification.getHeartbeatInterval())
                            && !tunnelSpecification.getIpv6Pop().isReachable(10000))
                   ) {
                    if (!timeoutSuspected)
                        timeoutSuspected = true;
                    else if (new Date().getTime() - tunnelSpecification.getCreationDate().getTime()
                            > TIC_RECHECK_BLOCKED_MILLISECONDS) {
                        if (readTunnelFromTIC()) {
                            // TIC had new data - signal an IO problem to rebuild tunnel
                            throw new IOException("Packet receiving had timeout and TIC information changed");
                        } else {
                            throw new ConnectionFailedException("This TIC tunnel doesn't receive data", null);
                        }
                    }
                } else
                    timeoutSuspected = false;

                Log.i(TAG, "Sent heartbeat.");
            }
        }
        Log.i(TAG, "Terminated loop of current ayiya object (interrupt or end of a copy thread)");
    }

    /**
     * Read tunnel information via the TIC protocol. Return true if anything changed on the current
     * tunnel.
     * @return true if something changed
     * @throws ConnectionFailedException if some permanent problem exists with TIC and the current config
     * @throws IOException if some (hopefully transient) technical problem came up.
     */
    private boolean readTunnelFromTIC() throws ConnectionFailedException, IOException {
        boolean tunnelChanged = false;
        // gather some client information for the nosy TIC
        Tic.ContextInfo contextInfo;
        try {
            contextInfo = new Tic.ContextInfo(
                    ayiyaVpnService.getPackageName(),
                    ayiyaVpnService.getPackageManager().getPackageInfo(ayiyaVpnService.getPackageName(), 0).versionName,
                    "Android",
                    Build.VERSION.RELEASE);
        } catch (PackageManager.NameNotFoundException e) {
            throw new ConnectionFailedException("Unable to read version name", e);
        }

        // Initialize new Tic object
        Tic tic = new Tic(ticConfig, contextInfo);
        try {
            // some status reporting...
            vpnStatus.setActivity(R.id.vpnservice_activity_query_tic);
            vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
            reportStatus();

            tic.connect(sslContext);
            List<String> tunnelIds = tic.listTunnels();
            TicTunnel newTunnelSpecification = selectFirstSuitable(tunnelIds, tic);
            tunnelChanged = !newTunnelSpecification.equals(tunnelSpecification);
            tunnelSpecification = newTunnelSpecification;
            vpnStatus.setActivity(R.id.vpnservice_activity_selected_tunnel);
        } finally {
            tic.close();
        }
        return tunnelChanged;
    }

    private static boolean checkExpiry (Date lastReceived, int heartbeatInterval) {
        Calendar oldestExpectedPacket = Calendar.getInstance();
        oldestExpectedPacket.add(Calendar.SECOND, -heartbeatInterval);
        if (lastReceived.before(oldestExpectedPacket.getTime())) {
            Log.i(TAG, "Our tunnel is having trouble - we didn't receive packets since "
                    + lastReceived + " (expected no earlier than " + oldestExpectedPacket.getTime()
                    + ")"
            );
            return true;
        }
        return false;
    }

    /**
     * Return the first tunnel from available tunnels that is suitable for this VpnThread.
     * @param tunnelIds the List of Strings containing tunnel IDs each
     * @param tic the connected Tic object
     * @return a TicTunnel specifying the tunnel to build up
     * @throws IOException in case of a communication problem
     * @throws ConnectionFailedException in case of a logical problem with the setup
     */
    private TicTunnel selectFirstSuitable(List<String> tunnelIds, Tic tic) throws IOException, ConnectionFailedException {
        for (String id: tunnelIds) {
            TicTunnel desc;
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
        builder.addAddress(tunnelSpecification.getIpv6Endpoint(), tunnelSpecification.getPrefixLength());
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
            postToast(ayiyaVpnService.getApplicationContext(), R.id.vpnservice_route_not_added, Toast.LENGTH_SHORT);
        }

        // register an intent to call up main activity from system managed dialog.
        Intent configureIntent = new Intent("android.intent.action.MAIN");
        configureIntent.setClass(ayiyaVpnService.getApplicationContext(), MainActivity.class);
        configureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.setConfigureIntent(PendingIntent.getActivity(ayiyaVpnService.getApplicationContext(), 0, configureIntent, 0));
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


    void reportStatus(/*int progressPerCent,
                               VpnStatusReport.Status status,
                               String activity,
                               TicTunnel activeTunnel,
                               boolean tunnelProvedWorking*/) {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS_REPORT, vpnStatus);
        // Broadcast locally
        LocalBroadcastManager.getInstance(ayiyaVpnService).sendBroadcast(statusBroadcast);
    }

    /**
     * Notify all threads waiting on a status change.
     * @param intent the intent that was broadcast to flag the status change. Not currently used.
     */
    public void onConnectivityChange(Intent intent) {
        synchronized (vpnStatus) {
            vpnStatus.notifyAll();
        }
    }
}
