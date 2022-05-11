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
 * A {@link Fragment} subclass showing state and controls for subscribing to tunnels.
 */
public class SubscribeTunnelFragment extends Fragment {

    public static final String ARG_SKU = "sku";
    public static final String ARG_TUNNELS = "tunnels";
    public static final String ARG_SUB_AVAILABLE = "subscription_available";
    public static final String ARG_SUB_RENEWING = "subscription_renewing";

    private String sku;

    private Tunnels tunnels;

    private static final String TAG = SubscribeTunnelFragment.class.getSimpleName();

    /** A Checkbox that shows if user accepted terms and conditions */
    private CheckBox acceptTerms;


    /** A TextView to show the end of current subscription period. */
    private TextView validUntilView;

    /** A layout containing the views to show valid until information, incl. label */
    private View validUntilLine;

    /** A CheckBox to show if the current subscription is going to renew */
    private CheckBox renewingView;

    /** A layout containing the views to show renewal information */
    private View renewingLine;

    /** A TextView showing user-readable information about subscription status */
    private TextView subscriptionStatusView;

    /** A Button to initiate a purchase */
    private Button subscribeButton;

    private Button manageSubscriptionsButton;

    private boolean subscriptionAvailable = false;

    private Date validUntil = null;

    /** a flag indicating if auto-renewal is active on the subscription */
    private boolean subscriptionRenewing;

    public SubscribeTunnelFragment() {
        super(R.layout.fragment_subscribe_tunnel);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle bundle = getArguments();
        if (bundle != null) {
            sku = bundle.getString(ARG_SKU);
            tunnels = (Tunnels)bundle.getSerializable(ARG_TUNNELS);
            if (tunnels != null && !tunnels.isEmpty()) {
                validUntil = tunnels.get(0).getExpiryDate();
            }
            subscriptionAvailable = bundle.getBoolean(ARG_SUB_AVAILABLE);
            subscriptionRenewing = bundle.getBoolean(ARG_SUB_RENEWING);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_subscribe_tunnel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        validUntilView = view.findViewById(R.id.validUntil);
        validUntilLine = view.findViewById(R.id.validUntilLine);
        renewingView = view.findViewById(R.id.renewing);
        renewingLine = view.findViewById(R.id.renewingLine);
        subscriptionStatusView = view.findViewById(R.id.subscriptionStatus);
        subscribeButton = view.findViewById(R.id.purchase);
        subscribeButton.setOnClickListener(this::onPurchaseSubsciption);
        acceptTerms = view.findViewById(R.id.acceptTerms);
        acceptTerms.setOnClickListener(this::onAcceptTerms);
        manageSubscriptionsButton = view.findViewById(R.id.consumepurchase);
        manageSubscriptionsButton.setOnClickListener(this::onManageSubscriptions);
        setupUI();
    }

    private void setupUI() {
        validUntilView.setText(
                validUntil == null ?
                        "" :
                        SimpleDateFormat.getDateTimeInstance().format(validUntil)
        );
        renewingView.setChecked(subscriptionRenewing);
        if (subscriptionAvailable) {
            int amount = tunnels == null ? 0 : tunnels.size();
            subscriptionStatusView.setText(
                getResources().getQuantityString(R.plurals.user_has_subscription, amount, amount)
            );
            acceptTerms.setEnabled(false);
            acceptTerms.setChecked(true);
            subscribeButton.setEnabled(false);
        } else {
            subscriptionStatusView.setText(R.string.user_not_subscribed);
            acceptTerms.setEnabled(true);
            acceptTerms.setChecked(false);
            subscribeButton.setEnabled(false);
        }
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
        subscribeButton.setEnabled(false); // gegen ungeduldige Benutzer
        acceptTerms.setEnabled(false); // jetzt ist er gefangen...

        Bundle result = new Bundle();
        result.putString(PurchaseTunnelActivity.SKU_KEY, sku);
        getParentFragmentManager().setFragmentResult(PurchaseTunnelActivity.PURCHASE_REQUEST_KEY, result);
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
                                            getActivity().getApplicationContext().getPackageName()
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
    public void onAcceptTerms(View clickedView) {
        if (!subscriptionAvailable) {
            subscribeButton.setEnabled(acceptTerms.isChecked());
        }
    }
}