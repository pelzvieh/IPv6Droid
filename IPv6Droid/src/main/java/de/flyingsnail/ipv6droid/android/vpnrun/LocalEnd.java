/*
 *
 *  * Copyright (c) 2024 Dr. Andreas Feldner.
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
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.UserNotificationCallback;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * This class encapsulates the information required to create a local
 * end of a VPN tunnel.
 *
 * @author pelzi
 */
public class LocalEnd {

    static private final String TAG = LocalEnd.class.getName();

    private final VpnThread vpnThread;
    private final VpnService.Builder builder;
    private final VpnService.Builder builderNotRouted;
    private final VpnStatusReport vpnStatus;
    private final TunnelSpec tunnel;
    private final UserNotificationCallback userNotificationCallback;
    /**
     * A flag that is set if routes are set to the VPN tunnel.
     */
    private boolean tunnelRouted;

    /**
     * A flag that is set if the tunnel should close down.
     */
    private boolean intendedToRun = true;

    /**
     * The file descriptor representing the local tun socket.
     */
    private ParcelFileDescriptor vpnFD;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private RemoteEnd remoteEnd = null;
    private final boolean forcedRoute;


    /**
     * Constructor.
     *
     * @param builder the VpnService.Builder that is used to re-created the VPN Service in environments w/o IPv6
     * @param builderNotRouted the VpnService.Builder that is used in environments with IPv6
     */
    LocalEnd(final VpnThread vpnThread,
             final VpnService.Builder builder,
             final VpnService.Builder builderNotRouted,
             final boolean forcedRoute,
             final VpnStatusReport vpnStatus,
             final TunnelSpec tunnel,
             final UserNotificationCallback userNotificationCallback
             ) {
        this.vpnThread = vpnThread;
        this.builder = builder;
        this.builderNotRouted = builderNotRouted;
        this.forcedRoute = forcedRoute;
        this.vpnStatus = vpnStatus;
        this.tunnel = tunnel;
        this.userNotificationCallback = userNotificationCallback;
    }
    /**
     * Run the tunnel as long as it should be running. This method ends via one of its declared
     * exceptions, or in case that this thread is interrupted.
     * @throws ConnectionFailedException in case that the current configuration seems permanently defective.
     */
    void refreshTunnelLoop() throws ConnectionFailedException {
        intendedToRun = true;

        Date lastStartAttempt = new Date(0L);
        // if we're called up, we assume the tunnel should be used as first assumption.
        tunnelRouted = true;
        while (intendedToRun) {
            try {
                if (vpnThread.isInterrupted())
                    throw new InterruptedException("Tunnel loop has interrupted status set");

                // timestamp base mechanism to prevent busy looping through e.g. IOException
                long lastIterationRun = new Date().getTime() - lastStartAttempt.getTime();
                if (lastIterationRun < 1000L)
                    //noinspection BusyWait
                    Thread.sleep(1000L - lastIterationRun);
                lastStartAttempt = new Date();

                // loop over IPv4 network changes
                Log.i(TAG, "Constructing remote end");
                remoteEnd = new RemoteEnd(this,
                        vpnStatus,
                        forcedRoute,
                        tunnelRouted,
                        executor,
                        userNotificationCallback,
                        tunnel);

                // check current nativeRouting information for existing IPv6 default route
                // then setup local tun and nativeRouting
                Log.i(TAG, "Building new local TUN  object");
                try { // catching NPE to circumvent rare Android bug, see https://github.com/pelzvieh/IPv6Droid/issues/44
                    if (tunnelRouted) {
                        Log.i(TAG, "No native IPv6 to use, setting routes to tunnel");
                        vpnFD = builder.establish();
                    } else {
                        Log.i(TAG, "Detected existing IPv6, not setting routes to tunnel");
                        vpnFD = builderNotRouted.establish();
                    }
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

                FileDescriptor localFD;
                try {
                    localFD = extractFD();
                } catch (IOException e) {
                    Log.e(TAG, "TUN device defective before connection up", e);
                    vpnStatus.setCause(e);
                    vpnStatus.setStatus(VpnStatusReport.Status.Disturbed);
                    return;
                }
                RemoteEnd.EndCause endCause = remoteEnd.refreshRemoteEnd(localFD);

                Log.i(TAG, "Refreshing remote VPN end stopped: " + endCause.toString());

                switch (endCause) {
                    case INHIBITS_ROUTING:
                        tunnelRouted = false;
                        break;
                    case REQUIRES_ROUTIING:
                        tunnelRouted = true;
                        break;
                    case EXPIRED:
                        Log.i(TAG, "The tunnel we're using just expired");
                        intendedToRun = false;
                        break;
                }

            } catch (InterruptedException e) {
                userNotificationCallback.notifyUserOfError(R.string.vpnthread_interrupted, e);
                Log.i(TAG, "Tunnel terminated by interrupt", e);
            } catch (ConnectionFailedException e) {
                throw e;
            } catch (Throwable t) {
                userNotificationCallback.notifyUserOfError(R.string.unexpected_runtime_exception, t);
                Log.e(TAG, "Caught unexpected throwable", t);
            } finally {
                if (remoteEnd != null)
                    remoteEnd.stop();
                final ParcelFileDescriptor myVpnFD = vpnFD;
                if (myVpnFD != null) {
                    try {
                        myVpnFD.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Cannot close vpn socket", e);
                    }
                }

                userNotificationCallback.postToast(R.string.vpnservice_tunnel_down, Toast.LENGTH_SHORT);
            }
        }
        Log.i(TAG, "Tunnel thread gracefully shut down");
    }

    /**
     * Asynchronously stops the current tunnel and this object's attempts to rebuild it.
     */
    void stop() {
        intendedToRun = false;

        final RemoteEnd myRemoteEnd = remoteEnd;
        if (myRemoteEnd != null) {
            myRemoteEnd.stop();
            remoteEnd = null;
        }

        final ParcelFileDescriptor myVpnFD = vpnFD;
        if (myVpnFD != null) {
            executor.submit(() -> {
                try {
                    myVpnFD.close();
                } catch (Exception e) {
                    Log.e(TAG, "Cannot close local socket", e);
                }
                Log.i(TAG, "VPN closed");
            });
        }
        vpnFD = null;

        vpnStatus.setStatus(VpnStatusReport.Status.Idle);
    }

    private FileDescriptor extractFD() throws IOException {
        FileDescriptor localFD;
        if (vpnFD != null) {
            vpnFD.checkError();
            localFD = vpnFD.getFileDescriptor();
        } else {
            localFD = null;
        }
        return localFD;
    }


    public Context getApplicationContext() {
        return vpnThread.getApplicationContext();
    }

    public Statistics addStatistics(Statistics stats) {
        return (remoteEnd == null ? stats : remoteEnd.addStatistics(stats))
                .setTunnelRouted(tunnelRouted);
    }

    public VpnThread getVpnThread() {
        return vpnThread;
    }
}
