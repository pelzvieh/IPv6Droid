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

/**
 * This represents the end-user configuration related to local nativeRouting.
 */
public class RoutingConfiguration implements Cloneable {
    /**
     * a flag if the ipv6 default route should be set to the tunnel
     */
    private boolean setDefaultRoute;

    /**
     * a String that gives a different route specification if default route is not to be set.
     */
    private String specificRoute;

    /**
     * a boolean flag if we should try to reconfigure the device's nativeRouting.
     */
    private boolean tryRoutingWorkaround;

    /**
     * a boolean flag if we should set DNS servers.
     */
    private boolean setNameServers;

    /**
     * a boolean flag if we shoud always route IPv6 traffic through the tunnel
     */
    private boolean forceRouting;

    /**
     * Initialize the RoutingConfiguration object.
     * @param setDefaultRoute a flag if the default route should be set
     * @param specificRoute a String giving a specific route
     * @param tryRoutingWorkaround a flag if we should reprogram the device's nativeRouting
     * @param setNameServers a flag if we should set Google name servers
     * @param forceRouting a flag if we should route IPv6 through the tunnel even in IPv6 capable networks
     */
    public RoutingConfiguration(boolean setDefaultRoute, String specificRoute,
                                boolean tryRoutingWorkaround, boolean setNameServers,
                                boolean forceRouting) {
        this.setDefaultRoute = setDefaultRoute;
        this.specificRoute = specificRoute;
        this.tryRoutingWorkaround = tryRoutingWorkaround;
        this.setNameServers = setNameServers;
        this.forceRouting = forceRouting;
    }

    public boolean isSetDefaultRoute() {
        return setDefaultRoute;
    }

    public void setSetDefaultRoute(boolean setDefaultRoute) {
        this.setDefaultRoute = setDefaultRoute;
    }

    public String getSpecificRoute() {
        return specificRoute;
    }

    public void setSpecificRoute(String specificRoute) {
        this.specificRoute = specificRoute;
    }

    public boolean isTryRoutingWorkaround() {
        return tryRoutingWorkaround;
    }

    public void setTryRoutingWorkaround(boolean tryRoutingWorkaround) {
        this.tryRoutingWorkaround = tryRoutingWorkaround;
    }

    public boolean isSetNameServers() {
        return setNameServers;
    }

    public void setSetNameServers(boolean setNameServers) {
        this.setNameServers = setNameServers;
    }

    public boolean isForceRouting() {
        return forceRouting;
    }

    public void setForceRouting(boolean forceRouting) {
        this.forceRouting = forceRouting;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}