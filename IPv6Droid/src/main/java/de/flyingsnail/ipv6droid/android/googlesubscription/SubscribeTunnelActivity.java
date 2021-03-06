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
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.SettingsActivity;
import de.flyingsnail.ipv6droid.android.TunnelPersisting;
import de.flyingsnail.ipv6droid.android.TunnelPersistingFile;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

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

    /** A TextView showing reasons when purchasing info is probably unsatisfactory. */
    private TextView purchasingInfoDebug;

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

    /** The result of the last action, i.e. current state of purchasing process */
    private @NonNull ResultType purchasingResult = ResultType.NO_SERVICE_AUTO_RECOVERY;

    /** The last diagnostic message, explaining a "negative" purchasingResult in detail. May be null. */
    private @Nullable String purchasingDebugMessage = null;

    /** the JobId passed to JobScheduler if we do automated retry. */
    public final static int RETRY_JOB_ID = 0xdeadaffe;


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Activity Life Cycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
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

        purchasingInfoView = findViewById(R.id.subscriptionStatus);
        purchaseButton = findViewById(R.id.subscribe);
        validUntil = findViewById(R.id.validUntil);
        acceptConditions = findViewById(R.id.acceptTerms);
        validUntilLine = findViewById(R.id.validUntilLine);
        purchasingInfoDebug = findViewById(R.id.purchasingInfoDebug);

        purchasingDebugMessage = null;
        purchasingResult = ResultType.NO_SERVICE_AUTO_RECOVERY;

        // ensure early enabling state of ui elements, standard will be set below
        purchasingInfoView.setText(R.string.user_subscription_checking);
        setUiStateManagerInitializing();

        // initialise SubscriptionManager and Perferences
        myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        startNewSubscriptionManager();

        // set standard UI state according current state (might have changed already...)
        displaySubscriptionState();
    }

    /**
     * This is used in onCreate as well as in scheduleRetry.
     */
    private void startNewSubscriptionManager() {
        subscriptionManager = new SubscriptionManager(this, this);
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


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Status update methods
    ////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Change display elements' enabled state according current subscription state,
     * set display of tunnels, validity and/or diganostic messages as apt.
     */
    private void displaySubscriptionState() {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(this.getMainLooper());

        mainHandler.post(() -> {
            final SubscriptionManager mySubscriptionManager = subscriptionManager;

            if (mySubscriptionManager != null) { // check if parent object wasn't destroyed
                Log.d(TAG, "Updating display state with current state");
                // set or hide detail view explaining the current state in detail
                if (purchasingDebugMessage != null) {
                    purchasingInfoDebug.setText(purchasingDebugMessage);
                    purchasingInfoDebug.setVisibility(View.VISIBLE);
                } else {
                    purchasingInfoDebug.setVisibility(View.GONE);
                }

                int nrTunnels = mySubscriptionManager.getTunnels().size();
                switch (purchasingResult) {
                    case HAS_TUNNELS:
                        if (nrTunnels > 0) {
                            purchasingInfoView.setText(
                                    String.format(getString(R.string.user_has_subscription), nrTunnels)
                            );
                            setUiStateManagerYieldsTunnels();
                        } else { // should not happen at all
                            Log.wtf(TAG, "Process status is HAS_TUNNELS/PURCHASE_COMPLETE, but there are no tunnels in SubscriptionManager!");
                            purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                            setUiStateManagerReady();
                        }
                        break;
                    case NO_SUBSCRIPTIONS:
                        setUiStateManagerReady();
                        purchasingInfoView.setText(R.string.user_not_subscribed);
                        break;
                    case TEMPORARY_PROBLEM:
                        setUiStateManagerReady();
                        purchasingInfoView.setText(R.string.technical_problem);
                        break;
                    case SUBSCRIPTION_UNPARSABLE:
                        setUiStateManagerReady();
                        purchasingInfoView.setText(R.string.user_has_unparsable_subscription_status);
                        break;
                    case NO_SERVICE_AUTO_RECOVERY:
                    case NO_SERVICE_TRY_AGAIN:
                        setUiStateManagerInitializing();
                        purchasingInfoView.setText(R.string.user_subscription_no_service);
                        break;
                    case NO_SERVICE_PERMANENT:
                        setUiStateManagerNotAvailable();
                        purchasingInfoView.setText(R.string.user_subscription_no_service_on_this_device);
                        break;
                    case CHECK_FAILED:
                        setUiStateManagerReady();
                        purchasingInfoView.setText(R.string.user_subscription_failed);
                        break;
                    case PURCHASE_FAILED:
                        setUiStateManagerReady();
                        purchasingInfoView.setText(R.string.user_subscription_aborted);
                        break;
                    case PURCHASE_STARTED:
                        setUiStateIAmBusy();
                        purchasingInfoView.setText(R.string.subscriptionPurchaseStarted);
                        break;
                    case PURCHASE_COMPLETED:
                        purchasingInfoView.setText(getString(R.string.user_subscription_purchase_done));
                        if (nrTunnels > 0) {
                            setUiStateManagerYieldsTunnels();
                        } else {
                            setUiStateIAmBusy();
                        }
                        break;
                    default:
                        Log.w(TAG, "Unimplemented Result type from subscriptions manager");
                        break;
                }
            } // if the parent object has not been destroy'ed yet
        });
    }

    private void setUiStateManagerInitializing() {
        acceptConditions.setEnabled(false); // we don't have a bound service yet
        purchaseButton.setEnabled(false);
        validUntilLine.setVisibility(View.GONE);
    }

    private void setUiStateManagerNotAvailable() {
        acceptConditions.setEnabled(false); // we will never have a bound service
        purchaseButton.setEnabled(false);
        validUntilLine.setVisibility(View.GONE);
    }


    private void setUiStateManagerReady() {
        acceptConditions.setEnabled(true);
        purchaseButton.setEnabled(acceptConditions.isChecked());
        validUntilLine.setVisibility(View.GONE);
    }

    private void setUiStateIAmBusy() {
        acceptConditions.setEnabled(false);
        purchaseButton.setEnabled(false);
    }

    private void setUiStateManagerYieldsTunnels() {
        purchaseButton.setEnabled(false);
        acceptConditions.setEnabled(false);
        acceptConditions.setChecked(true);
        Date validUntilDate = subscriptionManager.getTunnels().get(0).getExpiryDate();
        validUntil.setText(
                SimpleDateFormat.getDateInstance(
                        SimpleDateFormat.SHORT
                ).format(validUntilDate)
        );
        validUntilLine.setVisibility(View.VISIBLE);
    }

    /**
     * The callback method defined by SubscriptionCheckResultListener. This is used by our
     * SubscriptionManager instance to report failure, success of Google/our server queries and
     * their result.
     * @param result a ResultType indicating success, technical difficulties and if tunnels are available.
     * @param debugMessage a String giving details why the given result is achieved
     */
    @Override
    public void onSubscriptionCheckResult(ResultType result, String debugMessage) {
        purchasingResult = result;
        purchasingDebugMessage = debugMessage;
        switch (purchasingResult) {
            case HAS_TUNNELS:
            case NO_SUBSCRIPTIONS:
                // update caches
                updatePreferences();
                updateCachedTunnelList();
                break;

            case TEMPORARY_PROBLEM:
            case SUBSCRIPTION_UNPARSABLE:
            case NO_SERVICE_TRY_AGAIN:
            case CHECK_FAILED:
                // these are conditions that call for retrying
                scheduleRetry(this);
                break;

            case NO_SERVICE_AUTO_RECOVERY:
            case NO_SERVICE_PERMANENT:
            case PURCHASE_FAILED:
            case PURCHASE_STARTED:
            case PURCHASE_COMPLETED:
                // these are conditions that do not require programmatic action
                break;
            default:
                Log.w(TAG, "Unimplemented Result type from subscriptions manager");
                break;
        }
        displaySubscriptionState();
    }

    /**
     * Called on certain failure states, this method schedules retrying of subscription check.
     */
    static private void scheduleRetry(SubscribeTunnelActivity instance) {
        SubscriptionManager failedSubscriptionManager = instance.subscriptionManager;
        if (failedSubscriptionManager != null) { // not already destroyed...
            failedSubscriptionManager.destroy();
        }
        new AsyncTask<SubscribeTunnelActivity, Void, Void>() {
            @Override
            protected Void doInBackground(SubscribeTunnelActivity... subscribeTunnelActivities) {
                try {
                    Thread.sleep(30000L);
                    for (SubscribeTunnelActivity subscribeTunnelActivity: subscribeTunnelActivities) {
                        if (subscribeTunnelActivity == null ||
                                subscribeTunnelActivity.isDestroyed() ||
                                subscribeTunnelActivity.purchasingResult.equals(ResultType.HAS_TUNNELS) ||
                                subscribeTunnelActivity.purchasingResult.equals(ResultType.NO_SUBSCRIPTIONS)) {
                            Log.i(TAG, "Scheduled retry is obsolete");
                        } else {
                            Log.i(TAG, "Scheduled retry is launching a new instance of SubscriptionManager");
                            subscribeTunnelActivity.startNewSubscriptionManager();
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for retry", e);
                }
                return null;
            }
        }.execute(instance, null, null);
    }


    /**
     * This will replace the tunnel list read from persistent cache by the tunnel list
     * received from SubscriptionManager, plus will write the new list back to cache.
     */
    private void updateCachedTunnelList() {
        List<TicTunnel> subscribedTunnels;
        SubscriptionManager mySubscriptionManager = subscriptionManager;
        if (mySubscriptionManager == null || this.isDestroyed()) {
            return; // this Activity is already destroyed
        } else {
            subscribedTunnels = mySubscriptionManager.getTunnels();
        }
        // write tunnel list to cache
        TunnelPersisting tp = new TunnelPersistingFile(this.getApplicationContext());
        Tunnels cachedTunnels = new Tunnels();
        try {
            cachedTunnels = tp.readTunnels();
        } catch (IOException e) {
            Log.i(TAG, "No tunnel list yet cached");
        }
        cachedTunnels.replaceTunnelList(subscribedTunnels);
        try {
            tp.writeTunnels(cachedTunnels);
        } catch (IOException e) {
            Log.e(TAG, "Could not write cached tunnel list", e);
        }
    }

    /**
     * Update configuration of old-style TIC authentication to reflect that they are irrelevant.
     * Calls for a better solution.
     */
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

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // User action callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * User clicked the "purchase" button. We're checking state, and initiating Google purchase.
     * @param clickedView the button UI element
     */
    public void onPurchaseSubsciption (final View clickedView) {
        if (!acceptConditions.isChecked()) {
            purchaseButton.setEnabled(false);
            Toast.makeText(this, R.string.user_subscription_error_not_accepted, Toast.LENGTH_LONG).show();
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

    /**
     * User clicked the "manage subscriptions" button. We're constructing the magic Google URL
     * and launching the required intent.
     * @param clickedView the clicked UI element
     */
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
            Toast.makeText(this, R.string.user_subscription_management_not_launched, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Open Settings activity via intent.
     *
     * @param view the View that was clicked to call this method.
     */
    public void onOpenSettings (View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    /**
     * Handler if user clicked the accept terms checkbox
     * @param clickedView the view that the user actually clicked on
     */
    public void onAcceptTerms(View clickedView) {
        displaySubscriptionState();
    }
}
