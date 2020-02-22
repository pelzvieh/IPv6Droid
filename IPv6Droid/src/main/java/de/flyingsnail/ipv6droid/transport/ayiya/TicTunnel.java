/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.transport.ayiya;

import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * This represents the tunnel description as delivered by the tic protocol.
 * Created by pelzi on 17.08.13.
 */
public class TicTunnel implements de.flyingsnail.ipv6droid.transport.TunnelSpec {
    /** The tag to identify logger. */
    private static final String TAG = TicTunnel.class.getSimpleName();

    /** the id to use in tic queries */
    private String id;

    /**
     * The id told in the tunnel description. It is different in the examples given (no leading "T")
     * - no idea why we have two ids.
     */
    private String tunnelId;

    /**
     * The type of tunnel. 6in4 is the only one expected here.
     */
    private String type;

    /**
     * IPv6 endpoint of the tunnel
     */
    private Inet6Address ipv6Endpoint;

    /**
     * IPv6 address of the POP.
     */
    private Inet6Address ipv6Pop;

    /**
     * Prefix length of the tunnel endpoint.
     */
    private int prefixLength;

    /**
     * The name of the POP.
     */
    private String popName;

    /**
     * No idea what this is.
     */
    private Inet4Address ipv4Endpoint;

    /** POP address in IPv4 */
    private Inet4Address ipv4Pop;

    /**
     * A String representing the state configured by the user.
     */
    private String userState;

    /**
     * A String representing the state configured by the administrator.
     */
    private String adminState;

    /**
     * A String with the connection password
     */
    private String password;

    /**
     * The heartbeat interval in seconds.
     */
    private int heartbeatInterval;

    @Override
    public String getTunnelName() {
        return tunnelName;
    }

    @Override
    public void setTunnelName(String tunnelName) {
        this.tunnelName = tunnelName;
    }

    /**
     * The user-given name of the tunnel.
     */
    private String tunnelName;

    /** The maximum transmission unit in bytes. */
    private int mtu;

    /** The timestamp of this tunnel's creation */
    private Date creationDate = new Date();

    /** The timestamp of this tunnel's expiration */
    private Date expiryDate = null;

    /**
     * Default constructor. Required for json de-serialization.
     */
    public TicTunnel() {

    }

    /**
     * Constructor. All attributes apart from id created null.
     * @param id a String representing the id to use for querying the tic.
     */
    public TicTunnel(String id) {
        this.id = id;
    }

    @Override
    public Inet4Address getIPv4Pop() {
        return ipv4Pop;
    }

    @Override
    public void setIPv4Pop(Inet4Address ipv4Pop) {
        Log.d(TAG, "setting ipv4 of POP to " + ipv4Pop);
        this.ipv4Pop = ipv4Pop;
    }

    @Override
    public String getTunnelId() {
        return tunnelId;
    }

    @Override
    public void setTunnelId(String tunnelId) {
        this.tunnelId = tunnelId;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public Inet6Address getIpv6Endpoint() {
        return ipv6Endpoint;
    }

    @Override
    public void setIpv6Endpoint(Inet6Address ipv6Endpoint) {
        this.ipv6Endpoint = ipv6Endpoint;
    }

    @Override
    public Inet6Address getIpv6Pop() {
        return ipv6Pop;
    }

    @Override
    public void setIpv6Pop(Inet6Address ipv6Pop) {
        this.ipv6Pop = ipv6Pop;
    }

    @Override
    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    @Override
    public String getPopName() {
        return popName;
    }

    @Override
    public void setPopName(String popName) {
        this.popName = popName;
    }

    public String getUserState() {
        return userState;
    }

    public void setUserState(String userState) {
        this.userState = userState;
    }

    public String getAdminState() {
        return adminState;
    }

    public void setAdminState(String adminState) {
        this.adminState = adminState;
    }

    /**
     * Is this tunnel enabled?
     * @return true if both user and admin enabled this tunnel.
     */
    public boolean isEnabled() {
        Date expiry = getExpiryDate();
        boolean state =
                "enabled".equals(getUserState())
                        && "enabled".equals(getAdminState());
        if (!state)
            return false;
        else if (expiry == null)
            return true;
        else
            return new Date().before(expiry);
    }

    /**
     * Sets the explicit enabled states (user and admin enabled) to enabled. However, will not
     * reset expiry date, so isEnabled might be false after calling setEnabled(true).
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            setUserState("enabled");
            setAdminState("enabled");
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    @Override
    public int getMtu() {
        return mtu;
    }

    @Override
    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public boolean isValid() {
        return (mtu != 0) && (password != null) && (ipv4Pop != null) && (ipv6Pop != null);
    }

    /** required for unmarshalling json */
    public void setValid(boolean valid) {
        if (valid != isValid())
            Log.wtf(TAG, "Impossible to set valid state");
    }

    public Date getCreationDate() {
        return creationDate;
    }

    /** set ID. Required for json unmarshalling */
    public void setId(String id) {
        this.id = id;
    }


    /** Set the creation date. Required for unmarshalling from json. */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }


    @Override
    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiry) {
        expiryDate = expiry;
    }

    public void setExpiryMillis(long expiry) {
        this.expiryDate = new Date(expiry);
    }



    /**
     * This is for Tic, really - it takes the keywords as given by the Tic protocol to describe
     * a tunnel set the respective properties.
     * @param key a String representing the key as of Tic tunnel query.
     * @param value a String representing the value.
     * @return true if we could identify the key and parse the value.
     */
    protected boolean parseKeyValue(String key, String value) {
        // we cannot use Java 7 switch on String, so implementation is no fun at all :-(
        try {
            if ("TunnelId".equalsIgnoreCase(key))
                setTunnelId(value);
            else if ("Type".equalsIgnoreCase(key))
                setType(value);
            else if ("IPv6 Endpoint".equalsIgnoreCase(key))
                setIpv6Endpoint((Inet6Address)Inet6Address.getByName(value));
            else if ("IPv6 PoP".equalsIgnoreCase(key))
                setIpv6Pop((Inet6Address)Inet6Address.getByName(value));
            else if ("IPv6 PrefixLength".equalsIgnoreCase(key))
                setPrefixLength(Integer.parseInt(value));
            else if ("PoP Name".equalsIgnoreCase(key) || "PoP Id".equalsIgnoreCase(key))
                setPopName(value);
            else if ("IPv4 Endpoint".equalsIgnoreCase(key))
                key = key; // we're not interested
            else if ("IPv4 PoP".equalsIgnoreCase(key))
                setIPv4Pop((Inet4Address)Inet4Address.getByName(value));
            else if ("UserState".equalsIgnoreCase(key))
                setUserState(value);
            else if ("AdminState".equalsIgnoreCase(key))
                setAdminState(value);
            else if ("Password".equalsIgnoreCase(key))
                setPassword(value);
            else if ("Heartbeat_Interval".equalsIgnoreCase(key))
                setHeartbeatInterval(Integer.parseInt(value));
            else if ("Tunnel MTU".equalsIgnoreCase(key))
                setMtu(Integer.parseInt(value));
            else if ("Tunnel Name".equalsIgnoreCase(key))
                setTunnelName(value);
            else
                return false;

            return true; // if we're here, some method call succeeded.
        } catch (UnknownHostException e) {
            Log.e(TAG, "unable to resolve string intended to be an address: " + value);
            return false;
        }

    }

    /**
     * Return if the two instances are from business logic the same. They are, if they have the same
     * ID.
     * @param o the object to compare this to
     * @return a boolean indicating equality
     */
    @Override
    public boolean equals(Object o) {
        return (o != null) && (o instanceof TicTunnel) && (((TicTunnel) o).getTunnelId().equals(this.tunnelId));
    }

    /**
     * Return if this TicTunnel has all the same settings as another TicTunnel. In contrast to equals(),
     * this would return false on a different version of the same tunnel.
     * @param o the object to compare this to
     * @return a boolean indicating equality of all attributes
     */
    public boolean equalsDeep(Object o) {
        return equals (o)
                && getHeartbeatInterval() == ((TicTunnel)o).getHeartbeatInterval()
                && getTunnelName().equals(((TicTunnel)o).getTunnelName())
                && getIPv4Pop().equals(((TicTunnel)o).getIPv4Pop())
                && getType().equals(((TicTunnel)o).getType())
                && getIpv6Endpoint().equals(((TicTunnel)o).getIpv6Endpoint())
                && getIpv6Pop().equals(((TicTunnel)o).getIpv6Pop())
                && getPrefixLength() == ((TicTunnel)o).getPrefixLength()
                && getPopName().equals(((TicTunnel)o).getPopName())
                && getUserState().equals(((TicTunnel)o).getUserState())
                && getAdminState().equals(((TicTunnel)o).getAdminState())
                && getPassword().equals(((TicTunnel)o).getPassword())
                && getHeartbeatInterval() == ((TicTunnel)o).getHeartbeatInterval()
                && getMtu() == ((TicTunnel)o).getMtu()
        ;
    }

    @Override
    public int hashCode() {
        return (tunnelId == null) ? 0 : tunnelId.hashCode();
    }

    @Override
    public String toString() {
        return tunnelName + " (" + tunnelId + "), " + type
                + "\n Your endpoint " + ipv6Endpoint;
    }
}
