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

package de.flyingsnail.ipv6droid.transport.dtls;

import android.os.Build;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.util.Arrays;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Logger;

/**
 * This class implements the TlsAuthentication interface required by the Bouncycastle DTLS
 * framework. It creates the authenticator for client-side validation, and it validates the
 * certificate chain presented by the server.
 */
class IPv6TlsAuthentication implements TlsAuthentication {
    private final Logger logger = Logger.getLogger(IPv6TlsAuthentication.class.getName());

    private final CertificateFactory certificateFactory;

    private final PKIXRevocationChecker revocationChecker;

    private final CertPathBuilder certPathBuilder;

    private final Set<TrustAnchor> trustAnchors;

    private final Vector<SignatureAndHashAlgorithm> clientSigAlgs;

    private final TlsCredentialedSigner tlsCredentialedSigner;
    private final String dnsName;

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
        this.dnsName = dnsName;
        try {
            certificateFactory = CertificateFactory.getInstance("x.509");
        } catch (CertificateException e) {
            throw new IllegalStateException("No x.509 certificate factory available");
        }

        // copy the CA certificate from BouncyCastle object to standard Java object
        trustAnchors = new HashSet<>();
        try {
            trustAnchors.add(
                    new TrustAnchor((X509Certificate) certificateFactory.generateCertificate(
                            new ByteArrayInputStream(trustedCA.getEncoded())),
                            null
                    )
            );
        } catch (CertificateException | IOException e) {
            throw new IllegalStateException("Cannot create trust anchors", e);
        }

        try {
            certPathBuilder = CertPathBuilder.getInstance("PKIX");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No PKIX cert path builder available", e);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            revocationChecker = (PKIXRevocationChecker)certPathBuilder.getRevocationChecker();
            revocationChecker.setOptions(EnumSet.of(
                    PKIXRevocationChecker.Option.PREFER_CRLS,
                    PKIXRevocationChecker.Option.SOFT_FAIL));
        } else {
            revocationChecker = null;
        }
    }

    @Override
    public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
        TlsCertificate[] chain = serverCertificate.getCertificate().getCertificateList();
        logger.info("Cert chain received of "+chain.length);
        if (chain.length == 0)
            throw new TlsFatalAlert(AlertDescription.certificate_required);

        final List<X509Certificate> stdChain;
        try {
            stdChain = new ArrayList<>(chain.length);
            for (TlsCertificate bcCert: chain) {
                stdChain.add((X509Certificate)certificateFactory.generateCertificate(
                        new ByteArrayInputStream(bcCert.getEncoded())));
            }
        } catch (CertificateException e) {
            throw new TlsFatalAlert(AlertDescription.certificate_unknown, e);
        }

        X509CertSelector target = new X509CertSelector();
        target.setCertificate(stdChain.get(0));
        target.addSubjectAlternativeName(2, dnsName);

        PKIXBuilderParameters params;
        try {
            params = new PKIXBuilderParameters(trustAnchors, target);
            CertStoreParameters intermediates = new CollectionCertStoreParameters(java.util.Arrays.asList(stdChain));
            params.addCertStore(CertStore.getInstance("Collection", intermediates));
            params.setRevocationEnabled(false);

            if (revocationChecker != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                params.addCertPathChecker(revocationChecker);
            } else {
                params.setRevocationEnabled(false);
            }
            certPathBuilder.build(params);
            logger.info("Server authenticated by valid certificate chain");
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new TlsFatalAlert(AlertDescription.internal_error, e);
        } catch (CertPathBuilderException e) {
            throw new TlsFatalAlert (AlertDescription.unknown_ca, e);
        }
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
