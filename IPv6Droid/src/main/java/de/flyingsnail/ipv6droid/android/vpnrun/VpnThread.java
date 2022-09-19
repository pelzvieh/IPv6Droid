/*
 *
 *  * Copyright (c) 2022 Dr. Andreas Feldner.
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

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.TrafficStats;
import android.net.VpnService;
import android.os.Build;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.DTLSTunnelReader;
import de.flyingsnail.ipv6droid.android.IPv6DroidVpnService;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.RoutingConfiguration;
import de.flyingsnail.ipv6droid.android.SubscriptionTunnelReader;
import de.flyingsnail.ipv6droid.android.TunnelReader;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.transport.AuthenticationFailedException;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * This class does the actual work, i.e. logs in to TIC, reads available tunnels and starts
 * a copy thread for each direction.
 */
public class VpnThread extends Thread {
    /**
     * The tag for logging.
     */
    private static final String TAG = VpnThread.class.getName();

    /**
     * The IPv6 address of the Google DNS servers.
     */
    private static final Inet6Address[] PUBLIC_DNS = new Inet6Address[4];
    /**
     * An implementation of the TunnelReader interface
     */
    private final TunnelReader tunnelReader;

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

    public IPv6DroidVpnService getService() {
        return service;
    }

    /**
     * The service that created this thread.
     */
    private final IPv6DroidVpnService service;

    /**
     * The configuration of the intended nativeRouting.
     */
    private final RoutingConfiguration routingConfiguration;

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
     * An int used to tag socket traffic initiated from the parent thread
     */
    private static final int TAG_PARENT_THREAD=0x01;
    /**
     * Our app's Context
     */
    private final Context applicationContext;
    /**
     * The Date when the local side of the tunnel was created
     * (should be equivalent of connection stability to apps using the tunnel).
     */
    private Date startedAt;
    private LocalEnd localEnd;

    /**
     * The constructor setting all required fields.
     * @param service the Service that created this thread
     * @param cachedTunnels the previously working tunnel spec, or null if none
     * @param routingConfiguration the nativeRouting configuration
     * @param sessionName the name of this thread
     */
    public VpnThread(@NonNull IPv6DroidVpnService service,
                     @Nullable Tunnels cachedTunnels,
                     @NonNull RoutingConfiguration routingConfiguration,
                     @NonNull String sessionName) {
        setName(sessionName);
        this.service = service;
        this.vpnStatus = new VpnStatusReport(service);
        try {
            this.routingConfiguration = (RoutingConfiguration)routingConfiguration.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cloning of RoutingConfiguration failed", e);
        }
        this.tunnels = cachedTunnels;
        // extract the application context
        this.applicationContext = service.getApplicationContext();
        TunnelReader tr;
        try {
            tr = new DTLSTunnelReader(service);
            Log.i(TAG, "Using DTLS config");
        } catch (ConnectionFailedException e1) {
            Log.i(TAG, "Falling back to subscription tunnels", e1);
            tr = new SubscriptionTunnelReader(service);
        }

        this.tunnelReader = tr;
        this.startedAt = new Date(0L);
        this.closeTunnel = false;
    }


    @Override
    public void run() {
        if (closeTunnel)
            throw new IllegalStateException("Starting a VpnThread that should close");
        startedAt = new Date();
        try {

            TrafficStats.setThreadStatsTag(TAG_PARENT_THREAD);

            vpnStatus.setProgressPerCent(5);
            vpnStatus.setStatus(VpnStatusReport.Status.Connecting);
            vpnStatus.setActivity(R.string.vpnservice_activity_reconnect);

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
                        switch (tunnels.size()) {
                            case 0:
                                throw new ConnectionFailedException("No suitable tunnels found", null);
                            case 1:
                                tunnels.setActiveTunnel(tunnels.get(0));
                                break;
                            default:
                                throw new ConnectionFailedException("You must select a tunnel from list", null);
                        }
                    }
                } else {
                    Log.i(TAG, "Using cached TicTunnel instead of contacting TIC");
                }
                vpnStatus.setTunnels(tunnels);
                vpnStatus.setProgressPerCent(25);
                vpnStatus.setActivity(R.string.vpnservice_activity_selected_tunnel);

                // build vpn device on local machine
                builder = service.createBuilder();
                TunnelSpec activeTunnel = tunnels.getActiveTunnel();
                //noinspection ConstantConditions
                configureBuilderFromTunnelSpecification(builder, activeTunnel, false);
                builderNotRouted = service.createBuilder();
                configureBuilderFromTunnelSpecification(builderNotRouted, activeTunnel, true);
            }
            LocalEnd myLocalEnd = new LocalEnd(this, builder, builderNotRouted,
                    routingConfiguration.isForceRouting(), vpnStatus, tunnels.getActiveTunnel(),
                    service);
            localEnd = myLocalEnd; // avoid race condition with next statements
            myLocalEnd.refreshTunnelLoop();
            closeTunnel = true;
            myLocalEnd.stop();
            localEnd = null;

            // important status change
            vpnStatus.setProgressPerCent(0);
            vpnStatus.setStatus(VpnStatusReport.Status.Idle);
            vpnStatus.setActivity(R.string.vpnservice_activity_closing);
            vpnStatus.setCause(null);
        } catch (AuthenticationFailedException e) {
            Log.e(TAG, "Authentication step failed", e);
            service.notifyUserOfError(R.string.vpnservice_authentication_failed, e);
            vpnStatus.setCause(e);
        } catch (ConnectionFailedException e) {
            Log.e(TAG, "This configuration will not work on this device", e);
            service.notifyUserOfError(R.string.vpnservice_invalid_configuration, e);
            vpnStatus.setCause(e);
        } catch (IOException e) {
            Log.e(TAG, "IOException caught before reading in tunnel data", e);
            service.notifyUserOfError(R.string.vpnservice_io_during_startup, e);
            vpnStatus.setCause(e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to run tunnel", t);
            // something went wrong in an unexpected way
            service.notifyUserOfError(R.string.vpnservice_unexpected_problem, t);
            vpnStatus.setCause(t);
        } finally {
            closeTunnel = true;
        }
        vpnStatus.clear(); // back at zero
    }


    /**
     * Request the tunnel control loop (running in a different thread) to stop.
     */
    public void requestTunnelClose() {
        if (isIntendedToRun()) {
            Log.i(TAG, "Shutting down");
            closeTunnel = true;
            cleanAll();
            setName(getName() + " (shutting down)");
            this.interrupt();
        }
    }

    /**
     * Request local end to stop which will in return ask all dependend objects to stop.
     */
    private void cleanAll() {
        synchronized (this) {
            if (localEnd != null)
                localEnd.stop();
            localEnd = null;
        }
    }

    /**
     * Read tunnel information via the TIC protocol. Return true if anything changed on the current
     * tunnel.
     * @return true if something changed
     * @throws ConnectionFailedException if some permanent problem exists with TIC and the current config
     * @throws IOException if some (hopefully transient) technical problem came up.
     */
    boolean readTunnels() throws ConnectionFailedException, IOException {
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
            // update tunnel list in status and indirectly MainActivity
            vpnStatus.setTunnels(tunnels);
        }
        return tunnelChanged;
    }

    static boolean checkExpiry(@NonNull Date lastReceived, int heartbeatInterval) {
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
        builder.addAddress(tunnelSpecification.getIpv6Endpoint(), 128);
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
                service.notifyUserOfError(R.string.vpnservice_route_not_added, e);
                service.postToast(R.string.vpnservice_route_not_added, Toast.LENGTH_SHORT);
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
        builder.setConfigureIntent(PendingIntent.getActivity(applicationContext, 0, configureIntent, PendingIntent.FLAG_IMMUTABLE));
        Log.i(TAG, "Builder is configured");
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
                    startedAt,
                    activeTunnel.getIPv4Pop(),
                    (Inet6Address)Inet6Address.getByName(applicationContext.getString(R.string.ipv6_test_host)),
                    activeTunnel.getIpv6Endpoint(),
                    activeTunnel.getMtu());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return localEnd == null ? stats : localEnd.addStatistics(stats);
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

    Context getApplicationContext() {
        return applicationContext;
    }

    public void reportStatus() {
        vpnStatus.reportStatus();
    }
}
