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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.flyingsnail.ipv6droid.R;

/**
 * Initialise BillingClient library and read active subscriptions, non-expired in-app purchases,
 * and product details. Highly asynchronous, full of listeners.
 */
class IPv6DroidBillingStatusReader implements BillingClientStateListener, PurchasesResponseListener, SkuDetailsResponseListener {

    private final String TAG = IPv6DroidBillingStatusReader.class.getName();

    private final List<Integer> permanentErrors = Arrays.asList(
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
    );
    
    private final PurchaseManager purchaseManager;

    private boolean purchasesRead = false;

    final private Context originatingContext;

    final private BillingClient googleBillingClient;

    /**
     * The SubscriptionCheckResultListener that should be informed about this Manager's progress.
     */
    final private SubscriptionCheckResultListener listener;

    /**
     * As we need to issue more than one asynchronous queries to Google Billing, we need
     * to count back with the answers to find out when all answers arrived.
     */
    final private AtomicInteger outstandingPurchaseQueries = new AtomicInteger(0);

    public IPv6DroidBillingStatusReader(PurchaseManager purchaseManager, Context context, BillingClient googleBillingClient, SubscriptionCheckResultListener checkResultListener) {
        this.purchaseManager = purchaseManager;
        originatingContext = context;
        this.googleBillingClient = googleBillingClient;
        this.listener = checkResultListener;
        this.googleBillingClient.startConnection(this);
    }

    @Override
    public void onBillingServiceDisconnected() {
        Log.i(TAG, "Billing service is disconnected");
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                null,
                originatingContext.getString(R.string.SubManConnectionLost));
        // the BillingClient itself will come back to us with onBillingSetupFinished if it re-established the connection
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        Log.i(TAG, "Billing setup is finished");
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            try {
                // retrieve list of Google-supported subscription SKU and filter with our supported SKU. Required for purchase init
                final List<String> clientPurchasesSKU = SubscriptionBuilder.getSupportedSubscriptionSku();
                final SkuDetailsParams.Builder subsParams = SkuDetailsParams.newBuilder();
                subsParams.setSkusList(clientPurchasesSKU);
                subsParams.setType(BillingClient.SkuType.SUBS);
                googleBillingClient.querySkuDetailsAsync(subsParams.build(), this);
                Log.d(TAG, "issued query for subscription SkuDetails");

                // retrieve list of Google-supported one-time purchase SKU and filter with our supported SKU. Required for purchase init
                List<String> clientPurchaseSKU = SubscriptionBuilder.getSupportedPurchasesSku();
                final SkuDetailsParams.Builder purchaseParams = SkuDetailsParams.newBuilder();
                purchaseParams.setSkusList(clientPurchaseSKU).setType(BillingClient.SkuType.INAPP);
                googleBillingClient.querySkuDetailsAsync(purchaseParams.build(), this);
                Log.d(TAG, "issued query for purchase SkuDetails");

                // retrieve list of purchased subscriptions
                purchasesRead = false; // in case we already were initialized earlier
                outstandingPurchaseQueries.addAndGet(2); // we're going to place 2 queries right now
                googleBillingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, this);
                Log.d(TAG, "issued query for subscriptions");
                googleBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, this);
                Log.d(TAG, "issued query for purchases");
            } catch (RuntimeException re) {
                Log.e(TAG, "Unexpected exception when issuing billing queries", re);
                listener.onSubscriptionCheckResult(
                        SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT,
                        null,
                        "Unexcpeted exception when issuing billing queries: " + re.getMessage()
                );
            }
        } else {
            Log.e(TAG, "Setup of Billing library yields error code. Debug message: " + billingResult.getDebugMessage());
            listener.onSubscriptionCheckResult(
                    permanentErrors.contains(billingResult.getResponseCode()) ?
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                    null,
                    billingResult.getDebugMessage());
        }
    }

    @Override
    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
        // Caution: separate queries are issued (one-time, and subscription type purchases), so separate responses will arrive!
        Log.i(TAG, "Response on purchases query recieved, size: " + list.size());
        purchasesRead = outstandingPurchaseQueries.decrementAndGet() == 0;
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (Purchase purchase : list) {
                Log.i(TAG, "Initial load of purchases retrieved purchase " + purchase.getOrderId());
                try {
                    if (purchaseManager.onGoogleProductPurchased(purchase)) {
                        synchronized (listener) { // is in race with "HAS_TUNNELS"
                            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED, purchase, null);
                        }
                    }
                } catch (RuntimeException re) {
                    Log.e(TAG, "Runtime exception caught during handling of subscription purchase", re);
                }
            }
        } else {
            listener.onSubscriptionCheckResult(
                    SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                    null,
                    billingResult.getDebugMessage());
        }

        // if this was the last outstanding answer, we might have to report "NO_PURCHASES"
        if (purchasesRead && !hasPurchases()) {
            // we positively know the available SKU and that no products/subscriptons are active
            // this will enable the purchase process in suitable caller contexts
            listener.onSubscriptionCheckResult(
                    SubscriptionCheckResultListener.ResultType.NO_PURCHASES, null,null);
        }
    }

    @Override
    public void onSkuDetailsResponse(@NonNull BillingResult skuQueryResult, @Nullable List<SkuDetails> skuDetailsList) {
        if (skuQueryResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (skuDetailsList == null || skuDetailsList.isEmpty()) {
                Log.i(TAG, "received list of SKU details is empty");
            } else {
                Log.i(TAG, "Received list of sku details");
                synchronized (purchaseManager) {
                    purchaseManager.addSupportedSKUDetails(skuDetailsList);
                    listener.onAvailableSkuUpdate(purchaseManager.getSupportedSKUDetails());
                }
            }
        } else {
            Log.e(TAG, "Failed to read supported SKU: " + skuQueryResult.getDebugMessage());
            listener.onSubscriptionCheckResult(
                    permanentErrors.contains(skuQueryResult.getResponseCode()) ?
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                    null,
                    skuQueryResult.getDebugMessage()
            );
        }
    }

    /**
     * Query if active purchases are already known. Might change if queries are still pending!
     * @return true if the purchaseManager associated already knows about active purchases.
     */
    public boolean hasPurchases() {
        synchronized (purchaseManager) {
            return !purchaseManager.getSupportedSKUDetails().isEmpty();
        }
    }
}
