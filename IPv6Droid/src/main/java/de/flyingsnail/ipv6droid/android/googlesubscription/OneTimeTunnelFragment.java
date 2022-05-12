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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
    public static final String ARG_TUNNELS = "tunnels";
    public static final String ARG_PURCHASE_AVAILABLE = "purchaseAvailable";

    private String sku;

    private static final String TAG = OneTimeTunnelFragment.class.getSimpleName();

    /** A Checkbox that shows if user accepted terms and conditions */
    private CheckBox acceptTerms;

    /** A TextView to show the end of current subscription period. */
    private TextView validUntilView;

    /** A TextView showing user-readable information about subscription status */
    private TextView purchaseStatusView;

    /** A Button to initiate a purchase */
    private Button purchaseButton;

    /** End of validity period, if there's a purchase still valid. */
    private @Nullable Date validUntil;

    /**
     * The list of Tunnels available.
     */
    private Tunnels tunnels;

    /** Flag, if an unconsumed purchase is available. */
    private boolean purchaseAvailable;
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
            tunnels = bundle.getParcelable(ARG_TUNNELS);
            if (tunnels != null && !tunnels.isEmpty()) {
                validUntil = tunnels.get(0).getExpiryDate();
            }
            purchaseAvailable = bundle.getBoolean(ARG_PURCHASE_AVAILABLE, false);
            Log.i(TAG, "Received bundle with " + sku + ", " + tunnels + ", and " + purchaseAvailable);
        }
        if (tunnels == null) {
            tunnels = new Tunnels();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "Creating View for fragment");
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_onetime_tunnel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        validUntilView = view.findViewById(R.id.validUntil);
        purchaseStatusView = view.findViewById(R.id.subscriptionStatus);
        purchaseButton = view.findViewById(R.id.purchase);
        purchaseButton.setOnClickListener(this::onPurchaseSubscription);
        acceptTerms = view.findViewById(R.id.acceptTerms);
        acceptTerms.setOnClickListener(this::onAcceptTerms);
        consumePurchasedExplanation = view.findViewById(R.id.explain_onetime_purchase);
        consumePurchasedButton = view.findViewById(R.id.consumepurchase);
        consumePurchasedButton.setOnClickListener(this::onConsumePurchased);

        purchaseStatusView.setText(R.string.user_purchase_checking);
        setupUI();
        Log.i(TAG, "Initialized fragment");
    }

    private void setupUI() {
        validUntilView.setText(
                validUntil == null ?
                        "" :
                        SimpleDateFormat.getDateTimeInstance().format(validUntilView)
        );
        if (tunnels.isTunnelActive()) {
            purchaseStatusView.setText(
                    getResources().getQuantityString(R.plurals.user_has_subscription, tunnels.size(), tunnels.size())
            );
            // we have an active tunnel, nothing can be bought or consumed
            acceptTerms.setVisibility(View.VISIBLE);
            acceptTerms.setEnabled(false);
            purchaseButton.setVisibility(View.VISIBLE);
            purchaseButton.setEnabled(false);
            consumePurchasedExplanation.setVisibility(View.GONE);
            consumePurchasedButton.setVisibility(View.GONE);
        } else if (purchaseAvailable) {
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

        Bundle result = new Bundle();
        result.putString(PurchaseTunnelActivity.SKU_KEY, sku);
        getParentFragmentManager().setFragmentResult(PurchaseTunnelActivity.PURCHASE_REQUEST_KEY, result);
    }

    /**
     * Handler if user clicked the accept terms checkbox
     * @param clickedView the view that the user actually clicked on
     */
    public void onAcceptTerms(View clickedView) {
        if (!tunnels.isTunnelActive() && !purchaseAvailable) {
            purchaseButton.setEnabled(acceptTerms.isChecked());
        }
    }

    private void onConsumePurchased(View view) {
        if (!tunnels.isTunnelActive() && purchaseAvailable) {
            Bundle result = new Bundle();
            result.putString(PurchaseTunnelActivity.SKU_KEY, sku);
            getParentFragmentManager().setFragmentResult(PurchaseTunnelActivity.CONSUME_REQUEST_KEY, result);
        }
    }

}