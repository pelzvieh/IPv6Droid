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
     * a boolean flag if we should set DNS servers.
     */
    private boolean setNameServers;

    /**
     * a boolean flag if we shoud always route IPv6 traffic through the tunnel
     */
    private boolean forceRouting;

    /**
     * a boolean flag if we should use a SOCKS5 proxy for IPv4 UDP traffic
     */
    private final boolean useSocksProxy;
    /**
     * a String giving the SOCKS5 proxie's host name
     */
    private final String socksHost;

    /**
     * an int giving the SOCKS5 proxie's port number
     */
    private final int socksPort;

    /**
     * a String giving the SOCKS5 username
     */
    private final String socksUser;

    /**
     * a String giving the SOCKS5 password
     */
    private final String socksPassword;

    /**
     * Initialize the RoutingConfiguration object.
     * @param setDefaultRoute a flag if the default route should be set
     * @param specificRoute a String giving a specific route
     * @param setNameServers a flag if we should set Google name servers
     * @param forceRouting a flag if we should route IPv6 through the tunnel even in IPv6 capable networks
     * @param useSocksProxy a flag if we should use a SOCKS5 proxy
     * @param socksHost the host name of the SOCKS5 proxy to use if useSocksProxy is set
     * @param socksPort the port number of the SOCKS5 proxy to use if useSocksProxy is set
     * @param socksUser the user name for SOCKS5 User method authentication, or null/empty
     */
    public RoutingConfiguration(boolean setDefaultRoute,
                                String specificRoute,
                                boolean setNameServers,
                                boolean forceRouting,
                                boolean useSocksProxy,
                                String socksHost,
                                int socksPort,
                                String socksUser,
                                String socksPassword) {
        this.setDefaultRoute = setDefaultRoute;
        this.specificRoute = specificRoute;
        this.setNameServers = setNameServers;
        this.forceRouting = forceRouting;
        this.useSocksProxy = useSocksProxy;
        this.socksHost = socksHost;
        this.socksPort = socksPort;
        this.socksUser = (socksUser == null || socksPassword == null || !socksPassword.isEmpty()) ?
                socksUser : null;
        this.socksPassword = (socksPassword == null || !socksPassword.isEmpty()) ?
                socksPassword : null;
    }

    public String getSocksUser() {
        return socksUser;
    }

    public String getSocksPassword() {
        return socksPassword;
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

    public boolean isUseSocksProxy() {
        return useSocksProxy;
    }

    public String getSocksHost() {
        return socksHost;
    }

    public int getSocksPort() {
        return socksPort;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}