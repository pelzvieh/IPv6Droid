package de.flyingsnail.ipv6droid.android;

import android.net.TrafficStats;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import de.flyingsnail.ipv6droid.R;

/**
 * A helper class that basically allows to run a thread that copies packets from an input
 * to an output stream.
 */
class CopyThread extends Thread {
    private static final String TAG = CopyThread.class.getName();
    // overall count of copied bytes
    private long byteCount = 0l;
    // overall count of copied packets
    private long packetCount = 0l;

    // data about a packet burst
    private class Burstinfo {
        // count of bytes in a specific burst
        public long byteCount = 0l;
        // count of packets in a specific burst
        public long packetCount = 0l;
        // the start timestamp of the burst
        public Date firstPacketReceived = new Date();
        // the timestamp of the last packet received in that burst
        public Date lastPacketReceived = firstPacketReceived;
    }
    // the info about the last completed burst
    private Burstinfo lastCompletedBurst = null;
    // the info about the currently running burst
    private Burstinfo currentBurst = new Burstinfo();
    // the average time span of a burst in milliseconds
    private long averageBurstLength = 0l;
    // the average time span between two bursts in milliseconds
    private long averageBurstPause = 0l;
    // the average number of bytes per burst
    private long averageBurstBytes;
    // the average number of packets per burst
    private long averageBurstPackets;

    // the stream to read from
    private InputStream in;
    // the stream to write to
    private OutputStream out;
    // a flag that indicates that this thread should stop itself
    private boolean stopCopy;
    private final int networkTag;
    // instance of the service controlling this thread
    private AyiyaVpnService ayiyaVpnService;
    // instance of the Thread controlling the copy threads.
    private VpnThread vpnThread;
    // time when last packet received
    private Date lastPacketReceived;

    // The time to wait for additional packets until sending them out
    private final long packetBundlingPeriod;
    // The maximum size of packet buffer
    private final static int MAX_PACKET_BUFFER_LENGTH = 10;
    // the actual size of packet buffer
    private final int packetBufferLength;
    // the outgoing packet queue
    private final Queue<byte[]> packetQueue;
    // the pool of unused packet buffers
    private final Queue<byte[]> bufferPool;

    /**
     * Instantiate and run(!) a thread that copies from in to out until interrupted.
     * @param in The stream to copy from.
     * @param out The stream to copy to.
     * @param ayiyaVpnService the service instance of the active AyiyaVpnService that controls this thread.
     * @param vpnThread the VpnThread controlling the copy threads.
     * @param threadName a String giving the name of the Thread (as shown in some logs and debuggers)
     * @param networkTag an int representing the tag for network statistics of this thread
     * @param packetBundlingPeriod a long that gives the time in millisecs that the copy thread will try
     *                             to delay until a packet is sent out, waiting for additional packets.
     * @return The thread that does so until interrupted.
     */
    public CopyThread(final InputStream in,
                      final OutputStream out,
                      AyiyaVpnService ayiyaVpnService,
                      VpnThread vpnThread,
                      String threadName,
                      int networkTag,
                      long packetBundlingPeriod) {
        super();
        this.in = in;
        this.out = out;
        this.networkTag = networkTag;
        this.setName(threadName);
        this.ayiyaVpnService = ayiyaVpnService;
        this.vpnThread = vpnThread;
        this.packetBundlingPeriod = packetBundlingPeriod;
        this.packetBufferLength = (packetBundlingPeriod > 0) ? MAX_PACKET_BUFFER_LENGTH : 0;
        // allocate packet buffer
        packetQueue = new ArrayBlockingQueue<byte[]>(packetBufferLength == 0 ? 1 : packetBufferLength);
        bufferPool = new ArrayBlockingQueue<byte[]>(packetBufferLength+1);
        for (int i = 0; i <= packetBufferLength; i++)
            bufferPool.add(new byte[32767]);
    }

    /**
     * Signal that this thread should end now.
     */
    public void stopCopy() {
        Log.i(TAG, "Stopping copy thread " + getName());
        stopCopy = true;
        if (this.isAlive())
            this.interrupt();
        cleanAll();
        setName(getName() + " (shutting down)");
    }

    /**
     * Close all sockets, null all fields
     */
    synchronized private void cleanAll() {
        if (in != null) {
            try {
                in.close();
                in = null;
            } catch (IOException e) {
                Log.e(TAG, "Copy thread could not gracefully close input", e);
            }
        }
        if (out != null) {
            try {
                out.flush();
            } catch (IOException e) {
                Log.e(TAG, "Copy thread could not gracefully flush output", e);
            }
            try {
                out.close();
                out = null;
            } catch (IOException e) {
                Log.e(TAG, "Copy thread could not gracefully close output", e);
            }
        }
        bufferPool.clear();
        packetQueue.clear();
    }

    @Override
    public void run() {
        try {
            TrafficStats.setThreadStatsTag(networkTag);
            Log.i(TAG, "Copy thread started");

            int recvZero = 0;
            long lastWrite = new Date().getTime();
            stopCopy = false;

            // @TODO there *must* be a suitable utility class for that...?
            while (!stopCopy) {
                byte[]packet = bufferPool.remove();
                int len = in.read (packet);
                if (stopCopy || isInterrupted())
                    break;
                if (len > 0) {
                    out.write(packet, 0, len);
                    // statistics
                    updateStatistics (len);
                    recvZero = 0;
                } else {
                    recvZero++;
                    if (recvZero == 10000) {
                        ayiyaVpnService.notifyUserOfError(R.string.copythreadexception, new IllegalStateException(
                                Thread.currentThread().getName() + ": received 0 byte packages"
                        ));
                    }
                    Thread.sleep(100 + ((recvZero < 10000) ? recvZero : 10000)); // wait minimum 0.1, maximum 10 seconds
                }
                bufferPool.add(packet);
            }
            Log.i(TAG, "Copy thread " + getName() + " ordinarily stopped");
        } catch (Exception e) {
            if (e instanceof InterruptedException || e instanceof SocketException || e instanceof IOException) {
                Log.i(TAG, "Copy thread " + getName() + " ran into expected Exception, will end gracefully");
            } else {
                Log.e(TAG, "Copy thread " + getName() + " got exception", e);
                ayiyaVpnService.notifyUserOfError(R.string.copythreadexception, e);
            }
        } finally {
            cleanAll();
        }
    }

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
    private void updateStatistics(long len) {
        byteCount += len;
        if (packetCount == 0)
            vpnThread.notifyFirstPacketReceived();
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
    private void burstCompleted (Burstinfo completedBurst, Burstinfo previousBurst) {
        long burstSpan = completedBurst.lastPacketReceived.getTime() - completedBurst.firstPacketReceived.getTime();
        long burstPause = completedBurst.firstPacketReceived.getTime() - previousBurst.lastPacketReceived.getTime();

        // rolling average of burst length
        averageBurstLength = rollingAverage(averageBurstLength, burstSpan);
        // rolling average of burst pause
        averageBurstPause = rollingAverage(averageBurstPause, burstPause);
        // rolling average of bytes per burst
        averageBurstBytes = rollingAverage(averageBurstBytes, completedBurst.byteCount);
        // rolling average of packets per burst
        averageBurstPackets = rollingAverage(averageBurstPackets, completedBurst.packetCount);
    }

    /**
     * Helper method to calculate a rolling average
     * @param previousAverage
     * @param newValue
     * @return updated rolling average
     */
    private long rollingAverage(long previousAverage, long newValue) {
        // we implement a rolling average window
        return (previousAverage == 0.0) ?
                        newValue :
                        (long)(newValue * 0.01 + 0.99*previousAverage);
    }
}
