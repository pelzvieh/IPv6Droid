/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.IPv6Droid;
import de.flyingsnail.ipv6droid.android.UserNotificationCallback;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworkProperty;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworksRepository;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.android.statistics.TransmissionStatistics;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TransporterBuilder;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.ayiya.Ayiya;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * This class encapsulates the information required to run a tunnel with a fixed local
 * end via a remote end that changes as native OS network changes between networks.
 *
 * @author pelzi
 */
public class RemoteEnd {
    static final Logger logger = Logger.getLogger(RemoteEnd.class.getName());
    private final VpnStatusReport vpnStatus;
    private final Date expiryDate;
    private boolean intendedToRun;
    private final boolean forcedRoute;
    private final boolean isRouted;
    private Inet4Address localIp = null;
    private final ConnectivityManager connectivityManager;
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

    /**
     * The tunnel protocol object
     */
    private final Transporter transporter;

    private final UserNotificationCallback userNotificationCallback;
    /**
     * Are we connected to some network?
     */
    private boolean deviceConnected;
    /**
     * What is the currently connected network, or the network that was
     * last connect, if currently we're offline?
     */
    private NetworkProperty currentNetworkProperty = null;

    /**
     * The end cause of the function RefreshRemoteEnd. Might be set by asynchronous event
     * listeners prior to them shooting the copy threads.
     */
    private EndCause endCause = null;

    enum EndCause {
        REQUIRES_ROUTIING, INHIBITS_ROUTING, FD_INVALID, EXPIRED, ON_REQUEST
    }

    /**
     * Constructor. It is important that this be called before the VPN is started by the
     * Android system.
     * @param vpnStatus a VpnStatusReport object to report to
     * @param forcedRoute a boolean indicating if route through VPN should set up even if local
     *                    network is IPv6 capable
     * @param isRouted a boolean indicating if the VPN is going to be set up routed
     * @param userNotificationCallback a UserNotificationCallback to generate user notifications to
     * @param tunnel a TunnelSpec specifying the tunnel to be set up
     * @throws ConnectionFailedException in case of a permanent problem with the tunnel
     */
    RemoteEnd(final VpnStatusReport vpnStatus,
              final boolean forcedRoute,
              final boolean isRouted,
              final UserNotificationCallback userNotificationCallback,
              final TunnelSpec tunnel) throws ConnectionFailedException {
        this.vpnStatus = vpnStatus;

        intendedToRun = true;
        this.forcedRoute = forcedRoute;
        this.isRouted = isRouted;
        this.reconnectCount = 0;
        this.connectivityManager = IPv6Droid.getInstance()
                .getApplicationContext()
                .getSystemService(ConnectivityManager.class);
        this.userNotificationCallback = userNotificationCallback;
        this.expiryDate = tunnel.getExpiryDate();

        // Prepare the tunnel to PoP
        try {
            transporter = TransporterBuilder.createTransporter(tunnel);
        } catch (NoSuchAlgorithmException e) {
            throw new ConnectionFailedException("Cannot construct a transporter for this tunnel type", e);
        }

        // the statistics collector
        this.ingoingStatistics = new TransmissionStatistics();
        this.outgoingStatistics = new TransmissionStatistics();
    }

    private void onNetworkError(Throwable throwable) {
        logger.log(Level.WARNING, "Error in network property observable pipeline ", throwable);
        stop();
    }

    /**
     * Callback for device online/offline
     * @param isConnected a Boolean indicating if the device is online or offline.
     */
    private void onIsOnline(Boolean isConnected) {
        deviceConnected = Objects.requireNonNullElse(isConnected, Boolean.FALSE);
        if (isConnected) {
            synchronized (this) {
                this.notifyAll();
            }
        } else {
            // note: we're not terminating the copyThreads here. Reason is that
            // perhaps connectivity will be restored on the same network and we
            // can just go on by then.
            logger.info("We're no longer connected.");
            vpnStatus.setProgressPerCent(45);
            vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
            vpnStatus.setActivity(R.string.vpnservice_activity_connectivity);
        }
    }

    /**
     * Callback for change of the properties of the currently online network
     * (including a change of the Network itself).
     * @param networkProperty the NetworkProperty of the currently online network.
     */
    private void onNetworkChanged(@NonNull NetworkProperty networkProperty) {
        // memorize the Network associated with the previous networkProperty
        Network currentNetwork = (currentNetworkProperty == null)
                ? null : currentNetworkProperty.getNetwork();
        // act on new NetworkProperty
        currentNetworkProperty = networkProperty;
        if (currentNetwork == null || currentNetwork.getNetworkHandle() != networkProperty.getNetwork().getNetworkHandle()) {
            logger.info("transporter object no longer functional after connectivity change - reconnecting");
            cleanCopyThreads();
        }
        // check if our routing is still valid, otherwise invalidate vpnFD
        if (isTunnelRoutingRequired(networkProperty) ^ isRouted) {
            logger.info("tunnel routing requirement changed, forcing re-build of local vpn socket");
            endCause = isRouted ? EndCause.INHIBITS_ROUTING : EndCause.REQUIRES_ROUTIING;
            stop();
        }
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
        endCause = null;
        // subscribe to the required network changes
        NetworksRepository networksRepository = IPv6Droid.getInstance().getNetworksRepository();
        @NonNull final Disposable deviceOnlineSubscriptionDisposer = networksRepository
                .getDeviceOnline()
                .subscribe(this::onIsOnline);
        @NonNull final Disposable onlineNetworkPropertySubscriptionDisposer = networksRepository
                .getOnlineNetworkProperty()
                .subscribe(this::onNetworkChanged, this::onNetworkError);
        try { // try-finally to ensure disposal of disposables (no Autoclosables, unfortunately)

            while (intendedToRun && localFD.valid()) {
                try {
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
                    // Check if our tunnel is still valid, as this can easily change whilst waiting on connectivity
                    if (new Date().after(expiryDate)) {
                        endCause = EndCause.EXPIRED;
                        break;
                    }

                    vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
                    vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);

                    // timestamp base mechanism to prevent busy looping through e.g. IOException
                    Date now = new Date();
                    long lastIterationRun = now.getTime() - lastStartAttempt.getTime();
                    if (lastIterationRun < 1000L)
                        //noinspection BusyWait
                        Thread.sleep(1000L - lastIterationRun);
                    lastStartAttempt = new Date();

                    // setup tunnel to PoP
                    logger.info("Connecting transporter object");
                    vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
                    vpnStatus.setActivity(R.string.vpnservice_activity_connecting);

                    DatagramSocket popSocket = transporter.prepare();
                    currentNetworkProperty.getNetwork().bindSocket(popSocket);  // use the given Network explicitly
                    // the certification revocation check will open its own socket, needs to be bound to native
                    if (!connectivityManager.bindProcessToNetwork(currentNetworkProperty.getNetwork())) {
                        logger.info(String.format("Network %d alread became unavailable", currentNetworkProperty.getNetwork().getNetworkHandle()));
                    }
                /* this is from Android VpnService how-to. Let's try without, as we've bound
                   this socket to the OS native network above.
                localEnd.getVpnThread().getService().protect(popSocket); // do not redirect to VPN
                 */

                    logger.info("Connecting transporter");
                    transporter.connect();

                    logger.info("Transporter connected");
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
                        logger.log(Level.WARNING, "local address is not Inet4Address", e);
                        // affects only statistics display
                    }

                    // start the copying threads
                    logger.info("Starting copy threads");
                    synchronized (this) {
                        outThread = new CopyThread(
                                localIn, popOut, userNotificationCallback, this,
                                "Transport from local to POP", TAG_OUTGOING_THREAD,
                                0, outgoingStatistics);
                        inThread = new CopyThread(
                                popIn, localOut, userNotificationCallback, this,
                                "Transport from POP to local", TAG_INCOMING_THREAD,
                                0, ingoingStatistics);
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
                    connectivityManager.bindProcessToNetwork(null);

                    // now do a ping on IPv6 level. This should involve receiving one packet
                    if (!Inet6Address.getByName(IPv6Droid.getInstance().getApplicationContext().getString(R.string.ipv6_test_host)).isReachable(10000)) {
                        logger.log(Level.WARNING, "Warning: couldn't ping pop via ipv6!");
                    }

                    vpnStatus.setActivity(R.string.vpnservice_activity_online);

                    // loop until interrupted or tunnel defective
                    vpnMonitor.loop();
                    logger.info("monitored heartbeat loop ended");
                } catch (IOException e) {
                    logger.log(Level.INFO, "Tunnel connection broke down, closing and reconnecting transporter (remote end)", e);
                    vpnStatus.setProgressPerCent(50);
                    vpnStatus.setCause(e);
                    vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
                } catch (InterruptedException | RuntimeException | ConnectionFailedException e) {
                    logger.warning("refresh remote end loop received unexpected exception");
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
            logger.info("refreshRemoteEnd loop terminated - " +
                    endCause);
            return endCause;
        } finally {
            // dispose of the disposables...
            deviceOnlineSubscriptionDisposer.dispose();
            onlineNetworkPropertySubscriptionDisposer.dispose();
        }
    }

    void stop() {
        logger.info("Stopping remote end");
        intendedToRun = false;
        cleanCopyThreads();
    }

    /**
     * Waits until the device's active connection is connected.
     *
     */
    private void waitOnConnectivity() throws InterruptedException {
        while (!deviceConnected && isIntendedToRun()) {
            logger.info("Waiting for device to connect to a network");
            vpnStatus.setProgressPerCent(45);
            vpnStatus.setStatus(VpnStatusReport.Status.NoNetwork);
            vpnStatus.setActivity(R.string.vpnservice_activity_connectivity);
            synchronized (this) {
                this.wait();
            }
        }
        logger.info("We're connected to network " + currentNetworkProperty.getNetwork());
    }

    /**
     * Query for the current local IP of our tunnel.
     * @return a Inet4Address giving the current local IP.
     */
    private Inet4Address getLocalIp() {
        return localIp;
    }


    /**
     * A copy thread calls back to state that it is gone.
     * @param diedThread the CopyThread that died.
     */
    protected void copyThreadDied(CopyThread diedThread) {
        // if one copy thread died, the transporter is useless anyway.
        logger.info("A copy thread died, closing transporter out-of-sync");
        transporter.close();
        // no special treatment for inThread required, a dying inThread is immediately noticed
        // by VpnThread.
        final CopyThread myInThread = inThread; // Race-Conditions vermeiden
        if (diedThread != inThread && diedThread == outThread && myInThread != null) {
            logger.info("outThread notified us of its death, killing inThread as well");
            myInThread.stopCopy();
            // inThread is now dying as well, not going unnoticed by monitoredHeartbeatLoop
        }
    }

    /**
     * Check if we're on mobile network
     * @return true if we're on a mobile network currently
     */
    boolean isNetworkMobile() {
        NetworkInfo ni = connectivityManager.getNetworkInfo(currentNetworkProperty.getNetwork());
        logger.info("Current network information: " + ni);
        return ni != null && ni.getType() == ConnectivityManager.TYPE_MOBILE;
    }

    Transporter getTransporter() {
        return transporter;
    }

    /**
     * Request copy threads to close, reset thread fields, and close transporter object
     */
    private void cleanCopyThreads() {
        // detach our process from any network it might still be attached to...
        try {
            connectivityManager.bindProcessToNetwork(null);
        } finally {
            logger.fine("Unbound process from network");
        }
        final Transporter myTransporter = transporter; // avoid race condition
        if (myTransporter != null) {
            try {
                myTransporter.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Cannot close transporter object", e);
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


    private boolean isTunnelRoutingRequired(@NonNull final NetworkProperty networkProperty) {
        return forcedRoute || !ipv6DefaultExists(networkProperty);
    }


    /**
     * Check for existing IPv6 connectivity. We're using the nativeRouting info of the operating system.
     * @return true if there's existing IPv6 connectivity
     */
    private boolean ipv6DefaultExists(@NonNull final NetworkProperty networkProperty) {
        logger.fine("Checking if we have an IPv6 default route on current network");
        List<RouteInfo> routes = networkProperty.getProperties() != null
                ? networkProperty.getProperties().getRoutes()
                : Collections.emptyList();
        for (RouteInfo routeInfo : routes) {
            // isLoggable would be useful here, but checks for an (outdated?) convention of TAG shorter than 23 chars
            logger.fine("Checking if route is an IPv6 default route: " + routeInfo);
            // @todo strictly speaking, we shouldn't check for default route, but for the configured route of the tunnel
            if (routeInfo.isDefaultRoute() && routeInfo.getGateway() instanceof Inet6Address) {
                logger.info("Identified a valid IPv6 default route existing: " + routeInfo);
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
            vpnStatus.setTunnelProvedWorking();
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
                .setNativeDnsSetting(currentNetworkProperty.getProperties() != null ? currentNetworkProperty.getProperties().getDnsServers() : null)
                .setNativeRouting(currentNetworkProperty.getProperties().getRoutes())
                .setReconnectCount(reconnectCount);
    }

}
