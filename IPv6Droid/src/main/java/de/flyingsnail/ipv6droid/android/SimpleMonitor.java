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

import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.Transporter;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * This loop monitors the two copy threads. It detects tunnel defects from exceptions and exits
 * by one of its declared exceptions when either it is no longer intended to run or the given
 * transporter doesn't seem to work any more. It just exits if one of the copy threads
 * terminated (see there).
 */
class SimpleMonitor implements Monitor {
    private final String TAG = SimpleMonitor.class.getName();

    private final CopyThread inThread;
    private final CopyThread outThread;
    private final VpnThread vpnThread;
    private final Transporter transporter;

    SimpleMonitor(VpnThread vpnThread, CopyThread inThread, CopyThread outThread) {
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
     * @throws InterruptedException on interrupts during various waits
     */
    public void loop() throws InterruptedException, IOException, ConnectionFailedException {
        TunnelSpec activeTunnel = transporter.getTunnelSpec();
        while (vpnThread.isIntendedToRun() && (inThread != null && inThread.isAlive()) && (outThread != null && outThread.isAlive())) {
            // wait for the heartbeat interval to finish or until inThread dies.
            // Note: the inThread is reading from the network socket to the POP
            // in case of network changes, this socket breaks immediately, so
            // inThread crashes on external network changes even if no transfer
            // is active.
            inThread.join();
            if (!vpnThread.isIntendedToRun())
                break;
            // re-check cached network information
            if (!vpnThread.isCurrentSocketAdressStillValid()) {
                throw new IOException("IP address changed");
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
            if (deathCause instanceof IOException) {
                throw (IOException) deathCause;
            } else {
                throw new IOException(deathCause);
            }
        }
    }
}
