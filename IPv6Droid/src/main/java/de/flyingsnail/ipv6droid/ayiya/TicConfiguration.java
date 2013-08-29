/*
 * Copyright (c) 2013 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.ayiya;

/**
 * This represents the end user configuration required for the TIC protocol. It is just username,
 * password and host name of the tic host. TIC itself will deliver the more complex tunnel
 * configuration data (@see TicTunnel).
 *
 * Created by pelzi on 19.08.13.
 */
public class TicConfiguration implements Cloneable {
    /** the username to login to TIC */
    private String username;
    /** the password to login to TIC */
    private String password;
    /** the TIC server */
    private String server;

    /** Constructor initializing all fields */
    public TicConfiguration(String username,
                            String password,
                            String server ) {
        this.username = username;
        this.password = password;
        this.server = server;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cloning failed", e);
        }
    }
}
