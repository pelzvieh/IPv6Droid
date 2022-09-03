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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.MainActivity;
import de.flyingsnail.ipv6droid.android.SettingsActivity;
import de.flyingsnail.ipv6droid.android.Tunnels;

/**
 * Guides the user through managing her subscriptions.
 */
public class PurchaseTunnelActivity
        extends AppCompatActivity
        implements AdapterView.OnItemClickListener {
    private static final String TAG = PurchaseTunnelActivity.class.getSimpleName();
    private static final String ACTIVE_SKU_FRAGMENT_TAG = "active_sku_fragment";

    /**
     * A RecyclerView showing the list of SKUs.
     */
    private ListView productListView;

    private SlidingPaneLayout slidingPanes;

    /**
     * The Adapter providing the purchasable products to the ListView.
     */
    private SkuAdapter adapter;

    private String noSkuPlaceholder;

    private PurchaseTunnelViewModel sharedViewModel;

    private class SkuAdapter extends BaseAdapter {

        List<ProductDetails> skuDetails;

        SkuAdapter() {
            notifyDataSetChanged();
        }

        List<ProductDetails> getProductDetails() {
            return Collections.unmodifiableList(skuDetails);
        }

        @Override
        public int getCount() {
            return skuDetails.size() + 1;
        }

        @Override
        public Object getItem(int i) {
            if (i < skuDetails.size()) {
                return skuDetails.get(i);
            } else {
                return noSkuPlaceholder;
            }
        }

        @Override
        public long getItemId(int i) {
            if (i < skuDetails.size()) {
                return skuDetails.get(i).getProductId().hashCode();
            } else {
                return 0L;
            }
        }

        @Override
        public boolean isEnabled(int position) {
            if (position < skuDetails.size()) {
                ProductDetails details = skuDetails.get(position);
                final Tunnels tunnels = sharedViewModel.getTunnels().getValue();
                final Purchase activePurchase = sharedViewModel.getActivePurchase().getValue();
                if ((tunnels == null || tunnels.isEmpty()) && activePurchase == null) {
                    // enable all tabs if nothing is purchased
                    return true;
                } else if (activePurchase != null) {
                    // enable only the tab of an active purchase/subscription if any
                    return activePurchase.getProducts().contains(details.getProductId());
                } else {
                    // enable all one-time purchase tabs to show cached valid tunnels
                    return BillingClient.ProductType.INAPP.equals(details.getProductType());
                }
            } else {
                // the one special pane is always enabled
                return true;
            }
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (!(view instanceof TextView)) {
                view = getLayoutInflater().inflate(R.layout.sku_tab_template, viewGroup, false);
            }
            if (i < skuDetails.size()) {
                ((TextView) view).setText(skuDetails.get(i).getDescription());
            } else {
                ((TextView) view).setText(R.string.description_no_billing);
            }
            if (i == productListView.getSelectedItemPosition()) {
                view.setBackgroundColor(getColor(R.color.material_on_background_emphasis_high_type));
            } else {
                view.setBackgroundColor(getColor(R.color.cardview_light_background));
            }
            return view;
        }

        /**
         * Refresh the list of SKU. Call from UI thread.
         */
        @Override
        public synchronized void notifyDataSetChanged() {
            final List<ProductDetails> newSkuDetails = sharedViewModel.getProductDetailList().getValue();
            skuDetails = newSkuDetails == null ?
                    new ArrayList<>(1)
                    : new ArrayList<>(newSkuDetails);
            super.notifyDataSetChanged();
        }
    }

    public PurchaseTunnelActivity() {
        super(R.layout.activity_purchase_tunnel);
    }


    /** A TextView showing reasons when purchasing info is probably unsatisfactory. */
    private TextView purchasingInfoDebug;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Activity Life Cycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_tunnel);
        noSkuPlaceholder = getString(R.string.description_no_billing);

        // construct the ViewModel for this activity and its Fragments
        sharedViewModel = new ViewModelProvider(this).get(PurchaseTunnelViewModelProd.class);

        Toolbar myToolbar = findViewById(R.id.subscriptionsToolbar);
        productListView = findViewById(R.id.sku_list);
        purchasingInfoDebug = findViewById(R.id.purchasingInfoDebug);
        slidingPanes = findViewById(R.id.sliding_panes);

        setSupportActionBar(myToolbar);

        ActionBar supportActionBar = getActionBar();
        if (supportActionBar != null) {
            // set up icon or application icon if up not allowed
            sharedViewModel.getTunnels().observe(this, tunnelSpecs -> onAppUsable(supportActionBar, tunnelSpecs));
        }

        productListView.setOnItemClickListener(this);

        // ensure early enabling state of ui elements, standard will be set below
        setUiStateManagerInitializing();

        // build SKU list, initialise adapter and set up automatic update
        adapter = new SkuAdapter();
        productListView.setAdapter(adapter);
        sharedViewModel.getProductDetailList().observe(this,
                skuDetails -> onSkuListChanged());

        // Update visibility of debug message
        sharedViewModel.getPurchasingDebugMessage().observe(this,
                this::onPurchasingDebugMessageChanged);

        // update on state changes of subscription check
        sharedViewModel.getSubscriptionCheckResult().observe(this,
                this::onPurchasingResult);

        // update on state changes of certification check
        sharedViewModel.getCertificationResult().observe(this,
                this::onCertificationResult);
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
            Object selectedObject = adapter.getItem(i);
            productListView.setSelection(i);
            changeActiveSkuView(selectedObject);
            slidingPanes.open();
        } else {
            Log.wtf(TAG, "Received itemClick event not from our item list");
        }
    }

    /**
     * Destroy this activity.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "PurchaseTunnelActivity gets destroyed.");
        super.onDestroy();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Status update methods
    ////////////////////////////////////////////////////////////////////////////////////////////////


    private void onPurchasingResult(SubscriptionCheckResultListener.ResultType purchasingResult) {
        final Tunnels tunnels = sharedViewModel.getTunnels().getValue();
        int nrTunnels = (tunnels == null) ? 0 : tunnels.size();
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

    private void onCertificationResult (CertificationResultListener.ResultType certificationResult) {
        final Tunnels tunnels = sharedViewModel.getTunnels().getValue();
        int nrTunnels = (tunnels == null) ? 0 : tunnels.size();
        switch (certificationResult) {
            case PURCHASE_REJECTED:
            case TECHNICAL_FAILURE:
                setUiStateManagerReady();
                break;

            case OK:
                if (nrTunnels > 0) {
                    setUiStateManagerYieldsTunnels();
                } else {
                    setUiStateIAmBusy();
                }
        }
    }

    private void onAppUsable(ActionBar supportActionBar, Tunnels tunnelSpecs) {
        boolean cachedTunnelsAvailable = !tunnelSpecs.isEmpty();
        if (MainActivity.isConfigurationRequired(this, cachedTunnelsAvailable)) {
            supportActionBar.setIcon(R.drawable.ic_launcher);
        } else {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void onSkuListChanged() {
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
                        adapter.getItem(refPosition)
                );
            }
        }
    }

    private void onPurchasingDebugMessageChanged(final String purchasingDebugMessage) {
        // set or hide detail view explaining the current state in detail
        if (purchasingDebugMessage != null) {
            this.purchasingInfoDebug.setText(purchasingDebugMessage);
            this.purchasingInfoDebug.setVisibility(View.VISIBLE);
        } else {
            this.purchasingInfoDebug.setVisibility(View.GONE);
        }
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
        final Purchase activePurchase = sharedViewModel.getActivePurchase().getValue();
        if (activePurchase != null) {
            for (ProductDetails skuDetails : adapter.getProductDetails()) {
                if (activePurchase.getProducts().contains(skuDetails.getProductId())) {
                    changeActiveSkuView(skuDetails);
                    slidingPanes.open();
                    break;
                }
            }
        }
    }

    private void changeActiveSkuView(Object targetObject) {
        if (targetObject instanceof ProductDetails) {
            // todo setSelection for appropriate entry in list view
            ProductDetails details = (ProductDetails) targetObject;
            if (BillingClient.ProductType.SUBS.equals(details.getProductType())) {
                Log.i(TAG, "Switching to subscription fragment for " + details.getProductId());
                Bundle bundle = new Bundle();
                bundle.putString(SubscribeTunnelFragment.ARG_PRODUCT_ID, details.getProductId());
                getSupportFragmentManager()
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.productpurchasecontainer, SubscribeTunnelFragment.class, bundle, ACTIVE_SKU_FRAGMENT_TAG)
                        .commit();
            } else if (BillingClient.ProductType.INAPP.equals(details.getProductType())) {
                Log.i(TAG, "Switching to (one-time) purchase fragment for " + details.getProductId());
                Bundle bundle = new Bundle();
                bundle.putString(OneTimeTunnelFragment.ARG_SKU, details.getProductId());
                final FragmentManager fragmentManager = getSupportFragmentManager();
                if (!fragmentManager.isDestroyed() && !fragmentManager.isStateSaved()) {
                    fragmentManager
                            .beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.productpurchasecontainer, OneTimeTunnelFragment.class, bundle, ACTIVE_SKU_FRAGMENT_TAG)
                            .commit();
                }
            } else {
                Log.e(TAG, "Attempt to switch to a ProductDetail view of unsupported ProductType " + details.getProductType());
            }
        } else {
            if (adapter != null) {
                productListView.setSelection(adapter.getCount() - 1);
            }
            Log.i(TAG, "Switching to no tunnel fragment");
            final FragmentManager fragmentManager = getSupportFragmentManager();
            if (!fragmentManager.isDestroyed() && !fragmentManager.isStateSaved()) {
                fragmentManager
                        .beginTransaction()
                        .setReorderingAllowed(true)
                        .replace(R.id.productpurchasecontainer, NoTunnelFragment.class, null, ACTIVE_SKU_FRAGMENT_TAG)
                        .commit();
            }
        }
    }
}
