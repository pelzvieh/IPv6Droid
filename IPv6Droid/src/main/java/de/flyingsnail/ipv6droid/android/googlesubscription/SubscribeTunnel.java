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
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.SettingsActivity;

public class SubscribeTunnel extends Activity {
    private static final String TAG = SubscribeTunnel.class.getSimpleName();

    private static final int RESULT_OK = 0;
    private static final int RC_BUY = 3;

    /**
     * The proxy implementing the Google InAppBillingService interface. Is set asynchronously
     * by binding serviceCon.
     */
    private IInAppBillingService service;

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
            purchaseButton.setEnabled(true); // we don't have a bound service yet

            try {
                subscriptions.addAll(getSubscriptions());
                purchasingInfoView.setText(R.string.user_has_subscription);
                purchasingInfoView.setTextColor(Color.BLACK);
            } catch (RemoteException re) {
                purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                purchasingInfoView.setTextColor(Color.RED);
                Log.e(TAG, "Could not read existing subscriptions");
            }
        }
    };


    private TextView purchasingInfoView;
    private Button purchaseButton;

    private Set<Subscription> subscriptions;

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

        subscriptions = new HashSet<Subscription>(1);
        purchaseButton.setEnabled(false); // we don't have a bound service yet

        Intent serviceIntent =
                new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);

    }

    /** Retrieve the active subscriptions by querying google play, then resolving the SKU list */
    private Set<Subscription> getSubscriptions() throws RemoteException {
        Bundle activeSubs = service.getPurchases(3, getPackageName(),
                "subs", null);
        HashSet<Subscription> result = new HashSet<Subscription>(1);
        if (activeSubs.getInt("RESPONSE_CODE") == RESULT_OK) {
            List<String> skus = activeSubs.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
            List<String> skuData = activeSubs.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
            List<String> skuSignature = activeSubs.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");

            for (int index = 0; index < skus.size(); index++) {
                Subscription subscription = Subscription.create(skus.get(index));
                if (subscription != null) {
                    subscription.setData(skuData.get(index));
                    subscription.setSignature(skuSignature.get(index));
                    result.add(subscription);
                }
            }
            // TODO verify purchases by call to IPv6Server.
            // TODO set preferences accordingly, using developerPayload as username
            // SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        }
        return result;
    }

    public void onPurchaseSubsciption (View clickedView) throws RemoteException, IntentSender.SendIntentException {
        if (service != null) {
            // TODO call IPv6Server to generate a new username, pass this as developerPayload
            String developerPayload = "GoogleSubscriber-000-TEST";
            Bundle bundle = service.getBuyIntent(3, getClass().getPackage().getName(),
                    Subscription.getSupportedSku().get(0), "subs", developerPayload);

            PendingIntent pendingIntent = bundle.getParcelable("BUY_INTENT");
            if (bundle.getInt("RESPONSE_CODE") == RESULT_OK && pendingIntent != null) {
                // Start purchase flow (this brings up the Google Play UI).
                // Result will be delivered through onActivityResult().
                startIntentSenderForResult(pendingIntent.getIntentSender(), RC_BUY, new Intent(),
                        Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0));
            }
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

            if (resultCode == RESULT_OK && responseCode == RESULT_OK) {
                // TODO refactor, this code is duplicated in Subscription
                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");
                    Subscription subscription = Subscription.create(sku);
                    // TODO call IPv6Server to verify purchase signature

                    if (subscription != null) {
                        purchasingInfoView.setText(R.string.user_has_subscription);
                        purchasingInfoView.setTextColor(Color.BLACK);
                        subscription.setData(purchaseData);
                        subscription.setSignature(dataSignature);
                        subscriptions.add(subscription);
                    } else
                        purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                }
                catch (JSONException e) {
                    purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                    purchasingInfoView.setTextColor(Color.RED);
                    Log.wtf(TAG, "Cannot parse JSON response from Google on subscription status", e);
                }
            } else {
                purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                purchasingInfoView.setTextColor(Color.RED);
                Log.e(TAG, "Error reading subscription status");
            }
        }
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
