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

import android.util.Log;

import java.io.IOException;
import java.net.Inet6Address;
import java.util.Date;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TunnelBrokenException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;

/**
 * This loop monitors the two copy threads and generates heartbeats in the heartbeat
 * interval. It detects tunnel defects by a number of means and exits by one of its
 * declared exceptions when either it is no longer intended to run or the given transporter doesn't
 * seem to work any more. It just exits if one of the copy threads terminated (see there).
 *
 */
class HeartbeatMonitor implements Monitor {
    private final String TAG = HeartbeatMonitor.class.getName();
    /**
     * Time that we must wait before contacting TIC again. This applies to cached tunnels even!
     */
    private static final int TIC_RECHECK_BLOCKED_MILLISECONDS = 60 * 60 * 1000; // 60 minutes

    private final CopyThread inThread;
    private final CopyThread outThread;
    private final VpnThread vpnThread;
    private final Transporter transporter;

    HeartbeatMonitor(VpnThread vpnThread, CopyThread inThread, CopyThread outThread) {
        this.inThread = inThread;
        this.outThread = outThread;
        this.vpnThread = vpnThread;
        this.transporter = vpnThread.getTransporter();
    }

    /**
     * @throws IOException in case of a (usually temporary) technical problem with the current transporter.
     *   Often, this means that our IP address did change.
     * @throws ConnectionFailedException in case of a more fundamental problem, e.g. if the tunnel
     *   is not enabled any more in TIC, or the given and up-to-date TIC information in the tunnel
     *   repeatedly doesn't lead to a working tunnel.
     * @throws InterruptedException if the thread receives an interrupt in one of its wait
     */
    public void loop() throws InterruptedException, IOException, ConnectionFailedException {
        boolean timeoutSuspected = false;
        long lastPacketDelta = 0L;
        TunnelSpec activeTunnel = transporter.getTunnelSpec();
        long heartbeatInterval = activeTunnel.getHeartbeatInterval() * 1000L;
        if (heartbeatInterval < 300000L && vpnThread.isNetworkMobile()) {
            Log.i(TAG, "Lifting heartbeat interval to 300 secs");
            heartbeatInterval = 300000L;
        }
        while (vpnThread.isIntendedToRun() && (inThread != null && inThread.isAlive()) && (outThread != null && outThread.isAlive())) {
            // wait for the heartbeat interval to finish or until inThread dies.
            // Note: the inThread is reading from the network socket to the POP
            // in case of network changes, this socket breaks immediately, so
            // inThread crashes on external network changes even if no transfer
            // is active.
            inThread.join(heartbeatInterval - lastPacketDelta);
            if (!vpnThread.isIntendedToRun())
                break;
            // re-check cached network information
            if (!vpnThread.isCurrentSocketAdressStillValid()) {
                throw new IOException("IP address changed");
            }
            // determine last package transmission time
            lastPacketDelta = new Date().getTime() - transporter.getLastPacketSentTime().getTime();
            // if no traffic occurred, send a heartbeat package
            if (inThread.isAlive() && outThread.isAlive() &&
                    lastPacketDelta >= heartbeatInterval - 100) {
                try {
                    Log.i(TAG, "Sending heartbeat");
                    transporter.beat();
                    lastPacketDelta = 0L;
                } catch (TunnelBrokenException e) {
                    throw new IOException ("Transporter object claims it is broken", e);
                }

            /* See if we're receiving packets:
               no valid packet after one heartbeat - definitely not working
               no new packets for more than heartbeat interval? Might be device sleep!
               but if not pingable, probably broken.
               In the latter case we give it another heartbeat interval time to recover. */
                if (vpnThread.isDeviceConnected() &&
                        !transporter.isValidPacketReceived() && // if the tunnel worked in a session, don't worry if it pauses - it's 100% network problems
                        VpnThread.checkExpiry(transporter.getLastPacketReceivedTime(),
                                activeTunnel.getHeartbeatInterval()) &&
                        !Inet6Address.getByName(vpnThread.getApplicationContext().getString(R.string.ipv6_test_host)).isReachable(10000)
                ) {
                    if (!timeoutSuspected)
                        timeoutSuspected = true;
                    else if (activeTunnel instanceof TicTunnel && new Date().getTime() - ((TicTunnel)activeTunnel).getCreationDate().getTime()
                            > TIC_RECHECK_BLOCKED_MILLISECONDS) {
                        ayiyaTunnelRefresh();
                        continue;
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
                throw new IOException("Ayiya claims it is broken", deathCause);
            } else if (deathCause instanceof IOException) {
                throw (IOException) deathCause;
            } else {
                throw new IOException(deathCause);
            }
        }
    }

    private void ayiyaTunnelRefresh() throws ConnectionFailedException {
        boolean tunnelChanged;
        try {
            tunnelChanged = vpnThread.readTunnels(); // no need to update activeTunnel - we're going to quit
        } catch (IOException ioe) {
            Log.i(TAG, "TIC and Ayiya both disturbed - assuming network problems", ioe);
            return;
        }
        if (tunnelChanged) {
            // TIC had new data - signal a configuration problem to rebuild tunnel
            throw new ConnectionFailedException("TIC information changed", null);
        } else {
            throw new ConnectionFailedException("This TIC tunnel doesn't receive data", null);
        }
    }
}
