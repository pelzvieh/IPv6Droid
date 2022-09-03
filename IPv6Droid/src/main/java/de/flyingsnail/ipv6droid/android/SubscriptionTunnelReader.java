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

package de.flyingsnail.ipv6droid.android;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import de.flyingsnail.ipv6droid.BuildConfig;
import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.googlesubscription.CertificationResultListener;
import de.flyingsnail.ipv6droid.android.googlesubscription.PurchaseManager;
import de.flyingsnail.ipv6droid.android.googlesubscription.PurchaseManagerImpl;
import de.flyingsnail.ipv6droid.android.googlesubscription.PurchaseToTunnel;
import de.flyingsnail.ipv6droid.android.googlesubscription.SubscriptionCheckResultListener;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * Created by pelzi on 18.10.17.
 */

public class SubscriptionTunnelReader implements
        TunnelReader,
        SubscriptionCheckResultListener,
        CertificationResultListener {
    final private PurchaseManager purchaseManager;

    private boolean finished = false;
    private boolean failed = false;
    private boolean noSubscriptions = false;
    private final PurchaseToTunnel helper;
    private List<TunnelSpec> readTunnels;

    public SubscriptionTunnelReader(final Application application) {
        // initialize baseUrl to point to the IPv6Server to use
        URI baseUrl;
        try {
            baseUrl = new URI(application.getString(R.string.certification_default_url_base));
            String overrideHost = BuildConfig.target_host;
            if (!overrideHost.trim().isEmpty()) {
                baseUrl = new URI ("http", baseUrl.getUserInfo(), overrideHost,
                        8080, baseUrl.getPath(), baseUrl.getQuery(), baseUrl.getFragment());
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("App packaging faulty, illegal URL: " + e);
        }

        purchaseManager = new PurchaseManagerImpl(application);
        purchaseManager.addResultListener(this);
        helper = new PurchaseToTunnel(baseUrl);
    }

    /**
     * Query tunnels from active Google subscription
     * @return List&lt;TicTunnel&gt; with all tunnel definition associated with the Google acccount
     * @throws ConnectionFailedException in case of a permanent problem, e.g. no subscription active
     * @throws IOException in case of a temporary problem to query.
     */
    @Override
    public synchronized List<TunnelSpec> queryTunnels() throws ConnectionFailedException, IOException {
        if (!finished) {
            try {
                wait(10000L);
            } catch (InterruptedException e) {
                throw new IOException (e);
            }
        }
        if (!finished)
            throw new IOException ("Timeout, subscription query not finalised after 10 secs.");
        if (failed)
            throw new IOException("Temporary failure of subscription query");
        if (noSubscriptions)
            throw new ConnectionFailedException("This user has no active subscriptions", null);
        return Objects.requireNonNull(readTunnels, "finished, not failed, yet no tunnels");
    }

    @Override
    public synchronized void onSubscriptionCheckResult(SubscriptionCheckResultListener.ResultType result, Purchase p, String debugMessage) {
        switch (result) {
            case HAS_TUNNELS:
                helper.certifyTunnelsForPurchase(p, this);
                break;
            case NO_PURCHASES:
            case SUBSCRIPTION_UNPARSABLE:
                finished = true;
                failed = false;
                // todo check usage of this: absence of one-time purchases does not imply no active tunnels
                noSubscriptions = true;
                notifyAll();
                break;
            case TEMPORARY_PROBLEM:
            case CHECK_FAILED:
            case NO_SERVICE_PERMANENT:
            case NO_SERVICE_TRY_AGAIN:
                finished = true;
                failed = true;
                notifyAll();
                break;
            case NO_SERVICE_AUTO_RECOVERY:
                finished = false;
                failed = true;
                break;
        }
    }

    /**
     * Notify listener on a change of available SKU.
     *
     * @param knownSku a List of ProductDetails objects representing the now known list of purchasable products.
     */
    @Override
    public void onAvailableProductsUpdate(@NonNull List<ProductDetails> knownSku) {
        // no action required
    }

    /**
     * Callback to inform about an ansychronous attempt to get tunnels from a purchase.
     *
     * @param purchase   the Purchase that should have turned into tunnel certificates.
     * @param tunnels    the Tunnels created from the Purchase. May be empty.
     * @param resulttype the ResultType classifying the outcome of the attempt, @see {ResultType}
     * @param e          an Exception giving details of failure and rejections.
     */
    @Override
    public void onCertificationRequestResult(
            @NonNull Purchase purchase,
            @NonNull List<TunnelSpec> tunnels,
            CertificationResultListener.ResultType resulttype,
            @Nullable Exception e) {
        switch (resulttype) {
            case OK:
                readTunnels = tunnels;
                finished = true;
                failed = false;
                noSubscriptions = false;
                notifyAll();
                break;

            case PURCHASE_REJECTED:
                if (e != null) {
                    purchaseManager.revalidateCache(purchase, e); // will notify callbacks again
                }
                break;

            case TECHNICAL_FAILURE:
                finished = true;
                failed = true;
                notifyAll();
                break;
        }
    }
}
