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
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Initialise BillingClient library and read active subscriptions, non-expired in-app purchases,
 * and product details. Highly asynchronous, full of listeners.
 */
class IPv6DroidBillingStatusReader implements BillingClientStateListener, PurchasesResponseListener, ProductDetailsResponseListener, Closeable {

    private final String TAG = IPv6DroidBillingStatusReader.class.getName();

    private final List<Integer> permanentErrors = Arrays.asList(
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
    );
    
    private final PurchaseManagerImpl purchaseManager;

    private boolean purchasesRead = false;

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

    /**
     * Flag if billing client is currently connected.
     */
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);

    public IPv6DroidBillingStatusReader(PurchaseManagerImpl purchaseManager, Application application, SubscriptionCheckResultListener checkResultListener) {
        this.purchaseManager = purchaseManager;
        this.listener = checkResultListener;
        googleBillingClient = BillingClient.newBuilder(application.getApplicationContext()).
                setListener(purchaseManager).
                enablePendingPurchases().
                build();
        this.googleBillingClient.startConnection(this);
    }

    /**
     * Request to mark a previous Purchase as consumed. This is async, the result will be
     * delivered to the registered PurchaseManager instance.
     * @param purchase a Purchase that should be marked as consumed.
     */
    public void consumePurchase(com.android.billingclient.api.Purchase purchase) {
        ensureConnected(() -> {
            ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
            googleBillingClient.consumeAsync(params, purchaseManager);
        });
    }

    // execute the given Runnable if the client is currently online. Start connection of the client
    // if required and execute task once it becomes online.
    private void ensureConnected(Runnable task) {
        if (Boolean.FALSE.equals(connected.getValue())) {
            googleBillingClient.startConnection(this);
            connected.observeForever(connected -> {
                if (connected) {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }


    @Override
    public void onBillingServiceDisconnected() {
        Log.i(TAG, "Billing service is disconnected");
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                null,
                "Connection to Google billing is lost");
        connected.setValue(false);
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        Log.i(TAG, "Billing setup is finished");
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            connected.setValue(true);
            try {
                queryProductDetails();

                // retrieve list of purchased subscriptions
                purchasesRead = false; // in case we already were initialized earlier
                outstandingPurchaseQueries.addAndGet(2); // we're going to place 2 queries right now
                googleBillingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                        this);
                Log.d(TAG, "issued query for subscriptions");
                googleBillingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                        this);
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
            connected.setValue(false);
            Log.e(TAG, "Setup of Billing library yields error code. Debug message: " + billingResult.getDebugMessage());
            listener.onSubscriptionCheckResult(
                    permanentErrors.contains(billingResult.getResponseCode()) ?
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                    null,
                    billingResult.getDebugMessage());
        }
    }

    private void queryProductDetails() {
        // retrieve list of Google-supported subscription SKU and filter with our supported SKU. Required for purchase init
        final QueryProductDetailsParams.Builder subsParams = QueryProductDetailsParams.newBuilder();
        List<QueryProductDetailsParams.Product> queriedProducts = new ArrayList<>(10);
        for (String productId: SubscriptionBuilder.getSupportedSubscriptionProductIds()) {
            queriedProducts.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .setProductId(productId)
                    .build()
            );
        }
        for (String productId: SubscriptionBuilder.getSupportedPurchasesProductIds()) {
            queriedProducts.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .setProductId(productId)
                    .build()
            );
        }
        subsParams.setProductList(queriedProducts);

        googleBillingClient.queryProductDetailsAsync(subsParams.build(), this);
        Log.d(TAG, "issued query for ProductDetails");
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
    public void onProductDetailsResponse(@NonNull BillingResult billingResult, @Nullable List<ProductDetails> productDetailsList) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (productDetailsList == null || productDetailsList.isEmpty()) {
                Log.i(TAG, "received list of product details is empty");
            } else {
                Log.i(TAG, "Received list of product details");
                synchronized (purchaseManager) {
                    purchaseManager.addSupportedProductDetails(productDetailsList);
                    listener.onAvailableProductsUpdate(purchaseManager.getSupportedProductDetails());
                }
            }
        } else {
            Log.e(TAG, "Failed to read supported SKU: " + billingResult.getDebugMessage());
            listener.onSubscriptionCheckResult(
                    permanentErrors.contains(billingResult.getResponseCode()) ?
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                    null,
                    billingResult.getDebugMessage()
            );
        }
    }

    /**
     * Query if active purchases are already known. Might change if queries are still pending!
     * @return true if the purchaseManager associated already knows about active purchases.
     */
    public boolean hasPurchases() {
        synchronized (purchaseManager) {
            return !purchaseManager.getSupportedProductDetails().isEmpty();
        }
    }

    /**
     * Launch a billing workflow for the supplied parameters.
     * todo find out if the client must be online for this to work
     * @param originatingContext the Activity from which the user initiated a purchase
     * @param flowParams the BillingFlowParams describing the product and offer to be purchased.
     * @return the BillingResult stating if the billing workflow was successfully started.
     */
    public BillingResult launchBillingFlow(Activity originatingContext, BillingFlowParams flowParams) {
        return googleBillingClient.launchBillingFlow(originatingContext, flowParams);
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        googleBillingClient.endConnection();
        connected.setValue(false);
    }
}
