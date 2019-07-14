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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.SettingsActivity;
import de.flyingsnail.ipv6droid.android.TunnelPersisting;
import de.flyingsnail.ipv6droid.android.TunnelPersistingFile;
import de.flyingsnail.ipv6droid.android.Tunnels;

import static de.flyingsnail.ipv6droid.android.googlesubscription.Subscription.GOOGLESUBSCRIPTION;

/**
 * Guides the user through managing her subscriptions.
 */
public class SubscribeTunnelActivity extends Activity implements SubscriptionCheckResultListener {
    private static final String TAG = SubscribeTunnelActivity.class.getSimpleName();

    private static final int RESPONSE_CODE_OK = 0;
    private static final int RC_BUY = 3;

    /** A private instance of SubscriptionManager */
    private SubscriptionManager subscriptionManager;

    /** A TextView showing user-readable information about subscription status */
    private TextView purchasingInfoView;

    /** A Button to initiate a purchase */
    private Button purchaseButton;

    /** A TextView to show the end of current subscription period. */
    private TextView validUntil;

    /** A Checkbox that shows if user accepted terms and conditions */
    private CheckBox acceptConditions;

    /** A layout containing the views to show valid until information, incl. label */
    private View validUntilLine;

    /** The app preferences */
    private SharedPreferences myPreferences;


    private void displayActiveSubscriptions() {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(this.getMainLooper());

        Runnable updateView = new Runnable() {
            @Override
            public void run() {
                final SubscriptionManager mySubscriptionManager = subscriptionManager;
                if (mySubscriptionManager != null) { // check if parent object wasn't destroyed
                    Log.d(TAG, "Updating display state with current state");
                    int nrTunnels = mySubscriptionManager.getTunnels().size();
                    if (nrTunnels > 0) {
                        purchasingInfoView.setText(String.format(getString(R.string.user_has_subscription), nrTunnels));
                        purchaseButton.setEnabled(false);
                        acceptConditions.setEnabled(false);
                        acceptConditions.setChecked(true);
                        Date validUntilDate = mySubscriptionManager.getTunnels().get(0).getExpiryDate();
                        validUntil.setText(
                                SimpleDateFormat.getDateInstance(
                                        SimpleDateFormat.SHORT
                                ).format(validUntilDate)
                        );
                        validUntilLine.setVisibility(View.VISIBLE);

                        // update caches
                        updatePreferences();
                        updateCachedTunnelList();
                    } else {
                        purchasingInfoView.setText(R.string.user_not_subscribed);
                        purchaseButton.setEnabled(acceptConditions.isChecked());
                        validUntilLine.setVisibility(View.GONE);
                    }
                } // if the parent object has not been destroy'ed yet
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
        cachedTunnels.replaceTunnelList(subscriptionManager.getTunnels());
        try {
            tp.writeTunnels(cachedTunnels);
        } catch (IOException e) {
            Log.e(TAG, "Could not write cached tunnel list", e);
        }
    }

    private void updatePreferences() {
        // write username, server name and password preferences derived from the subscription
        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);
        SharedPreferences.Editor editor = myPreferences.edit();
        //TODO chunk and cheapness!
        editor.putString(MainActivity.TIC_USERNAME, GOOGLESUBSCRIPTION);
        editor.putString(MainActivity.TIC_PASSWORD, GOOGLESUBSCRIPTION);
        editor.putString(MainActivity.TIC_HOST, GOOGLESUBSCRIPTION);
        editor.apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscribe_tunnel);
        try {
            //noinspection ConstantConditions
            getActionBar().setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException npe) {
            Log.d(TAG, "No action bar", npe);
        }

        purchasingInfoView = (TextView)findViewById(R.id.subscriptionStatus);
        purchaseButton = (Button) findViewById(R.id.subscribe);
        validUntil = (TextView)findViewById(R.id.validUntil);
        acceptConditions = (CheckBox)findViewById(R.id.acceptTerms);
        validUntilLine = findViewById(R.id.validUntilLine);

        purchasingInfoView.setText(R.string.user_subscription_checking);
        purchaseButton.setEnabled(false); // we don't have a bound service yet
        validUntilLine.setVisibility(View.GONE);

        subscriptionManager = new SubscriptionManager(this, this);
        myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    public void onPurchaseSubsciption (final View clickedView) {
        if (!acceptConditions.isChecked()) {
            Toast.makeText(this, R.string.user_subscription_error_not_accepted, Toast.LENGTH_LONG);
            return;
        }
        purchaseButton.setEnabled(false); // gegen ungeduldige Benutzer
        acceptConditions.setEnabled(false); // jetzt ist er gefangen...
        SubscriptionManager mySubscriptionManager = this.subscriptionManager; // avoid race condition
        if (mySubscriptionManager != null) {
            purchasingInfoView.setText(R.string.user_subscription_starting_wizard);
            mySubscriptionManager.initiatePurchase();
        }
    }

    public void onManageSubscriptions (final View clickedView) {
        String UriTemplate = "https://play.google.com/store/account/subscriptions?sku=%s&package=%s";

        try {
            startActivity(
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                    String.format(
                                            UriTemplate,
                                            SubscriptionBuilder.SKU_TUNNEL_SUBSCRIPTION,
                                            getApplicationContext().getPackageName()
                                    )
                            )
                    )
            );
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.user_subscription_management_not_launched, Toast.LENGTH_LONG);
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


    /**
     * The callback method defined by SubscriptionCheckResultListener. This is used by our
     * SubscriptionManager instance to report failure, success of Google/our server queries and
     * their result.
     * @param result a ResultType indicating success, technical difficulties and if tunnels are available.
     */
    @Override
    public void onSubscriptionCheckResult(ResultType result) {
        switch (result) {
            case HAS_TUNNELS:
                displayActiveSubscriptions();
                break;
            case TEMPORARY_PROBLEM:
                purchasingInfoView.setText(R.string.technical_problem);
                break;
            case SUBSCRIPTION_UNPARSABLE:
                purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                break;
            case NO_SERVICE:
                purchasingInfoView.setText(R.string.user_subscription_failed);
                purchaseButton.setEnabled(false); // we don't have a bound service anymore
                break;
            case CHECK_FAILED:
                purchasingInfoView.setText(R.string.user_subscription_failed);
                break;
            case PURCHASE_FAILED:
                purchasingInfoView.setText(R.string.user_subscription_aborted);
                purchaseButton.setEnabled(true);
                break;
            case PURCHASE_COMPLETED:
                purchasingInfoView.setText(R.string.user_subscription_purchase_done);
                break;
            case NO_SUBSCRIPTIONS:
                displayActiveSubscriptions();
                break;
            default:
                Log.w(TAG, "Unimplemented Result type from subscriptions manager");
                break;
        }
    }


    /**
     * Destroy this activity. Destroys our SubscriptionManager in turn.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "SubscribeTunnelActivity gets destroyed.");
        super.onDestroy();
        subscriptionManager.destroy();
        subscriptionManager = null;
    }

}
