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

package de.flyingsnail.ipv6droid.android;

import java.io.Serializable;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
* Created by pelzi on 05.09.13.
*/
public class VpnStatusReport implements Serializable {
    private int progressPerCent;
    private Status status;
    private int activity;
    private TicTunnel activeTunnel;
    private boolean tunnelProvedWorking;
    private List<TicTunnel> ticTunnelList;
    private Throwable cause;

    /**
     * Constructor setting defaults.
     */
    public VpnStatusReport() {
        clear();
    }

    /**
     * Reset to state as freshly constructed.
     */
    public void clear() {
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

    public void setCause(Throwable cause) {
        this.cause = cause;
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
        return activity == that.activity && status == that.status;

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

    /**
     * Return the progress of creating the tunnel in percent (between 0 and 100)
     * @return the progress
     */
    public int getProgressPerCent() {
        return progressPerCent;
    }

    /**
     * Get the classified status of the tunnel.
     * @return the Status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Return a string ressource ID expanding to a human-readable description of the current activity.
     * @return an int representing the ressource ID.
     */
    public int getActivity() {
        return activity;
    }

    /**
     * Get the currently active (or build) tunnel
     * @return the TicTunnel
     */
    public TicTunnel getActiveTunnel() {
        return activeTunnel;
    }

    public void setTicTunnelList(List<TicTunnel> ticTunnelList) {
        this.ticTunnelList = ticTunnelList;
    }

    /**
     * Get the list of TicTunnels available to the user.
     * @return a List<TicTunnel></TicTunnel>
     */
    public List<TicTunnel> getTicTunnelList() {
        return ticTunnelList;
    }

    /**
     * Get the cause of the current status. Only set if the status is disrupted in any way.
     * @return the Throwable specifying the cause of disruption, or null if no disruption.
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }

    @Override
    public String toString() {
        // @todo internationalize
        return "changed to " + status.toString() + ", " +
                (activeTunnel == null ? "no tunnel" : "tunnel " + activeTunnel.getTunnelName()) +
                (cause == null ? "" : ", cause class " + cause.getClass().getName());
    }
}
