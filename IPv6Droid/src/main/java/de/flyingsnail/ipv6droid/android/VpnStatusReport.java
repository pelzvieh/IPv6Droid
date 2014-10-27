/*
 * Copyright (c) 2014 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android;

import java.io.Serializable;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
* Created by pelzi on 05.09.13.
*/
class VpnStatusReport implements Serializable {
    private int progressPerCent;
    private Status status;
    private int activity;
    private TicTunnel activeTunnel;
    private boolean tunnelProvedWorking;
    private List<TicTunnel> ticTunnelList;

    /**
     * Constructor setting defaults.
     */
    public VpnStatusReport() {
        progressPerCent = 0;
        status = Status.Idle;
        activity = R.string.vpnservice_activity_wait;
        activeTunnel = null;
        tunnelProvedWorking = false;
    }

    public void setProgressPerCent(int progressPerCent) {
        this.progressPerCent = progressPerCent;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setActivity(int activity) {
        this.activity = activity;
    }

    public void setActiveTunnel(TicTunnel activeTunnel) {
        this.activeTunnel = activeTunnel;
    }

    public void setTunnelProvedWorking(boolean tunnelProvedWorking) {
        this.tunnelProvedWorking = tunnelProvedWorking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VpnStatusReport)) return false;

        VpnStatusReport that = (VpnStatusReport) o;

        if (progressPerCent != that.progressPerCent) return false;
        if (tunnelProvedWorking != that.tunnelProvedWorking) return false;
        if (activeTunnel != null ? !activeTunnel.equals(that.activeTunnel) : that.activeTunnel != null)
            return false;
        if (activity != that.activity) return false;
        if (status != that.status) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = progressPerCent;
        result = 31 * result + status.hashCode();
        result = 31 * result + activity;
        result = 31 * result + (activeTunnel != null ? activeTunnel.hashCode() : 0);
        result = 31 * result + (tunnelProvedWorking ? 1 : 0);
        return result;
    }

    /**
     * Yield if the tunnel received valid packets yet.
     *
     * @return a boolean
     */
    public boolean isTunnelProvedWorking() {
        return tunnelProvedWorking;
    }

    public int getProgressPerCent() {
        return progressPerCent;
    }

    public Status getStatus() {
        return status;
    }

    public int getActivity() {
        return activity;
    }

    public TicTunnel getActiveTunnel() {
        return activeTunnel;
    }

    public void setTicTunnelList(List<TicTunnel> ticTunnelList) {
        this.ticTunnelList = ticTunnelList;
    }

    public List<TicTunnel> getTicTunnelList() {
        return ticTunnelList;
    }

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }
}
