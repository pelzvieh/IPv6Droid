/*
 *
 *  * Copyright (c) 2022 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.googlesubscription;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.Purchase;

import java.util.List;

import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public interface CertificationResultListener {
    /**
     * Callback to inform about an ansychronous attempt to get tunnels from a purchase.
     * @param purchase the Purchase for which the request was initiated. Guaranteed to be the
     *                 unaltered object as passed.
     * @param tunnels the Tunnels created from the Purchase. May be empty.
     * @param resulttype the ResultType classifying the outcome of the attempt, @see {ResultType}
     * @param e an Exception giving details of failure and rejections.
     */
    void onCertificationRequestResult(
            @NonNull Purchase purchase,
            @NonNull List<TunnelSpec> tunnels,
            CertificationResultListener.ResultType resulttype,
            @Nullable Exception e);

    enum ResultType {
        OK,
        TECHNICAL_FAILURE,
        PURCHASE_REJECTED
    }
}
