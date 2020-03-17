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

package de.flyingsnail.ipv6droid.android.googlesubscription;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.Inet4Address;
import java.util.Date;

import de.flyingsnail.ipv6server.restapi.SubscriptionsApi;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


/**
 * Create stubs on the REST api of IPv6server
 * Created by pelzi on 27.06.16.
 */
class RestProxyFactory {
    static SubscriptionsApi createSubscriptionsClient(String baseUrl) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Inet4Address.class, new SimpleInetDeserializer())
                .registerTypeAdapter(Date.class, new SimpleDateSerializer())
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        return retrofit.create(SubscriptionsApi.class);
    }
}
