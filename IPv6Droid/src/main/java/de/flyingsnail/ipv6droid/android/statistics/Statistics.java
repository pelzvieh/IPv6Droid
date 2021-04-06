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

package de.flyingsnail.ipv6droid.android.statistics;

import android.net.RouteInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Created by pelzi on 01.05.15.
 * This class is a transporter for statistical information from the VPN management thread to the
 * GUI classes.
 */
public class Statistics {
    private Date startedAt;
    private long bytesTransmitted;
    private int reconnectCount;
    private long bytesReceived;
    private long packetsTransmitted;
    private long packetsReceived;
    private double bytesPerBurstTransmitted;
    private double bytesPerBurstReceived;
    private double packetsPerBurstTransmitted;
    private double packetsPerBurstReceived;
    private double timeSpanPerBurstTransmitted;
    private double timeSpanPerBurstReceived;
    private double timeLapseBetweenBurstsTransmitted;
    private double timeLapseBetweenBurstsReceived;
    private Inet4Address brokerIPv4;
    private Inet4Address myIPv4;
    private Inet6Address brokerIPv6;
    private Inet6Address myIPv6;
    private int mtu;
    private List<RouteInfo> nativeRouting;
    private List<RouteInfo> vpnRouting;
    private List<InetAddress> nativeDnsSetting;
    private List<InetAddress> vpnDnsSetting;
    private Date timestamp;
    private boolean tunnelRouted;

    /** Constructor setting all fields at once. */
    public Statistics(@NonNull TransmissionStatistics outgoingStatistics,
                      @NonNull TransmissionStatistics ingoingStatistics,
                      Date startedAt,
                      int reconnectCount,
                      Inet4Address brokerIPv4, Inet4Address myIPv4,
                      Inet6Address brokerIPv6, Inet6Address myIPv6,
                      int mtu,
                      List<RouteInfo> nativeRouting, List<RouteInfo> vpnRouting,
                      List<InetAddress> nativeDnsSetting,List<InetAddress> vpnDnsSetting,
                      boolean tunnelRouted)
    {
        this.brokerIPv4 = brokerIPv4;
        this.myIPv4 = myIPv4;
        this.brokerIPv6 = brokerIPv6;
        this.myIPv6 = myIPv6;
        this.mtu = mtu;
        this.nativeRouting = nativeRouting;
        this.vpnRouting = vpnRouting;
        this.nativeDnsSetting = nativeDnsSetting;
        this.vpnDnsSetting = vpnDnsSetting;

        this.tunnelRouted = tunnelRouted;
        this.timestamp = new Date();
        this.startedAt = startedAt;
        this.reconnectCount = reconnectCount;

        addOutgoingStatistics(outgoingStatistics);
        addIngoingStatistics(ingoingStatistics);
    }

    /**
     * Constructor setting fields that are known right at the point of tunnel startup.
     * @param startedAt a Date giving the point of time when the tunnel was started
     * @param brokerIPv4 an Inet4Address giving the broker's IP address
     * @param brokerIPv6 an Inet6Address giving the broker's IP address
     * @param myIPv6 an Inet6Address giving my own IP
     * @param mtu an int giving the maximum number of bytes transferable in a single packet
     */
    public Statistics(Date startedAt, Inet4Address brokerIPv4, Inet6Address brokerIPv6, Inet6Address myIPv6, int mtu) {
        this.startedAt = startedAt;
        this.brokerIPv4 = brokerIPv4;
        this.brokerIPv6 = brokerIPv6;
        this.myIPv6 = myIPv6;
        this.mtu = mtu;
    }

    /**
     * Set transmission information from a TransmissionStatistics object.
     * @param outgoingStatistics the TransmissionStatistics object representing transmitted data
     * @return this
     */
    public Statistics addOutgoingStatistics (@Nullable final TransmissionStatistics outgoingStatistics) {
        if (outgoingStatistics != null) {
            this.bytesTransmitted = outgoingStatistics.getByteCount();
            this.packetsTransmitted = outgoingStatistics.getPacketCount();
            this.bytesPerBurstTransmitted = outgoingStatistics.getAverageBurstBytes();
            this.packetsPerBurstTransmitted = outgoingStatistics.getAverageBurstPackets();
            this.timeSpanPerBurstTransmitted = outgoingStatistics.getAverageBurstLength();
            this.timeLapseBetweenBurstsTransmitted = outgoingStatistics.getAverageBurstPause();
        }
        return this;
    }

    /**
     * Set receiption informaiton from a TransmissionStatistics object
     * @param ingoingStatistics the TransmissionStatistics object representing received data
     * @return this
     */
    public Statistics addIngoingStatistics (@Nullable final TransmissionStatistics ingoingStatistics) {
        if (ingoingStatistics != null) {
            this.bytesReceived = ingoingStatistics.getByteCount();
            this.packetsReceived = ingoingStatistics.getPacketCount();
            this.bytesPerBurstReceived = ingoingStatistics.getAverageBurstBytes();
            this.packetsPerBurstReceived = ingoingStatistics.getAverageBurstPackets();
            this.timeSpanPerBurstReceived = ingoingStatistics.getAverageBurstLength();
            this.timeLapseBetweenBurstsReceived = ingoingStatistics.getAverageBurstPause();
        }
        return this;
    }

    public Statistics setStartedAt(Date startedAt) {
        this.startedAt = startedAt;
        return this;
    }

    public Statistics setBytesTransmitted(long bytesTransmitted) {
        this.bytesTransmitted = bytesTransmitted;
        return this;
    }

    public Statistics setReconnectCount(int reconnectCount) {
        this.reconnectCount = reconnectCount;
        return this;
    }

    public Statistics setBytesReceived(long bytesReceived) {
        this.bytesReceived = bytesReceived;
        return this;
    }

    public Statistics setPacketsTransmitted(long packetsTransmitted) {
        this.packetsTransmitted = packetsTransmitted;
        return this;
    }

    public Statistics setPacketsReceived(long packetsReceived) {
        this.packetsReceived = packetsReceived;
        return this;
    }

    public Statistics setBytesPerBurstTransmitted(double bytesPerBurstTransmitted) {
        this.bytesPerBurstTransmitted = bytesPerBurstTransmitted;
        return this;
    }

    public Statistics setBytesPerBurstReceived(double bytesPerBurstReceived) {
        this.bytesPerBurstReceived = bytesPerBurstReceived;
        return this;
    }

    public Statistics setPacketsPerBurstTransmitted(double packetsPerBurstTransmitted) {
        this.packetsPerBurstTransmitted = packetsPerBurstTransmitted;
        return this;
    }

    public Statistics setPacketsPerBurstReceived(double packetsPerBurstReceived) {
        this.packetsPerBurstReceived = packetsPerBurstReceived;
        return this;
    }

    public Statistics setTimeSpanPerBurstTransmitted(double timeSpanPerBurstTransmitted) {
        this.timeSpanPerBurstTransmitted = timeSpanPerBurstTransmitted;
        return this;
    }

    public Statistics setTimeSpanPerBurstReceived(double timeSpanPerBurstReceived) {
        this.timeSpanPerBurstReceived = timeSpanPerBurstReceived;
        return this;
    }

    public Statistics setTimeLapseBetweenBurstsTransmitted(double timeLapseBetweenBurstsTransmitted) {
        this.timeLapseBetweenBurstsTransmitted = timeLapseBetweenBurstsTransmitted;
        return this;
    }

    public Statistics setTimeLapseBetweenBurstsReceived(double timeLapseBetweenBurstsReceived) {
        this.timeLapseBetweenBurstsReceived = timeLapseBetweenBurstsReceived;
        return this;
    }

    public Statistics setBrokerIPv4(Inet4Address brokerIPv4) {
        this.brokerIPv4 = brokerIPv4;
        return this;
    }

    public Statistics setMyIPv4(Inet4Address myIPv4) {
        this.myIPv4 = myIPv4;
        return this;
    }

    public Statistics setBrokerIPv6(Inet6Address brokerIPv6) {
        this.brokerIPv6 = brokerIPv6;
        return this;
    }

    public Statistics setMyIPv6(Inet6Address myIPv6) {
        this.myIPv6 = myIPv6;
        return this;
    }

    public Statistics setMtu(int mtu) {
        this.mtu = mtu;
        return this;
    }

    public Statistics setNativeRouting(List<RouteInfo> nativeRouting) {
        this.nativeRouting = nativeRouting;
        return this;
    }

    public Statistics setVpnRouting(List<RouteInfo> vpnRouting) {
        this.vpnRouting = vpnRouting;
        return this;
    }

    public Statistics setNativeDnsSetting(List<InetAddress> nativeDnsSetting) {
        this.nativeDnsSetting = nativeDnsSetting;
        return this;
    }

    public Statistics setVpnDnsSetting(List<InetAddress> vpnDnsSetting) {
        this.vpnDnsSetting = vpnDnsSetting;
        return this;
    }

    public Statistics setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public Statistics setTunnelRouted(boolean tunnelRouted) {
        this.tunnelRouted = tunnelRouted;
        return this;
    }

    public Date getStartedAt() {
        return startedAt;
    }

    public int getReconnectCount() {
        return reconnectCount;
    }

    public long getBytesTransmitted() {
        return bytesTransmitted;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getPacketsTransmitted() {
        return packetsTransmitted;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public double getBytesPerBurstTransmitted() {
        return bytesPerBurstTransmitted;
    }

    public double getBytesPerBurstReceived() {
        return bytesPerBurstReceived;
    }

    public double getPacketsPerBurstTransmitted() {
        return packetsPerBurstTransmitted;
    }

    public double getPacketsPerBurstReceived() {
        return packetsPerBurstReceived;
    }

    public double getTimeSpanPerBurstTransmitted() {
        return timeSpanPerBurstTransmitted;
    }

    public double getTimeSpanPerBurstReceived() {
        return timeSpanPerBurstReceived;
    }

    public double getTimeLapseBetweenBurstsTransmitted() {
        return timeLapseBetweenBurstsTransmitted;
    }

    public double getTimeLapseBetweenBurstsReceived() {
        return timeLapseBetweenBurstsReceived;
    }

    public Inet4Address getBrokerIPv4() {
        return brokerIPv4;
    }

    public Inet4Address getMyIPv4() {
        return myIPv4;
    }

    public Inet6Address getBrokerIPv6() {
        return brokerIPv6;
    }

    public Inet6Address getMyIPv6() {
        return myIPv6;
    }

    public int getMtu() {
        return mtu;
    }

    public List<RouteInfo> getNativeRouting() {
        return nativeRouting;
    }

    public List<RouteInfo> getVpnRouting() {
        return vpnRouting;
    }

    public List<InetAddress> getNativeDnsSetting() {
        return nativeDnsSetting;
    }

    public List<InetAddress> getVpnDnsSetting() {
        return vpnDnsSetting;
    }

    public Date getTimestamp() { return timestamp; }

    public boolean isTunnelRouted() {
        return tunnelRouted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Statistics)) return false;

        Statistics that = (Statistics) o;

        if (bytesTransmitted != that.bytesTransmitted) return false;
        if (bytesReceived != that.bytesReceived) return false;
        if (packetsTransmitted != that.packetsTransmitted) return false;
        if (packetsReceived != that.packetsReceived) return false;
        if (mtu != that.mtu) return false;
        if (tunnelRouted != that.tunnelRouted) return false;
        if (!brokerIPv4.equals(that.brokerIPv4)) return false;
        if (!Objects.equals(myIPv4, that.myIPv4)) return false;
        if (!brokerIPv6.equals(that.brokerIPv6)) return false;
        if (!myIPv6.equals(that.myIPv6)) return false;
        if (!Objects.equals(nativeRouting, that.nativeRouting))
            return false;
        if (!Objects.equals(vpnRouting, that.vpnRouting))
            return false;
        return Objects.equals(vpnDnsSetting, that.vpnDnsSetting)
                && Objects.equals(nativeDnsSetting, that.nativeDnsSetting);

    }

    @Override
    public int hashCode() {
        int result = (int) (bytesTransmitted ^ (bytesTransmitted >>> 32));
        result = 31 * result + (int) (bytesReceived ^ (bytesReceived >>> 32));
        result = 31 * result + (int) (packetsTransmitted ^ (packetsTransmitted >>> 32));
        result = 31 * result + (int) (packetsReceived ^ (packetsReceived >>> 32));
        result = 31 * result + brokerIPv4.hashCode();
        result = 31 * result + myIPv4.hashCode();
        result = 31 * result + brokerIPv6.hashCode();
        result = 31 * result + myIPv6.hashCode();
        result = 31 * result + mtu;
        result = 31 * result + (nativeRouting != null ? nativeRouting.hashCode() : 0);
        result = 31 * result + (vpnRouting != null ? vpnRouting.hashCode() : 0);
        result = 31 * result + (nativeDnsSetting != null ? nativeDnsSetting.hashCode() : 0);
        result = 31 * result + (vpnDnsSetting != null ? vpnDnsSetting.hashCode() : 0);
        result = 31 * result + (tunnelRouted ? 1 : 0);
        return result;
    }
}
