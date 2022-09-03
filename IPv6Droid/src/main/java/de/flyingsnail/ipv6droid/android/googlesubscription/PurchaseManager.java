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

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.List;

public interface PurchaseManager {

    /**
     * Add a listener to be called for subscription changes.
     * @param listener the {@link SubscriptionCheckResultListener} to call on changes
     */
    void addResultListener (SubscriptionCheckResultListener listener);

    /**
     * Remove a (previously added) {@link SubscriptionCheckResultListener} from being called on changes.
     * @param listener the {@link SubscriptionCheckResultListener} to remove
     */
    void removeResultListener (SubscriptionCheckResultListener listener);

    /**
     * Declare the supplied purchase to be consumed.
     * @param purchase a Purchase that must be marked consumed.
     */
    void consumePurchase(com.android.billingclient.api.Purchase purchase);

    /**
     * Validation of a Purchase from our local BillingClient failed, so we
     * do an online query to force an update of the local cache.
     *
     * @param p the Purchase that failed to verify
     * @param e the Exception that was thrown
     */
    void revalidateCache(@NonNull Purchase p, @NonNull Exception e);

    /**
     * Initiate the Google subscription purchase workflow on behalf of the originating activity.
     * Failure to do so, or successful purchase will be reported to the SubscriptionCheckResultListener
     * given to the constructor of this PurchaseManager.
     *
     * @param productId a String giving the product ID selected by the user.
     * @param offerId a String giving the selected offer for the selected product. May be null,
     *                must be null in case of a one-time purchase productId.
     * @param originatingContext an Activity where user initiated the requested purchase.
     */
    void initiatePurchase(String productId, String offerId, Activity originatingContext) throws IllegalStateException;

    /**
     * Get the list of subscription products that can be purchased.
     *
     * @return List&lt;SkuDetails&gt;
     */
    @NonNull
    List<ProductDetails> getSupportedProductDetails();

    /**
     * Close the connection to Google.
     */
    void destroy();

    /**
     * Re-open connection to Google.
     */
    void restart();
}
