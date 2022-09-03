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

import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.flyingsnail.ipv6droid.R;

class ListenerBroadCaster implements SubscriptionCheckResultListener {
    final static String TAG = SubscriptionCheckResultListener.class.getSimpleName();
    /** the list of final {@link SubscriptionCheckResultListener} instances to call */
    final private List<SubscriptionCheckResultListener> listeners = new ArrayList<>(10);

    ListenerBroadCaster() {
    }

    /**
     * React on an achieved result of subscription/purchase check.
     *
     * @param result         a ResultType indicating the status of purchase or subscription
     * @param activePurchase a Purchase object giving details of the active purchase, if any. May be null.
     * @param debugMessage   a String indicating the origin of a problem, if applicable. May be null.
     */
    @Override
    public void onSubscriptionCheckResult(@NonNull ResultType result, @Nullable Purchase activePurchase, @Nullable String debugMessage) {
        for (SubscriptionCheckResultListener listener: listeners) {
            try {
                listener.onSubscriptionCheckResult(result, activePurchase, debugMessage);
            } catch (RuntimeException e) {
                Log.e(TAG, "Exception when calling listener " + listener, e);
            }
        }
    }

    /**
     * Notify listener on a change of available SKU.
     *
     * @param knownProducts a List&lt;ProductDetails&gt; representing the now known list of products.
     */
    @Override
    public void onAvailableProductsUpdate(@NonNull List<ProductDetails> knownProducts) {
        for (SubscriptionCheckResultListener listener: listeners) {
            try {
                listener.onAvailableProductsUpdate(knownProducts);
            } catch (RuntimeException e) {
                Log.e(TAG, "Exception when calling listener " + listener, e);
            }
        }
    }

    protected void addListener (@NonNull SubscriptionCheckResultListener listener) {
        listeners.add(listener);
    }

    protected void removeListener (@NonNull SubscriptionCheckResultListener listener) {
        if (!listeners.remove(listener))
            Log.e(TAG, "Attempt to remove an unregistered listener: " + listener);
    }
}

/**
 * Created by pelzi on 18.10.17.
 */
@SuppressWarnings("ConstantConditions")
public class PurchaseManagerImpl implements ConsumeResponseListener, PurchasesUpdatedListener, PurchaseManager {
    private static final String TAG = PurchaseManagerImpl.class.getSimpleName();

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
     * The SubscriptionCheckResultListener that should be informed about this Manager's progress.
     */
    private final ListenerBroadCaster listener = new ListenerBroadCaster();

    /**
     * The list of ProductDetails that can be purchased on this client.
     */
    private final @NonNull List<ProductDetails> supportedProductDetails = new ArrayList<>(0);

    /**
     * The accompanied status reader
     */
    private final IPv6DroidBillingStatusReader statusReader;

    /**
     * Construct a PurchaseManager. It will query Google subscriptions.
     * If constructed from an Activity, it can initiate
     * the Google subscription purchase workflow and aids in implementing the protocol.
     *
     * @param application The application object representing the currently executed app.
     */
    public PurchaseManagerImpl(final @NonNull Application application) {
        // start status is "no service"
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                null,
                application.getString(R.string.SubManStartingUp));

        // initiate connection and keep status handling running
        statusReader = new IPv6DroidBillingStatusReader(this, application, listener);
    }

    private boolean isSupportedByThisClient (final String sku) {
        return SubscriptionBuilder.getSupportedSubscriptionProductIds().contains(sku)
            || SubscriptionBuilder.getSupportedPurchasesProductIds().contains(sku);
    }

    /**
     * Deal with a Google purchase information.
     * @param purchase a Purchase object from the Google Billing API
     * @return true if purchase deals with a product relevant for this app
     */
    protected boolean onGoogleProductPurchased (final @NonNull com.android.billingclient.api.Purchase purchase) {
        if (purchase.getPurchaseState() == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED) {
            final List<String> productIds = purchase.getProducts();
            boolean anySupported = false;
            for (String productId: productIds) {
                Log.d(TAG, "Examining product ID " + productId);
                anySupported = anySupported || isSupportedByThisClient(productId);
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

    /**
     * Add a listener to be called for subscription changes.
     *
     * @param listener the {@link SubscriptionCheckResultListener} to call on changes
     */
    @Override
    public void addResultListener(SubscriptionCheckResultListener listener) {
        this.listener.addListener(listener);
    }

    /**
     * Remove a (previously added) {@link SubscriptionCheckResultListener} from being called on changes.
     *
     * @param listener the {@link SubscriptionCheckResultListener} to remove
     */
    @Override
    public void removeResultListener(SubscriptionCheckResultListener listener) {
        this.listener.removeListener(listener);
    }

    @Override
    public void consumePurchase(com.android.billingclient.api.Purchase purchase) {
        statusReader.consumePurchase(purchase);
    }

    /**
     * Validation of a Purchase from our local BillingClient failed, so we
     * do an online query to force an update of the local cache.
     * @param p the Purchase that failed to verify
     * @param e the Exception that was thrown
     */
    @Override
    public void revalidateCache(@NonNull Purchase p, @NonNull Exception e) {
        listener.onSubscriptionCheckResult(
                SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM,
                p,
                e.toString());
        // nasty inconsistencies of the internal cache of BillingClient were seen
        // when purchases were cancelled
        // todo test if still required with google billing 5.
/*        for (String type: typesForSkus (p.getSkus())) {
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
 */
    }

    /** Helper to extract the product types from a list of product ids */
    private Set<String> typesForSkus(List<String> productIds) {
        Set<String> types = new HashSet<>(2);
        for (ProductDetails details: supportedProductDetails) {
            if (productIds.contains(details.getProductId())) {
                types.add(details.getProductType());
            }
        }
        return types;
    }


    /**
     * Initiate the Google subscription purchase workflow on behalf of the originating activity.
     * Failure to do so, or successful purchase will be reported to the SubscriptionCheckResultListener
     * given to the constructor of this PurchaseManager.
     * @param productId a String giving the product ID selected by the user.
     */
    @Override
    public void initiatePurchase(final String productId, final String offerId, final Activity originatingContext) throws IllegalStateException {
        if (supportedProductDetails.isEmpty()) {
            throw new IllegalStateException("initiatePurchase can only be called if supported SKU are known");
        }
        ProductDetails selectedProduct = null;
        for (ProductDetails d: supportedProductDetails) {
            if (d.getProductId().equals(productId)) {
                selectedProduct = d;
            }
        }
        if (selectedProduct == null) {
            throw new IllegalStateException("User selected a product that we do not offer");
        }
        BillingFlowParams.ProductDetailsParams.Builder builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(selectedProduct);
        if (offerId != null)
                builder.setOfferToken(offerId);

        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                Arrays.asList(
                        builder.build()
                );

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();
        int responseCode = statusReader.launchBillingFlow(originatingContext, flowParams).getResponseCode();

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
    @Override
    public @NonNull List<ProductDetails> getSupportedProductDetails() {
        return Collections.unmodifiableList(supportedProductDetails);
    }

    /**
     * Extend the list of subscription products that can be purchased. We're only allowing
     * purchase of products that are supported by this client as anything else would surely
     * turn out very frustrating to the user.
     * @param supportedProductDetails a List of ProductDetails
     */
    void addSupportedProductDetails(List<ProductDetails> supportedProductDetails) {
        for (ProductDetails details: supportedProductDetails) {
            if (isSupportedByThisClient(details.getProductId())) {
                this.supportedProductDetails.add(details);
            }
        }
    }


    /**
     * Close the connection to Google.
     */
    @Override
    public void destroy() {
        Log.i(TAG, "Destroying Purchase Manager.");
        try {
            statusReader.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close statusReader", e);
        }
    }

    /**
     * Re-open connection to Google.
     */
    @Override
    public void restart() {
        Log.i(TAG, "Re-starting purchase manager");
        try {
            statusReader.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to close statusReader", e);
        }
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
                if (PurchaseManagerImpl.this.onGoogleProductPurchased(purchase)) {
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
