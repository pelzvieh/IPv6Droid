/*
 *
 *  * Copyright (c) 2016 Dr. Andreas Feldner.
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

import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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
        ArrayList<String> result = new ArrayList<String>(1);
        result.add(SKU_TUNNEL_SUBSCRIPTION);
        return result;
    }

    public static void setData(Subscription subscription, String googlePurchaseData) throws IllegalArgumentException {
        JSONObject jo;
        try {
            jo = new JSONObject(googlePurchaseData);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unparsable data supplied", e);
        }
        try {
            subscription.setSku(jo.getString("productId"));
        } catch (JSONException e) {
            handleJsonException(e);
        }
        try {
            subscription.setOrderId(jo.getString("orderId"));
        } catch (JSONException e) {
            handleJsonException(e);
        }
        try {
            subscription.setPurchaseTime(jo.getInt("purchaseTime"));
        } catch (JSONException e) {
            handleJsonException(e);
        }
        try {
            subscription.setPurchaseToken(jo.getString("purchaseToken"));
        } catch (JSONException e) {
            handleJsonException(e);
        }
        try {
            subscription.setDeveloperPayload(jo.getString("developerPayload"));
        } catch (JSONException e) {
            handleJsonException(e);
        }

    }

    private static void handleJsonException(JSONException e) {
        if (!e.getMessage().startsWith("No value for"))
            Log.e(TAG, "JSONException with unknown message", e);
    }
}