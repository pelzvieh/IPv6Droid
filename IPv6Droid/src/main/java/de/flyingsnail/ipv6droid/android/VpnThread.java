/*
 * Copyright (c) 2015 Dr. Andreas Feldner.
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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.AuthenticationFailedException;
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
    private static final int TIC_RECHECK_BLOCKED_MILLISECONDS = 60 * 60 * 1000; // 60 minutes

    private static final Inet6Address[] GOOGLE_DNS = new Inet6Address[2];
    private Inet4Address localIp = null;

    static {
        try {
            GOOGLE_DNS[0] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x20,0x01,0x48,0x60,0x48,0x60,0,0,0,0,0,0,0,0,(byte)0x88,(byte)0x88}
            );
            GOOGLE_DNS[1] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x20,0x01,0x48,0x60,0x48,0x60,0,0,0,0,0,0,0,0,(byte)0x88,(byte)0x44}
            );
        } catch (UnknownHostException e) {
            Log.e(TAG, "Static initializer for Google DNS failed", e);
        }
    }

    /**
     * The routing information of current network setting.
     */
    private List<RouteInfo> routeInfos;

    /**
     * The start ID of the onStartCommand call that lead to this thread being constructed. Used
     * to stop the according service.
     */
    private final int startId;

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
    private CopyThread inThread = null;

    /**
     * The thread that copies from local to PoP.
     */
    private CopyThread outThread = null;

    /**
     * The cached TicTunnel containing the previosly working configuration.
     */
    private TicTunnel tunnelSpecification;

    /**
     * An instance of StatusReport that continously gets updated during the lifecycle of this
     * VpnThread. Also, this object is (mis-) used as the synchronization object between threads
     * waiting for connectivity changes and the thread announcing such a change.
     */
    private final VpnStatusReport vpnStatus;


    /**
     * A flag that is set if the tunnel should close down.
     */
    private boolean closeTunnel;

    /**
     * A flag that is set if routes are set to the VPN tunnel.
     */
    private boolean tunnelRouted;

    /**
     * An int used to tag socket traffic initiated from the parent thread
     */
    private final int TAG_PARENT_THREAD=0x01;
    /**
     * An int used to tag socket traffic initiated from the copy thread PoP->Local
     */
    private final int TAG_INCOMING_THREAD=0x02;
    /**
     * An int used to tag socket traffic initiated from the copy thread Local->PoP
     */
    private final int TAG_OUTGOING_THREAD=0x03;
    private ConnectivityManager connectivityManager;
    private Context applicationContext;
    private Ayiya ayiya;

    /**
     * The constructor setting all required fields.
     * @param ayiyaVpnService the Service that created this thread
     * @param cachedTunnel the previously working tunnel spec, or null if none
     * @param config the tic configuration
     * @param routingConfiguration the routing configuration
     * @param sessionName the name of this thread
     * @param startId the start ID of the onStartCommand that led to this thread being constructed
     */
    VpnThread(AyiyaVpnService ayiyaVpnService,
              TicTunnel cachedTunnel,
              TicConfiguration config,
              RoutingConfiguration routingConfiguration,
              String sessionName,
              int startId) {
        setName(sessionName);
        this.vpnStatus = new VpnStatusReport();
        this.ayiyaVpnService = ayiyaVpnService;
        this.ticConfig = (TicConfiguration)config.clone();
        this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
        this.tunnelSpecification = cachedTunnel;
        this.startId = startId;
        // extract the application context
        this.applicationContext = ayiyaVpnService.getApplicationContext();
        // resolve system service "ConnectivityManager"
        connectivityManager = (ConnectivityManager)applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        // initialise routing info
        this.routeInfos = getNetworkDetails();
    }


    @Override
    public void run() {
        try {
            TrafficStats.setThreadStatsTag(TAG_PARENT_THREAD);
            handler = new Handler(applicationContext.getMainLooper());

            vpnStatus.setProgressPerCent(5);
            vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
            vpnStatus.setActivity(R.string.vpnservice_activity_wait);
            reportStatus();
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
            configureBuilderFromTunnelSpecification(builder, tunnelSpecification, false);
            VpnService.Builder builderNotRouted = ayiyaVpnService.createBuilder();
            configureBuilderFromTunnelSpecification(builderNotRouted, tunnelSpecification, true);
            refreshTunnelLoop(builder, builderNotRouted);

            // important status change
            vpnStatus.setProgressPerCent(0);
            vpnStatus.setStatus(VpnStatusReport.Status.Idle);
            vpnStatus.setActivity(R.string.vpnservice_activity_closing);
            reportStatus();
        } catch (AuthenticationFailedException e) {
            Log.e(TAG, "Authentication step failed", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_authentication_failed, e);
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This configuration will not work on this device", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_invalid_configuration, e);
        } catch (IOException e) {
            Log.e(TAG, "IOException caught before reading in tunnel data", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_io_during_startup, e);
        } catch (InterruptedException e) {
            Log.i(TAG, "VpnThread interrupted outside of control loops", e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // if this thread fails, the service per se is out of order
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_unexpected_problem, t);
        }
        // if this thread fails, the service per se is out of order
        ayiyaVpnService.stopSelf(startId);
        vpnStatus.clear(); // back at zero
        reportStatus();
    }


    /**
     * Request the tunnel control loop (running in a different thread) to stop.
     */
    protected void requestTunnelClose() {
        Log.i(TAG, "Shutting down");
        closeTunnel = true;
        cleanAll();
        setName(getName() + " (shutting down)");
        interrupt();
    }

    /**
     * Request copy threads to close, close sockets.
     */
    private void cleanAll() {
        if (inThread != null)
            inThread.stopCopy();
        if (outThread != null)
            outThread.stopCopy();
        if (ayiya != null) {
            try {
                ayiya.close();
            } catch (Exception e) {
                Log.e(TAG, "Cannot close ayiya object", e);
            }
        }
        try {
            if (vpnFD != null)
                vpnFD.close();
        } catch (Exception e) {
            Log.e(TAG, "Cannot close local socket", e);
        }
    }

    /**
     * Run the tunnel as long as it should be running. This method ends via one of its declared
     * exceptions, or in case that this thread is interrupted.
     * @param builder the VpnService.Builder that is used to re-created the VPN Service in environments w/o IPv6
     * @param builderNotRouted the VpnService.Builder that is used in environments with IPv6
     * @throws ConnectionFailedException in case that the current configuration seems permanently defective.
     */
    private void refreshTunnelLoop(VpnService.Builder builder, VpnService.Builder builderNotRouted) throws ConnectionFailedException {
        // Prepare the tunnel to PoP
        ayiya = new Ayiya(tunnelSpecification);
        closeTunnel = false;

        while (!closeTunnel) {
            try {
                if (Thread.currentThread().interrupted())
                    throw new InterruptedException("Tunnel loop has interrupted status set");
                Log.i(TAG, "Building new local TUN and new AYIYA object");

                // check current routing information for existing IPv6 default route
                // then setup local tun and routing
                if (ipv6DefaultExists()) {
                    Log.i(TAG, "Detected existing IPv6, not setting routes to tunnel");
                    vpnFD = builderNotRouted.establish();
                    tunnelRouted = false;
                } else {
                    Log.i(TAG, "No IPv6 detected, setting routes to tunnel");
                    vpnFD = builder.establish();
                    tunnelRouted = true;
                }

                vpnStatus.setActivity(R.string.vpnservice_activity_localnet);
                vpnStatus.setProgressPerCent(50);

                // Packets to be sent are queued in this input stream.
                FileInputStream localIn = new FileInputStream(vpnFD.getFileDescriptor());

                // Packets received need to be written to this output stream.
                FileOutputStream localOut = new FileOutputStream(vpnFD.getFileDescriptor());

                // due to issue #... which doesn't appear to get fixed upstream, check local health
                if (routingConfiguration.isTryRoutingWorkaround() && !checkRouting()) {
                    Log.e(TAG, "Routing broken on this device, no default route is set for IPv6");
                    postToast(applicationContext, R.string.routingbroken, Toast.LENGTH_LONG);
                    try {
                        fixRouting();
                        if (checkRouting()) {
                            Log.i(TAG, "VPNService routing was broken on this device, but could be fixed by the workaround");
                            postToast(applicationContext, R.string.routingfixed, Toast.LENGTH_LONG);
                        }
                    } catch (RuntimeException re) {
                        ayiyaVpnService.notifyUserOfError(R.string.routingbroken, re);
                        Log.e(TAG, "Error fixing routing", re);
                    }
                }

                // setup tunnel to PoP
                ayiya.connect();
                vpnStatus.setProgressPerCent(75);
                vpnStatus.setActivity(R.string.vpnservice_activity_ping_pop);
                reportStatus();

                // Initialize the input and output streams from the ayiya socket
                DatagramSocket popSocket = ayiya.getSocket();
                ayiyaVpnService.protect(popSocket);
                InputStream popIn = ayiya.getInputStream();
                OutputStream popOut = ayiya.getOutputStream();

                // update network info
                try {
                    localIp = (Inet4Address) popSocket.getLocalAddress();
                } catch (ClassCastException e) {
                    Log.e(VpnThread.TAG, "local address is not Inet4Address", e);
                    // affects only statistics display
                }

                // start the copying threads
                outThread = new CopyThread (localIn, popOut, ayiyaVpnService, "AYIYA from local to POP", TAG_OUTGOING_THREAD, 0);
                inThread = new CopyThread (popIn, localOut, ayiyaVpnService, "AYIYA from POP to local", TAG_INCOMING_THREAD, 0);
                outThread.start();
                inThread.start();

                // now do a ping on IPv6 level. This should involve receiving one packet
                if (tunnelSpecification.getIpv6Pop().isReachable(10000)) {
                    postToast(applicationContext, R.string.vpnservice_tunnel_up, Toast.LENGTH_SHORT);
                    /* by laws of logic, a successful ping on IPv6 *must* already have set the flag
                       validPacketReceived in the Ayiya instance.
                     */
                } else {
                    Log.e(TAG, "Warning: couldn't ping pop via ipv6!");
                }

                vpnStatus.setActivity(R.string.vpnservice_activity_online);

                // loop until interrupted or tunnel defective
                monitoredHeartbeatLoop(ayiya);

            } catch (IOException e) {
                Log.i(TAG, "Tunnel connection broke down, closing and reconnecting ayiya", e);
                localIp = null;
                vpnStatus.setProgressPerCent(50);
                vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
                vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);
                reportStatus();
                try {
                    waitOnConnectivity();
                } catch (InterruptedException e1) {
                    Log.i(VpnThread.TAG, "refresh tunnel loop received interrupt while waiting on connectivity");
                }
            } catch (InterruptedException e) {
                Log.i(VpnThread.TAG, "refresh tunnel loop received interrupt", e);
            } catch (RuntimeException e) {
                ayiyaVpnService.notifyUserOfError(R.string.unexpected_runtime_exception, e);
                throw e;
            } finally {
                cleanAll();
                localIp = null;
                postToast(applicationContext, R.string.vpnservice_tunnel_down, Toast.LENGTH_SHORT);
            }
        }
        Log.i(TAG, "Tunnel thread gracefully shut down");
    }

    /**
     * Check for existing IPv6 connectivity. We're using the routing info of the operating system.
     * @return true if there's existing IPv6 connectivity
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean ipv6DefaultExists() {
        if (Build.VERSION.SDK_INT >= 21) {
            Log.d(TAG, "Checking if we have an IPv6 default route on current network");

            for (RouteInfo routeInfo : routeInfos) {
                // isLoggable would be useful here, but checks for an (outdated?) convention of TAG shorter than 23 chars
                Log.d(TAG, "Checking if route is an IPv6 default route: " + routeInfo);
                // @todo strictly speaking, we shouldn't check for default route, but for the configured route of the tunnel
                if (routeInfo.isDefaultRoute() && routeInfo.getGateway() instanceof Inet6Address) {
                    Log.i(TAG, "Identified a valid IPv6 default route existing: " + routeInfo);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *     Android 4.4 has introduced a bug with VPN routing. The android developers appear very
     *     pleased with their broken idea and unwilling to fix in any forseeable future.
     *     This methods tries to check if our device suffers from this problem.
     *     @return true if routing is OK
     */
    private boolean checkRouting() {
        try {
            Log.d(TAG, "about to check routing configuration after Builder.establish");
            Process routeChecker = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/ip", "-f", "inet6", "route", "show", "default"});
            BufferedReader reader = new BufferedReader (
                    new InputStreamReader(
                            routeChecker.getInputStream()));
            BufferedReader errreader = new BufferedReader (
                    new InputStreamReader(
                            routeChecker.getErrorStream()));
            String output = reader.readLine();
            String errors = errreader.readLine();
            try {
                routeChecker.waitFor();
            } catch (InterruptedException e) {
                // we got interrupted, so we kill our process
                routeChecker.destroy();
                Log.i(TAG, "route checker command interrupted", e);
            }
            int exitValue = 0;
            try {
                exitValue = routeChecker.exitValue();
            } catch (IllegalStateException ise) {
                // command still running. Hmmm.
                Log.wtf(TAG, "routeChecker still running after waitFor/destroy", ise);
            }
            if (output == null || exitValue != 0) {
                Log.e(TAG, "error checking route: " + errors);
                return false; // default route is not set on ipv6
            } else {
                Log.i(TAG, "Routing checked and found to be OK");
                return true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Routing could not be checked", e);
            return false; // we cannot even check :-(
        }
    }

    /**
     * Try to fix the problem detected by checkRouting. This requires rooted devices :-(
     */
    private void fixRouting() {
        try {
            Log.d(TAG, "Starting attempt to fix routing");
            Process routeAdder = Runtime.getRuntime().exec(
                    new String[]{
                            "/system/xbin/su", "-c",
                            "/system/bin/ip -f inet6 route add default dev tun0"});
            BufferedReader reader = new BufferedReader (
                    new InputStreamReader(
                            routeAdder.getErrorStream()));
            String errors = reader.readLine();
            try {
                routeAdder.waitFor();
            } catch (InterruptedException e) {
                // we got interrupted, so we kill our process
                routeAdder.destroy();
            }
            if (errors != null) {
                Log.e(TAG, "Command to set default route created error message: " + errors);
                throw new IllegalStateException("Error adding default route");
            }
            Log.i(TAG, "Added IPv6 default route to dev tun0");

            Process filterCleaner = Runtime.getRuntime().exec(
                    new String[]{
                            "/system/xbin/su", "-c",
                            "/system/bin/ip6tables -t filter -F OUTPUT"});
            reader = new BufferedReader (
                    new InputStreamReader(
                            filterCleaner.getErrorStream()));
            errors = reader.readLine();
            try {
                filterCleaner.waitFor();
            } catch (InterruptedException e) {
                // we got interrupted, so we kill our process
                filterCleaner.destroy();
            }
            int exitValue = 0;
            try {
                exitValue = filterCleaner.exitValue();
            } catch (IllegalStateException ise) {
                // command still running. Hmmm.
            }
            if (exitValue != 0) {
                Log.e(TAG, "error flushing OUTPUT: " + errors);
                throw new IllegalStateException("Error flushing OUTPUT filter");
            } else {
                Log.i(TAG, "succeeded flushing OUTPUT");
            }
            Log.d(TAG, "Routing should be fixed, command result is " + routeAdder.exitValue());
        } catch (IOException e) {
            Log.d(TAG, "Failed to fix routing", e);
        }
    }

    /**
     * Waits until the device's active connection is connected.
     * @throws InterruptedException in case of an interrupt during waiting.
     */
    private void waitOnConnectivity() throws InterruptedException {
        while (!isDeviceConnected()) {
            vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);
            reportStatus();
            synchronized (vpnStatus) {
                vpnStatus.wait();
            }
        }
    }

    private ConnectivityManager getConnectivityManager() {
        return connectivityManager;
    }

    /**
     * Check if we're on network
     * @return true if we're online
     */
    private boolean isDeviceConnected() {
        NetworkInfo ni = getConnectivityManager().getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * Check if we're on mobile network
     * @return true if we're on a mobile network currently
     */
    private boolean isNetworkMobile() {
        NetworkInfo ni = getConnectivityManager().getActiveNetworkInfo();
        return ni != null && ni.getType() == ConnectivityManager.TYPE_MOBILE;
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
        long lastPacketDelta = 0l;
        long heartbeatInterval = tunnelSpecification.getHeartbeatInterval() * 1000;
        if (heartbeatInterval < 300000l && isNetworkMobile()) {
            Log.i(TAG, "Lifting heartbeat interval to 300 secs");
            heartbeatInterval = 300000l;
        }
        while (!closeTunnel && inThread.isAlive() && outThread.isAlive()) {
            if (ayiya.isValidPacketReceived()) {
                // major status update, just once per session
                vpnStatus.setTunnelProvedWorking(true);
                vpnStatus.setStatus(VpnStatusReport.Status.Connected);
                vpnStatus.setProgressPerCent(100);
            }

            reportStatus();

            // wait for the heartbeat interval or until inThread dies.
            // Note: the inThread is reading from the network socket to the POP
            // in case of network changes, this socket breaks immediately, so
            // inThread crashes on external network changes even if no transfer
            // is active.
            inThread.join(heartbeatInterval - lastPacketDelta);
            lastPacketDelta = new Date().getTime() - ayiya.getLastPacketSentTime().getTime();
            if (inThread.isAlive() && outThread.isAlive() && lastPacketDelta >= heartbeatInterval - 100) {
                try {
                    Log.i(TAG, "Sending heartbeat");
                    ayiya.beat();
                    lastPacketDelta = 0l;
                } catch (TunnelBrokenException e) {
                    throw new IOException ("Ayiya object claims it is broken", e);
                }

                /* See if we're receiving packets:
                   no valid packet after one heartbeat - definitely not working
                   no new packets for more than heartbeat interval? Might be device sleep!
                   but if not pingable, probably broken.
                   In the latter case we give it another heartbeat interval time to recover. */
                if (isDeviceConnected() &&
                        !ayiya.isValidPacketReceived() && // if the tunnel worked in a session, don't worry if it pauses - it's 100% network problems
                        checkExpiry (ayiya.getLastPacketReceivedTime(),
                                tunnelSpecification.getHeartbeatInterval()) &&
                        !tunnelSpecification.getIpv6Pop().isReachable(10000)
                        ) {
                    if (!timeoutSuspected)
                        timeoutSuspected = true;
                    else if (new Date().getTime() - tunnelSpecification.getCreationDate().getTime()
                            > TIC_RECHECK_BLOCKED_MILLISECONDS) {
                        boolean tunnelChanged = false;
                        try {
                            tunnelChanged = readTunnelFromTIC();
                        } catch (IOException ioe) {
                            Log.i (VpnThread.TAG, "TIC and Ayiya both disturbed - assuming network problems", ioe);
                            continue;
                        }
                        if (tunnelChanged) {
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
            vpnStatus.setActivity(R.string.vpnservice_activity_query_tic);
            vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
            reportStatus();

            tic.connect();
            List<String> tunnelIds = tic.listTunnels();
            List<TicTunnel> availableTunnels = expandSuitables (tunnelIds, tic);

            if (!availableTunnels.contains(tunnelSpecification)) {
                tunnelChanged = true;
                vpnStatus.setTicTunnelList(availableTunnels);
                reportStatus();
                if (availableTunnels.isEmpty())
                    throw new ConnectionFailedException("No suitable tunnels found", null);
                else if (availableTunnels.size()>1)
                    throw new ConnectionFailedException("You must select a tunnel from list", null);
                tunnelSpecification = availableTunnels.get(0);
            }
            vpnStatus.setActivity(R.string.vpnservice_activity_selected_tunnel);
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
     * Return the tunnel descriptions from available tunnels that are suitable for this VpnThread.
     * @param tunnelIds the List of Strings containing tunnel IDs each
     * @param tic the connected Tic object
     * @return a List&lt;TicTunnel&gt; containing suitable tunnel specifications from the ID list
     * @throws IOException in case of a communication problem
     * @throws ConnectionFailedException in case of a logical problem with the setup
     */
    private List<TicTunnel> expandSuitables(List<String> tunnelIds, Tic tic) throws IOException, ConnectionFailedException {
        List<TicTunnel> retval = new ArrayList<TicTunnel>(tunnelIds.size());
        for (String id: tunnelIds) {
            TicTunnel desc;
            try {
                desc = tic.describeTunnel(id);
            } catch (TunnelNotAcceptedException e) {
                continue;
            }
            if (desc.isValid() && desc.isEnabled() && "ayiya".equals(desc.getType())){
                Log.i(TAG, "Tunnel " + id + " is suitable");
                retval.add(desc);
            }
        }
        return retval;
    }

    /**
     * <ul>
     * <li>Allow inet v4 traffic in general, as it would be disabled by default when setting up an v6
     * VPN, but only on Android 5.0 and later.</li>
     * <li>Configure builder to generate a blocking socket.</li>
     * <li>Allow applications to intentively bypass the VPN.</li>
     * </ul>
     * The respective methods are only available in API 21
     * and later.
     * Method is separate from configureBuilderFromTunnelSpecification only to safely apply the
     * TargetApi annotation and concentrate API specific code at one place.
     */
    @TargetApi(21)
    private void configureNewSettings(VpnService.Builder builder) {
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setBlocking(true);
            builder.allowBypass();
            builder.allowFamily(OsConstants.AF_INET);
        }
    }

    /**
     * Setup VpnService.Builder object (in effect, the local tun device)
     * @param builder the Builder to configure
     * @param tunnelSpecification the TicTunnel specification of the tunnel to set up.
     */
    private void configureBuilderFromTunnelSpecification(VpnService.Builder builder,
                                                         TicTunnel tunnelSpecification,
                                                         boolean suppressRouting) {
        builder.setMtu(tunnelSpecification.getMtu());
        builder.setSession(tunnelSpecification.getPopName());
        builder.addAddress(tunnelSpecification.getIpv6Endpoint(), tunnelSpecification.getPrefixLength());
        if (!suppressRouting) {
            try {
                if (routingConfiguration.isSetDefaultRoute())
                    builder.addRoute(Inet6Address.getByName("::"), 0);
                else {
                    String routeDefinition = routingConfiguration.getSpecificRoute();
                    StringTokenizer tok = new StringTokenizer(routeDefinition, "/");
                    if (!tok.hasMoreTokens())
                        throw new UnknownHostException("Empty string as route");
                    Inet6Address address = (Inet6Address) Inet6Address.getByName(tok.nextToken());
                    int prefixLen = 128;
                    if (tok.hasMoreTokens())
                        prefixLen = Integer.parseInt(tok.nextToken());
                    builder.addRoute(address, prefixLen);
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "Could not add requested IPv6 route to builder", e);
                ayiyaVpnService.notifyUserOfError(R.string.vpnservice_route_not_added, e);
                postToast(applicationContext, R.string.vpnservice_route_not_added, Toast.LENGTH_SHORT);
            }

            // add Google DNS server, if configured so
            if (routingConfiguration.isSetNameServers()) {
                for (Inet6Address dns : GOOGLE_DNS) {
                    builder.addDnsServer(dns);
                }
            }
        }

        // call method allowFamily on Builder object on API 21 and later (required in Android 5 and later)
        configureNewSettings(builder);

        // register an intent to call up main activity from system managed dialog.
        Intent configureIntent = new Intent("android.intent.action.MAIN");
        configureIntent.setClass(applicationContext, MainActivity.class);
        configureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.setConfigureIntent(PendingIntent.getActivity(applicationContext, 0, configureIntent, 0));
        Log.i(TAG, "Builder is configured");
    }

    /**
     * Generate an Android Toast
     * @param ctx the Context of the app
     * @param resId the ressource ID of the string to post
     * @param duration constant coding the duration
     */
    private void postToast (final Context ctx, final int resId, final int duration) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, resId, duration).show();
            }
        });
    }

    /**
     * Broadcast the status.
     */
    void reportStatus() {
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS_REPORT, vpnStatus);
        // Broadcast locally
        LocalBroadcastManager.getInstance(ayiyaVpnService).sendBroadcast(statusBroadcast);
    }

    /**
     * Read route infos of the currently active network. Weird code, but the best I could imagine
     * out of the ConnectivityManager API.
     */
    @TargetApi(21)
    private ArrayList<RouteInfo> getNetworkDetails () {
        ArrayList<RouteInfo> routeInfos = new ArrayList<RouteInfo>(10);
        if (Build.VERSION.SDK_INT >= 21) {
            Log.d(VpnThread.TAG, "getNetworkDetails trying to read routing");
            ConnectivityManager cm = getConnectivityManager();
            NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                List<Network> networks = Arrays.asList(cm.getAllNetworks());
                Network activeNetwork = null;
                for (Network n : networks) {
                    NetworkInfo ni = cm.getNetworkInfo(n);
                    if (ni.getType() == (activeNetworkInfo.getType()))
                        activeNetwork = n;
                }
                if (activeNetwork != null && activeNetworkInfo.isConnected()) {
                    routeInfos.addAll(cm.getLinkProperties(activeNetwork).getRoutes());
                }
            }
        }
        return routeInfos;
    }

    /**
     * Read out current statistics values
     * @return the Statistics object with current values
     */
    public Statistics getStatistics() {
        Log.d(VpnThread.TAG, "getStatistics() called");
        if (!isTunnelUp()) {
            throw new IllegalStateException("Attempt to get Statistics on a non-running tunnel");
        }
        Statistics stats;

        stats = new Statistics(
                outThread.getByteCount(),
                inThread.getByteCount(),
                outThread.getPacketCount(),
                inThread.getPacketCount(),
                tunnelSpecification.getIPv4Pop(),
                localIp,
                tunnelSpecification.getIpv6Pop(),
                tunnelSpecification.getIpv6Endpoint(),
                tunnelSpecification.getMtu(),
                routeInfos, // Routing table of the interface
                new ArrayList<InetAddress>(0),
                tunnelRouted
        );
        return stats;
    }

    /**
     * Notify all threads waiting on a status change.
     * @param intent the intent that was broadcast to flag the status change. Not currently used.
     */
    public void onConnectivityChange(Intent intent) {
        Log.i(TAG, "Connectivity changed");
        if (!intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
            synchronized (vpnStatus) {
                vpnStatus.notifyAll();
            }
            routeInfos = getNetworkDetails();
        }
        // check if our sockets are still valid
        if (ayiya != null && inThread != null && inThread.isAlive()) {
            if (!ayiya.isAlive()) {
                Log.i(TAG, "ayiya object no longer functional after connectivity change");
                inThread.stopCopy();
                interrupt();
            }
        }
    }

    /**
     * Query if the tunnel is currently running
     * @return true if the tunnel is running
     */
    public boolean isTunnelUp() {
        return (vpnStatus != null) && (vpnStatus.getStatus().equals(VpnStatusReport.Status.Connected));
    }

}
