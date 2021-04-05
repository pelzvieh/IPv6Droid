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

import android.os.Build;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.PKIXRevocationChecker.Option;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;


/**
 * @author pelzi
 *
 */
class ChainChecker {
  
  private final Logger logger = Logger.getLogger(ChainChecker.class.getName());
  
  private final CertificateFactory certificateFactory;

  private PKIXRevocationChecker revocationChecker;

  private final CertPathBuilder certPathBuilder;

  private final Set<TrustAnchor> trustAnchors;
  
  public ChainChecker(final TlsCertificate trustedCA, String dnsName) {
    try {
      certificateFactory = CertificateFactory.getInstance("x.509", "BC");
    } catch (CertificateException | NoSuchProviderException e) {
      throw new IllegalStateException("No x.509 certificate factory available");
    }

    trustAnchors = new HashSet<>();
    try {
      trustAnchors.add(
          new TrustAnchor ((X509Certificate) certificateFactory.generateCertificate(
              new ByteArrayInputStream(trustedCA.getEncoded())),
              null
          )
      );
    } catch (CertificateException | IOException e) {
      throw new IllegalStateException("Cannot create trust anchors", e);
    }
    
    try {
      certPathBuilder = CertPathBuilder.getInstance("PKIX", "BC");
    } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
      throw new IllegalStateException("No PKIX cert path builder available", e);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        revocationChecker = (PKIXRevocationChecker) certPathBuilder.getRevocationChecker();
        revocationChecker.setOptions(EnumSet.of(Option.PREFER_CRLS, Option.NO_FALLBACK, Option.SOFT_FAIL)); // TODO remove SOFT_FAIL as soon as we have CRL publication under control
      } catch (UnsupportedOperationException e) {
        logger.info("revocation checking not available on used bouncy castle provider");
        revocationChecker = null;
      }
    } else {
      logger.info("revocation checking not available on this plattform version");
      revocationChecker = null;
    }
  }

  /**
   * @param chain a TlsCertificate[] starting with the certificate of the peer, ending with the CA
   * @throws IOException in case of syntactical errors in the certificates presented
   * @throws TlsFatalAlert in case of unverifyable trust chain.
   */
  public void checkChain(TlsCertificate[] chain) throws IOException, TlsFatalAlert {
    final X509CertSelector target = new X509CertSelector();
    final List<X509Certificate> intermediates = new ArrayList<X509Certificate>(chain.length);
    // some pointless conversions required
    try {
      X509Certificate clientStdCert = (X509Certificate)certificateFactory.generateCertificate(
          new ByteArrayInputStream(chain[0].getEncoded())
      );
      target.setCertificate(clientStdCert);

      for (TlsCertificate interCert: chain) {
        intermediates.add((X509Certificate)certificateFactory.generateCertificate(
            new ByteArrayInputStream(interCert.getEncoded())
        ));
      }
    } catch (CertificateException e) {
      throw new TlsFatalAlert(AlertDescription.certificate_unknown, e);
    }
    
    try {
      PKIXBuilderParameters params = new PKIXBuilderParameters(trustAnchors, target);
      CertStoreParameters intermedParam = new CollectionCertStoreParameters(intermediates);
      params.addCertStore(CertStore.getInstance("Collection", intermedParam));
      if (revocationChecker != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        params.addCertPathChecker(revocationChecker);
      } else {
        params.setRevocationEnabled(false);
      }
      certPathBuilder.build(params);
      logger.info("Peer authenticated by valid certificate chain");
    } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
      throw new TlsFatalAlert(AlertDescription.internal_error, e);
    } catch (CertPathBuilderException e) {
      StringBuffer diagnostics = new StringBuffer("Failed to verify cert chain:\n");
      for (TlsCertificate cert: chain)
        diagnostics.append(
            "-----BEGIN CERTIFICATE-----\n" 
            + Base64.toBase64String(cert.getEncoded())
            + "\n-----END CERTIFICATE-----\n\n");
      logger.info(diagnostics.toString());

      throw new TlsFatalAlert (AlertDescription.unknown_ca, e);
    }
  }

}
