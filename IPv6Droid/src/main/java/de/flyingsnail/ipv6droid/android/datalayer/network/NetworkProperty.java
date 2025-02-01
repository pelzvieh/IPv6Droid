/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.datalayer.network;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;
import java.util.Objects;

public class NetworkProperty implements Cloneable {
    @NonNull
    Network network;
    @Nullable
    LinkProperties properties;
    @Nullable
    NetworkCapabilities capabilities;
    @Nullable
    Boolean blocked;
    @Nullable
    Date invalidAfter;

    public NetworkProperty(final @NonNull Network network) {
        this.network = network;
        blocked = null;
    }

    public @NonNull Network getNetwork() {
        return network;
    }

    public @Nullable LinkProperties getProperties() {
        return properties;
    }

    public @Nullable NetworkCapabilities getCapabilities() {
        return capabilities;
    }

    public boolean isBlocked() {
        return Objects.requireNonNullElse(blocked, Boolean.TRUE);
    }

    @Nullable
    public Date getInvalidAfter() {
        return invalidAfter;
    }

    @NonNull
    @Override
    public Object clone() {
        NetworkProperty clone = new NetworkProperty(network);
        clone.blocked = blocked;
        clone.properties = properties;
        clone.capabilities = capabilities;
        return clone;
    }
}
