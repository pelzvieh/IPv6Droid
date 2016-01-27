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

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import java.io.Serializable;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
* Created by pelzi on 05.09.13.
*/
public class VpnStatusReport implements Serializable {
    /**
     * The Action name for a status broadcast intent.
     */
    public static final String BC_STATUS = AyiyaVpnService.class.getName() + ".STATUS";

    /**
     * The extended data name for the status in a status broadcast intent.
     */
    public static final String EDATA_STATUS_REPORT = AyiyaVpnService.class.getName() + ".STATUS_REPORT";

    /**
     * An int indicating the progress of tunnel creation.
     */
    private int progressPerCent;

    /**
     * An enum indicating the status of tunnel creation from a defined set of status.
     */
    private Status status;

    /**
     * An int referring to a resource ID that points to an internationalized and human-readable
     * representation of the current activity.
     */
    private int activity;

    /**
     * A TicTunnel representing the tunnel that is created or running.
     */
    private TicTunnel activeTunnel;

    /**
     * A boolean indicating if bytes were already coming in from the PoP to our device (which is
     * the final prove that the tunnel is working.
     */
    private boolean tunnelProvedWorking;

    /**
     * A List of TicTunnel objects representing the tunnel definitions available for the account.
     */
    private List<TicTunnel> ticTunnelList;

    /**
     * A Throwable indicating, if applicable, the cause of a disturbed state (null otherwise).
     */
    private Throwable cause;

    /**
     * An Android Context object. Will be initialized by the constructor. Status-changing,
     * protected methods as clear() and all setters, will throw Exceptions if this is not set.
     */
    private Context context;

    /**
     * Constructor setting defaults.
     */
    public VpnStatusReport(Context context) {
        this.context = context;
        clear();
    }

    /**
     * Reset to state as freshly constructed.
     */
    protected void clear() {
        progressPerCent = 0;
        status = Status.Idle;
        activity = R.string.vpnservice_activity_wait;
        activeTunnel = null;
        tunnelProvedWorking = false;
        ticTunnelList = null;
        cause = null;
        reportStatus();
    }

    protected void setProgressPerCent(int progressPerCent) {
        boolean changed = this.progressPerCent != progressPerCent;
        this.progressPerCent = progressPerCent;
        if (changed)
            reportStatus();
    }

    protected void setStatus(Status status) {
        boolean changed = !this.status.equals(status);
        this.status = status;
        if (changed)
            reportStatus();
    }

    protected void setActivity(int activity) {
        boolean changed = this.activity != activity;
        this.activity = activity;
        if (changed)
            reportStatus();
    }

    protected void setActiveTunnel(TicTunnel activeTunnel) {
        boolean changed = (this.activeTunnel != activeTunnel) && (
                    this.activeTunnel == null || !this.activeTunnel.equals(activeTunnel)
                ) ;
        this.activeTunnel = activeTunnel;
        if (changed)
            reportStatus();
    }

    protected void setTunnelProvedWorking(boolean tunnelProvedWorking) {
        boolean changed = this.tunnelProvedWorking != tunnelProvedWorking;
        this.tunnelProvedWorking = tunnelProvedWorking;
        if (changed)
            reportStatus();
    }

    protected void setCause(@Nullable Throwable cause) {
        boolean changed = (this.cause != cause) && (
                this.cause == null || !this.cause.equals(cause)
        );
        this.cause = cause;
        if (changed)
            reportStatus();
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
    public @NonNull Status getStatus() {
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
    public @Nullable TicTunnel getActiveTunnel() {
        return activeTunnel;
    }

    public void setTicTunnelList(@NonNull List<TicTunnel> ticTunnelList) {
        this.ticTunnelList = ticTunnelList;
    }

    /**
     * Get the list of TicTunnels available to the user.
     * @return a List&lt;TicTunnel&gt; or null if no new list was queried in the running session.
     */
    public @Nullable List<TicTunnel> getTicTunnelList() {
        return ticTunnelList;
    }

    /**
     * Get the cause of the current status. Only set if the status is disrupted in any way.
     * @return the Throwable specifying the cause of disruption, or null if no disruption.
     */
    public @Nullable Throwable getCause() {
        return cause;
    }

    /**
     * Possible status as broadcasted by reportStatus.
     */
    public enum Status {
        Idle, Connecting, Connected, Disturbed
    }

    /**
     * Broadcast the status.
     */
    protected void reportStatus() {
        if (context == null)
            return; // we're outside any Android context
        Intent statusBroadcast = new Intent(BC_STATUS)
                .putExtra(EDATA_STATUS_REPORT, this);
        // Broadcast locally
        LocalBroadcastManager.getInstance(context).sendBroadcast(statusBroadcast);
    }



    @Override
    public String toString() {
        // @todo internationalize
        return "changed to " + status.toString() + ", " +
                (activeTunnel == null ? "no tunnel" : "tunnel " + activeTunnel.getTunnelName()) +
                (cause == null ? "" : ", cause class " + cause.getClass().getName());
    }
}
