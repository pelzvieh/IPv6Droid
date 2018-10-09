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

import android.util.LruCache;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * TransmissionStatistics keeps rolling averages of package transmission in a specific direction.
 */
public class TransmissionStatistics {
    private static final String TAG = TransmissionStatistics.class.getName();

    private static final double DECAY_TIME = 60000.0; // time of decay to 1/e in milliseconds
    public static final long BURST_TIMEOUT = 1000l;
    // overall count of copied bytes
    private long byteCount = 0l;
    // overall count of copied packets
    private long packetCount = 0l;

    // the info about the last completed burst
    private @Nullable Burstinfo lastCompletedBurst = null;
    // the info about the currently running burst
    private @Nullable Burstinfo currentBurst = null;
    // the average time span of a burst in seconds
    private double averageBurstLength = 0.0;
    // the average time span between two bursts in seconds
    private double averageBurstPause = 0.0;
    // the average number of bytes per burst
    private double averageBurstBytes = 0.0;
    // the average number of packets per burst
    private double averageBurstPackets = 0.0;
    // helper cache for depletion values on time spans
    LruCache<Long, Double> depletionCache = new LruCache<Long, Double>(10) {
        /**
         * Calculate a new depletion factor from a given time lapse. This will be called by LruCache
         * on cache misses.
         * @param key a Long giving the time lapse in milliseconds
         * @return a Double giving the depletion factor corresponding to the time lapse key.
         */
        @Override
        protected Double create(Long key) {
            return Math.exp(-((double) key) / DECAY_TIME);
        }
    };

    /**
     * Query the average number of bytes per burst.
     * @return a double giving the average number of bytes per burst
     */
    public double getAverageBurstBytes() {
        checkBurstCompletion();
        return averageBurstBytes;
    }

    /**
     * Query the average number of packets per burst.
     * @return a double giving the average number of packets per burst
     */
    public double getAverageBurstPackets() {
        checkBurstCompletion();
        return averageBurstPackets;
    }

    /**
     * Query the average time span between two bursts. The value returned accounts for the
     * time elapsed since the last burst and the call done - i.e. increases with time when no
     * packet bursts finish.
     * @return a double giving the average time span between two bursts in seconds.
     */
    public double getAverageBurstPause() {
        checkBurstCompletion();
        // avoid race conditions
        Burstinfo myCurrentBurst = currentBurst;
        Burstinfo myLastCompletedBurst = lastCompletedBurst;
        if (myCurrentBurst != null || myLastCompletedBurst == null)
            return averageBurstPause; // we're currently inside a burst, so the average lapse doesn't change
        else {
            // return the value as if a burst was just started and ended right now
            long lapse = dateDifference(new Date(), myLastCompletedBurst.lastPacketReceived);
            return rollingAverage(lapse, averageBurstPause, lapse/1000);
        }
    }

    /**
     * Query the average time span of a burst.
     * @return a double giving the average time span of a burst in seconds.
     */
    public double getAverageBurstLength() {
        checkBurstCompletion();
        if (currentBurst == null || lastCompletedBurst == null)
            return averageBurstLength; // we're outside a burst, so the average length doesn't change
        else {
            // return the value as if a burst was just started and ended right now
            return rollingAverage(
                    dateDifference(currentBurst.firstPacketReceived, lastCompletedBurst.firstPacketReceived),
                    averageBurstLength,
                    dateDifference(new Date(), currentBurst.firstPacketReceived)/1000);
        }
    }

    /**
     * helper method that checks if the current burst is closed (by timeout), and in this case takes
     * care to update the relevant statistics values by calling burstComplete, and rotating currentBurst
     * to lastCompletedBurst. In timeout case, this method leaves currentBurst as null.
     * @return a boolean as true if a timeout was detected, false if the currentBurst is yet to be used.
     */
    private synchronized boolean checkBurstCompletion() {
        // update per-burst info
        if (currentBurst != null && dateDifference(new Date(), currentBurst.lastPacketReceived) > BURST_TIMEOUT) {
            // new burst
            if (lastCompletedBurst != null)
                burstCompleted(currentBurst, lastCompletedBurst);
            lastCompletedBurst = currentBurst;
            currentBurst = null;
            return true;
        }
        return false;
    }

    /**
     * Helper method to update statistics information on a received packet. This method should
     * be called immediately after receiving a packet.
     * @param len the length of the packet that was received just now
     */
    public synchronized void updateStatistics(long len) {
        byteCount += len;
        packetCount++;

        // update per-burst info
        if (currentBurst == null || checkBurstCompletion()) {
            // new burst
            currentBurst = new Burstinfo();
            currentBurst.byteCount = len;
            currentBurst.packetCount = 1;
        } else {
            currentBurst.lastPacketReceived = new Date();
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
     *
     * @param minuend a Date giving the time from which to substract
     * @param subtrahend a Date giving the time to substract
     * @return a long indicating the time in milliseconds from subtrahend to minuend
     */
    private long dateDifference (Date minuend, Date subtrahend) {
        return minuend.getTime() - subtrahend.getTime();
    }
    /**
     * Helper method to update burst-related statistics values.
     * Should be called by other methods of this class if a burst is considered completed.
     * @param completedBurst the Burstinfo on the completed burst.
     */
    private void burstCompleted (@NonNull Burstinfo completedBurst, @NonNull Burstinfo previousBurst) {
        double burstSpan = (double)
                dateDifference(completedBurst.lastPacketReceived, completedBurst.firstPacketReceived)
            /1000.0;
        double burstPause = (double)
                dateDifference(completedBurst.firstPacketReceived, previousBurst.lastPacketReceived)
            /1000.0;
        long timeLapse = dateDifference(completedBurst.lastPacketReceived, previousBurst.lastPacketReceived);

        // rolling average of burst length
        averageBurstLength = rollingAverage(timeLapse, averageBurstLength, burstSpan);
        // rolling average of burst pause
        averageBurstPause = rollingAverage(timeLapse, averageBurstPause, burstPause);
        // rolling average of bytes per burst
        averageBurstBytes = rollingAverage(timeLapse, averageBurstBytes, completedBurst.byteCount);
        // rolling average of packets per burst
        averageBurstPackets = rollingAverage(timeLapse, averageBurstPackets, completedBurst.packetCount);
    }

    /**
     * Helper method to calculate a rolling average
     * @param timeLapse a long giving the time lapse since the previous value
     * @param previousAverage a long giving the previous rolling average value
     * @param newValue a long giving a new addon to the rolling average
     * @return updated rolling average
     */
    private double rollingAverage(long timeLapse, double previousAverage, double newValue) {
        // we implement a rolling average window
        double decay = depletionCache.get(timeLapse);
        return (previousAverage == 0.0) ?
                newValue :
                (newValue * (1.0-decay) + decay*previousAverage);
    }


}
