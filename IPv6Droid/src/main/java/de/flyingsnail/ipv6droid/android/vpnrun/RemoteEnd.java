/*
 *
 *  * Copyright (c) 2021 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.vpnrun;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.ExecutorService;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.UserNotificationCallback;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.android.statistics.TransmissionStatistics;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterBuilder;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.ayiya.Ayiya;

/**
 * This class encapsulates the information required to run a tunnel with a fixed local
 * end via a remote end that changes as native OS network changes between networks.
 *
 * @author pelzi
 */
public class RemoteEnd implements NetworkChangeListener {
    static final String TAG = RemoteEnd.class.getName();
    private final LocalEnd localEnd;
    private final VpnStatusReport vpnStatus;
    private boolean intendedToRun;
    private final boolean forcedRoute;
    private final boolean isRouted;
    private final NetworkHelper networkHelper;
    private Inet4Address localIp = null;
    private final ExecutorService executor;
    /**
     * The incoming statistics collector.
     */
    private final TransmissionStatistics ingoingStatistics;
    /**
     * The outgoing statistics collector.
     */
    private final TransmissionStatistics outgoingStatistics;

    /**
     * The thread that copies from PoP to local.
     */
    private CopyThread inThread = null;

    /**
     * The thread that copies from local to PoP.
     */
    private CopyThread outThread = null;

    /**
     * An int used to tag socket traffic initiated from the copy thread PoP->Local
     */
    private static final int TAG_INCOMING_THREAD=0x02;
    /**
     * An int used to tag socket traffic initiated from the copy thread Local->PoP
     */
    private static final int TAG_OUTGOING_THREAD=0x03;

    /**
     * A counter of how often the remote end was reconnected during lifetime of local end
     * (i.e. since "startedAt").
     */
    private int reconnectCount;
    private Network currentNetwork = null;

    /**
     * The tunnel protocol object
     */
    private final Transporter transporter;

    private final UserNotificationCallback service;

    enum EndCause {
        REQUIRES_ROUTIING, INHIBITS_ROUTING, FD_INVALID, ON_REQUEST
    }

    /**
     * Constructor. It is important that this be called before the VPN is started by the
     * Android system.
     * @param localEnd the LocalEnd that calls this constructor
     * @param vpnStatus a VpnStatusReport object to report to
     * @param forcedRoute a boolean indicating if route through VPN should set up even if local
     *                    network is IPv6 capable
     * @param isRouted a boolean indicating if the VPN is going to be set up routed
     * @param executor an ExecutorService to be used for asnychronous tasks.
     * @param userNotificationCallback a UserNotificationCallback to generate user notifications to
     * @param tunnel a TunnelSpec specifying the tunnel to be set up
     * @throws ConnectionFailedException
     */
    RemoteEnd(final LocalEnd localEnd,
              final VpnStatusReport vpnStatus,
              final boolean forcedRoute,
              final boolean isRouted,
              final ExecutorService executor,
              final UserNotificationCallback userNotificationCallback,
              final TunnelSpec tunnel) throws ConnectionFailedException {
        this.localEnd = localEnd;
        this.vpnStatus = vpnStatus;

        intendedToRun = true;
        this.forcedRoute = forcedRoute;
        this.isRouted = isRouted;
        this.reconnectCount = 0;
        this.executor = executor;
        this.service = userNotificationCallback;

        // Prepare the tunnel to PoP
        try {
            transporter = TransporterBuilder.createTransporter(tunnel);
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("Cannot construct a transporter for this tunnel type", e);
        }

        // the statistics collector
        this.ingoingStatistics = new TransmissionStatistics();
        this.outgoingStatistics = new TransmissionStatistics();
        networkHelper = new NetworkHelper(this,
                (ConnectivityManager) localEnd.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE));
    }

    /**
     * Run tunnels with a given local end (vpnFD remaining constant, local IP remaining constant,
     * all connections staying up. In effect, this method will (re-)connect the transporter part and run
     * a suitable Monitor on it.
     *
     * @throws ConnectionFailedException in case that the current configuration seems permanently defective
     */
    EndCause refreshRemoteEnd(FileDescriptor localFD) throws ConnectionFailedException, InterruptedException {
        Date lastStartAttempt = new Date(0L);
        EndCause endCause = null;
        networkHelper.start();

        while (intendedToRun && localFD.valid()) {
            try {
                // make sure we can connect to any network
                networkHelper.getConnectivityManager().bindProcessToNetwork(null);

                // Packets to be sent are queued in this input stream.
                FileInputStream localIn = new FileInputStream(localFD);

                // Packets received need to be written to this output stream.
                FileOutputStream localOut = new FileOutputStream(localFD);

                if (Thread.interrupted())
                    throw new InterruptedException("Tunnel loop has interrupted status set");

                // ensure we're online
                waitOnConnectivity();

                // Re-Check if we should close down, as this can easily happen when waiting on connectivity
                if (!intendedToRun) {
                    break;
                }
                if (isTunnelRoutingRequired() ^ isRouted) {
                    endCause = isRouted ? EndCause.INHIBITS_ROUTING : EndCause.REQUIRES_ROUTIING;
                    break;
                }

                vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
                vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);

                // timestamp base mechanism to prevent busy looping through e.g. IOException
                Date now = new Date();
                long lastIterationRun = now.getTime() - lastStartAttempt.getTime();
                if (lastIterationRun < 1000L)
                    Thread.sleep(1000L - lastIterationRun);
                lastStartAttempt = new Date();

                // setup tunnel to PoP
                Log.i(TAG, "Connecting transporter object");
                vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
                vpnStatus.setActivity(R.string.vpnservice_activity_connecting);

                DatagramSocket popSocket = transporter.prepare();
                currentNetwork.bindSocket(popSocket);  // use the given Network explicitly
                // the certification revocation check will open its own socket, needs to be bound to native
                networkHelper.getConnectivityManager().bindProcessToNetwork(currentNetwork);
                /* this is from Android VpnService how-to. Let's try without, as we've bound
                   this socket to the OS native network above.
                localEnd.getVpnThread().getService().protect(popSocket); // do not redirect to VPN
                 */

                Log.i(TAG, "Connecting transporter");
                transporter.connect();

                Log.i(TAG, "Transporter connected");
                vpnStatus.setProgressPerCent(75);
                vpnStatus.setStatus(VpnStatusReport.Status.Connected);
                vpnStatus.setCause(null);

                // Initialize the input and output streams from the transporter socket
                InputStream popIn = transporter.getInputStream();
                OutputStream popOut = transporter.getOutputStream();

                // update network info
                try {
                    localIp = (Inet4Address) popSocket.getLocalAddress();
                } catch (ClassCastException e) {
                    Log.e(TAG, "local address is not Inet4Address", e);
                    // affects only statistics display
                }

                // start the copying threads
                Log.i (TAG, "Starting copy threads");
                synchronized (this) {
                    outThread = new CopyThread(localIn, popOut, service, this, "Transport from local to POP", TAG_OUTGOING_THREAD, 0, outgoingStatistics);
                    inThread = new CopyThread(popIn, localOut, service, this, "Transport from POP to local", TAG_INCOMING_THREAD, 0, ingoingStatistics);
                    outThread.start();
                    inThread.start();
                }
                vpnStatus.setActivity(R.string.vpnservice_activity_ping_pop);
                vpnStatus.setCause(null);

                Monitor vpnMonitor =
                        transporter instanceof Ayiya ?
                                new HeartbeatMonitor(this, inThread, outThread) :
                                new SimpleMonitor(this, inThread, outThread);

                // now the tunnel is expected to work so future sockets are no longer bound to native
                networkHelper.getConnectivityManager().bindProcessToNetwork(null);

                // now do a ping on IPv6 level. This should involve receiving one packet
                if (!Inet6Address.getByName(localEnd.getApplicationContext().getString(R.string.ipv6_test_host)).isReachable(10000)) {
                    Log.e(TAG, "Warning: couldn't ping pop via ipv6!");
                }

                vpnStatus.setActivity(R.string.vpnservice_activity_online);

                // loop until interrupted or tunnel defective
                vpnMonitor.loop();
                Log.i(TAG, "monitored heartbeat loop ended");
            } catch (IOException e) {
                Log.i(TAG, "Tunnel connection broke down, closing and reconnecting transporter (remote end)", e);
                vpnStatus.setProgressPerCent(50);
                vpnStatus.setCause(e);
                vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            } catch (InterruptedException e) {
                networkHelper.stop();
                Log.i(TAG, "refresh remote end loop received interrupt");
                throw e;
            } catch (RuntimeException | ConnectionFailedException e) {
                Log.w(TAG, "refresh remote end loop received unexpected exception");
                stop();
                throw e;
            } finally {
                cleanCopyThreads();
                localIp = null;
            }
            reconnectCount++;
        }
        if (endCause == null) {
            endCause = intendedToRun ? EndCause.FD_INVALID : EndCause.ON_REQUEST;
        }
        Log.i(TAG, "refreshRemoteEnd loop terminated - " +
                endCause.toString());
        return endCause;
    }

    void stop() {
        networkHelper.stop();
        intendedToRun = false;
        cleanCopyThreads();
    }

    /**
     * Waits until the device's active connection is connected.
     *
     */
    private void waitOnConnectivity() throws InterruptedException {
        while (!isDeviceConnected()) {
            Log.i(TAG, "Waiting for device to connect to a network");
            vpnStatus.setProgressPerCent(45);
            vpnStatus.setStatus(VpnStatusReport.Status.NoNetwork);
            vpnStatus.setActivity(R.string.vpnservice_activity_connectivity);
            synchronized (this) {
                this.wait();
            }
        }
        currentNetwork = networkHelper.getNativeNetwork();
        Log.i(TAG, "We're connected to network " + currentNetwork.toString());
    }

    /**
     * Query for the current local IP of our tunnel.
     * @return a Inet4Address giving the current local IP.
     */
    private Inet4Address getLocalIp() {
        return localIp;
    }


    /**
     * The device has a new connection. Details can be queried from NetworkHelper.
     */
    @Override
    public void onNewConnection() {
        // if someone would call this, but not NetworkHelper. This would be really strange.
        if (networkHelper == null)
            return;
        // check if our routing is still valid, otherwise invalidate vpnFD
        if (isTunnelRoutingRequired() ^ isRouted) {
            Log.i(TAG, "tunnel routing requirement changed, forcing re-build of local vpn socket");
            stop();
        } else {
            // check if our sockets are still valid
            final Transporter myTransporter = transporter; // avoid race conditions
            final CopyThread myInThread = inThread;
            if (myTransporter != null && myInThread != null && myInThread.isAlive()) {
                /*
                   myTransporter.isAlive is not sufficient to detect impact of connectivity change!
                   Reason is probably that the formerly used network can still be used for a limited
                   time period.
                 */
                if (!(myTransporter.isAlive() && isCurrentSocketStillValid())) {
                    Log.i(TAG, "transporter object no longer functional after connectivity change - reconnecting");
                    executor.submit(() -> {
                        try {
                            cleanCopyThreads();
                        } catch (Throwable t) {
                            Log.e(TAG, "stopping copy threads failed", t);
                        }
                        return null;
                    });
                }
            } // vpn copy threads are still running
        }

        // wake up threads waiting on connectivity
        synchronized (this) {
            notifyAll();
        }

    }

    /**
     * The device just went offline.
     */
    @Override
    public void onDisconnected() {
        // if someone would call this, but not NetworkHelper. This would be really strange.
        if (networkHelper == null)
            return;

        currentNetwork = networkHelper.getNativeNetwork(); // usually null at this point...

        Log.i(TAG, "We're no longer connected.");
        vpnStatus.setProgressPerCent(45);
        vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
        vpnStatus.setActivity(R.string.vpnservice_activity_connectivity);
    }



    /**
     * A copy thread calls back to state that it is gone.
     * @param diedThread the CopyThread that died.
     */
    protected void copyThreadDied(CopyThread diedThread) {
        // if one copy thread died, the transporter is useless anyway.
        Log.i(TAG, "A copy thread died, closing transporter out-of-sync");
        transporter.close();
        // no special treatment for inThread required, a dying inThread is immediately noticed
        // by VpnThread.
        if (diedThread != inThread && diedThread == outThread && inThread != null) {
            Log.i(TAG, "outThread notified us of its death, killing inThread as well");
            inThread.stopCopy();
            // inThread is now dying as well, not going unnoticed by monitoredHeartbeatLoop
        }
    }

    /**
     * Check if we're still on the same network.
     *
     * @return true if the current local address still matches one of the link addresses
     */
    boolean isCurrentSocketStillValid() {
        Network nn = networkHelper == null ? null : networkHelper.getNativeNetwork();
        return nn != null && nn.equals(currentNetwork);
    }



    /**
     * Check if we're on mobile network
     * @return true if we're on a mobile network currently
     */
    boolean isNetworkMobile() {
        NetworkInfo ni = networkHelper.getConnectivityManager().getActiveNetworkInfo();
        return ni != null && ni.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    Transporter getTransporter() {
        return transporter;
    }

    /**
     * Check if we're on the network we think we are
     * @return true if we're online on the right network
     */
    boolean isDeviceConnected() {
        Network n = networkHelper.getNativeNetwork();
        return n != null;
    }

    /**
     * Request copy threads to close, reset thread fields, and close transporter object
     */
    private void cleanCopyThreads() {
        final Transporter myTransporter = transporter; // avoid race condition
        if (myTransporter != null) {
            try {
                myTransporter.close();
            } catch (Exception e) {
                Log.e(TAG, "Cannot close transporter object", e);
            }
        }
        // by closing the transporter, we were shooting the copy threads in their feet anyway
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
    }


    private boolean isTunnelRoutingRequired() {
        return forcedRoute || !ipv6DefaultExists();
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

    public boolean isIntendedToRun() {
        return intendedToRun;
    }

    Statistics addStatistics(Statistics stats) {
        return stats
                .addIngoingStatistics(ingoingStatistics)
                .addOutgoingStatistics(outgoingStatistics)
                .setMyIPv4(getLocalIp())
                .setNativeDnsSetting(networkHelper.getNativeDnsServers())
                .setVpnDnsSetting(networkHelper.getVpnDnsServers())
                .setNativeRouting(networkHelper.getNativeRouteInfos())
                .setVpnRouting(networkHelper.getVpnRouteInfos())
                .setReconnectCount(reconnectCount);
    }

}
