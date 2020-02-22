/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
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

import android.content.Context;

import java.io.IOException;
import java.util.List;

import de.flyingsnail.ipv6droid.android.googlesubscription.SubscriptionCheckResultListener;
import de.flyingsnail.ipv6droid.android.googlesubscription.SubscriptionManager;
import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * Created by pelzi on 18.10.17.
 */

public class SubscriptionTunnelReader implements TunnelReader, SubscriptionCheckResultListener {
    final private SubscriptionManager subscriptionManager;

    private boolean finished = false;
    private boolean failed = false;
    private boolean noSubscriptions = false;

    public SubscriptionTunnelReader(final Context context) {
        subscriptionManager = new SubscriptionManager(this, context);
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
        return subscriptionManager.getTunnels();
    }

    @Override
    public synchronized void onSubscriptionCheckResult(ResultType result, String debugMessage) {
        switch (result) {
            case HAS_TUNNELS:
                finished = true;
                failed = false;
                noSubscriptions = false;
                break;
            case NO_SUBSCRIPTIONS:
            case SUBSCRIPTION_UNPARSABLE:
                finished = true;
                failed = false;
                noSubscriptions = true;
                break;
            case TEMPORARY_PROBLEM:
            case CHECK_FAILED:
            case NO_SERVICE_PERMANENT:
            case NO_SERVICE_TRY_AGAIN:
                finished = true;
                failed = true;
                break;
            case NO_SERVICE_AUTO_RECOVERY:
                finished = false;
                failed = true;
                break;
        }
        notifyAll();
    }
}
