/*
 *
 *  * Copyright (c) 2021 Dr. Andreas Feldner.
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

import de.flyingsnail.ipv6server.svc.CertificationRejectedException;
import de.flyingsnail.ipv6server.svc.SubscriptionRejectedException;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.POST;

/**
 * This interface defines the API for the IPv6Droid client to get an DTLS certificate
 * for one of the tunnels associated with the user's account.
 * 
 * @author pelzi
 *
 */
public interface CertificationApi {

  /**
   * <p>Check validity of supplied TicTunnel that supposedly were returned from a previous call
   * to SubscriptionsApi, but are not validated at that time.</p>
   * <p>If valid, verify that the CSR can be matched to that tunnel. If so,
   * issue an X509 certificate and return the certificate path, starting with the client certificate at
   * position 0, and the trusted CA's certificate at last.</p>
   * <p>The following validation steps are run through:</p>
   * <ol>
   *   <li>Do we have such a TicTunnel created?</li>
   *   <li>Is the correct credential presented in the TicTunnel structure?</li>
   *   <li>Check if the public key supplied with the CSR is used for a previously issued certificate; if so,
   *       return the previous certificate if it is still valid, or (normally) issue a new certificate
   *       valid until the end of TicTunnel's validity (as from our database -- NOT taken from the supplied instance).</li>
   *   <li>Return the list of X509 certificates, PEM encoded, forming the certificate chain.</li>
   * </ol>
   * @param ipv6address the IPv6 address that the caller claims to have a tunnel for
   * @param tunnelPassword a String giving the password of that tunnel
   * @param csrPemString a String containing PEM encoded PKCS#10 certificate signing request
   * @return a List of Strings of PEM encoded X509 certificates forming a certificate chain, starting from
   *     the client's new or existing certificate, ending with the trusted CA's certificate.
   * @throws IOException in case of technical problems
   * @throws CertificationRejectedException in case of definitive falsification of supplied data
   */
  @FormUrlEncoded
  @POST("certification/associate")
  Call<List<String>> associateTunnelAndSignCSR(
          @Field("ipv6address") String ipv6address,
          @Field("tunnelpassword") String tunnelPassword,
          @Field("pem_encoded_pkcs10_signing_request") String csrPemString
  ) throws CertificationRejectedException, IOException;

  @FormUrlEncoded
  @POST("certification/checkandsign")
  Call<List<String>> checkSubscriptionAndSignCSR(
          @Field("data") String subscriptionData,
          @Field("signature") String signature,
          @Field("pem_encoded_pkcs10_signing_request") String csrPemString
  ) throws SubscriptionRejectedException, CertificationRejectedException, IOException;
}
