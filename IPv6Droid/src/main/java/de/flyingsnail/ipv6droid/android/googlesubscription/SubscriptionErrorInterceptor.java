/*
 *
 *  * Copyright (c) 2018 Dr. Andreas Feldner.
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

import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.core.BaseClientResponse;
import org.jboss.resteasy.client.core.ClientErrorInterceptor;

final class SubscriptionErrorInterceptor implements ClientErrorInterceptor {
    /**
     * Attempt to handle the current {@link ClientResponse}. If this method
     * returns successfully, the next registered
     * {@link ClientErrorInterceptor} will attempt to handle the
     * {@link ClientResponse}. If this method throws an exception, no further
     * interceptors will be processed.
     *
     * @param response
     * @throws RuntimeException RestEasy will abort request processing if any exception is
     *                          thrown from this method.
     */
    @Override
    public void handle(ClientResponse<?> response) throws RuntimeException {
            BaseClientResponse r = (BaseClientResponse) response;
            RuntimeException rte = new RuntimeException("Unexpected problem handling rest response: " + r.getResponseStatus().toString());
            rte.initCause(r.getException());
            throw rte;
    }
}
