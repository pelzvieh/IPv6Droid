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

package de.flyingsnail.ipv6droid.android.signinginterface;

import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;

public class SupplierChangedCallback extends ObservableList.OnListChangedCallback<ObservableList<String>> {
    private final ArrayAdapter<String> receiver;

    public SupplierChangedCallback(@NonNull final ArrayAdapter<String> receiver) {
        this.receiver = receiver;
    }

    @Override
    public void onChanged(ObservableList<String> sender) {
        receiver.clear();
        receiver.addAll(sender);
    }

    @Override
    public void onItemRangeChanged(ObservableList<String> sender, int positionStart, int itemCount) {
        onChanged(sender);
    }

    @Override
    public void onItemRangeInserted(ObservableList<String> sender, int positionStart, int itemCount) {
        for (int index = positionStart; index < positionStart+itemCount; index++) {
            receiver.insert(sender.get(index), index);
        }
    }

    @Override
    public void onItemRangeMoved(ObservableList<String> sender, int fromPosition, int toPosition, int itemCount) {
        onChanged(sender);
    }

    @Override
    public void onItemRangeRemoved(ObservableList<String> sender, int positionStart, int itemCount) {
        for (int index = positionStart; index < positionStart+itemCount; index++) {
            receiver.remove(sender.get(index));
        }
    }
}
