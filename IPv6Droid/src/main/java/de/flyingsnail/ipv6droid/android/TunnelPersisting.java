/*
 * Copyright (c) 2016 Dr. Andreas Feldner.
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

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * An object implementing TunnelPersisting allows to read and update a tunnels list
 * and the selected/updated tunnel.
 * Created by pelzi on 01.02.16.
 */
public interface TunnelPersisting  {
    /**
     * Read the last persisted state of tunnels from storage.
     * @return a @ref Tunnels object
     * @throws IOException in case of data access problems.
     */
    @NonNull Tunnels readTunnels() throws IOException;

    /**
     * Write the supplied Tunnels to persistent storage.
     * @param tunnels the Tunnels object representing the list of available tunnels and the
     *                selected tunnel.
     * @throws IOException in case of problems writing data.
     */
    void writeTunnels(@NonNull Tunnels tunnels) throws IOException;
}
