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

/**
 * Base class for all subscription types.
 * That said, we currently have only one subscription type.
 * Created by pelzi on 15.06.16.
 */
public class Subscription {
    private static final String SKU_TUNNEL_SUBSCRIPTION = "android.test.purchased";//"TUNNELSUB";
    private static final String TAG = Subscription.class.getSimpleName();

    private String signature;
    private String sku;
    private String orderId;
    private int purchaseTime;
    private String purchaseToken;
    private String developerPayload;

    public String getSku() {
        return sku;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getPurchaseTime() {
        return purchaseTime;
    }

    public String getPurchaseToken() {
        return purchaseToken;
    }

    public String getDeveloperPayload() {
        return developerPayload;
    }

    @Nullable
    public static Subscription create(String sku) {
        if (SKU_TUNNEL_SUBSCRIPTION.equals(sku))
            return new Subscription();
        else
            return null;
    }

    public void setData(String data) {
        try {
            JSONObject jo = new JSONObject(data);
            sku = jo.getString("productId");
            orderId = jo.getString("orderId");
            purchaseTime = jo.getInt("purchaseTime");
            purchaseToken = jo.getString("purchaseToken");
            developerPayload = jo.getString("developerPayload");
            // TODO call IPv6Server to verify purchase signature
        }
        catch (JSONException e) {
            Log.wtf(TAG, "Cannot parse JSON response from Google on subscription status", e);
        }

    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSignature() {
        return signature;
    }

    public String getSKU() {
        return SKU_TUNNEL_SUBSCRIPTION;
    }

    public static List<String> getSupportedSku() {
        ArrayList<String> result = new ArrayList<String>(1);
        result.add(SKU_TUNNEL_SUBSCRIPTION);
        return result;
    }
}
