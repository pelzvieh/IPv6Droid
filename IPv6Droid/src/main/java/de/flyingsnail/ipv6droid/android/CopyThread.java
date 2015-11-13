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
    private long byteCount = 0l;
    private long packetCount = 0l;
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
                    byteCount += len;
                    if (packetCount == 0)
                        vpnThread.notifyFirstPacketReceived();
                    packetCount++;
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
}
