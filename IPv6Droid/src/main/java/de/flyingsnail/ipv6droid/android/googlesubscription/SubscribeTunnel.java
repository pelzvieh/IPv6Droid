/*
 * Copyright (c) 2016 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */
package de.flyingsnail.ipv6droid.android.googlesubscription;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.SettingsActivity;
import de.flyingsnail.ipv6droid.android.TunnelPersisting;
import de.flyingsnail.ipv6droid.android.TunnelPersistingFile;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;
import de.flyingsnail.ipv6server.restapi.SubscriptionsApi;

public class SubscribeTunnel extends Activity {
    private static final String TAG = SubscribeTunnel.class.getSimpleName();

    private static final int RESPONSE_CODE_OK = 0;
    private static final int RC_BUY = 3;

    /**
     * The client representing the SubscrptionsApi of the IPv6Server.
     */
    SubscriptionsApi subscriptionsClient = RestProxyFactory.createSubscriptionsClient();


    /**
     * The proxy implementing the Google InAppBillingService interface. Is set asynchronously
     * by binding serviceCon.
     */
    private IInAppBillingService service;
    private TextView purchasingInfoView;
    private Button purchaseButton;

    /**
     * A list of TicTunnels associated with the current user's subscriptions
     */
    private List<TicTunnel> tunnels;


    /**
     * An instance implementing ServiceConnection by setting this object's service class when bound.
     */
    ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            purchaseButton.setEnabled(false); // we don't have a bound service yet
        }

        @Override
        public void onServiceConnected(ComponentName name,
                                       IBinder serviceBind) {
            service = IInAppBillingService.Stub.asInterface(serviceBind);
            purchaseButton.setEnabled(true);

            tunnels.clear();
            try {
                getTunnelsFromSubscription();
            } catch (RemoteException e) {
                purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                purchasingInfoView.setTextColor(Color.RED);
                Log.e(TAG, "Could not read existing subscriptions");
            }
        }
    };

    private void displayActiveSubscriptions() {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(this.getMainLooper());

        Runnable updateView = new Runnable() {
            @Override
            public void run() {
                if(tunnels.size() > 0) {
                    purchasingInfoView.setText(String.format(getString(R.string.user_has_subscription), tunnels.size()));
                    purchasingInfoView.setTextColor(Color.BLACK);
                    purchaseButton.setEnabled(false);

                    updatePreferences();

                    updateCachedTunnelList();
                } else {
                    purchasingInfoView.setText(R.string.user_not_subscribed);
                    purchasingInfoView.setTextColor(Color.BLACK);
                    purchaseButton.setEnabled(true);
                }
            } // This is your code
        };
        mainHandler.post(updateView);
    }

    private void updateCachedTunnelList() {
        // write tunnel list to cache
        TunnelPersisting tp = new TunnelPersistingFile(this.getApplicationContext());
        Tunnels cachedTunnels = new Tunnels();
        try {
            cachedTunnels = tp.readTunnels();
        } catch (IOException e) {
            Log.i(TAG, "No tunnel list yet cached");
        }
        cachedTunnels.replaceTunnelList(tunnels);
        try {
            tp.writeTunnels(cachedTunnels);
        } catch (IOException e) {
            Log.e(TAG, "Could not write cached tunnel list", e);
        }
    }

    private void updatePreferences() {
        // write username, server name and password preferences derived from the subscription
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = myPreferences.edit();
        //TODO chunk and cheapness!
        editor.putString(MainActivity.TIC_USERNAME, "<googlesubscription>");
        editor.putString(MainActivity.TIC_PASSWORD, "<googlesubscription>");
        editor.putString(MainActivity.TIC_HOST, "<googlesubscription>");
        editor.apply();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscribe_tunnel);
        try {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException npe) {
            Log.d(TAG, "No action bar", npe);
        }

        purchasingInfoView = (TextView)findViewById(R.id.subscriptionStatus);
        purchaseButton = (Button) findViewById(R.id.subscribe);

        tunnels = new ArrayList<TicTunnel>();
        purchaseButton.setEnabled(false); // we don't have a bound service yet

        Intent serviceIntent =
                new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);

    }

    /** Retrieve the active subscriptions by querying google play, then resolving the SKU list */
    private void getTunnelsFromSubscription() throws RemoteException {
        purchasingInfoView.setText(R.string.user_subscription_checking);
        purchasingInfoView.setTextColor(Color.BLACK);
        purchaseButton.setEnabled(false); // we don't want the user to purchase while we're checking
        boolean foundRelevantSubscription = false; // if we're through the subscriptions w/o a tunnel subscription, we offer to subscribe

        String continuationToken = null;
        do {
            // loop on INAPP_CONTINUATION_TOKEN
            Bundle activeSubs = service.getPurchases(3, getPackageName(),
                    "subs", continuationToken);
            if (activeSubs.getInt("RESPONSE_CODE") == RESPONSE_CODE_OK) {
                final List<String> skus = activeSubs.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                final List<String> skuData = activeSubs.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                final List<String> skuSignature = activeSubs.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                continuationToken = activeSubs.getString("INAPP_CONTINUATION_TOKEN", null);

                purchasingInfoView.setText(R.string.user_subscription_verifying);

                // create api stub
                for (int index = 0; index < skus.size(); index++) {
                    Log.d(TAG, "Examining index " + index + ",\n SKU " + skus.get(index)
                            + ",\n Data '" + skuData.get(index) + "',\n signature '" + skuSignature.get(index));
                    try {
                        // @TODO extract to ayiya package, handle skuData and skuSignature as alternative credentials
                        if (SubscriptionBuilder.getSupportedSku().contains(skus.get(index))) {
                            // this one is relevant!
                            foundRelevantSubscription = true;
                            new AsyncTask<Integer, Void, Exception>() {
                                @Override
                                protected Exception doInBackground(Integer... params) {
                                    int index = params[0];
                                    List<TicTunnel> tunnels = null;
                                    try {
                                        tunnels = subscriptionsClient.checkSubscriptionAndReturnTunnels(
                                                skuData.get(index),
                                                skuSignature.get(index)
                                        );
                                        SubscribeTunnel.this.tunnels.addAll(tunnels);
                                        displayActiveSubscriptions();
                                    } catch (RuntimeException e) {
                                        Log.e(TAG, "Cannot verify subscription", e);
                                        return e;
                                    }
                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Exception e) {
                                    if (e != null) {
                                        purchasingInfoView.setTextColor(Color.RED);

                                        if (e instanceof IOException || e instanceof RuntimeException) {
                                            purchasingInfoView.setText(R.string.technical_problem);
                                        } else {
                                            purchasingInfoView.setText(String.format(getString(R.string.user_subscription_failed), e.getMessage()));
                                        }
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
        displayActiveSubscriptions();
    }

    public void onPurchaseSubsciption (View clickedView) throws RemoteException, IntentSender.SendIntentException {
        if (service != null) {
            purchaseButton.setEnabled(false); // gegen ungeduldige Benutzer
            purchasingInfoView.setText(R.string.user_subscription_starting_wizard);

            new AsyncTask<Void, Void, Exception>() {
                @Override
                protected Exception doInBackground(Void... voids) {
                    try {
                        String developerPayload = subscriptionsClient.createNewPayload();
                        Bundle bundle = service.getBuyIntent(3, getPackageName(),
                                SubscriptionBuilder.getSupportedSku().get(0), "subs", developerPayload);

                        PendingIntent pendingIntent = bundle.getParcelable("BUY_INTENT");
                        if (bundle.getInt("RESPONSE_CODE") == RESPONSE_CODE_OK && pendingIntent != null) {
                            // Start purchase flow (this brings up the Google Play UI).
                            // Result will be delivered through onActivityResult().
                            startIntentSenderForResult(pendingIntent.getIntentSender(), RC_BUY, new Intent(),
                                    Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0));
                        }
                    } catch (Exception e) {
                        return e;
                    }
                    return null;
                }
                @Override
                protected void onPostExecute(Exception e) {
                    if (e != null) {
                        purchasingInfoView.setText(R.string.technical_problem);
                        purchasingInfoView.setTextColor(Color.RED);
                        purchaseButton.setEnabled(true);
                    }
                }

            }.execute();
        }
    }

    /**
     * Open Settings activity via intent .
     *
     * @param view the View that was clicked to call this method.
     */
    public void onOpenSettings (View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_BUY) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == Activity.RESULT_OK && responseCode == RESPONSE_CODE_OK) {
                purchasingInfoView.setText(R.string.user_subscription_purchase_done);
                Log.i(TAG, "Purchase succeeded!");
                Log.d(TAG, purchaseData);
                Log.d(TAG, dataSignature);
                List<TicTunnel> tunnels = null;
                try {
                    tunnels = subscriptionsClient.checkSubscriptionAndReturnTunnels(
                            purchaseData,
                            dataSignature
                    );
                    this.tunnels.addAll(tunnels);
                    displayActiveSubscriptions();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Subscription information failed to verify", e);
                    purchasingInfoView.setText(R.string.technical_problem);
                    purchasingInfoView.setTextColor(Color.RED);
                }
            } else {
                Log.w(TAG, "Failed purchase, resultCode=" + resultCode + ", responseCode=" + responseCode);
                purchasingInfoView.setText(R.string.user_subscription_aborted);
                purchasingInfoView.setTextColor(Color.RED);
                purchaseButton.setEnabled(true);
            }
        } else
            Log.wtf(TAG, "Activity result for unknown request type: " + requestCode);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "SubscribeTunnel gets destroyed.");
        super.onDestroy();

        // very important:
        Log.d(TAG, "unbinding serviceConnection to Google InAppBilling.");
        if (service != null) {
            unbindService(serviceConn);
        }
    }

}
