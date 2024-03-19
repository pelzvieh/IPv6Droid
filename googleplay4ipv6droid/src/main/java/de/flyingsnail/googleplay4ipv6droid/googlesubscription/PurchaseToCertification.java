/*
 *
 *  * Copyright (c) 2024 Dr. Andreas Feldner.
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

package de.flyingsnail.googleplay4ipv6droid.googlesubscription;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.Purchase;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.flyingsnail.ipv6server.restapi.CertificationApi;
import de.flyingsnail.ipv6server.svc.CertificationRejectedException;
import de.flyingsnail.ipv6server.svc.SubscriptionRejectedException;
import retrofit2.Call;
import retrofit2.Response;

public class PurchaseToCertification {
    private static final String TAG = PurchaseToCertification.class.getSimpleName();

    /** The keystore alias of the private key to use */
    private String alias;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * The client representing the SubscrptionsApi of the IPv6Server.
     */
    private final CertificationApi certificationClient;

    /**
     * Constructor.

     * @param baseUrl the URI pointing to the base of IPv6Server REST services.
     */
    public PurchaseToCertification(final @NonNull URI baseUrl) {
        // construct certification client
        certificationClient = RestProxyFactory.createCertificationClient(baseUrl.toString());
    }

    /**
     * Attempt to get certPath from given callback. The attempt involves network activity and
     * will be done asynchronously
     * @param purchase the Purchase to get Certificates from
     * @param csr the String giving the certificate signing request, PEM encoded
     * @param callback the CertificationResultListener that should receive the result of this attempt.
     */
    public void certifyTunnelsForPurchase(final Purchase purchase,
                                          final String csr,
                                          final CertificationResultListener callback) {
        final List<String> sku = purchase.getProducts();
        final String skuData = purchase.getOriginalJson();
        final String skuSignature = purchase.getSignature();

        Log.d(TAG, "Examining SKU " + sku
                + ",\n Data '" + skuData + "',\n signature '" + skuSignature);

        try {
            final Call<List<String>> subsCall = certificationClient.checkSubscriptionAndSignCSR(
                    skuData,
                    skuSignature,
                    csr
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
                    callback.onCertificationRequestResult(
                            purchase, caChain, CertificationResultListener.ResultType.OK, null);
                } catch (Exception e) {
                    callback.onCertificationRequestResult(
                            purchase,
                            List.of(),
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
}
