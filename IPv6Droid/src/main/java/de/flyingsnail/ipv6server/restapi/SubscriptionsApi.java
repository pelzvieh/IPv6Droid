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

package de.flyingsnail.ipv6server.restapi;

import java.io.IOException;
import java.util.List;

import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;
import de.flyingsnail.ipv6server.svc.SubscriptionRejectedException;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;


//@Produces(MediaType.APPLICATION_JSON)
//@Consumes(MediaType.APPLICATION_JSON)
public interface SubscriptionsApi {
  /**
   * Create a unique new payload for a new tunnel subscription purchase. The generated payload
   * is stored for a reasonable time (around 1 hour) on the server, to allow verification of a
   * claimed subscription later.
   *
   * @return the String representing the unique payload
   * @throws IOException in case of communication problems with the server.
   */
  @GET("subscriptions/new")
  @Headers("Accept: application/json")
  Call<String> createNewPayload();
  
  /**
   * Invalidate a payload (if it is not already used). The client indicates by this call
   * that it is about to forget this payload anyway, so no further calls involving this
   * payload will occur.
   * @throws IOException in case of communication problems with the server.
   */
  @DELETE("subscriptions/{payload}")
  Call<Void> deleteUnusedPayload(@Path("payload") String payload);
  
  /**
   * Check validity of supplied Subscription object that supposedly is filled from
   * Google Play API. If valid, return the list of tunnels associated with this subscription.
   * The following validation steps are run through:
   * <ol>
   *   <li>Validate signature</li>
   *   <li>Check for existence of client_token in our User database
   *   <li>Check that the user entry with the given client_token has the same orderid associated</li>
   *   <li>If all tests pass, return the list of tunnels associated with the matching user
   *       entry.</li>
   * </ol>
   * @param subscriptionData the Subscription JSON string as sent by Google
   * @param signature the corresponding signature string as sent by Google
   * @return a List of TicTunnel objects constructed
   * @throws IOException in case of technical problems
   * @throws SubscriptionRejectedException in case of definitive falsification of data
   */
  @FormUrlEncoded
  @POST("subscriptions/check")
  Call<List<TicTunnel>> checkSubscriptionAndReturnTunnels(
          @Field("data") String subscriptionData,
          @Field("signature") String signature);
}