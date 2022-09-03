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

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.List;

import de.flyingsnail.ipv6droid.android.Tunnels;

public interface PurchaseTunnelViewModel {
    /**
     * Query the result status of Subscription/Purchase check.
     * @return LiveData&lt;SubscriptionCheckResultListener.ResultType&gt; that can be observed for changes of the result of the purchase
     * check.
     */
    LiveData<SubscriptionCheckResultListener.ResultType> getSubscriptionCheckResult();

    /**
     * Query the result status of Certification. This property is guaranteed to update <em>after</em>
     * the Tunnels property has updated with a changed list of tunnels.
     *
     * @return LiveData&lt;CertificationResultListener.ResultType&gt; that can be observed for changes of the result of the certification
     *      check.
     */
    LiveData<CertificationResultListener.ResultType> getCertificationResult();

    /**
     * Query if there's currently a certification request running.
     * Use getCertificationResult instead to get more fine-grained status information
     * @return LiveData&lt;Boolean&gt; that can be observed for changes of certification request status.
     */
    @Deprecated LiveData<Boolean> getCertificationRunning();

    /**
     * Query diagnostic message explaining result of purchase and/or certification process.
     * @return LiveData&lt;String&gt; that can be observed for changes of debug message.
     */
    LiveData<String> getPurchasingDebugMessage();

    /**
     * Query the Purchase that is basis for current available tunnels
     * @return LiveData&lt;Purchase&gt; that can be observed for changes of the active Purchase.
     */
    LiveData<Purchase> getActivePurchase();

    /**
     * Query the list of tunnels available.
     * @return LiveData&lt;Tunnels&gt; that can be observed for changes of available tunnels or the
     * tunnel being used on this device.
     */
    LiveData<Tunnels> getTunnels();

    /**
     * Query the list of products (identified each by their SKU) that can be used on this device.
     * @return LiveData&lt;List&lt;SkuDetails&gt;&gt; that can be observed for changes of the
     * List of available SKU represented by their SkuDetails object.
     */
    LiveData<List<ProductDetails>> getProductDetailList();

    /**
     * Initiate a purchase with the Google Billing client. The normal flow of this operation
     * is that the user is guided through the purchase by Google, after which the purchased
     * item is directly consumed into a tunnel for this device, resulting in an observable
     * change of the Tunnels property.
     *
     * @param productId a String giving the product identifier of the product to purchase.
     * @param offerId a String giving the ID of the offer to use (as set in play console)
     * @param originatingActivity the Activity where the user issued the purchase.
     */
    void initiatePurchase(@NonNull String productId, @Nullable String offerId, @NonNull Activity originatingActivity);

    /**
     * Initiate consumption of an available, unconsumed product. This is a reconsolidation
     * operation that would occur only if consumption of a product failed (typically for technical
     * reasons) after its purchase.
     * @param sku a String giving the product identifier of a Purchase that should be consumed
     *            into a tunnel.
     */
    void consumePurchase(String sku);
}
