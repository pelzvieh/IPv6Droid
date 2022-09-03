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

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.billingclient.api.Purchase;

import java.text.SimpleDateFormat;
import java.util.Date;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.Tunnels;

/**
 * A {@link Fragment} showing details of a specific one-time purchase SKU.
 * Depending on the purchase status, showing details of the still-valid purchase
 * or allowing to purchase one quantity.
 */
public class OneTimeTunnelFragment extends Fragment {

    public static final String ARG_SKU = "sku";

    private String sku;

    private static final String TAG = OneTimeTunnelFragment.class.getSimpleName();

    private PurchaseTunnelViewModel sharedViewModel;

    /** A Checkbox that shows if user accepted terms and conditions */
    private CheckBox acceptTerms;

    /** A TextView to show the end of current subscription period. */
    private TextView validUntilView;

    /** A TextView showing user-readable information about subscription status */
    private TextView purchaseStatusView;

    /** A Button to initiate a purchase */
    private Button purchaseButton;

    private View consumePurchasedExplanation;
    private Button consumePurchasedButton;

    public OneTimeTunnelFragment() {
        super(R.layout.fragment_onetime_tunnel);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Creating Fragment");
        super.onCreate(savedInstanceState);

        final Bundle bundle = getArguments();
        if (bundle != null) {
            sku = bundle.getString(ARG_SKU);
            Log.i(TAG, "Received bundle with " + sku);
        }
    }

    /*@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "Creating View for fragment");
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_onetime_tunnel, container, false);
    }*/

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        validUntilView = view.findViewById(R.id.validUntil);
        purchaseStatusView = view.findViewById(R.id.subscriptionStatus);
        purchaseButton = view.findViewById(R.id.purchase);
        purchaseButton.setOnClickListener(this::onPurchaseSubscription);
        acceptTerms = view.findViewById(R.id.acceptTerms);
        acceptTerms.setOnClickListener(this::onAcceptTerms);
        consumePurchasedExplanation = view.findViewById(R.id.explain_onetime_purchase);
        consumePurchasedButton = view.findViewById(R.id.consumepurchase);
        consumePurchasedButton.setOnClickListener(this::onConsumePurchased);

        // retrieve the PurchaseTunnelViewModel instance shared with our Activity
        sharedViewModel = new ViewModelProvider(requireActivity()).get(PurchaseTunnelViewModelProd.class);

        purchaseStatusView.setText(R.string.user_purchase_checking);

        // adapt UI on availability of an active tunnel
        sharedViewModel.getTunnels().observe(getViewLifecycleOwner(),
                this::onIsTunnelActive
        );

        // adapt UI on availability of an unconsumed purchase
        sharedViewModel.getActivePurchase().observe(getViewLifecycleOwner(),
                this::onIsPurchaseAvailable
        );
        Log.i(TAG, "Initialized fragment");
    }

    private void onIsTunnelActive(final Tunnels tunnels) {
        boolean active = tunnels != null && tunnels.isTunnelActive() && tunnels.getActiveTunnel() != null;
        int amount = tunnels == null ? 0 : tunnels.size();
        Date validUntil = active ? tunnels.getActiveTunnel().getExpiryDate() : null;

        if (active) {
            // when we're having an active tunnel, no purchase-related buttons are enabled
            purchaseStatusView.setText(
                    getResources().getQuantityString(R.plurals.user_has_subscription, amount, amount)
            );
            // we have an active tunnel, nothing can be bought or consumed
            acceptTerms.setVisibility(View.VISIBLE);
            acceptTerms.setEnabled(false);
            purchaseButton.setVisibility(View.VISIBLE);
            purchaseButton.setEnabled(false);
            consumePurchasedExplanation.setVisibility(View.GONE);
            consumePurchasedButton.setVisibility(View.GONE);
            validUntilView.setText(validUntil == null ?
                    "" :
                    SimpleDateFormat.getDateTimeInstance().format(validUntil)
            );
        }
    }

    private void onIsPurchaseAvailable(Purchase activePurchase) {
        boolean purchaseAvailable =
                activePurchase != null && activePurchase.getProducts().contains(sku);

        // don't dare to interfere with the view if we have an active tunnel
        Tunnels tunnels = sharedViewModel.getTunnels().getValue();
        if (tunnels == null || !tunnels.isTunnelActive()) {
            // we do not have an active tunnel, so it depends if there's an unconsumed purchase around
            if (purchaseAvailable) {
                // unconsumed purchase ready for consumption
                purchaseStatusView.setText(R.string.purchase_available_for_consumption);
                acceptTerms.setVisibility(View.GONE);
                purchaseButton.setVisibility(View.GONE);
                consumePurchasedExplanation.setVisibility(View.VISIBLE);
                consumePurchasedButton.setVisibility(View.VISIBLE);
                consumePurchasedButton.setEnabled(true);
            } else {
                // nothing available, offer to purchase
                purchaseStatusView.setText(R.string.user_consumed_purchases);
                acceptTerms.setVisibility(View.VISIBLE);
                acceptTerms.setEnabled(true);
                acceptTerms.setChecked(false);
                purchaseButton.setVisibility(View.VISIBLE);
                purchaseButton.setEnabled(false);
                consumePurchasedExplanation.setVisibility(View.GONE);
                consumePurchasedButton.setVisibility(View.GONE);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // User action callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * User clicked the "purchase" button. We're checking state, and initiating Google purchase.
     * @param clickedView the button UI element
     */
    public void onPurchaseSubscription(final View clickedView) {
        acceptTerms.setEnabled(false); // don't allow disabling after purchase...
        if (!acceptTerms.isChecked()) {
            acceptTerms.setEnabled(true);
            purchaseButton.setEnabled(false);
            Toast.makeText(getActivity(), R.string.user_subscription_error_not_accepted, Toast.LENGTH_LONG).show();
            return;
        }
        purchaseButton.setEnabled(false); // inhibit multiple submissions

        sharedViewModel.initiatePurchase(sku, null, requireActivity());
    }

    /**
     * Handler if user clicked the accept terms checkbox
     * @param clickedView the view that the user actually clicked on
     */
    public void onAcceptTerms(View clickedView) {
        final Tunnels tunnels = sharedViewModel.getTunnels().getValue();
        final Purchase purchase = sharedViewModel.getActivePurchase().getValue();
        if ((tunnels != null && tunnels.isTunnelActive()) || (purchase != null && purchase.getProducts().contains(sku))) {
            purchaseButton.setEnabled(false);
        } else {
            purchaseButton.setEnabled(acceptTerms.isChecked());
        }
    }

    private void onConsumePurchased(View view) {
        final Tunnels tunnels = sharedViewModel.getTunnels().getValue();
        final Purchase purchase = sharedViewModel.getActivePurchase().getValue();

        if (!(tunnels != null && tunnels.isTunnelActive()) && (purchase != null && purchase.getProducts().contains(sku))) {
            sharedViewModel.consumePurchase(sku);
        }
    }
}