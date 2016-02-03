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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
 * A ArrayList&lt;TicTunnel&gt; extended by information on a potentially selected tunnel.
 * Created by pelzi on 28.01.16.
 */
public class Tunnels extends ArrayList<TicTunnel> implements Cloneable {
    // Version number for serialized state
    static final long serialVersionUID =-9178679015599058965L;
    // the TicTunnel that is currently active/selected for activation
    private @Nullable TicTunnel activeTunnel;

    /**
     * A Serializable for the whole purpose to have a Serializable for which Android didn't implement
     * special treatment - only this can be sent over Intents :-(
     */
    private class AndroidParcelShield implements Serializable {
        public Tunnels tunnels;
        public AndroidParcelShield(Tunnels t) {
            tunnels = t;
        }
    }

    /**
     * Constructs a new {@code Tunnels} instance with zero initial capacity.
     */
    public Tunnels() {
        super();
        activeTunnel = null;
    }

    public Tunnels(Serializable serializable) {
        super();
        activeTunnel = null;
        if (serializable instanceof AndroidParcelShield)
            setAll(((AndroidParcelShield)serializable).tunnels);
    }

    /**
     * Replace the actual list of tunnels, trying to keep activeTunnel if it is still present.
     * @param ticTunnelList a List&lt;TicTunnel&gt; giving the new list of tunnels to set.
     * @return a boolean, true if the previous activeTunnel is still set, i.e. was null or is
     * contained in the new tunnels list
     */
    public boolean replaceTunnelList(@NonNull List<TicTunnel> ticTunnelList) {
        TicTunnel tunnel = getActiveTunnel();
        clear();
        addAll(ticTunnelList);
        try {
            if (tunnel != null)
                setActiveTunnel(tunnel);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Removes all elements from this {@code ArrayList}, leaving it empty.
     *
     * @see #isEmpty
     * @see #size
     */
    @Override
    public void clear() {
        super.clear();
        activeTunnel = null;
    }

    /**
     * Removes the object at the specified location from this list. Nulls activeTunnel if this is
     * the removed tunnel.
     *
     * @param index the index of the object to remove.
     * @return the removed object.
     * @throws IndexOutOfBoundsException when {@code location < 0 || location >= size()}
     */
    @Override
    public TicTunnel remove(int index) {
        TicTunnel removed = super.remove(index);
        if (removed.equals(activeTunnel))
            activeTunnel = null;
        return removed;
    }

    /**
     * Removes the object from this list. Nulls activeTunnel if this is the removed tunnel.
     * @param object the Object that should be removed
     * @return a boolean, true if the object was a TicTunnel contained in the list, and removed.
     */
    @Override
    public boolean remove(Object object) {
        boolean removed = super.remove(object);
        if (removed && object.equals(activeTunnel))
            activeTunnel = null;
        return removed;
    }

    /**
     * Constructs a new instance of {@code Tunnels} with the specified
     * initial capacity.
     *
     * @param capacity the initial capacity of this {@code Tunnels}.
     */
    public Tunnels(int capacity) {
        super(capacity);
        activeTunnel = null;
    }

    /**
     * Constructs a new instance of {@code Tunnels} containing the elements of
     * the specified collection, and setting the active Tunnel.
     *
     * @param collection the collection of elements to add.
     * @param activeTunnel the TicTunnel to set as active. Must be contained in collection, may be null.
     */
    public Tunnels(Collection<? extends TicTunnel> collection, @Nullable TicTunnel activeTunnel) throws IllegalArgumentException {
        super(collection);
        setActiveTunnel(activeTunnel);
    }

    /**
     *
     * @return The active/selected for activation TicTunnel or null if not selected
     */
    public @Nullable TicTunnel getActiveTunnel() {
        return activeTunnel;
    }

    public boolean isTunnelActive() {
        return activeTunnel != null;
    }

    public void setActiveTunnel(@Nullable TicTunnel activeTunnel) throws IllegalArgumentException {
        if (activeTunnel != null && !contains(activeTunnel))
            throw new IllegalArgumentException("Attempt to set an active tunnel that is not contained in the tunnel list");
        this.activeTunnel = activeTunnel;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) &&
                (activeTunnel == null ?
                        ((Tunnels)o).activeTunnel == null :
                        activeTunnel.equals(((Tunnels)o).activeTunnel)
                );
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (activeTunnel != null ? activeTunnel.hashCode() : 0);
        return result;
    }

    /**
     * Re-sets this tunnel list and the selected tunnel to the data of the supplied Tunnels object.
     * This is used to restore settings from a persistence mechanism.
     * @param newVal the blueprint that this object should follow
     */
    public void setAll(Tunnels newVal) {
        clear();
        addAll(newVal);
        setActiveTunnel(newVal.getActiveTunnel());
    }


    public Serializable getAndroidSerializable () {
        return new AndroidParcelShield(this);
    }
}
