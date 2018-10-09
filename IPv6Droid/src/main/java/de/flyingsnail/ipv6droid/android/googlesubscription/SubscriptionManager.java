/*
 *
 *  * Copyright (c) 2017 Dr. Andreas Feldner.
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
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;
import de.flyingsnail.ipv6server.restapi.SubscriptionsApi;

/**
 * Created by pelzi on 18.10.17.
 */
public class SubscriptionManager {
    private static final String TAG = SubscriptionManager.class.getSimpleName();

    private static final int RESPONSE_CODE_OK = 0;
    private static final int RC_BUY = 3;

    static {
        // add exception handler to client factory
        ResteasyProviderFactory pf = ResteasyProviderFactory.getInstance();
        pf.addClientErrorInterceptor(new SubscriptionErrorInterceptor());
    }
    /**
     * The client representing the SubscrptionsApi of the IPv6Server.
     */
    SubscriptionsApi subscriptionsClient;


    /**
     * The proxy implementing the Google InAppBillingService interface. Is set asynchronously
     * by binding serviceCon.
     */
    private IInAppBillingService service;

    /**
     * The SubscriptionCheckResultListener that should be informed about this Manager's progress.
     */
    private SubscriptionCheckResultListener listener;

    /**
     * The Context that constructed this Manager.
     */
    private final @NonNull Context originatingContext;

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
    public SubscriptionManager(SubscriptionCheckResultListener resultListener, final @NonNull Context context) {
        tunnels = new ArrayList<TicTunnel>();
        listener = resultListener;
        originatingContext = context;

        subscriptionsClient = RestProxyFactory.createSubscriptionsClient();

        Intent serviceIntent =
                new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (!originatingContext.bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "bindService failed");
            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.NO_SERVICE);
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
     * A list of TicTunnels associated with the current user's subscriptions
     */
    private List<TicTunnel> tunnels;

    /**
     * An instance implementing ServiceConnection by setting this object's service class when bound.
     */
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.NO_SERVICE);
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder serviceBind) {
            service = IInAppBillingService.Stub.asInterface(serviceBind);

            tunnels.clear();
            try {
                getTunnelsFromSubscription();
            } catch (RemoteException e) {
                Log.e(TAG, "Could not read existing subscriptions");
                listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.SUBSCRIPTION_UNPARSABLE);
            }
        }
    };

    /**
     * Retrieve the active subscriptions by querying google play, then resolving the SKU list. Then
     * verify the subscription with the server and get the list of provisiond tunnels.
     * This re-populates the tunnels field of this SubscribeTunnelActivity activity.
     */
    private void getTunnelsFromSubscription() throws RemoteException {
        boolean foundRelevantSubscription = false; // if we're through the subscriptions w/o a tunnel subscription, we offer to subscribe

        String continuationToken = null;
        do {
            // loop on INAPP_CONTINUATION_TOKEN
            Bundle activeSubs = service.getPurchases(3, originatingContext.getPackageName(),
                    "subs", continuationToken);
            if (activeSubs.getInt("RESPONSE_CODE") == RESPONSE_CODE_OK) {
                final List<String> skus = activeSubs.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                final List<String> skuData = activeSubs.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                final List<String> skuSignature = activeSubs.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                continuationToken = activeSubs.getString("INAPP_CONTINUATION_TOKEN", null);

                if (skus == null || skuData == null || skuSignature == null)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        throw new RemoteException("service returned null as one of the expected arrays");
                    } else {
                        throw new RemoteException();
                    }

                // create api stub
                for (int index = 0; index < skus.size(); index++) {
                    Log.d(TAG, "Examining index " + index + ",\n SKU " + skus.get(index)
                            + ",\n Data '" + skuData.get(index) + "',\n signature '" + skuSignature.get(index));
                    try {
                        if (SubscriptionBuilder.getSupportedSku().contains(skus.get(index))) {
                            // this one is relevant!
                            foundRelevantSubscription = true;
                            new AsyncTask<Integer, Void, Exception>() {
                                @Override
                                protected Exception doInBackground(Integer... params) {
                                    int index = params[0];
                                    List<TicTunnel> subscribedTunnels;
                                    try {
                                        subscribedTunnels = subscriptionsClient.checkSubscriptionAndReturnTunnels(
                                                skuData.get(index),
                                                skuSignature.get(index)
                                        );
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
                                            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
                                        } else {
                                            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.CHECK_FAILED);
                                        }
                                    } else {
                                        listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.HAS_TUNNELS);
                                    }
                                }
                            }.execute(index);
                        }
                    } catch (RuntimeException re) {
                        Log.e(TAG, "unable to handle active subscription " + skus.get(index), re);
                    }
                }
            }
        } while (continuationToken != null);

        // if we had no luck with this guy's subscriptions, still we need to update the Activity state
        if (!foundRelevantSubscription)
            listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.NO_SUBSCRIPTIONS);
    }


    /**
     * Initiate the Google subscription purchase workflow on behalf of the orignating activity.
     * The originating acitivity's onActivityResult will be called on completion and should then
     * delegate to handlePurchaseActivityResult
     */
    public void initiatePurchase() throws IllegalArgumentException {
        if (!(originatingContext instanceof Activity)) {
            throw new IllegalArgumentException("initiatePurchase can only be called if SubscriptionManager is constructed from an Activity");
        }
        final Activity originatingActivity = (Activity)originatingContext;

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... voids) {
                String developerPayload = null;
                try {
                    developerPayload = subscriptionsClient.createNewPayload();
                    if (service == null) // should not happen because purchaseButton is enabled only after successful connection
                        throw new IllegalStateException("InAppSubscriptionService not bound");
                    final Bundle bundle = service.getBuyIntent(3, originatingContext.getPackageName(),
                            SubscriptionBuilder.getSupportedSku().get(0), "subs", developerPayload);

                    final PendingIntent pendingIntent = bundle.getParcelable("BUY_INTENT");
                    if (bundle.getInt("RESPONSE_CODE") == RESPONSE_CODE_OK && pendingIntent != null) {
                        // Start purchase flow (this brings up the Google Play UI).
                        // Result will be delivered through onActivityResult().
                        originatingActivity.startIntentSenderForResult(pendingIntent.getIntentSender(), RC_BUY, new Intent(),
                                Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0));
                        // this is async, so at this point, we still need a valid developerPayload...
                    } else
                        return new RuntimeException("Subscription service returned " + pendingIntent);
                } catch (Exception e) {
                    try {
                        if (developerPayload != null)
                            subscriptionsClient.deleteUnusedPayload(developerPayload);
                    } catch (Exception e1) {
                        Log.w(TAG, "Failed to revoke payload " + developerPayload, e1);
                    }
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(final Exception e) {
                if (e != null) {
                    listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
                    Log.e(TAG, "Exception on checking purchase", e);
                }
            }

        }.execute();
    }

    /**
     * Let the IPv6Server generate the tunnels and activate the account. It should be called
     * after a Google purchase was successfully completed by that account.
     *
     * @param purchaseData  the String describing the purchase, provided by Google
     * @param dataSignature the String representing the signature to purchaseData, provided by Google
     */
    private void providePurchasedTunnels(String purchaseData, String dataSignature) {
        new AsyncTask<Pair<String, String>, Void, List<TicTunnel>>() {
            @Override
            protected List<TicTunnel> doInBackground(Pair<String, String>... params) {
                Pair<String, String> purchase = params[0];
                try {
                    List<TicTunnel> tunnels = subscriptionsClient.checkSubscriptionAndReturnTunnels(
                            purchase.first,
                            purchase.second
                    );
                    return tunnels;
                } catch (Exception e) {
                    Log.w(TAG, "Subscription information failed to verify", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(List<TicTunnel> newTunnels) {
                if (newTunnels != null) {
                    tunnels.addAll(newTunnels);
                    listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.HAS_TUNNELS);
                } else {
                    listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.TEMPORARY_PROBLEM);
                }
            }
        }.execute(new Pair<String, String>(purchaseData, dataSignature));
    }

    /**
     * The initiated purchase from Google has failed for whatever reason. Clean up
     * the generated intermediate account (identified by developerPayload).
     *
     * @param developerPayload the String identifying the temporary account that was generated
     *                         on initiation.
     */
    private void purchaseFailed(@Nullable final String developerPayload) {
        try {
            if (developerPayload != null)
                subscriptionsClient.deleteUnusedPayload(developerPayload);
        } catch (Exception e1) {
            Log.w(TAG, "Failed to revoke payload " + developerPayload, e1);
        }
        listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED);
    }

    public boolean handlePurchaseActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_BUY) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
                String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
                String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

                if (responseCode == RESPONSE_CODE_OK) {
                    listener.onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType.PURCHASE_COMPLETED);
                    Log.i(TAG, "Purchase succeeded!");
                    Log.d(TAG, purchaseData);
                    Log.d(TAG, dataSignature);
                    providePurchasedTunnels(purchaseData, dataSignature);
                } else {
                    Log.w(TAG, "Failed purchase, resultCode=" + resultCode + ", responseCode=" + responseCode);
                    String developerPayload = null;
                    try {
                        JSONObject jsonObject = new JSONObject(purchaseData);
                        developerPayload = jsonObject.getString("developerPayload");
                    } catch (JSONException e) {
                        Log.wtf(TAG, "Could not parse purchaseData string");
                    }
                    purchaseFailed(developerPayload);
                }
            } else {
                Log.w(TAG, "Failed purchase, resultCode=" + resultCode);
                purchaseFailed(null);
            }
            return true;
        } else
            return false;
    }

    public void destroy() {
        // very important:
        Log.d(TAG, "unbinding serviceConnection to Google InAppBilling.");
        if (service != null) {
            originatingContext.unbindService(serviceConn);
        }
    }
}
