/*
 *
 *  * Copyright (c) 2016 Dr. Andreas Feldner.
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

import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import de.flyingsnail.ipv6server.restapi.SubscriptionsApi;

/**
 * Create stubs on the REST api of IPv6server
 * Created by pelzi on 27.06.16.
 */
public class RestProxyFactory {
    static {
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());
    }
    public static SubscriptionsApi createSubscriptionsClient() {
        return ProxyFactory.create(SubscriptionsApi.class, "https://flyingsnail.de/services/services/");
    }
}
