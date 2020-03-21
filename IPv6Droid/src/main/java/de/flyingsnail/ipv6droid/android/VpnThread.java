/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
 *  *
 *  *     This program is free software; you can redistribute it and/or modify
 *  *     it under the terms of the GNU General Public License as published by
 *  *     the Free Software Foundation; either version 2 of the License, or
 *  *     (at your option) any later version.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU General Public License along
 *  *     with this program; if not, write to the Free Software Foundation, Inc.,
 *  *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *  *
 *  * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 *
 *
 */

package de.flyingsnail.ipv6droid.android;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.googlesubscription.SubscribeTunnelActivity;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.android.statistics.TransmissionStatistics;
import de.flyingsnail.ipv6droid.transport.AuthenticationFailedException;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterBuilder;
import de.flyingsnail.ipv6droid.transport.TunnelBrokenException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;

/**
 * This class does the actual work, i.e. logs in to TIC, reads available tunnels and starts
 * a copy thread for each direction.
 */
class VpnThread extends Thread implements NetworkChangeListener {
    /**
     * The tag for logging.
     */
    private static final String TAG = VpnThread.class.getName();

    /**
     * Time that we must wait before contacting TIC again. This applies to cached tunnels even!
     */
    private static final int TIC_RECHECK_BLOCKED_MILLISECONDS = 60 * 60 * 1000; // 60 minutes

    /**
     * The IPv6 address of the Google DNS servers.
     */
    private static final Inet6Address[] PUBLIC_DNS = new Inet6Address[4];
    /**
     * An implementation of the TunnelReader interface
     */
    private final TunnelReader tunnelReader;
    private NetworkHelper networkHelper;
    private Inet4Address localIp = null;

    static {
        try {
            // quad9 primary 2620:fe::fe
            PUBLIC_DNS[0] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x26,0x20,0x00,(byte)0xfe,0,0,0,0,0,0,0,0,0,0,0,(byte)0xfe}
            );
            // quad9 secondary 2620:fe::9
            PUBLIC_DNS[1] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x26,0x20,0x00,(byte)0xfe,0,0,0,0,0,0,0,0,0,0,0,0x9}
            );
            // Google primary 2001:4860:4860::8888
            PUBLIC_DNS[2] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x20,0x01,0x48,0x60,0x48,0x60,0,0,0,0,0,0,0,0,(byte)0x88,(byte)0x88}
            );
            // Google secondary 2001:4860:4860::8844
            PUBLIC_DNS[3] = (Inet6Address)Inet6Address.getByAddress(
                    new byte[]{0x20,0x01,0x48,0x60,0x48,0x60,0,0,0,0,0,0,0,0,(byte)0x88,(byte)0x44}
            );
        } catch (UnknownHostException e) {
            Log.e(TAG, "Static initializer for Google DNS failed", e);
        }
    }

    /**
     * The service that created this thread.
     */
    private AyiyaVpnService ayiyaVpnService;

    /**
     * The configuration of the intended nativeRouting.
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
     * The cached Tunnels object containing the previously working configuration.
     */
    private Tunnels tunnels;

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
    private static final int TAG_PARENT_THREAD=0x01;
    /**
     * An int used to tag socket traffic initiated from the copy thread PoP->Local
     */
    private static final int TAG_INCOMING_THREAD=0x02;
    /**
     * An int used to tag socket traffic initiated from the copy thread Local->PoP
     */
    private static final int TAG_OUTGOING_THREAD=0x03;
    /**
     * Our app's Context
     */
    private Context applicationContext;
    /**
     * The tunnel protocol object
     */
    private Transporter transporter;
    /**
     * The incoming statistics collector.
     */
    private final TransmissionStatistics ingoingStatistics;
    /**
     * The outgoing statistics collector.
     */
    private final TransmissionStatistics outgoingStatistics;
    /**
     * The Date when the local side of the tunnel was created
     * (should be equivalent of connection stability to apps using the tunnel).
     */
    private Date startedAt;
    /**
     * A counter of how often the remote end was reconnected during lifetime of local end
     * (i.e. since "startedAt").
     */
    private int reconnectCount;

    /**
     * The constructor setting all required fields.
     * @param ayiyaVpnService the Service that created this thread
     * @param cachedTunnels the previously working tunnel spec, or null if none
     * @param routingConfiguration the nativeRouting configuration
     * @param sessionName the name of this thread
     */
    VpnThread(@NonNull AyiyaVpnService ayiyaVpnService,
              @Nullable Tunnels cachedTunnels,
              @NonNull RoutingConfiguration routingConfiguration,
              @NonNull String sessionName) {
        setName(sessionName);
        this.ayiyaVpnService = ayiyaVpnService;
        this.vpnStatus = new VpnStatusReport(ayiyaVpnService);
        try {
            this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cloning of RoutingConfiguration failed", e);
        }
        this.tunnels = cachedTunnels;
        // extract the application context
        this.applicationContext = ayiyaVpnService.getApplicationContext();
        // the statistics collector
        this.ingoingStatistics = new TransmissionStatistics();
        this.outgoingStatistics = new TransmissionStatistics();
        TunnelReader tr;
        try {
            tr = new TicTunnelReader(ayiyaVpnService);
            Log.i(TAG, "Using Tic Tunnel config");
        } catch (ConnectionFailedException e) {
            try {
                tr = new DTLSTunnelReader(ayiyaVpnService);
                Log.i(TAG, "Using DTLS config");
            } catch (ConnectionFailedException e1) {
                Log.i(TAG, "Falling back to subscription tunnels", e1);
                tr = new SubscriptionTunnelReader(ayiyaVpnService);
            }
        }
        this.tunnelReader = tr;
        this.startedAt = new Date(0l);
        this.reconnectCount = 0;
    }


    @Override
    public void run() {
        closeTunnel = false;
        try {
            networkHelper = new NetworkHelper(this, ayiyaVpnService);

            TrafficStats.setThreadStatsTag(TAG_PARENT_THREAD);
            handler = new Handler(applicationContext.getMainLooper());

            vpnStatus.setProgressPerCent(5);
            vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
            vpnStatus.setActivity(R.string.vpnservice_activity_wait);

            waitOnConnectivity();
            vpnStatus.setCause(null);

            // startup process during which no parallel shutdown is allowed
            VpnService.Builder builder;
            VpnService.Builder builderNotRouted;
            synchronized (this) {
                if (tunnels == null || !tunnels.checkCachedTunnelAvailability() || !tunnels.isTunnelActive()) {
                    // some status reporting...
                    vpnStatus.setActivity(R.string.vpnservice_activity_query_tic);
                    readTunnels(); // ensures tunnels to be set, preserves active tunnel if still valid
                    vpnStatus.setTunnels(tunnels);
                    // check for active tunnel
                    if (!tunnels.isTunnelActive()) {
                        if (tunnels.isEmpty())
                            throw new ConnectionFailedException("No suitable tunnels found", null);
                        else
                            throw new ConnectionFailedException("You must select a tunnel from list", null);
                    }
                } else {
                    Log.i(TAG, "Using cached TicTunnel instead of contacting TIC");
                }
                vpnStatus.setTunnels(tunnels);
                vpnStatus.setProgressPerCent(25);
                vpnStatus.setActivity(R.string.vpnservice_activity_selected_tunnel);

                // build vpn device on local machine
                builder = ayiyaVpnService.createBuilder();
                TunnelSpec activeTunnel = tunnels.getActiveTunnel();
                //noinspection ConstantConditions
                configureBuilderFromTunnelSpecification(builder, activeTunnel, false);
                builderNotRouted = ayiyaVpnService.createBuilder();
                configureBuilderFromTunnelSpecification(builderNotRouted, activeTunnel, true);
            }
            refreshTunnelLoop(builder, builderNotRouted);

            // important status change
            vpnStatus.setProgressPerCent(0);
            vpnStatus.setStatus(VpnStatusReport.Status.Idle);
            vpnStatus.setActivity(R.string.vpnservice_activity_closing);
            vpnStatus.setCause(null);
        } catch (AuthenticationFailedException e) {
            Log.e(TAG, "Authentication step failed", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_authentication_failed, e);
            vpnStatus.setCause(e);
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This configuration will not work on this device", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_invalid_configuration, e);
            vpnStatus.setCause(e);
        } catch (IOException e) {
            Log.e(TAG, "IOException caught before reading in tunnel data", e);
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_io_during_startup, e);
            vpnStatus.setCause(e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // something went wrong in an unexpected way
            ayiyaVpnService.notifyUserOfError(R.string.vpnservice_unexpected_problem, t);
            vpnStatus.setCause(t);
        } finally {
            networkHelper.destroy();
        }
        vpnStatus.clear(); // back at zero
    }


    /**
     * Request the tunnel control loop (running in a different thread) to stop.
     */
    void requestTunnelClose() {
        if (networkHelper != null)
            networkHelper.destroy();
        if (isIntendedToRun()) {
            Log.i(TAG, "Shutting down");
            closeTunnel = true;
            cleanAll();
            setName(getName() + " (shutting down)");
            interrupt();
        }
    }

    /**
     * Request copy threads to close, reset thread fields, and close transporter object
     */
    private synchronized void cleanCopyThreads() {
        final CopyThread myInThread = inThread; // Race-Conditions vermeiden
        if (myInThread != null) {
            inThread = null;
            myInThread.stopCopy();
        }
        final CopyThread myOutThread = outThread; // Race-Conditions vermeiden
        if (myOutThread != null) {
            outThread = null;
            myOutThread.stopCopy();
        }
        final Transporter myAyiya = transporter; // Race-Conditions vermeiden
        if (myAyiya != null) {
            try {
                myAyiya.close();
            } catch (Exception e) {
                Log.e(TAG, "Cannot close transporter object", e);
            }
        }
    }
    /**
     * Request copy threads to close, close transporter object and VPN socket.
     */
    private synchronized void cleanAll() {
        try {
            if (vpnFD != null) {
                vpnFD.close();
                Log.i(TAG, "VPN closed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot close local socket", e);
        }
        // close internet facing socket and stop copy threads
        cleanCopyThreads();
        vpnStatus.setStatus (VpnStatusReport.Status.Idle);
        vpnFD = null;
    }

    /**
     * Run tunnels with a given local end (vpnFD remaining constant, local IP remaining constant,
     * all connections staying up. In effect, this method will (re-)connect the transporter part and run
     * monitoredHeartbeatLoop on it.
     *
     * @throws ConnectionFailedException in case that the current configuration seems permanently defective
     */
    private void refreshRemoteEnd() throws ConnectionFailedException, InterruptedException {
        Date lastStartAttempt = new Date(0L);
        FileDescriptor localFD;
        try {
            localFD = refreshFD();
        } catch (IOException e) {
            Log.e(TAG, "TUN device defective before connection up", e);
            vpnStatus.setCause(e);
            vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            return;
        }
        while (!closeTunnel && localFD != null && localFD.valid()) {
            try {
                // Packets to be sent are queued in this input stream.
                FileInputStream localIn = new FileInputStream(localFD);

                // Packets received need to be written to this output stream.
                FileOutputStream localOut = new FileOutputStream(localFD);

                if (interrupted())
                    throw new InterruptedException("Tunnel loop has interrupted status set");

                // ensure we're online
                vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);
                waitOnConnectivity();

                // Re-Check if we should close down, as this can easily happen when waiting on connectivity
                if (closeTunnel) {
                    break;
                }

                // timestamp base mechanism to prevent busy looping through e.g. IOException
                Date now = new Date();
                long lastIterationRun = now.getTime() - lastStartAttempt.getTime();
                if (lastIterationRun < 1000L)
                    Thread.sleep(1000L - lastIterationRun);
                lastStartAttempt = new Date();

                // setup tunnel to PoP
                Log.i(TAG, "Connecting transporter object");
                transporter.connect();
                vpnStatus.setProgressPerCent(75);
                vpnStatus.setStatus(VpnStatusReport.Status.Connected);

                // Initialize the input and output streams from the transporter socket
                DatagramSocket popSocket = transporter.getSocket();
                ayiyaVpnService.protect(popSocket);
                InputStream popIn = transporter.getInputStream();
                OutputStream popOut = transporter.getOutputStream();

                // update network info
                try {
                    localIp = (Inet4Address) popSocket.getLocalAddress();
                } catch (ClassCastException e) {
                    Log.e(VpnThread.TAG, "local address is not Inet4Address", e);
                    // affects only statistics display
                }

                // start the copying threads
                Log.i (TAG, "Starting copy threads");
                synchronized (this) {
                    outThread = new CopyThread(localIn, popOut, ayiyaVpnService, this, "AYIYA from local to POP", TAG_OUTGOING_THREAD, 0, outgoingStatistics);
                    inThread = new CopyThread(popIn, localOut, ayiyaVpnService, this, "AYIYA from POP to local", TAG_INCOMING_THREAD, 0, ingoingStatistics);
                    outThread.start();
                    inThread.start();
                }
                vpnStatus.setActivity(R.string.vpnservice_activity_ping_pop);
                vpnStatus.setCause(null);

                // now do a ping on IPv6 level. This should involve receiving one packet
                if (Inet6Address.getByName(applicationContext.getString(R.string.ipv6_test_host)).isReachable(10000)) {
                    postToast(applicationContext, R.string.vpnservice_tunnel_up, Toast.LENGTH_SHORT);
                } else {
                    Log.e(TAG, "Warning: couldn't ping pop via ipv6!");
                }

                vpnStatus.setActivity(R.string.vpnservice_activity_online);

                // loop until interrupted or tunnel defective
                monitoredHeartbeatLoop();
                Log.i(TAG, "monitored heartbeat loop ended");
                localFD = refreshFD();
            } catch (IOException e) {
                Log.i(TAG, "Tunnel connection broke down, closing and reconnecting transporter (remote end)", e);
                vpnStatus.setProgressPerCent(50);
                vpnStatus.setCause(e);
                vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            } catch (InterruptedException e) {
                Log.i(VpnThread.TAG, "refresh remote end loop received interrupt", e);
                throw e;
            } finally {
                cleanCopyThreads();
                localIp = null;
            }
            reconnectCount++;
        }
        Log.i(VpnThread.TAG, "refreshRemoteEnd loop terminated - " +
                (closeTunnel ? "explicit close down requested" : "TUN device invalid"));
    }

    private FileDescriptor refreshFD() throws IOException {
        FileDescriptor localFD;
        if (vpnFD != null) {
            vpnFD.checkError();
            localFD = vpnFD.getFileDescriptor();
        } else {
            localFD = null;
        }
        return localFD;
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
        try {
            transporter = TransporterBuilder.createTransporter(tunnels.getActiveTunnel());
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("Cannot construct a transporter for this tunnel type", e);
        }
        closeTunnel = false;

        Date lastStartAttempt = new Date(0L);
        while (!closeTunnel) {
            try {
                if (interrupted())
                    throw new InterruptedException("Tunnel loop has interrupted status set");

                // timestamp base mechanism to prevent busy looping through e.g. IOException
                long lastIterationRun = new Date().getTime() - lastStartAttempt.getTime();
                if (lastIterationRun < 1000L)
                    Thread.sleep(1000L - lastIterationRun);
                lastStartAttempt = new Date();

                // check current nativeRouting information for existing IPv6 default route
                // then setup local tun and nativeRouting
                Log.i(TAG, "Building new local TUN  object");
                try { // catching NPE to circumvent rare Android bug, see https://github.com/pelzvieh/IPv6Droid/issues/44
                    if (isTunnelRoutingRequired()) {
                        Log.i(TAG, "No native IPv6 to use, setting routes to tunnel");
                        vpnFD = builder.establish();
                        tunnelRouted = true;
                    } else {
                        Log.i(TAG, "Detected existing IPv6, not setting routes to tunnel");
                        vpnFD = builderNotRouted.establish();
                        tunnelRouted = false;
                    }
                    startedAt = new Date();
                    reconnectCount = 0;
                } catch (NullPointerException npe) {
                    vpnFD = null;
                    Log.e (TAG, "NullPointerException from VpnService.Builder call", npe);
                    vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);
                    vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
                    vpnStatus.setCause(npe);
                    continue; // just try again
                }
                if (vpnFD == null)
                    throw new ConnectionFailedException("App is not correctly prepared to use VpnService calls", null);

                vpnStatus.setActivity(R.string.vpnservice_activity_localnet);
                vpnStatus.setProgressPerCent(50);

                // loop over IPv4 network changes
                refreshRemoteEnd();

                Log.i(TAG, "Refreshing remote VPN end stopped");

            } catch (InterruptedException e) {
                Log.i(VpnThread.TAG, "refresh tunnel loop received interrupt", e);
            } catch (ConnectionFailedException e) {
                throw e;
            } catch (Throwable t) {
                ayiyaVpnService.notifyUserOfError(R.string.unexpected_runtime_exception, t);
                Log.e(TAG, "Caught unexpected throwable", t);
                throw new ConnectionFailedException(t.getMessage(), t);
            } finally {
                cleanAll();
                postToast(applicationContext, R.string.vpnservice_tunnel_down, Toast.LENGTH_SHORT);
            }
        }
        Log.i(TAG, "Tunnel thread gracefully shut down");
    }

    private boolean isTunnelRoutingRequired() {
        return routingConfiguration.isForceRouting() || !ipv6DefaultExists();
    }

    /**
     * Check for existing IPv6 connectivity. We're using the nativeRouting info of the operating system.
     * @return true if there's existing IPv6 connectivity
     */
    private boolean ipv6DefaultExists() {
        Log.d(TAG, "Checking if we have an IPv6 default route on current network");

        for (RouteInfo routeInfo : networkHelper.getNativeRouteInfos()) {
            // isLoggable would be useful here, but checks for an (outdated?) convention of TAG shorter than 23 chars
            Log.d(TAG, "Checking if route is an IPv6 default route: " + routeInfo);
            // @todo strictly speaking, we shouldn't check for default route, but for the configured route of the tunnel
            if (routeInfo.isDefaultRoute() && routeInfo.getGateway() instanceof Inet6Address) {
                Log.i(TAG, "Identified a valid IPv6 default route existing: " + routeInfo);
                return true;
            }
        }
        return false;
    }

    /**
     * Request a status update broadcast w/o a change. Simply delegates to the respective method
     * of the status object.
     */
    void reportStatus() {
        if (vpnStatus != null)
            vpnStatus.reportStatus();
    }

    private ConnectivityManager getConnectivityManager() {
        return networkHelper.getConnectivityManager();
    }

    /**
     * Check if we're on network
     * @return true if we're online
     */
    private boolean isDeviceConnected() {
        NetworkInfo ni = networkHelper.getConnectivityManager().getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * Check if we're on mobile network
     * @return true if we're on a mobile network currently
     */
    private boolean isNetworkMobile() {
        NetworkInfo ni = networkHelper.getConnectivityManager().getActiveNetworkInfo();
        return ni != null && ni.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    /**
     * This loop monitors the two copy threads and generates heartbeats in the heartbeat
     * interval. It detects tunnel defects by a number of means and exits by one of its
     * declared exceptions when either it is no longer intended to run or the given transporter doesn't
     * seem to work any more. It just exits if one of the copy threads terminated (see there).
     *
     * @throws IOException in case of a (usually temporary) technical problem with the current transporter.
     *   Often, this means that our IP address did change.
     * @throws ConnectionFailedException in case of a more fundamental problem, e.g. if the tunnel
     *   is not enabled any more in TIC, or the given and up-to-date TIC information in the tunnel
     *   repeatedly doesn't lead to a working tunnel.
     */
    private void monitoredHeartbeatLoop() throws InterruptedException, IOException, ConnectionFailedException {
        boolean timeoutSuspected = false;
        long lastPacketDelta = 0L;
        TunnelSpec activeTunnel = tunnels.getActiveTunnel();
        @SuppressWarnings("ConstantConditions") long heartbeatInterval = activeTunnel.getHeartbeatInterval() * 1000L;
        if (heartbeatInterval < 300000L && isNetworkMobile()) {
            Log.i(TAG, "Lifting heartbeat interval to 300 secs");
            heartbeatInterval = 300000L;
        }
        while (!closeTunnel && (inThread != null && inThread.isAlive()) && (outThread != null && outThread.isAlive())) {
            // wait for the heartbeat interval to finish or until inThread dies.
            // Note: the inThread is reading from the network socket to the POP
            // in case of network changes, this socket breaks immediately, so
            // inThread crashes on external network changes even if no transfer
            // is active.
            inThread.join(heartbeatInterval - lastPacketDelta);
            if (closeTunnel)
                break;
            // re-check cached network information
            final Transporter myAyiya = transporter; // prevents race condition on null check below
            if (!isCurrentSocketAdressStillValid())
                networkHelper.updateNetworkDetails(null);
            // determine last package transmission time
            lastPacketDelta = new Date().getTime() - transporter.getLastPacketSentTime().getTime();
            // if no traffic occurred, send a heartbeat package
            if ((inThread != null && inThread.isAlive()) &&
                    (outThread != null && outThread.isAlive()) &&
                    lastPacketDelta >= heartbeatInterval - 100) {
                try {
                    Log.i(TAG, "Sending heartbeat");
                    transporter.beat();
                    lastPacketDelta = 0L;
                } catch (TunnelBrokenException e) {
                    throw new IOException ("Ayiya object claims it is broken", e);
                }

                /* See if we're receiving packets:
                   no valid packet after one heartbeat - definitely not working
                   no new packets for more than heartbeat interval? Might be device sleep!
                   but if not pingable, probably broken.
                   In the latter case we give it another heartbeat interval time to recover. */
                if (isDeviceConnected() &&
                        !transporter.isValidPacketReceived() && // if the tunnel worked in a session, don't worry if it pauses - it's 100% network problems
                        checkExpiry(transporter.getLastPacketReceivedTime(),
                                activeTunnel.getHeartbeatInterval()) &&
                        !Inet6Address.getByName(applicationContext.getString(R.string.ipv6_test_host)).isReachable(10000)
                ) {
                    if (!timeoutSuspected)
                        timeoutSuspected = true;
                    else if (activeTunnel instanceof TicTunnel && new Date().getTime() - ((TicTunnel)activeTunnel).getCreationDate().getTime()
                            > TIC_RECHECK_BLOCKED_MILLISECONDS) {
                        // todo explicit AYIYA code, refactor out of here
                        boolean tunnelChanged;
                        try {
                            tunnelChanged = readTunnels(); // no need to update activeTunnel - we're going to quit
                        } catch (IOException ioe) {
                            Log.i (VpnThread.TAG, "TIC and Ayiya both disturbed - assuming network problems", ioe);
                            continue;
                        }
                        if (tunnelChanged) {
                            vpnStatus.setTunnels(tunnels); // update tunnel list in MainActivity
                            // TIC had new data - signal a configuration problem to rebuild tunnel
                            throw new ConnectionFailedException("TIC information changed", null);
                        } else {
                            throw new ConnectionFailedException("This TIC tunnel doesn't receive data", null);
                        }
                    }
                } else
                    timeoutSuspected = false;

                Log.i(TAG, "Sent heartbeat.");
            }
        }
        Log.i(TAG, "Terminated loop of current transporter object (interrupt or end of a copy thread)");
        Throwable deathCause = null;
        final CopyThread myInThread = inThread;
        final CopyThread myOutThread = outThread;
        if (myInThread != null && !myInThread.isAlive())
            deathCause = myInThread.getDeathCause();
        if (deathCause == null && myOutThread != null && !myOutThread.isAlive())
            deathCause = myOutThread.getDeathCause();
        if (deathCause != null) {
            if (deathCause instanceof TunnelBrokenException) {
                // launch SubscribeTunnelActivity to re-check subscription status
                Log.i(TAG, "Forcing re-check of subscrioption after a TunnelBrokenException", deathCause);
                // @todo sollte eigentlich ohne Benutzer-Sichtbarkeit funktionieren
                Intent setupIntent = new Intent(ayiyaVpnService, SubscribeTunnelActivity.class);
                ayiyaVpnService.startActivity(setupIntent);
                throw new IOException("Ayiya claims it is broken", deathCause);
            }
            if (deathCause instanceof IOException)
                throw (IOException) deathCause;
        }
    }

    /**
     * Method called by the inbound copy thread if the first packet was transmitted.
     */
    void notifyFirstPacketReceived() {
        if (transporter.isValidPacketReceived()) {
            // major status update, just once per session
            vpnStatus.setTunnelProvedWorking(true);
            vpnStatus.setStatus(VpnStatusReport.Status.Connected);
            vpnStatus.setProgressPerCent(100);
            vpnStatus.setCause(null);
        }
    }
    /**
     * Read tunnel information via the TIC protocol. Return true if anything changed on the current
     * tunnel.
     * @return true if something changed
     * @throws ConnectionFailedException if some permanent problem exists with TIC and the current config
     * @throws IOException if some (hopefully transient) technical problem came up.
     */
    private boolean readTunnels() throws ConnectionFailedException, IOException {
        boolean tunnelChanged = false;

        List<? extends TunnelSpec> availableTunnels = tunnelReader.queryTunnels();
        boolean activeTunnelValid = false;
        if (tunnels == null)
            tunnels = new Tunnels(availableTunnels, null);
        else
            activeTunnelValid = tunnels.replaceTunnelList(availableTunnels);
        if (!activeTunnelValid) {
            // previous activeTunnel no longer present!
            tunnelChanged = true;
            if (tunnels.size() == 1) {
                tunnels.setActiveTunnel(tunnels.get(0));
            }
        }
        return tunnelChanged;
    }

    private static boolean checkExpiry (@NonNull Date lastReceived, int heartbeatInterval) {
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
     * Setup VpnService.Builder object (in effect, the local tun device)
     * @param builder the Builder to configure
     * @param tunnelSpecification the TicTunnel specification of the tunnel to set up.
     */
    private void configureBuilderFromTunnelSpecification(@NonNull VpnService.Builder builder,
                                                         @NonNull TunnelSpec tunnelSpecification,
                                                         boolean suppressRouting) {
        builder.setMtu(tunnelSpecification.getMtu());
        builder.setSession(tunnelSpecification.getPopName());
        builder.addAddress(tunnelSpecification.getIpv6Endpoint(), /*tunnelSpecification.getPrefixLength()*/ 128);
        if (Build.VERSION.SDK_INT >= 29)
            builder.setMetered (false);
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

            // add public DNS server, if configured so
            if (routingConfiguration.isSetNameServers()) {
                for (Inet6Address dns : PUBLIC_DNS) {
                    builder.addDnsServer(dns);
                }
            }
        }

        // Configure builder to generate a blocking socket
        builder.setBlocking(true);
        // Allow applications to intentionally bypass the VPN.
        builder.allowBypass();
        // Explicitly allow usage of IPv4 (i.e. traffic outside of the VPN)
        builder.allowFamily(OsConstants.AF_INET);

        // register an intent to call up main activity from system managed dialog.
        Intent configureIntent = new Intent("android.intent.action.MAIN");
        configureIntent.setClass(applicationContext, MainActivity.class);
        configureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.setConfigureIntent(PendingIntent.getActivity(applicationContext, 0, configureIntent, 0));
        Log.i(TAG, "Builder is configured");
    }

    /**
     * Waits until the device's active connection is connected.
     *
     */
    private void waitOnConnectivity() throws InterruptedException {
        while (!isDeviceConnected()) {
            vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);
            synchronized (vpnStatus) {
                vpnStatus.wait();
            }
        }
    }

    /**
     * Generate an Android Toast
     * @param ctx the Context of the app
     * @param resId the ressource ID of the string to post
     * @param duration constant coding the duration
     */
    private void postToast (final @NonNull Context ctx, final int resId, final int duration) {
        handler.post(() -> Toast.makeText(ctx, resId, duration).show());
    }


    /**
     * Read route and DNS info of the currently active and the VPN network. Weird code, but the best
     * I could imagine out of the ConnectivityManager API. In API versions 28, network changes
     * are no longer handled by CONNECTIVITY_ACTION broadcast, but by requestNetwork callback methods.
     * These provide the required details as parameters.
     *
     * <p>So when running in version 28 following,
     * we only use this method to</p>
     * <ul>
     * <li>store the supplied value of native network properties</li>
     * <li>enumerate the network properties of our VPN network</li>
     * </ul>
     * <p>In result, the networkDetails field will be updated.</p>
     */
    private void updateNetworkDetails(@Nullable final LinkProperties newLinkProperties) {

        // force-set native link properties to supplied information

        // direct way to read network available from API 23

        // reconstruct link properties for VPN network, and, prior to API 23, attempt to
        // find the native network's link properties.
        networkHelper.updateNetworkDetails(newLinkProperties);
    }

    /**
     * Read out current statistics values
     * @return the Statistics object with current values
     */
    @SuppressLint("Assert")
    public synchronized Statistics getStatistics() {
        Log.d(VpnThread.TAG, "getStatistics() called");
        if (!isTunnelUp()) {
            throw new IllegalStateException("Attempt to get Statistics on a non-running tunnel");
        }

        TunnelSpec activeTunnel = tunnels.getActiveTunnel();
        assert(activeTunnel != null);
        Statistics stats = null;
        try {
            stats = new Statistics(
                    outgoingStatistics,
                    ingoingStatistics,
                    startedAt,
                    reconnectCount,
                    activeTunnel.getIPv4Pop(),
                    localIp,
                    (Inet6Address)Inet6Address.getByName(applicationContext.getString(R.string.ipv6_test_host)),
                    activeTunnel.getIpv6Endpoint(),
                    activeTunnel.getMtu(),
                    networkHelper.getNativeRouteInfos(),
                    networkHelper.getVpnRouteInfos(),
                    networkHelper.getNativeDnsServers(),
                    networkHelper.getVpnDnsServers(),
                    tunnelRouted
            );
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return stats;
    }

    /**
     * Check if the local address of current Ayiya is still valid considering the cached network
     * details. A useful call to this method therefore should be precedet by a validation or update
     * of the networkDetails cache.
     * <p>This method constantly returns false when run on Android versions prior to 21.</p>
     *
     * @return true if the current local address still matches one of the link addresses
     */
    private boolean isCurrentSocketAdressStillValid() {
        Transporter myAyiya = transporter; // prevents race condition on null check below
        return networkHelper.isCurrentSocketAdressStillValid(myAyiya != null ? myAyiya.getSocket() : null);
    }

    /**
     * Query if the tunnel is currently running
     * @return true if the tunnel is running
     */
    public boolean isTunnelUp() {
        return (vpnStatus != null) && (vpnStatus.getStatus().equals(VpnStatusReport.Status.Connected));
    }

    /**
     * Query if a tunnel should be running generally. In contrast to @ref #isTunnelUp, this will
     * also return true if the tunnel is currently pausing due to lack of network connectivity.
     * @return a boolean, true if this thread is active and trying to keep a tunnel up.
     */
    public boolean isIntendedToRun() {
        return isAlive() && !closeTunnel;
    }

    /**
     * A copy thread calls back to state that it is gone.
     * @param diedThread the CopyThread that died.
     */
    protected void copyThreadDied(CopyThread diedThread) {
        // no special treatment for inThread required, a dying inThread is immediately noticed
        // by VpnThread.
        if (diedThread != inThread && diedThread == outThread && inThread != null) {
            Log.i(TAG, "outThread notified us of its death, killing inThread as well");
            inThread.stopCopy();
            // inThread is now dying as well, not going unnoticed by monitoredHeartbeatLoop
        }
    }

    /**
     * The device has a new connection. Details can be queried from NetworkHelper.
     */
    @Override
    public void onNewConnection() {
        // there is a race condition in the constructor so that networkhelper is not yet assigned.
        if (networkHelper == null)
            return;
        // check if our routing is still valid, otherwise invalidate vpnFD
        if (isTunnelRoutingRequired() ^ tunnelRouted) {
            Log.i(TAG, "tunnel routing requirement changed, forcing re-build of local vpn socket");
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        if (vpnFD != null) {
                            vpnFD.close();
                        }
                        Log.i(TAG, "VPN closed");
                        cleanCopyThreads();
                    } catch (Throwable t) {
                        Log.e(TAG, "stopping copy threads failed", t);
                    }
                    return null;
                }
            }.execute();
        } else {
            // check if our sockets are still valid
            final Transporter myAyiya = transporter; // avoid race conditions
            final CopyThread myInThread = inThread;
            if (myAyiya != null && myInThread != null && myInThread.isAlive()) {
                    /*
                       myAyiya.isAlive is not sufficient to detect impact of connectivity change!
                       Reason is probably that the formerly used network can still be used for a limited
                       time period.
                     */
                if (!(myAyiya.isAlive() && isCurrentSocketAdressStillValid())) {
                    Log.i(TAG, "transporter object no longer functional after connectivity change - reconnecting");
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            try {
                                cleanCopyThreads();
                            } catch (Throwable t) {
                                Log.e(TAG, "stopping copy threads failed", t);
                            }
                            return null;
                        }

                    }.execute();
                }
            } // vpn copy threads are still running
        }

        // wake up threads waiting on connectivity
        synchronized (vpnStatus) {
            vpnStatus.notifyAll();
        }

    }

    /**
     * The device just went offline.
     */
    @Override
    public void onDisconnected() {
        Log.i(TAG, "We're not connected anyway.");
    }
}
