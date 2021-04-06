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

import androidx.annotation.NonNull;

/**
 * Base class for all subscription types.
 * That said, we currently have only one subscription type.
 * Created by pelzi on 15.06.16.
 */
public class Subscription {
    public static final String GOOGLESUBSCRIPTION = "<googlesubscription>";
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

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getSignature() {
        return signature;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setPurchaseTime(int purchaseTime) {
        this.purchaseTime = purchaseTime;
    }

    public void setPurchaseToken(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }

    public void setDeveloperPayload(String developerPayload) {
        this.developerPayload = developerPayload;
    }

    @Override
    @NonNull
    public String toString() {
        return "Subscription{" +
                "signature='" + signature + '\'' +
                ", sku='" + sku + '\'' +
                ", orderId='" + orderId + '\'' +
                ", purchaseTime=" + purchaseTime +
                ", purchaseToken='" + purchaseToken + '\'' +
                ", developerPayload='" + developerPayload + '\'' +
                '}';
    }
}
