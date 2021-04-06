/*
 *
 *  * Copyright (c) 2021 Dr. Andreas Feldner.
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

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionBuilder {
    static final String SKU_TUNNEL_SUBSCRIPTION = "de.flyingsnail.ipv6.tunnelsub";
    static final String TAG = SubscriptionBuilder.class.getSimpleName();

    public SubscriptionBuilder() {

    }

    @Nullable
    public static Subscription create(String sku) {
        if (SKU_TUNNEL_SUBSCRIPTION.equals(sku))
            return new Subscription();
        else
            return null;
    }

    public static List<String> getSupportedSku() {
        ArrayList<String> result = new ArrayList<>(1);
        result.add(SKU_TUNNEL_SUBSCRIPTION);
        return result;
    }

}