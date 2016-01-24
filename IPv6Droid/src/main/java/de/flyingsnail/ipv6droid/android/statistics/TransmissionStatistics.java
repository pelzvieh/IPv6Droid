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
package de.flyingsnail.ipv6droid.android.statistics;

import android.support.annotation.NonNull;

import java.util.Date;

/**
 * TransmissionStatistics keeps rolling averages of package transmission in a specific direction.
 */
public class TransmissionStatistics {
    private static final String TAG = TransmissionStatistics.class.getName();

    private static final double DECAY_TIME = 60000.0; // time of decay to 1/e in milliseconds
    // overall count of copied bytes
    private long byteCount = 0l;
    // overall count of copied packets
    private long packetCount = 0l;

    // the info about the last completed burst
    private Burstinfo lastCompletedBurst = null;
    // the info about the currently running burst
    private Burstinfo currentBurst = new Burstinfo();
    // the average time span of a burst in milliseconds
    private long averageBurstLength = 0l;
    // the average time span between two bursts in milliseconds
    private long averageBurstPause = 0l;
    // the average number of bytes per burst
    private long averageBurstBytes = 0l;
    // the average number of packets per burst
    private long averageBurstPackets = 0l;

    /**
     * Query the average number of bytes per burst.
     * @return a long giving the average number of bytes per burst
     */
    public long getAverageBurstBytes() {
        return averageBurstBytes;
    }

    /**
     * Query the average number of packets per burst.
     * @return a long giving the average number of packets per burst
     */
    public long getAverageBurstPackets() {
        return averageBurstPackets;
    }

    /**
     * Query the average time span between two bursts.
     * @return a long giving the average time span between two bursts in milliseconds.
     */
    public long getAverageBurstPause() {
        return averageBurstPause;
    }

    /**
     * Query the average time span of a burst.
     * @return a long giving the average time span of a burst in milliseconds.
     */
    public long getAverageBurstLength() {
        return averageBurstLength;
    }

    /**
     * Helper method to update statistics information on a received packet. This method should
     * be called immediately after receiving a packet.
     * @param len the length of the packet that was received just now
     */
    public void updateStatistics(long len) {
        byteCount += len;
        packetCount++;

        // update per-burst info
        Date now = new Date();
        if (now.getTime() - 1000l > currentBurst.lastPacketReceived.getTime()) {
            // new burst
            if (lastCompletedBurst != null)
                burstCompleted(currentBurst, lastCompletedBurst);
            lastCompletedBurst = currentBurst;
            currentBurst = new Burstinfo();
            currentBurst.firstPacketReceived = now;
            currentBurst.lastPacketReceived = now;
            currentBurst.byteCount = len;
            currentBurst.packetCount = 1;
        } else {
            currentBurst.lastPacketReceived = now;
            currentBurst.byteCount += len;
            currentBurst.packetCount++;
        }
    }

    /**
     * Get the number of bytes copied by this thread.
     * @return a long giving the number of bytes that was copied by this thread.
     */
    public long getByteCount() {
        return byteCount;
    }

    /**
     * Get the number of packets copied by this thread.
     * @return a long giving the number of packets that was copied by this thread.
     */
    public long getPacketCount() {
        return packetCount;
    }

    /**
     * Helper method to update burst-related statistics values.
     * Should be called by other methods of this class if a burst is considered completed.
     * @param completedBurst the Burstinfo on the completed burst.
     */
    private void burstCompleted (@NonNull Burstinfo completedBurst, @NonNull Burstinfo previousBurst) {
        long burstSpan = completedBurst.lastPacketReceived.getTime() - completedBurst.firstPacketReceived.getTime();
        long burstPause = completedBurst.firstPacketReceived.getTime() - previousBurst.lastPacketReceived.getTime();
        long timeLapse = completedBurst.lastPacketReceived.getTime() - previousBurst.lastPacketReceived.getTime();

        double previousDepletion = Math.exp(-((double)timeLapse)/DECAY_TIME);

        // rolling average of burst length
        averageBurstLength = rollingAverage(previousDepletion, averageBurstLength, burstSpan);
        // rolling average of burst pause
        averageBurstPause = rollingAverage(previousDepletion, averageBurstPause, burstPause);
        // rolling average of bytes per burst
        averageBurstBytes = rollingAverage(previousDepletion, averageBurstBytes, completedBurst.byteCount);
        // rolling average of packets per burst
        averageBurstPackets = rollingAverage(previousDepletion, averageBurstPackets, completedBurst.packetCount);
    }

    /**
     * Helper method to calculate a rolling average
     * @param decay a double giving the time-based depletion factor of the previousAverage
     * @param previousAverage a long giving the previous rolling average value
     * @param newValue a long giving a new addon to the rolling average
     * @return updated rolling average
     */
    private long rollingAverage(double decay, long previousAverage, long newValue) {
        // we implement a rolling average window
        return (previousAverage == 0.0) ?
                newValue :
                (long)(newValue * (1-decay) + decay*previousAverage);
    }


}
