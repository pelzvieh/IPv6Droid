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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.flyingsnail.ipv6droid.BuildConfig;
import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.DTLSTunnelReader;
import de.flyingsnail.ipv6droid.android.dtlsrequest.AndroidBackedKeyPair;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6server.restapi.CertificationApi;
import de.flyingsnail.ipv6server.svc.CertificationRejectedException;
import de.flyingsnail.ipv6server.svc.SubscriptionRejectedException;
import retrofit2.Call;
import retrofit2.Response;

public class PurchaseToTunnel {
    private static final String TAG = PurchaseToTunnel.class.getSimpleName();

    /** The keystore alias of the private key to use */
    private String alias;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * The client representing the SubscrptionsApi of the IPv6Server.
     */
    private final CertificationApi certificationClient;

    /**
     * A list of TicTunnels associated with the current user's subscriptions
     */
     private final List<TunnelSpec> tunnels;


    /**
     * Constructor.

     * @param context A Context that represents the Context that initiated creation of this Manager.
     */
    public PurchaseToTunnel(final @NonNull Context context) {
        tunnels = new ArrayList<>();
        // construct certification client
        URI baseUrl;
        try {
            baseUrl = new URI(context.getString(R.string.certification_default_url_base));
            String overrideHost = BuildConfig.target_host;
            if (!overrideHost.trim().isEmpty()) {
                baseUrl = new URI ("http", baseUrl.getUserInfo(), overrideHost,
                        8080, baseUrl.getPath(), baseUrl.getQuery(), baseUrl.getFragment());
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("App packaging faulty, illegal URL: " + e);
        }
        certificationClient = RestProxyFactory.createCertificationClient(baseUrl.toString());

    }

    /**
     * Attempt to get tunnels from given callback. The attempt involves network activity and
     * will be done asynchronously
     * @param purchase the Purchase to get Tunnels from
     * @param callback the CertificationResultListener that should receive the result of this attempt.
     */
    public void certifyTunnelsForPurchase(Purchase purchase, CertificationResultListener callback) {
        final List<String> sku = purchase.getSkus();
        final String skuData = purchase.getOriginalJson();
        final String skuSignature = purchase.getSignature();

        Log.d(TAG, "Examining SKU " + sku
                + ",\n Data '" + skuData + "',\n signature '" + skuSignature);

        try {
            final Call<List<String>> subsCall = certificationClient.checkSubscriptionAndSignCSR(
                    skuData,
                    skuSignature,
                    getCsr()
            );
            executor.submit(() -> {
                try {
                    List<String> caChain;
                    Response<List<String>> subscribedTunnelsResponse = subsCall.execute();
                    if (!subscribedTunnelsResponse.isSuccessful()) {
                        throw new IllegalStateException("subscribedTunnels returns exception " +
                                (subscribedTunnelsResponse.errorBody() != null ? subscribedTunnelsResponse.errorBody().string() : "<none>"));
                    }
                    caChain = subscribedTunnelsResponse.body();
                    Log.d(TAG, String.format("Successfully retrieved %d certs from server", caChain != null ? caChain.size() : 0));
                    TunnelSpec tunnel = DTLSTunnelReader.createTunnelspec(alias, caChain);
                    tunnels.add(tunnel);
                    Log.d(TAG, String.format("Added valid tunnel %s", tunnel.getTunnelId()));
                    callback.onCertificationRequestResult(
                            purchase, tunnels, CertificationResultListener.ResultType.OK, null);
                } catch (Exception e) {
                    callback.onCertificationRequestResult(
                            purchase,
                            tunnels,
                            (e instanceof IOException) ?
                                    CertificationResultListener.ResultType.TECHNICAL_FAILURE
                                  : CertificationResultListener.ResultType.PURCHASE_REJECTED,
                            e);
                }
            });
        } catch (RuntimeException | IOException | SubscriptionRejectedException | CertificationRejectedException re) {
            Log.e(TAG, "unable to handle active subscription " + sku, re);
        }
    }

    private String getCsr() throws IOException {
        final List<String> aliases = AndroidBackedKeyPair.listAliases();
        // iterate over existing aliases and try to build CSR from each and return on success
        for (final String alias: aliases) {
            try {
                this.alias = alias;
                return new AndroidBackedKeyPair(alias).getCertificationRequest();
            } catch (IOException e) {
                Log.i(TAG, "Key pair alias " + alias + " did not convert to CSR: " + e);
            }
        }
        // no alias exists or none was convertible to a CSR
        final String newAlias = "IPv6Droid-"+aliases.size();
        AndroidBackedKeyPair.create(newAlias);
        this.alias = newAlias;
        return new AndroidBackedKeyPair(newAlias).getCertificationRequest();
    }

    /**
     * Get the current list of tunnels
     *
     * @return List&lt;TicTunnel&gt;
     */
    public List<TunnelSpec> getTunnels() {
        return Collections.unmodifiableList(tunnels);
    }
}
