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
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.flyingsnail.ipv6droid.R;

/**
 * Created by pelzi on 18.10.17.
 */
@SuppressWarnings("ConstantConditions")
public class PurchaseManager implements ConsumeResponseListener, PurchasesUpdatedListener {
    private static final String TAG = PurchaseManager.class.getSimpleName();

    private static final Map<Integer, SubscriptionCheckResultListener.ResultType> codeMapping = new HashMap<Integer, SubscriptionCheckResultListener.ResultType>() {{
        put (BillingResponseCode.OK, SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED);
        put (BillingResponseCode.BILLING_UNAVAILABLE, SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED);
        put (BillingResponseCode.DEVELOPER_ERROR, SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT);
        put (BillingResponseCode.ERROR, SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
        put (BillingResponseCode.FEATURE_NOT_SUPPORTED, SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED);
        put (BillingResponseCode.ITEM_ALREADY_OWNED, SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED);
        put (BillingResponseCode.ITEM_NOT_OWNED, SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM); // we do not consume, this should never happen
        put (BillingResponseCode.ITEM_UNAVAILABLE, SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM); // Should happen only when items are being managed in Play Store
        put (BillingResponseCode.SERVICE_DISCONNECTED, SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY);
        put (BillingResponseCode.SERVICE_TIMEOUT, SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
        put (BillingResponseCode.SERVICE_UNAVAILABLE, SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
        put (BillingResponseCode.USER_CANCELED, SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED);
    }};

    /**
     * An instance of the Google BillingClient.
     */
    private final BillingClient googleBillingClient;

    /**
     * The SubscriptionCheckResultListener that should be informed about this Manager's progress.
     */
    final private SubscriptionCheckResultListener listener;

    /**
     * The Context that constructed this Manager.
     */
    private final @NonNull Context originatingContext;

    /**
     * The list of SkuDetails that can be purchased on this client.
     */
    private final @NonNull List<SkuDetails> supportedSKUDetails = new ArrayList<>(0);

    /**
     * The accompanied status reader
     */
    private final IPv6DroidBillingStatusReader statusReader;

    /**
     * Construct a PurchaseManager. It will query Google subscriptions.
     * If constructed from an Activity, it can initiate
     * the Google subscription purchase workflow and aids in implementing the protocol.
     *
     * @param resultListener A SubscriptionCheckResultListener that will handle the results of
     *                       managing subscriptions. All results of management will be passed via
     *                       calls to this callback; it should handle appropriate updates of the
     *                       user interface.
     * @param context A Context that represents the Context that initiated creation of this Manager.
     *                If the Context is actually an Acitivty, the method initiatePurchase can be
     *                used to start purchases.
     */
    public PurchaseManager(SubscriptionCheckResultListener resultListener,
                           final @NonNull Context context) {
        listener = resultListener;
        originatingContext = context;

        // start status is "no service"
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                null,
                context.getString(R.string.SubManStartingUp));

        googleBillingClient = BillingClient.newBuilder(context).
                setListener(this).
                enablePendingPurchases().
                build();

        // initiate connection and keep status handling running
        statusReader = new IPv6DroidBillingStatusReader(this, context, googleBillingClient, resultListener);
    }

    private boolean isSupportedByThisClient (final String sku) {
        return SubscriptionBuilder.getSupportedSubscriptionSku().contains(sku)
            || SubscriptionBuilder.getSupportedPurchasesSku().contains(sku);
    }

    /**
     * Deal with a Google purchase information.
     * @param purchase a Purchase object from the Google Billing API
     * @return true if purchase deals with a product relevant for this app
     */
    boolean onGoogleProductPurchased (final @NonNull com.android.billingclient.api.Purchase purchase) {
        if (purchase.getPurchaseState() == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) {
            final List<String> skus = purchase.getSkus();
            boolean anySupported = false;
            for (String sku: skus) {
                Log.d(TAG, "Examining SKU " + sku);
                anySupported = anySupported || isSupportedByThisClient(sku);
            }
            if (anySupported) {
                listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED,
                        purchase,
                        null);
            }
            return anySupported;
        } else {
            Log.d(TAG, "Purchase is not in state PURCHASED - ignoring for now");
            return false;
        }
    }

    public void consumePurchase(com.android.billingclient.api.Purchase purchase) {
        ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build();
        googleBillingClient.consumeAsync(params, this);
    }

    /**
     * Validation of a Purchase from our local BillingClient failed, so we
     * do an online query to force an update of the local cache.
     * @param p the Purchase that failed to verify
     * @param e the Exception that was thrown
     */
    public void revalidateCache (@NonNull Purchase p, @NonNull Exception e) {
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM,
                p,
                e.toString());
        // nasty inconsistencies of the internal cache of BillingClient were seen
        // when purchases were cancelled
        for (String type: typesForSkus (p.getSkus())) {
            googleBillingClient.queryPurchaseHistoryAsync(
                    type,
                    (billingResult, purchasesList) -> {
                        if (billingResult.getResponseCode() == BillingResponseCode.OK) {
                            Log.i(TAG, "Received online update of purchases");
                            if (purchasesList == null || purchasesList.isEmpty()) {
                                listener.onSubscriptionCheckResult(
                                        SubscriptionCheckResultListener.ResultType.NO_PURCHASES,
                                        null,
                                        "Status corrected online"
                                );
                            } else {
                                for (PurchaseHistoryRecord record : purchasesList) {
                                    Log.i(TAG, "Purchase " + record.getSkus() + ": " + record.getPurchaseToken());
                                }
                            }
                        } else {
                            Log.i(TAG, "No online update of purchases available, rc=" + billingResult.getResponseCode());
                        }
                    }
            );
        }
    }

    /** Helper to extract the purchase types from a list of SKUs */
    private Set<String> typesForSkus(List<String> skus) {
        Set<String> types = new HashSet<>(2);
        for (SkuDetails details: supportedSKUDetails) {
            if (skus.contains(details.getSku())) {
                types.add(details.getSku());
            }
        }
        return types;
    }


    /**
     * Initiate the Google subscription purchase workflow on behalf of the originating activity.
     * Failure to do so, or successful purchase will be reported to the SubscriptionCheckResultListener
     * given to the constructor of this PurchaseManager.
     * @param sku a String giving the product ID selected by the user.
     */
    public void initiatePurchase(final String sku) throws IllegalStateException {
        if (!(originatingContext instanceof Activity)) {
            throw new IllegalStateException("initiatePurchase can only be called if PurchaseManager is constructed from an Activity");
        }
        if (supportedSKUDetails.isEmpty()) {
            throw new IllegalStateException("initiatePurchase can only be called if supported SKU are known");
        }
        SkuDetails selectedProduct = null;
        for (SkuDetails d:  supportedSKUDetails) {
            if (d.getSku().equals(sku)) {
                selectedProduct = d;
            }
        }
        if (selectedProduct == null) {
            throw new IllegalStateException("User selected a product that we do not offer");
        }

        final Activity originatingActivity = (Activity)originatingContext;

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(selectedProduct)
                .build();
        int responseCode = googleBillingClient.launchBillingFlow(originatingActivity, flowParams).getResponseCode();

        if (responseCode == BillingResponseCode.OK) {
            listener.onSubscriptionCheckResult(
                    SubscriptionCheckResultListener.ResultType.PURCHASE_STARTED,
                    null,
                    null);
            Log.i(TAG, "Purchase started!");
        } else {
            Log.w(TAG, "Failed purchase, responseCode=" + responseCode + ", responseCode=" + responseCode);
        }

    }

    /**
     * Get the list of subscription products that can be purchased.
     * @return List&lt;SkuDetails&gt;
     */
    public @NonNull List<SkuDetails> getSupportedSKUDetails() {
        return Collections.unmodifiableList(supportedSKUDetails);
    }

    /**
     * Extend the list of subscription products that can be purchased. We're only allowing
     * purchase of products that are supported by this client as anything else would surely
     * turn out very frustrating to the user.
     * @param supportedSKUDetails a List of SkuDetails
     */
    void addSupportedSKUDetails(List<SkuDetails> supportedSKUDetails) {
        for (SkuDetails details: supportedSKUDetails) {
            if (isSupportedByThisClient(details.getSku())) {
                this.supportedSKUDetails.add(details);
            }
        }
    }


    /**
     * Close the connection to Google.
     */
    public void destroy() {
        Log.i(TAG, "Destroying Purchase Manager.");
        googleBillingClient.endConnection();
    }

    @Override
    public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String s) {
        if (billingResult.getResponseCode() == BillingResponseCode.OK) {
            Log.i(TAG, "Consumed successfully");
        } else {
            Log.w(TAG, "Consuming failed: " + billingResult.getDebugMessage());
        }
    }

    /**
     * Spontaneous asynchronous callback by Google: user purchased a product associated with
     * our app.
     * @param billingResult a BillingResult object
     * @param purchases a List of Purchase objects
     */
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<com.android.billingclient.api.Purchase> purchases) {
        Log.i(TAG, "received Google purchase update");
        if (billingResult.getResponseCode() == BillingResponseCode.OK
                && purchases != null) {
            Purchase activePurchase = null;
            for (com.android.billingclient.api.Purchase purchase : purchases) {
                if (PurchaseManager.this.onGoogleProductPurchased(purchase)) {
                    activePurchase = purchase;
                    break;
                }
            }
            if (activePurchase != null) {
                listener.onSubscriptionCheckResult(
                        SubscriptionCheckResultListener.ResultType.HAS_TUNNELS,
                        activePurchase,
                        activePurchase != null ? null : billingResult.getDebugMessage());
            }
        } else {
            Log.e(TAG, "Purchase not completed: " + billingResult.getDebugMessage());
            // unclear if we should change app status if billing library *spontaneously* starts
            // babbling errors...
            listener.onSubscriptionCheckResult(
                    map(billingResult.getResponseCode()),
                    null,
                    billingResult.getDebugMessage());
        }
    }

    private SubscriptionCheckResultListener.ResultType map(final int billingResponseCode) {
        SubscriptionCheckResultListener.ResultType mapped = codeMapping.get(billingResponseCode);
        return mapped != null ? mapped : SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED;
    }
}
