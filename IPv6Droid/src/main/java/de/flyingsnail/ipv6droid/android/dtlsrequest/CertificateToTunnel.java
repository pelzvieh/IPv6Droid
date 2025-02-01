/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.dtlsrequest;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.transport.dtls.TransporterParams;

public class CertificateToTunnel {
    private final Logger logger = Logger.getLogger(CertificateToTunnel.class.getName());
    private String alias;
    /**
     * Generate a PEM encoded certificate signing request, using on-device key management.
     * @return a String representing a certificate signing request
     * @throws IOException in case of problems generating the CSR
     */
    public String getCsr() throws IOException {
        final List<String> aliases = AndroidBackedKeyPair.listAliases();
        // iterate over existing aliases and try to build CSR from each and return on success
        for (final String alias: aliases) {
            try {
                this.alias = alias;
                return new AndroidBackedKeyPair(alias).getCertificationRequest();
            } catch (IOException e) {
                logger.info("Key pair alias " + alias + " did not convert to CSR: " + e);
                // try the other ones, if none works, generate a new
            }
        }
        // no alias exists or none was convertible to a CSR
        final String newAlias = "IPv6Droid-"+aliases.size();
        AndroidBackedKeyPair.create(newAlias);
        this.alias = newAlias;
        return new AndroidBackedKeyPair(newAlias).getCertificationRequest();
    }

    /**
     * Create a tunnel specification from a given certificate chain.
     * @param keyAlias a String giving the keyAlias that corresponds to the signed certificate
     *                 (indirectly the handle to the private key)
     * @param certConfig a List&lt;String&gt; giving the certificate chain from this device's
     *                   certificate up to the trusted CA certificate.
     * @return a TransporterParams instance that can be used to run a corresponding tunnel
     * @throws IOException in case of technical problems with the given alias, or the given certConfig.
     */
    public static TransporterParams createTunnelspec(String keyAlias, List<String> certConfig) throws IOException {
        final TransporterParams params = new TransporterParams();
        // build params and perform parsing
        try {
            params.setCertChainEncoded(certConfig);
            params.setPrivateKeyAlias(keyAlias);
        } catch (IllegalArgumentException | IllegalStateException illegal) {
            throw new IOException("Invalid certificate configuration", illegal);
        }
        params.setHeartbeatInterval(10*60); // 10 Minutes
        params.setMtu(1300);
        return params;
    }

    public TransporterParams createTunnelSpec(List<String> certConfig) throws IOException {
        if (alias != null) {
            return createTunnelspec(alias, certConfig);
        } else {
            throw new IOException(new IllegalStateException("You must call getCSR first or provide the keyAlias to be used"));
        }
    }
}
