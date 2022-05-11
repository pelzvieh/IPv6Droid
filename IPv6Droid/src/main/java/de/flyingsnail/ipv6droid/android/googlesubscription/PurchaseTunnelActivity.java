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

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.SettingsActivity;
import de.flyingsnail.ipv6droid.android.TunnelPersisting;
import de.flyingsnail.ipv6droid.android.TunnelPersistingFile;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * Guides the user through managing her subscriptions.
 */
public class PurchaseTunnelActivity
        extends AppCompatActivity
        implements SubscriptionCheckResultListener,
                    AdapterView.OnItemClickListener,
                    CertificationResultListener {
    private static final String TAG = PurchaseTunnelActivity.class.getSimpleName();
    private static final String ACTIVE_SKU_FRAGMENT_TAG = "active_sku_fragment";
    static final String PURCHASE_REQUEST_KEY = "purchaseRequest";
    public static final String CONSUME_REQUEST_KEY = "consume";
    static final String SKU_KEY = "sku";

    private ScheduledExecutorService executor;

    /** A private instance of PurchaseManager */
    private PurchaseManager purchaseManager;

    /**
     * A RecyclerView showing the list of SKUs.
     */
    private ListView productListView;

    private SlidingPaneLayout slidingPanes;

    /**
     * The Adapter providing the purchasable products to the ListView.
     */
    private SkuAdapter adapter;
    private Tunnels cachedTunnels;
    private boolean certificationRunning = false;
    private SkuDetails noSkuPlaceholder;

    public PurchaseTunnelActivity() {
        super(R.layout.activity_purchase_tunnel);
    }

    private class SkuAdapter extends BaseAdapter {

        List<SkuDetails> skuDetails;

        SkuAdapter() {
            notifyDataSetChanged();
        }

        private synchronized List<SkuDetails> getSkuDetails() {
            return skuDetails;
        }

        @Override
        public int getCount() {
            return getSkuDetails().size();
        }

        @Override
        public Object getItem(int i) {
            return getSkuDetails().get(i);
        }

        @Override
        public long getItemId(int i) {
            return getSkuDetails().get(i).getSku().hashCode();
        }

        @Override
        public boolean isEnabled(int position) {
            SkuDetails details = getSkuDetails().get(position);
            if (details == noSkuPlaceholder) {
                // the one special pane is always enabled
                return true;
            } else if (cachedTunnels.isEmpty() && activePurchase == null) {
                // enable all tabs if nothing is purchased
                return true;
            } else if (activePurchase != null) {
                // enable only the tab of an active purchase/subscription if any
                return activePurchase.getSkus().contains(getSkuDetails().get(position).getSku());
            } else {
                // enable all one-time purchase tabs to show cached valid tunnels
                return BillingClient.SkuType.INAPP.equals(getSkuDetails().get(position).getType());
            }
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (!(view instanceof TextView)) {
                view = getLayoutInflater().inflate(R.layout.sku_tab_template, viewGroup, false);
            }
            ((TextView)view).setText(getSkuDetails().get(i).getDescription());
            return view;
        }

        /**
         * Refresh the list of SKU. Call from UI thread.
         */
        @Override
        public synchronized void notifyDataSetChanged() {
            skuDetails = new ArrayList<>(purchaseManager.getSupportedSKUDetails());
            skuDetails.add(noSkuPlaceholder);
            super.notifyDataSetChanged();
        }
    }

    /** The result of the last action, i.e. current state of purchasing process */
    private @NonNull
    SubscriptionCheckResultListener.ResultType purchasingResult = SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY;

    /** The last diagnostic message, explaining a "negative" purchasingResult in detail. May be null. */
    private @Nullable
    String purchasingDebugMessage = null;

    /** A TextView showing reasons when purchasing info is probably unsatisfactory. */
    private TextView purchasingInfoDebug;

    /** The purchase that is currently valid and active, corresponding to the available tunnels. */
    private Purchase activePurchase = null;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Activity Life Cycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_tunnel);
        // init the noSkuPlaceholder; required on each onCreate because of internationalisation
        try {
            noSkuPlaceholder = new SkuDetails("{\"productId\": \"none\", \"type\": \"fake\", \"description\": \"" +
                    getString(R.string.description_no_billing) +
                    "\"}");
        } catch (JSONException e) {
            Log.wtf(TAG, e);
            throw new IllegalStateException("Hard coded json not interpreted on this platform", e);
        }

        Toolbar myToolbar = findViewById(R.id.subscriptionsToolbar);
        productListView = findViewById(R.id.sku_list);
        purchasingInfoDebug = findViewById(R.id.purchasingInfoDebug);
        slidingPanes = findViewById(R.id.sliding_panes);

        setSupportActionBar(myToolbar);

        TunnelPersisting tp = new TunnelPersistingFile(this.getApplicationContext());
        boolean cachedTunnelsAvailable;
        try {
            cachedTunnels = tp.readTunnels();
            // remove expired tunnels
            if (!cachedTunnels.checkCachedTunnelAvailability()) {
                for (TunnelSpec tunnel: cachedTunnels) {
                    if (!tunnel.isEnabled()) {
                        cachedTunnels.remove(tunnel);
                    }
                }
            }
            cachedTunnelsAvailable = !cachedTunnels.isEmpty();
        } catch (IOException e) {
            cachedTunnels = new Tunnels();
            cachedTunnelsAvailable = false;
            Log.i(TAG, "No tunnel list yet cached");
        }

        ActionBar supportActionBar = getActionBar();
        if (supportActionBar != null) {
            if (MainActivity.isConfigurationRequired(this, cachedTunnelsAvailable)) {
                supportActionBar.setIcon(R.drawable.ic_launcher);
            } else {
                supportActionBar.setDisplayHomeAsUpEnabled(true);
            }
        }
        executor = Executors.newScheduledThreadPool(1);

        purchasingResult = SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY;

        productListView.setOnItemClickListener(this);

        purchasingDebugMessage = null;

        // ensure early enabling state of ui elements, standard will be set below
        setUiStateManagerInitializing();

        // set standard UI state according current state (might have changed already...)
        displaySubscriptionState();

        // initialise PurchaseManager and Preferences
        startNewPurchaseManager();

        // listen for purchase requests from the active SKU fragment
        getSupportFragmentManager().setFragmentResultListener(PURCHASE_REQUEST_KEY, this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                // We use a String here, but any type that can be put in a Bundle is supported
                String requestedSku = bundle.getString(SKU_KEY);
                PurchaseManager myPurchaseManager = purchaseManager;
                if (myPurchaseManager != null) {
                    purchaseManager.initiatePurchase(requestedSku);
                } else {
                    Toast.makeText(PurchaseTunnelActivity.this,
                            "Sorry, internal error: purchaseManager lost",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });

        // listen for consume requests from the active one-time SKU fragment
        getSupportFragmentManager().setFragmentResultListener(CONSUME_REQUEST_KEY, this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                // We use a String here, but any type that can be put in a Bundle is supported
                String requestedSku = bundle.getString(SKU_KEY);
                if (activePurchase.getSkus().contains(requestedSku)) {
                    requestCertification(activePurchase);
                } else {
                    Toast.makeText(getApplicationContext(), "No purchase for requested SKU", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * This is used in onCreate as well as in scheduleRetry.
     */
    private void startNewPurchaseManager() {
        purchaseManager = new PurchaseManager(this, this);
        // build SKU list and set adapter
        adapter = new SkuAdapter();
        productListView.setAdapter(adapter);
    }

    /**
     * Destroy this activity. Destroys our PurchaseManager in turn.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "PurchaseTunnelActivity gets destroyed.");
        executor.shutdownNow();
        super.onDestroy();
        purchaseManager.destroy();
        purchaseManager = null;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Status update methods
    ////////////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Change display elements' enabled state according current subscription state,
     * set display of tunnels, validity and/or diagnostic messages as apt.
     */
    private void displaySubscriptionState() {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(this.getMainLooper());

        mainHandler.post(() -> {
            Log.d(TAG, "Updating display state with current state");
            if (adapter != null) {
                adapter.notifyDataSetChanged();
                final int refPosition = Math.max(productListView.getSelectedItemPosition(), 0);

                if (!adapter.isEnabled(refPosition)) {
                    Log.i(TAG, "Need to find a different selectable item");
                    boolean foundEnabled = false;
                    for (int item = 0; item < adapter.getCount(); item++) {
                        if (adapter.isEnabled(item)) {
                            Log.d(TAG, "Selecting item " + item);
                            productListView.setSelection(item);
                            foundEnabled = true;
                            break;
                        }
                    }
                    if (!foundEnabled) {
                        Log.i(TAG, "No enabled SKU tab");
                        productListView.setEnabled(false);
                        changeActiveSkuView(null);
                    }
                } else {
                    // the current selection is still valid, but we replace it by a new instance
                    changeActiveSkuView(
                            (SkuDetails) adapter.getItem(refPosition)
                    );
                }
                // set or hide detail view explaining the current state in detail
                if (purchasingDebugMessage != null) {
                    purchasingInfoDebug.setText(purchasingDebugMessage);
                    purchasingInfoDebug.setVisibility(View.VISIBLE);
                } else {
                    purchasingInfoDebug.setVisibility(View.GONE);
                }

                int nrTunnels = cachedTunnels.size();
                switch (purchasingResult) {
                    case HAS_TUNNELS:
                        setUiStateManagerYieldsTunnels();
                        break;
                    case NO_PURCHASES:
                    case TEMPORARY_PROBLEM:
                    case SUBSCRIPTION_UNPARSABLE:
                    case CHECK_FAILED:
                    case PURCHASE_FAILED:
                        setUiStateManagerReady();
                        break;
                    case NO_SERVICE_AUTO_RECOVERY:
                    case NO_SERVICE_TRY_AGAIN:
                        setUiStateManagerInitializing();
                        break;
                    case NO_SERVICE_PERMANENT:
                        setUiStateManagerNotAvailable();
                        break;
                    case PURCHASE_STARTED:
                        setUiStateIAmBusy();
                        break;
                    case PURCHASE_COMPLETED:
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
            }
        });
    }

    private void setUiStateManagerInitializing() {
        productListView.setEnabled(false);
        changeActiveSkuView(null);
        slidingPanes.open();
    }

    private void setUiStateManagerNotAvailable() {
        productListView.setEnabled(false);
        changeActiveSkuView(null);
        slidingPanes.open();
    }


    private void setUiStateManagerReady() {
        productListView.setEnabled(true);
        slidingPanes.close();
    }

    private void setUiStateIAmBusy() {
        productListView.setEnabled(false);
        slidingPanes.open();
    }

    private void setUiStateManagerYieldsTunnels() {
        try {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        } catch (NullPointerException e) {
            Log.d(TAG,  "No action bar");
        }
        for (SkuDetails skuDetails: adapter.getSkuDetails()) {
            if (activePurchase.getSkus().contains(skuDetails.getSku())) {
                changeActiveSkuView(skuDetails);
                slidingPanes.open();
                break;
            }
        }
    }

    /**
     * The callback method defined by SubscriptionCheckResultListener. This is used by our
     * PurchaseManager instance to report failure, success of Google/our server queries and
     * their result.
     * @param result a ResultType indicating success, technical difficulties and if tunnels are available.
     * @param debugMessage a String giving details why the given result is achieved
     */
    @Override
    public void onSubscriptionCheckResult(@NonNull SubscriptionCheckResultListener.ResultType result,
                                          @Nullable Purchase activePurchase,
                                          @Nullable String debugMessage) {
        purchasingResult = result;
        purchasingDebugMessage = debugMessage;
        this.activePurchase = activePurchase;
        switch (purchasingResult) {
            case HAS_TUNNELS:
                Log.i(TAG, "Subscription check says: you have tunnels");
                if (cachedTunnels == null || !cachedTunnels.isTunnelActive() && activePurchase != null) {
                    // ugh, we didn't know yet!
                    requestCertification(activePurchase);
                }
                break;

            case PURCHASE_COMPLETED:
                Log.i(TAG, "Subscription check says: a purchase is completed");
                if (activePurchase != null) {
                    requestCertification(activePurchase);
                } else {
                    Log.e(TAG, "Received PURCHASE_COMPLETED without activePurchase");
                }
                break;


            case NO_PURCHASES:
                /* this means: our stored tunnel list is master. No subscription implying
                   prolongation, prior purchases are consumed and reflected in the stored state.
                 */
                Log.i(TAG, "Subscription check says: all your products are consumed");
                displaySubscriptionState();
                break;

            case TEMPORARY_PROBLEM:
            case SUBSCRIPTION_UNPARSABLE:
            case NO_SERVICE_TRY_AGAIN:
            case CHECK_FAILED:
                // these are conditions that call for retrying
                Log.i(TAG, "Subscription check says: things went messy, try later");
                scheduleRetry();
                displaySubscriptionState();
                break;

            case NO_SERVICE_AUTO_RECOVERY:
            case NO_SERVICE_PERMANENT:
            case PURCHASE_FAILED:
            case PURCHASE_STARTED:
                // these are conditions that do not require programmatic action
                Log.i(TAG, "Subscription check says: uninteresting intermediate stuff happened");
                displaySubscriptionState();
                break;
            default:
                displaySubscriptionState();
                Log.w(TAG, "Unimplemented Result type from subscriptions manager");
                break;
        }
    }

    /**
     * Notify listener on a change of available SKU.
     *
     * @param knownSku a List of SkuDetails objects representing the now known list of SKU.
     */
    @Override
    public void onAvailableSkuUpdate(@NonNull List<SkuDetails> knownSku) {
        Log.i(TAG, "Sku update knownSku size: " + knownSku.size());
        adapter.notifyDataSetChanged();
        displaySubscriptionState();
    }

    synchronized private void requestCertification(Purchase activePurchase) {
        Log.i(TAG, "Requesting certificates for purchase");
        if (!certificationRunning) {
            final PurchaseToTunnel helper = new PurchaseToTunnel(this);
            certificationRunning = true;
            helper.certifyTunnelsForPurchase(activePurchase, this);
        }
    }

    @Override
    public void onCertificationRequestResult(
            @NonNull Purchase purchase,
            @NonNull List<TunnelSpec> tunnels,
            CertificationResultListener.ResultType resultType,
            @Nullable Exception e) {
        certificationRunning = false;
        switch(resultType) {
            case OK:
                Log.i(TAG, "successfully retrieved tunnels from purchase");
                purchaseManager.consumePurchase(purchase);
                Log.i(TAG, "Marked purchase as consumed");
                updateCachedTunnelList(tunnels);
                displaySubscriptionState();
                break;

            case TECHNICAL_FAILURE:
                Log.e(TAG, "Failed to retrieve tunnels for purchase", e);
                scheduleRetry();
                displaySubscriptionState();
                break;

            case PURCHASE_REJECTED:
                Log.e(TAG, "A purchase from local billing client seems to have been rejected", e);
                Toast.makeText(this, "Could not verify purchase", Toast.LENGTH_LONG).show();
                if (e != null) {
                    purchaseManager.revalidateCache(purchase, e);
                }
                break;
        }
    }

    /**
     * Called on certain failure states, this method schedules retrying of subscription check.
     */
    private void scheduleRetry() {
        PurchaseManager failedPurchaseManager = purchaseManager;
        if (failedPurchaseManager != null) { // not already destroyed...
            failedPurchaseManager.destroy();
        }
        executor.schedule(()-> {
            if (isDestroyed() ||
                purchasingResult.equals(SubscriptionCheckResultListener.ResultType.HAS_TUNNELS) ||
                purchasingResult.equals(SubscriptionCheckResultListener.ResultType.NO_PURCHASES)
            ) {
                Log.i(TAG, "Scheduled retry is obsolete");
            } else {
                Log.i(TAG, "Scheduled retry is launching a new instance of PurchaseManager");
                startNewPurchaseManager();
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * This will replace the tunnel list read from persistent cache by the tunnel list
     * received from PurchaseManager, plus will write the new list back to cache.
     */
    private void updateCachedTunnelList(List<TunnelSpec> subscribedTunnels) {
        // write tunnel list to cache
        TunnelPersisting tp = new TunnelPersistingFile(this.getApplicationContext());
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
     * Open Settings activity via intent.
     *
     * @param view the View that was clicked to call this method.
     */
    public void onOpenSettings (View view) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    /**
     * React on a tap of the user on one of the products in the sku_list view.
     * @param adapterView the AdapterView holding the SKU entries.
     * @param view the View of the SKU that the user tapped on
     * @param i an int giving the position of the entry in the list.
     * @param l a long giving the ID of the entry.
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (adapterView == productListView) {
            Log.i(TAG, "User clicked on item " + i);
            SkuDetails details = (SkuDetails)adapter.getItem(i);
            productListView.setSelection(i);
            changeActiveSkuView(details);
            slidingPanes.open();
        } else {
            Log.wtf(TAG, "Received itemClick event not from our item list");
        }
    }

    private void changeActiveSkuView(SkuDetails details) {
        if (details != null && BillingClient.SkuType.SUBS.equals(details.getType())) {
            Log.i(TAG, "Switching to subscription fragment for " + details.getSku());
            Bundle bundle = new Bundle();
            bundle.putString(SubscribeTunnelFragment.ARG_SKU, details.getSku());
            if (cachedTunnels != null)
                bundle.putSerializable(SubscribeTunnelFragment.ARG_TUNNELS, cachedTunnels);
            boolean subscribedToThisSKU =
                    activePurchase != null && activePurchase.getSkus().contains(details.getSku());
            bundle.putBoolean(SubscribeTunnelFragment.ARG_SUB_AVAILABLE, subscribedToThisSKU);
            bundle.putBoolean(SubscribeTunnelFragment.ARG_SUB_RENEWING,
                    subscribedToThisSKU && activePurchase.isAutoRenewing());
            getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.productpurchasecontainer, SubscribeTunnelFragment.class, bundle, ACTIVE_SKU_FRAGMENT_TAG)
                    .commit();
        } else if (details != null && BillingClient.SkuType.INAPP.equals(details.getType())) {
            Log.i(TAG, "Switching to (one-time) purchase fragment for " + details.getSku());
            Bundle bundle = new Bundle();
            bundle.putString(OneTimeTunnelFragment.ARG_SKU, details.getSku());
            if (cachedTunnels != null)
                bundle.putSerializable(OneTimeTunnelFragment.ARG_TUNNELS, cachedTunnels);
            boolean subscribedToThisSKU =
                    activePurchase != null && activePurchase.getSkus().contains(details.getSku());
            bundle.putBoolean(OneTimeTunnelFragment.ARG_PURCHASE_AVAILABLE, subscribedToThisSKU);
            final FragmentManager fragmentManager = getSupportFragmentManager();
            if (!fragmentManager.isDestroyed() && !fragmentManager.isStateSaved()) {
                fragmentManager
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.productpurchasecontainer, OneTimeTunnelFragment.class, bundle, ACTIVE_SKU_FRAGMENT_TAG)
                        .commit();
            }
        } else {
            Log.i(TAG, "Switching to no tunnel fragment");
            Bundle bundle = new Bundle();
            bundle.putString(NoTunnelFragment.ARG_MESSAGE, purchasingDebugMessage);
            final FragmentManager fragmentManager = getSupportFragmentManager();
            if (!fragmentManager.isDestroyed() && !fragmentManager.isStateSaved()) {
                fragmentManager
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.productpurchasecontainer, NoTunnelFragment.class, bundle, ACTIVE_SKU_FRAGMENT_TAG)
                        .commit();
            }
        }
    }
}
