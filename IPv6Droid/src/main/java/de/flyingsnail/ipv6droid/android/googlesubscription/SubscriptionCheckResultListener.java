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

/**
 * Created by pelzi on 18.10.17.
 */

public interface SubscriptionCheckResultListener {
    enum ResultType {
        /** We successfully read provisioned tunnels, we're ready to use them */
        HAS_TUNNELS,
        /** We know that the user does not have a subscription  */
        NO_SUBSCRIPTIONS,
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
         * SubscriptionManager took action to automatically recover from this condition. */
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

    void onSubscriptionCheckResult(ResultType result, String debugMessage);
}
