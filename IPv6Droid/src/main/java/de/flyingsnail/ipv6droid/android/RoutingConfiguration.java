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

/**
 * This represents the end-user configuration related to local routing.
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
    private boolean tryRoutingWorkaround;

    public RoutingConfiguration(boolean setDefaultRoute, String specificRoute, boolean tryRoutingWorkaround) {
        this.setDefaultRoute = setDefaultRoute;
        this.specificRoute = specificRoute;
        this.tryRoutingWorkaround = tryRoutingWorkaround;
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

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cloning failed", e);
        }
    }
}