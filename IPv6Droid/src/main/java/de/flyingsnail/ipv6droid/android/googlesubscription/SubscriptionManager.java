/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
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
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;
import de.flyingsnail.ipv6server.restapi.SubscriptionsApi;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by pelzi on 18.10.17.
 */
public class SubscriptionManager {
    private static final String TAG = SubscriptionManager.class.getSimpleName();

    private static final int RESPONSE_CODE_OK = 0;
    private static final int RC_BUY = 3;
    static Map<Integer, SubscriptionCheckResultListener.ResultType> codeMapping = new HashMap<Integer, SubscriptionCheckResultListener.ResultType>() {{
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
     * Helper implementation of the PurchasesUpdatedListener interface. Receives updates on purchases from Google.
     */
    private final PurchasesUpdatedListener googlePurchasesUpdatesListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchases) {
            Log.i(TAG, "received Google purchase update");
            if (billingResult.getResponseCode() == BillingResponseCode.OK
                    && purchases != null) {
                boolean validPurchase = false;
                for (Purchase purchase : purchases) {
                    if (SubscriptionManager.this.onGoogleProductPurchased(purchase)) {
                        validPurchase = true;
                    }
                }
                listener.onSubscriptionCheckResult(validPurchase ?
                        SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED :
                        SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED,
                        validPurchase ? null : billingResult.getDebugMessage());
            } else {
                Log.e(TAG, "Purchase not completed: " + billingResult.getDebugMessage());
                listener.onSubscriptionCheckResult(
                        map(billingResult.getResponseCode()),
                        billingResult.getDebugMessage());
            }
        }
        private SubscriptionCheckResultListener.ResultType map (final int billingResponseCode) {
            SubscriptionCheckResultListener.ResultType mapped = codeMapping.get(billingResponseCode);
            return mapped != null ? mapped : SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED;
        }
    };

    /**
     * The client representing the SubscrptionsApi of the IPv6Server.
     */
    SubscriptionsApi subscriptionsClient;

    /**
     * An instance of the Google BillingClient.
     */
    private BillingClient googleBillingClient;

    /**
     * The SubscriptionCheckResultListener that should be informed about this Manager's progress.
     */
    private SubscriptionCheckResultListener listener;

    /**
     * The Context that constructed this Manager.
     */
    private final @NonNull Context originatingContext;

    /**
     * The list of SkuDetails that can be purchased on this client.
     */
    private List<SkuDetails> supportedSKUDetails = new ArrayList<>(0);

    /**
     * Construct a SubscriptionManager. It will query Google subscriptions, check and retrieve the
     * associated tunnels from the IPv6Server. If constructed from an Activity, it can initiate
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
    public SubscriptionManager(SubscriptionCheckResultListener resultListener,
                               final @NonNull Context context) {
        tunnels = new ArrayList<>();
        listener = resultListener;
        originatingContext = context;

        // start status is "no service"
        listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                context.getString(R.string.SubManStartingUp));

        subscriptionsClient = RestProxyFactory.createSubscriptionsClient();
        googleBillingClient = BillingClient.newBuilder(context).
                setListener(googlePurchasesUpdatesListener).
                enablePendingPurchases().
                build();
        googleBillingClient.startConnection(stateListener);
    }

    /**
     * A list of TicTunnels associated with the current user's subscriptions
     */
    private List<TicTunnel> tunnels;

    /**
     * An instance implementing ServiceConnection by setting this object's service class when bound.
     */
    private BillingClientStateListener stateListener = new BillingClientStateListener() {
        @Override
        public void onBillingServiceDisconnected() {
            listener.onSubscriptionCheckResult(
                    SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY,
                    originatingContext.getString(R.string.SubManConnectionLost));
            // the BillingClient itself will come back to us with onBillingSetupFinished if it re-established the connection
        }

        @Override
        public void onBillingSetupFinished(BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingResponseCode.OK) {
                tunnels.clear();
                BillingResult featureSupportedResult = googleBillingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);
                if (featureSupportedResult.getResponseCode() != BillingResponseCode.OK) {
                    Log.e(TAG, "Subscriptions are not supported on this device: " +
                            featureSupportedResult.getDebugMessage());
                    listener.onSubscriptionCheckResult(
                            featureSupportedResult.getResponseCode() == BillingResponseCode.FEATURE_NOT_SUPPORTED ?
                                    SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                                    SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                            featureSupportedResult.getDebugMessage());
                    return;
                }

                // retrieve list of purchased subscriptions
                boolean subHandled = false;
                Purchase.PurchasesResult purchasesResult = googleBillingClient.queryPurchases(BillingClient.SkuType.SUBS);
                if (purchasesResult.getResponseCode() == BillingResponseCode.OK) {
                    for (Purchase purchase: purchasesResult.getPurchasesList()) {
                        Log.i(TAG, "Initial load of purchases retrieved purchase " + purchase.getOrderId());
                        try {
                            if (onGoogleProductPurchased(purchase)) {
                                subHandled = true;
                            }
                        } catch (RuntimeException re) {
                            Log.e(TAG, "Runtime exception caught during handling of subscription purchase", re);
                        }
                    }
                } else {
                    listener.onSubscriptionCheckResult(
                            SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                            purchasesResult.getBillingResult().getDebugMessage());
                }
                final boolean hasSubscriptions = subHandled;
                if (hasSubscriptions) {
                    synchronized (listener) { // is in race with "HAS_TUNNELS"
                        if (tunnels.size() == 0) { // the asynchronous query of IPv6Server about the tunnel details has not finished yet
                            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED, null);
                        }
                    }
                }

                // retrieve list of Google-supported SKU and filter with our supported SKU. Required for purchase init
                List<String> clientSKU = SubscriptionBuilder.getSupportedSku();
                SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                params.setSkusList(clientSKU).setType(BillingClient.SkuType.SUBS);
                googleBillingClient.querySkuDetailsAsync(params.build(),
                        (BillingResult skuQueryResult, List<SkuDetails> skuDetailsList) -> {
                            if (skuQueryResult.getResponseCode() == BillingResponseCode.OK) {
                                Log.i(TAG, "Received list of sku details");
                                SubscriptionManager.this.supportedSKUDetails = skuDetailsList;
                                // if the user has subscriptions, there's already check result reported
                                if (!hasSubscriptions) {
                                    if (skuDetailsList == null || skuDetailsList.isEmpty()) {
                                        Log.e(TAG, "received list of SKU details is empty");
                                        listener.onSubscriptionCheckResult(
                                                SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                                                originatingContext.getString(R.string.SubManEmptySKUdetails));
                                    } else {
                                        // we positively know the available SKU and that the user has no active purchase
                                        // this will enable the purchase process in suitable caller contexts
                                        listener.onSubscriptionCheckResult(
                                                SubscriptionCheckResultListener.ResultType.NO_SUBSCRIPTIONS, null);
                                    }
                                }
                            } else {
                                Log.e(TAG, "Failed to read supported SKU: " + skuQueryResult.getDebugMessage());
                                listener.onSubscriptionCheckResult(
                                        SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                                        skuQueryResult.getDebugMessage()
                                );
                            }
                        });
            } else {
                Log.e (TAG, "Setup of Billing library yields error code. Debug message: " + billingResult.getDebugMessage());
                listener.onSubscriptionCheckResult(
                        Arrays.asList(
                                BillingResponseCode.BILLING_UNAVAILABLE,
                                BillingResponseCode.DEVELOPER_ERROR,
                                BillingResponseCode.FEATURE_NOT_SUPPORTED
                        ).contains(billingResult.getResponseCode()) ?
                                SubscriptionCheckResultListener.ResultType.NO_SERVICE_PERMANENT :
                                SubscriptionCheckResultListener.ResultType.NO_SERVICE_TRY_AGAIN,
                        billingResult.getDebugMessage());
            }
        }
    };

    /**
     * Deal with a Google purchase information. Convenience method for {@link #onGoogleProductPurchased(String, String, String)}
     * @param purchase a Purchase object from the Google Billing API
     * @return true if purchase deals with a product relevant for this app
     */
    private boolean onGoogleProductPurchased (Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            return onGoogleProductPurchased(purchase.getSku(), purchase.getOriginalJson(), purchase.getSignature());
        } else {
            Log.d(TAG, "Purchase is not in state PURCHASED - ignoring for now");
            return false;
        }
    }

    /**
     * Deal with a Google purchase information (exploded parameters).
     * @param sku a String quoting the SKU (product identifier)
     * @param skuData a String quoting the JSON that describes the concrete purchase
     * @param skuSignature a String signing the skuData JSON
     * @return true if purchase deals with a product relevant for this app.
     */
    private boolean onGoogleProductPurchased(String sku, String skuData, String skuSignature) {
        Log.d(TAG, "Examining SKU " + sku
                + ",\n Data '" + skuData + "',\n signature '" + skuSignature);

        boolean isSubscriptionRelevant = false;
        try {
                if (SubscriptionBuilder.getSupportedSku().contains(sku)) {
                    // this one is relevant!
                    isSubscriptionRelevant = true;
                    Call<List<TicTunnel>> subsCall = subscriptionsClient.checkSubscriptionAndReturnTunnels(
                            skuData,
                            skuSignature
                    );
                    new AsyncTask<Void, Void, Exception>() {
                        @Override
                        protected Exception doInBackground(Void... params) {
                            List<TicTunnel> subscribedTunnels;
                            try {
                                Response<List<TicTunnel>> subscribedTunnelsResponse = subsCall.execute();
                                if (!subscribedTunnelsResponse.isSuccessful()) {
                                    throw new IllegalStateException("subscribedTunnels returns exception " + subscribedTunnelsResponse.errorBody().string());
                                }
                                subscribedTunnels = subscribedTunnelsResponse.body();
                                Log.d(TAG, String.format("Successfully retrieved %d tunnels from server", subscribedTunnels.size()));
                                // add only valid tunnels to save case distinction all through
                                // the app
                                for (TicTunnel tunnel : subscribedTunnels) {
                                    if (tunnel.isEnabled()) {
                                        tunnels.add(tunnel);
                                        Log.d(TAG, String.format("Added valid tunnel %s", tunnel.getTunnelId()));
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Cannot verify subscription", e);
                                return e;
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Exception e) {
                            if (e != null) {
                                if (e instanceof IOException || e instanceof RuntimeException) {
                                    listener.onSubscriptionCheckResult(
                                            SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM,
                                            e.toString());
                                } else {
                                    listener.onSubscriptionCheckResult(
                                            SubscriptionCheckResultListener.ResultType.CHECK_FAILED,
                                            e.toString());
                                }
                            } else {
                                synchronized (listener) { // is in race with "PURCHASE_COMPLETED"
                                    listener.onSubscriptionCheckResult(
                                            SubscriptionCheckResultListener.ResultType.HAS_TUNNELS,
                                            null);
                                }
                            }
                        }
                    }.execute();
                }
            } catch (RuntimeException re) {
                Log.e(TAG, "unable to handle active subscription " + sku, re);
            }
        return isSubscriptionRelevant;
    }


    /**
     * Initiate the Google subscription purchase workflow on behalf of the originating activity.
     * Failure to do so, or successful purchase will be reported to the SubscriptionCheckResultListener
     * given to the constructor of this SubscriptionManager.
     */
    public void initiatePurchase() throws IllegalStateException {
        if (!(originatingContext instanceof Activity)) {
            throw new IllegalStateException("initiatePurchase can only be called if SubscriptionManager is constructed from an Activity");
        }
        if (supportedSKUDetails == null || supportedSKUDetails.isEmpty()) {
            throw new IllegalStateException("initiatePurchase can only be called if supported SKU are known");
        }
        final Activity originatingActivity = (Activity)originatingContext;

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(supportedSKUDetails.get(0))
                .build();
        int responseCode = googleBillingClient.launchBillingFlow(originatingActivity, flowParams).getResponseCode();

        if (responseCode == BillingResponseCode.OK) {
            listener.onSubscriptionCheckResult(
                    SubscriptionCheckResultListener.ResultType.PURCHASE_STARTED,
                    null);
            Log.i(TAG, "Purchase started!");
        } else {
            Log.w(TAG, "Failed purchase, responseCode=" + responseCode + ", responseCode=" + responseCode);
        }

    }

    /**
     * Get the current list of tunnels
     *
     * @return List&lt;TicTunnel&gt;
     */
    public List<TicTunnel> getTunnels() {
        return tunnels;
    }

    /**
     * Get the list of subscription products that can be purchased.
     * @return List&lt;SkuDetails&gt;
     */
    public List<SkuDetails> getSupportedSKUDetails() {
        if (supportedSKUDetails == null) {
            throw new IllegalStateException("getSupportedSKUDetails can only be called if supported SKU are known");
        }

        return supportedSKUDetails;
    }

    /**
     * Close the connection to Google.
     */
    public void destroy() {
        Log.i(TAG, "Destroying Subscription Manager.");
        googleBillingClient.endConnection();
    }
}
