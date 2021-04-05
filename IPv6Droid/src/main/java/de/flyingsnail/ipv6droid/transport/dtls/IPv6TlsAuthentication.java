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

package de.flyingsnail.ipv6droid.transport.dtls;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * This class implements the TlsAuthentication interface required by the Bouncycastle DTLS
 * framework. It creates the authenticator for client-side validation, and it validates the
 * certificate chain presented by the server.
 */
class IPv6TlsAuthentication implements TlsAuthentication {
    private final Logger logger = Logger.getLogger(IPv6TlsAuthentication.class.getName());

    private final Vector<SignatureAndHashAlgorithm> clientSigAlgs;

    private final TlsCredentialedSigner tlsCredentialedSigner;

    private final ChainChecker chainChecker;

    /**
     * Constructor.
     * @param trustedCA a TlsCertificate giving the one CA certificate that we are going to trust.
     * @param clientSigAlgs a Vector of SignatureAndHashAlgorithm objects accepted by the client.
     * @param tlsCredentialedSigner a TlsCredentialedSigner, our signing object
     * @param dnsName the host name of the server, must be matched by certificate
     */
    IPv6TlsAuthentication(final TlsCertificate trustedCA,
                          final Vector<SignatureAndHashAlgorithm> clientSigAlgs,
                          final TlsCredentialedSigner tlsCredentialedSigner,
                          final String dnsName) {
        this.clientSigAlgs = clientSigAlgs;
        this.tlsCredentialedSigner = tlsCredentialedSigner;

        chainChecker = new ChainChecker(trustedCA, dnsName);
    }

    @Override
    public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
        TlsCertificate[] chain = serverCertificate.getCertificate().getCertificateList();
        logger.info("Cert chain received of "+chain.length);
        if (chain.length < 2) {
            throw new VerboseTlsFatalAlert(AlertDescription.no_certificate, null);
        }
        chainChecker.checkChain(chain);
    }

    @Override
    public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
        logger.info("Client credentials requested");
        short[] certificateTypes = certificateRequest.getCertificateTypes();
        if (certificateTypes == null ||
                !Arrays.contains(certificateTypes, ClientCertificateType.rsa_sign)) {
            logger.warning("Client certificate type rsa_sign not supported");
            return null;
        }
        if (!clientSigAlgs.contains(tlsCredentialedSigner.getSignatureAndHashAlgorithm())) {
            logger.warning("Signature algorithm SHA256withRSA not supported");
            return null;
        }
        return tlsCredentialedSigner;
    }
}
