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

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import de.flyingsnail.ipv6droid.BuildConfig;
import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.TunnelPersisting;
import de.flyingsnail.ipv6droid.android.TunnelPersistingFile;
import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * Holds data for PurchaseTunnelActivity and its Fragments.
 */
public class PurchaseTunnelViewModelProd
        extends AndroidViewModel
        implements SubscriptionCheckResultListener, CertificationResultListener, PurchaseTunnelViewModel {
    /**
     * Logging tag
     */
    private final static String TAG = PurchaseTunnelViewModelProd.class.getSimpleName();
    /**
     * Observable tunnel data.
     */
    private final MutableLiveData<Tunnels> tunnels = new MutableLiveData<>(new Tunnels());
    /**
     * Observable flag if a certification process is currently running
     */
    private final MutableLiveData<Boolean> certificationRunning = new MutableLiveData<>(false);
    /**
     * Observable for the purchase that is currently valid and active, corresponding to the
     * available tunnels.
     */
    private final MutableLiveData<Purchase> activePurchase = new MutableLiveData<>();

    /** The result of the last purchasing action, i.e. current state of purchasing process */
    private final @NonNull
    MutableLiveData<SubscriptionCheckResultListener.ResultType> purchasingResult =
            new MutableLiveData<>(SubscriptionCheckResultListener.ResultType.NO_SERVICE_AUTO_RECOVERY);

    /** The result of the last certification action, i.e. current state of purchasing process */
    private final @NonNull
    MutableLiveData<CertificationResultListener.ResultType> certificationResult =
            new MutableLiveData<>(CertificationResultListener.ResultType.OK);

    /** The last diagnostic message, explaining a "negative" purchasingResult in detail. May be null. */
    private final MutableLiveData<String> purchasingDebugMessage = new MutableLiveData<>();


    /** The observable list of known SkuDetails */
    private final MutableLiveData<List<ProductDetails>> productDetailList = new MutableLiveData<>();

    /**
     * An instance of an Executor service
     */
    private final ScheduledExecutorService executor;

    /** An instance of PurchaseManager */
    private PurchaseManager purchaseManager;

    /** a flag indicating if this model should be considered destroyed. */
    private boolean isDestroyed;
    private URI baseUrl;

    /**
     * Constructor.
     */
    public PurchaseTunnelViewModelProd(final Application application) {
        super(application);
        executor = Executors.newScheduledThreadPool(1);
        isDestroyed = false;

        loadCachedTunnels();

        // initialize baseUrl to point to the IPv6Server to use
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
        this.purchaseManager = new PurchaseManagerImpl(getApplication());

        // initialise PurchaseManager and Preferences
        purchaseManager.addResultListener(this);
    }

    /**
     * Read Tunnels from a persisting file.
     */
    private void loadCachedTunnels() {
        TunnelPersisting tp = new TunnelPersistingFile(getApplication().getApplicationContext());
        try {
            Tunnels cachedTunnels = tp.readTunnels();
            // remove expired tunnels
            if (!cachedTunnels.checkCachedTunnelAvailability()) {
                for (TunnelSpec tunnel: cachedTunnels) {
                    if (!tunnel.isEnabled()) {
                        cachedTunnels.remove(tunnel);
                    }
                }
            }
            tunnels.setValue(cachedTunnels);
        } catch (IOException e) {
            Log.i(TAG, "No tunnel list yet cached");
        }
    }

    /**
     * Query the result status of Subscription/Purchase check.
     *
     * @return LiveData&lt;SubscriptionCheckResultListener.ResultType&gt; that can be observed for changes of the result of the purchase
     * check.
     */
    @Override
    public LiveData<SubscriptionCheckResultListener.ResultType> getSubscriptionCheckResult() {
        return purchasingResult;
    }

    /**
     * Query the result status of Certification.
     *
     * @return LiveData&lt;CertificationResultListener.ResultType&gt; that can be observed for changes of the result of the certification
     * check.
     */
    @Override
    public LiveData<CertificationResultListener.ResultType> getCertificationResult() {
        return certificationResult;
    }

    @Deprecated
    @Override
    public LiveData<Boolean> getCertificationRunning() {
        return certificationRunning;
    }

    /**
     * Query diagnostic message explaining result of purchase and/or certification process.
     *
     * @return LiveData&lt;String&gt; that can be observed for changes of debug message.
     */
    @Override
    public LiveData<String> getPurchasingDebugMessage() {
        return purchasingDebugMessage;
    }

    @Override
    public LiveData<Purchase> getActivePurchase() {
        return activePurchase;
    }

    @Override
    public final LiveData<Tunnels> getTunnels() {
        return tunnels;
    }

    @Override
    public LiveData<List<ProductDetails>> getProductDetailList() {
        return productDetailList;
    }

    /**
     * React on an achieved result of subscription/purchase check.
     *
     * @param result         a ResultType indicating the status of purchase or subscription
     * @param activePurchase a Purchase object giving details of the active purchase, if any. May be null.
     * @param debugMessage   a String indicating the origin of a problem, if applicable. May be null.
     */
    @Override
    public void onSubscriptionCheckResult(@NonNull SubscriptionCheckResultListener.ResultType result,
                                          @Nullable Purchase activePurchase,
                                          @Nullable String debugMessage) {
        purchasingResult.setValue(result);
        purchasingDebugMessage.setValue(debugMessage);
        this.activePurchase.setValue(activePurchase);
        switch (result) {
            case HAS_TUNNELS:
                Log.i(TAG, "Subscription check says: you have tunnels");
                if (tunnels.getValue() == null || tunnels.getValue().isTunnelActive() && activePurchase != null) {
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
                break;

            case TEMPORARY_PROBLEM:
            case SUBSCRIPTION_UNPARSABLE:
            case NO_SERVICE_TRY_AGAIN:
            case CHECK_FAILED:
                // these are conditions that call for retrying
                Log.i(TAG, "Subscription check says: things went messy, try later");
                scheduleRetry();
                break;

            case NO_SERVICE_AUTO_RECOVERY:
            case NO_SERVICE_PERMANENT:
            case PURCHASE_FAILED:
            case PURCHASE_STARTED:
                // these are conditions that do not require programmatic action
                Log.i(TAG, "Subscription check says: uninteresting intermediate stuff happened");
                break;
            default:
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
    public void onAvailableProductsUpdate(@NonNull List<ProductDetails> knownSku) {
        Log.i(TAG, "Sku update knownSku size: " + knownSku.size());
        productDetailList.setValue(knownSku);
    }

    synchronized private void requestCertification(Purchase activePurchase) {
        Log.i(TAG, "Requesting certificates for purchase");
        if (Boolean.FALSE.equals(certificationRunning.getValue())) {
            final PurchaseToTunnel helper = new PurchaseToTunnel(baseUrl);
            certificationRunning.setValue(true);
            helper.certifyTunnelsForPurchase(activePurchase, this);
        }
    }


    /**
     * Callback to inform about an asynchronous attempt to get tunnels from a purchase.
     *
     * @param purchase   the Purchase for which the request was initiated. Guaranteed to be the
     *                   unaltered object as passed.
     * @param tunnels    the Tunnels created from the Purchase. May be empty.
     * @param resultType the ResultType classifying the outcome of the attempt, @see {ResultType}
     * @param e          an Exception giving details of failure and rejections.
     */
    @Override
    public void onCertificationRequestResult(
            @NonNull Purchase purchase,
            @NonNull List<TunnelSpec> tunnels,
            CertificationResultListener.ResultType resultType,
            @Nullable Exception e) {
        switch(resultType) {
            case OK:
                Log.i(TAG, "successfully retrieved tunnels from purchase");
                purchaseManager.consumePurchase(purchase);
                Log.i(TAG, "Marked purchase as consumed");
                updateCachedTunnelList(tunnels);
                break;

            case TECHNICAL_FAILURE:
                Log.e(TAG, "Failed to retrieve tunnels for purchase", e);
                scheduleRetry();
                break;

            case PURCHASE_REJECTED:
                Log.e(TAG, "A purchase from local billing client seems to have been rejected", e);
                if (e != null) {
                    purchasingDebugMessage.setValue(e.toString());
                    purchaseManager.revalidateCache(purchase, e);
                } else {
                    purchasingDebugMessage.setValue("Purchase not verifiable");
                }
                break;
        }

        certificationResult.setValue(resultType);
        certificationRunning.setValue(false);
    }

    /**
     * Called on certain failure states, this method schedules retrying of subscription check.
     */
    private void scheduleRetry() {
        executor.schedule(()-> {
            if (isDestroyed ||
                    SubscriptionCheckResultListener.ResultType.HAS_TUNNELS.equals(purchasingResult.getValue()) ||
                    SubscriptionCheckResultListener.ResultType.NO_PURCHASES.equals(purchasingResult.getValue())) {
                Log.i(TAG, "Scheduled retry is obsolete");
            } else {
                Log.i(TAG, "Scheduled retry is launching a new instance of PurchaseManager");
                purchaseManager.restart();
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * This will replace the tunnel list read from persistent cache by the tunnel list
     * received from PurchaseManager, plus will write the new list back to cache.
     */
    private void updateCachedTunnelList(List<TunnelSpec> subscribedTunnels) {
        // write tunnel list to cache
        TunnelPersisting tp = new TunnelPersistingFile(getApplication().getApplicationContext());
        Tunnels cachedTunnels = tunnels.getValue();
        if (cachedTunnels == null) {
            cachedTunnels = new Tunnels(subscribedTunnels, null);
        } else {
            cachedTunnels.replaceTunnelList(subscribedTunnels);
        }
        tunnels.setValue(cachedTunnels);

        try {
            tp.writeTunnels(cachedTunnels);
        } catch (IOException e) {
            Log.e(TAG, "Could not write cached tunnel list", e);
        }
    }

    /**
     * Initiate a purchase with the Google Billing client.
     * @param productId a String giving the product identifier of the product to purchase.
     * @param offerId a String giving the selected offer if productId relates to a subscription. May be null.
     * @param originatingActivity the Activity where the user initiated the purchase.
     */
    @Override
    public void initiatePurchase(final @NonNull String productId, final @Nullable String offerId, final @NonNull Activity originatingActivity) {
        if (isDestroyed)
            return;

        Purchase purchase = activePurchase.getValue();
        Tunnels t = tunnels.getValue();
        if (t == null) {
            if (purchase == null) {
                purchaseManager.initiatePurchase(productId, offerId, originatingActivity);
            } else {
                purchasingDebugMessage.setValue("Attempt to initiate purchase, although unconsumed purchase is available: " + purchase.getProducts());
            }
        } else {
            purchasingDebugMessage.setValue("Attempt to initiate purchase, although tunnels are available");
        }
    }

    /**
     * Initiate consumption of an available, unconsumed product. This is a reconsolidation
     * operation that would occur only if consumption of a product failed (typically for technical
     * reasons) after its purchase.
     *
     * @param requestedProductId a String giving the product identifier of a Purchase that should be consumed
     *            into a tunnel.
     */
    @Override
    public void consumePurchase(String requestedProductId) {
        if (isDestroyed)
            return;

        Purchase purchase = activePurchase.getValue();
        if (purchase != null && purchase.getProducts().contains(requestedProductId)) {
            requestCertification(purchase);
        } else {
            purchasingDebugMessage.setValue("No such purchase to consume: " + requestedProductId);
            purchasingResult.setValue(SubscriptionCheckResultListener.ResultType.PURCHASE_FAILED);
        }
    }

    /**
     * This method will be called when this ViewModel is no longer used and will be destroyed.
     * <p>
     * It is useful when ViewModel observes some data and you need to clear this subscription to
     * prevent a leak of this ViewModel.
     */
    @Override
    protected void onCleared() {
        isDestroyed = true;
        executor.shutdownNow();
        purchaseManager.destroy();
        purchaseManager = null;
    }
}
