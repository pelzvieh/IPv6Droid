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

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.List;

/**
 * Created by pelzi on 18.10.17.
 */

public interface SubscriptionCheckResultListener {
    enum ResultType {
        /** We successfully read provisioned tunnels, we're ready to use them */
        HAS_TUNNELS,
        /** We know that the user does not have a subscription  */
        NO_PURCHASES,
        /** We're having a problem, most probably temporary, to read required information */
        TEMPORARY_PROBLEM,
        /**
         * The user's attempt to purchase a subscription has failed, most probably because the user
         * aborted the process, or did not have a valid payment method at hand.
         */
        PURCHASE_FAILED,
        /** Checking for active subscriptions failed, perhaps permanently */
        CHECK_FAILED,
        /** We don't understand what Google says to us */
        SUBSCRIPTION_UNPARSABLE,
        /** A purchase has been completed, but the tunnels are not yet provisioned. */
        PURCHASE_COMPLETED,
        /** The purchase process has been initiated, user is currently interacting with google */
        PURCHASE_STARTED,
        /** We could not yet contact the Google subscription service, or lost connection.
         * PurchaseManager took action to automatically recover from this condition. */
        NO_SERVICE_AUTO_RECOVERY,
        /** There is no subscription service suitable to our needs on this device.
         * Retrying is pointless. */
        NO_SERVICE_PERMANENT,
        /** We cannot currently use the Google subscription service, nor took action to recover.
         * No indication is given as to when and under which circumstances it might work again.
         * Retry later if you want.
         */
        NO_SERVICE_TRY_AGAIN
    }

    /**
     * React on an achieved result of subscription/purchase check.
     * @param result a ResultType indicating the status of purchase or subscription
     * @param activePurchase a Purchase object giving details of the active purchase, if any. May be null.
     * @param debugMessage a String indicating the origin of a problem, if applicable. May be null.
     */
    void onSubscriptionCheckResult(@NonNull ResultType result,
                                   @Nullable Purchase activePurchase,
                                   @Nullable String debugMessage);

    /**
     * Notify listener on a change of available products.
     * @param knownProductDetails a List&lt;ProductDetails&gt representing the now known list of products.
     */
    void onAvailableProductsUpdate(@NonNull List<ProductDetails> knownProductDetails);
}
