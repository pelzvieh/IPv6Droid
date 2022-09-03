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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * A {@link Fragment} subclass showing state and controls for subscribing to tunnels.
 */
public class SubscribeTunnelFragment extends Fragment {

    public static final String ARG_PRODUCT_ID = "sku";

    private String productId;

    private static final String TAG = SubscribeTunnelFragment.class.getSimpleName();

    /** A TextView that shows the name of the subscription associated with this fragment. */
    private TextView subscriptionTitle;

    /** A Checkbox that shows if user accepted terms and conditions */
    private CheckBox acceptTerms;

    /** A RadioGroup containing an RadioButton for each available offer */
    private RadioGroup offerGroup;

    /** A RadioButton representing the subscription product's base plan. Always exists. */
    private RadioButton basePlanButton;

    /** A List&lt;RadioButton&gt; containing a RadioButton for each offer. May be empty. */
    private List<RadioButton> offerButtonList;

    /** A TextView to show the end of current subscription period. */
    private TextView validUntilView;

    /** A CheckBox to show if the current subscription is going to renew */
    private CheckBox renewingView;

    /** A TextView showing user-readable information about subscription status */
    private TextView subscriptionStatusView;

    /** A Button to initiate a purchase */
    private Button subscribeButton;

    private PurchaseTunnelViewModel sharedViewModel;

    public SubscribeTunnelFragment() {
        super(R.layout.fragment_subscribe_tunnel);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Creating SubscribeTunnelFragment");
        super.onCreate(savedInstanceState);
        final Bundle bundle = getArguments();
        if (bundle != null) {
            productId = bundle.getString(ARG_PRODUCT_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Log.i(TAG, "View created, inflating");
        return inflater.inflate(R.layout.fragment_subscribe_tunnel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // retrieve the PurchaseTunnelViewModel instance shared with our Activity
        sharedViewModel = new ViewModelProvider(requireActivity()).get(PurchaseTunnelViewModelProd.class);

        // title
        subscriptionTitle = view.findViewById(R.id.subscription_title);
        // identify relevant views
        validUntilView = view.findViewById(R.id.validUntil);
        renewingView = view.findViewById(R.id.renewing);
        subscriptionStatusView = view.findViewById(R.id.subscriptionStatus);
        subscribeButton = view.findViewById(R.id.purchase);
        subscribeButton.setOnClickListener(this::onPurchaseSubsciption);
        acceptTerms = view.findViewById(R.id.acceptTerms);
        acceptTerms.setOnClickListener(this::onAcceptTerms);
        offerGroup = view.findViewById(R.id.offers);
        basePlanButton = view.findViewById(R.id.baseplan);
        offerButtonList = new ArrayList<>(5);
        Button manageSubscriptionsButton = view.findViewById(R.id.consumepurchase);
        manageSubscriptionsButton.setOnClickListener(this::onManageSubscriptions);

        // react on product information
        sharedViewModel.getProductDetailList().observe(getViewLifecycleOwner(),
                this::onProductInformation);
        // react on purchase changes
        sharedViewModel.getActivePurchase().observe(getViewLifecycleOwner(),
                this::onSubscriptionAvailable);

        // react on Tunnels changes
        sharedViewModel.getTunnels().observe(getViewLifecycleOwner(),
                this::onTunnels);
        Log.i(TAG, "View creation finished, listeners set up");
    }

    private void onProductInformation(List<ProductDetails> productDetailsList) {
        // remove previously added radio buttons
        for (RadioButton offerButton: offerButtonList) {
            offerGroup.removeView(offerButton);
        }
        offerButtonList.clear();
        // find our product id in the list
        ProductDetails myProductDetails = null;
        for (ProductDetails productDetails: productDetailsList) {
            if (productId.equals(productDetails.getProductId())) {
                myProductDetails = productDetails;
                break;
            }
        }
        // set up UI with the newly found data
        if (myProductDetails != null) {
            subscriptionTitle.setText(myProductDetails.getTitle());
            List<ProductDetails.SubscriptionOfferDetails> offerDetails = myProductDetails.getSubscriptionOfferDetails();
            if (offerDetails != null && !offerDetails.isEmpty()) {
                ProductDetails.SubscriptionOfferDetails basePlan = offerDetails.get(0);
                offerDetails.remove(0);
                basePlanButton.setText(basePlan.getOfferToken());
                basePlanButton.setChecked(true);
                int id = 0xdeadaffe;
                for (ProductDetails.SubscriptionOfferDetails offer: offerDetails) {
                    RadioButton offerButton = new RadioButton(getContext());
                    offerButton.setId(id++);
                    offerButton.setText(offer.getOfferToken());
                    offerButtonList.add(offerButton);
                    offerGroup.addView(offerButton);
                    offerButton.setChecked(true);
                }
            }
        } else {
            acceptTerms.setEnabled(false);
            acceptTerms.setChecked(true);
            subscribeButton.setEnabled(false);
        }
    }

    private void onSubscriptionAvailable(Purchase activePurchase) {
        Log.i(TAG, "Listener for subscription triggered for " + activePurchase.getOrderId());
        boolean subscriptionAvailable =
                activePurchase.getProducts().contains(productId);

        if (subscriptionAvailable) {
            acceptTerms.setEnabled(false);
            acceptTerms.setChecked(true);
            subscribeButton.setEnabled(false);
            renewingView.setChecked(activePurchase.isAutoRenewing());
            Log.i(TAG, "Fragment set up for representing the current subscription");
        } else {
            subscriptionStatusView.setText(R.string.user_not_subscribed);
            acceptTerms.setEnabled(true);
            acceptTerms.setChecked(false);
            subscribeButton.setEnabled(false);
            validUntilView.setText("");
            renewingView.setChecked(false);
            Log.i(TAG, "Fragment set up to enable purchase of subscription");
        }
    }

    private void onTunnels(final Tunnels tunnels) {
        int amount = tunnels == null ? 0 : tunnels.size();
        subscriptionStatusView.setText(
            getResources().getQuantityString(R.plurals.user_has_subscription, amount, amount)
        );
        TunnelSpec activeTunnel = amount > 0 ? tunnels.getActiveTunnel() : null;
        Date validUntil = activeTunnel != null ? activeTunnel.getExpiryDate() : null;
        validUntilView.setText(
                validUntil == null ?
                        "" :
                        SimpleDateFormat.getDateTimeInstance().format(validUntil)
        );
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // User action callbacks
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * User clicked the "purchase" button. We're checking state, and initiating Google purchase.
     * @param clickedView the button UI element
     */
    public void onPurchaseSubsciption (final View clickedView) {
        if (!acceptTerms.isChecked()) {
            subscribeButton.setEnabled(false);
            Toast.makeText(getActivity(), R.string.user_subscription_error_not_accepted, Toast.LENGTH_LONG).show();
            return;
        }
        subscribeButton.setEnabled(false); // inhibit additional taps by user
        acceptTerms.setEnabled(false); // inhibit ex-post deactivation of acceptance
        String offerId = null;
        int selectedOffer = offerGroup.getCheckedRadioButtonId();

        if (selectedOffer > 0) {
            RadioButton selectedButton = requireView().findViewById(selectedOffer);
            if (selectedButton != null) {
                offerId = selectedButton.getText().toString();
            }
        }
        sharedViewModel.initiatePurchase(productId, offerId, requireActivity());
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
                                            SubscriptionBuilder.PRODUCT_ID_TUNNEL_SUBSCRIPTION,
                                            clickedView.getContext().getApplicationContext().getPackageName()
                                    )
                            )
                    )
            );
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getActivity(), R.string.user_subscription_management_not_launched, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Handler if user clicked the accept terms checkbox
     * @param clickedView the view that the user actually clicked on
     */
    public void onAcceptTerms(final View clickedView) {
        final Purchase activePurchase = sharedViewModel.getActivePurchase().getValue();
        final boolean subscriptionAvailable =
                activePurchase != null && activePurchase.getProducts().contains(productId);

        if (!subscriptionAvailable) {
            subscribeButton.setEnabled(acceptTerms.isChecked());
        }
    }
}