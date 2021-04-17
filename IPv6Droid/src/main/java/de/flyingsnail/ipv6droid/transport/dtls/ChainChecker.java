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

import androidx.annotation.NonNull;

import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.util.encoders.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertPath;
import java.security.cert.CertPathChecker;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author pelzi
 *
 */
class ChainChecker {
  
  private final Logger logger = Logger.getLogger(ChainChecker.class.getName());
  
  private final CertificateFactory certificateFactory;

  private final CertPathValidator certPathValidator;

  private final Set<TrustAnchor> trustAnchors;
  
  public ChainChecker(final TlsCertificate trustedCA, String dnsName) {
    try {
      CertificateFactory newFactory;
      try {
        newFactory = CertificateFactory.getInstance("X.509", "BC");
      } catch (NoSuchProviderException | CertificateException e) {
        logger.info("Bouncy Castle provider of CertificateFactory not available on this device");
        newFactory = CertificateFactory.getInstance("X.509");
      }
      certificateFactory = newFactory;
    } catch (CertificateException e) {
      throw new IllegalStateException("No X.509 certificate factory available");
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
      CertPathValidator myValidator;
      try {
        myValidator = CertPathValidator.getInstance("PKIX", certificateFactory.getProvider());
      } catch (NoSuchAlgorithmException e) {
        logger.info("CertPathValidator not provided by provider of CertificateFactory");
        /* this _could_ mean that the provider of the cert factory does not provide
           an PKIX algorithm. */
        myValidator = CertPathValidator.getInstance("PKIX");
        logger.info("CertificateFactory is " + certificateFactory.getProvider().getName() + "; PKIX provider is " + myValidator.getProvider().getName());
      }
      certPathValidator = myValidator;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No PKIX cert path builder available", e);
    }

  }

  private PKIXCertPathChecker setupRevocationChecker() {
    PKIXCertPathChecker revocationChecker;
    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        logger.info("Building wrapper certPathChecker");
        final PKIXRevocationChecker actualRevocationChecker = (PKIXRevocationChecker)certPathValidator.getRevocationChecker();
        Set<Option> options = new HashSet<Option>(3);
        Collections.addAll(options, new Option[]{Option.PREFER_CRLS, Option.NO_FALLBACK, Option.SOFT_FAIL});
        actualRevocationChecker.setOptions(options); // TODO remove SOFT_FAIL as soon as we have CRL publication under control
        revocationChecker = new PKIXRevocationChecker() {

          static final String cdpExtOid = "2.5.29.31";

          @Override
          public void init(boolean forward) throws CertPathValidatorException {
            logger.info("Initializing wrapping cert path checker");
            actualRevocationChecker.init(forward);
          }

          @Override
          public boolean isForwardCheckingSupported() {
            return actualRevocationChecker.isForwardCheckingSupported();
          }

          @Override
          public Set<String> getSupportedExtensions() {
            logger.info("Wrapped Support Extensions");
            Set<String> actuallySupportedExtensions = actualRevocationChecker.getSupportedExtensions();
            actuallySupportedExtensions.add(cdpExtOid);
            return actuallySupportedExtensions;
          }

          @Override
          public void check(Certificate cert, Collection<String> unresolvedCritExts) throws CertPathValidatorException {
            if (unresolvedCritExts.remove(cdpExtOid)) {
              logger.info("Wrapper revocation checker removed " + cdpExtOid + " extension from unresolved.");
            }
            actualRevocationChecker.check(cert, unresolvedCritExts);
          }

          @NonNull
          @Override
          public String toString() {
            return "wrapped revocation checker, delegating to " + actualRevocationChecker.toString();
          }

          @Override
          public List<CertPathValidatorException> getSoftFailExceptions() {
            return Collections.emptyList();
          }
        };
      } catch (UnsupportedOperationException e) {
        logger.info("revocation checking not available on used bouncy castle provider");
        revocationChecker = null;
      }
    } else {*/
      logger.info("revocation checking not available on this plattform version");
      revocationChecker = new PKIXCertPathChecker() {
        static final String cdpExtOid = "2.5.29.31";
        @Override
        public void init(boolean forward) throws CertPathValidatorException {
          logger.info("Using dummy revocation checker");
        }

        @Override
        public boolean isForwardCheckingSupported() {
          return true;
        }

        @Override
        public Set<String> getSupportedExtensions() {
          Set<String> xt = new HashSet<>(1);
          xt.add(cdpExtOid);
          return xt;
        }

        @Override
        public void check(Certificate cert, Collection<String> unresolvedCritExts) throws CertPathValidatorException {
          if (unresolvedCritExts.remove(cdpExtOid)) {
            logger.info("Dummy revocation checker removed " + cdpExtOid + " extension from unresolved.");
          }
        }

        @NonNull
        @Override
        public String toString() {
          return "dummy revocation checker";
        }
      };
    //}
    return revocationChecker;
  }

  /**
   * @param chain a TlsCertificate[] starting with the certificate of the peer, ending with the CA
   * @throws IOException in case of syntactical errors in the certificates presented
   * @throws TlsFatalAlert in case of unverifyable trust chain.
   */
  public void checkChain(TlsCertificate[] chain) throws IOException, TlsFatalAlert {
    final X509CertSelector target = new X509CertSelector();
    final List<X509Certificate> intermediates = new ArrayList<>(chain.length);
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
      /*CertStoreParameters intermedParam = new CollectionCertStoreParameters(intermediates);
      params.addCertStore(CertStore.getInstance("Collection", intermedParam));*/
      //if (revocationChecker != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      params.setRevocationEnabled(false);
      params.addCertPathChecker(setupRevocationChecker());
      String diag = "Cert Path Checkers used: ";
      for (CertPathChecker cpc: params.getCertPathCheckers()) {
        diag += "\n"+cpc.toString();
      }
      logger.info(diag);
      //} else {
      //  params.setRevocationEnabled(false);
      //}
      // build a standard cert path from the bc low level cert array
      ArrayList<X509Certificate> certArray = new ArrayList<>(chain.length);
      for (TlsCertificate bcCert: chain) {
        certArray.add((X509Certificate) certificateFactory.generateCertificate(
                new ByteArrayInputStream(bcCert.getEncoded())
        ));
      }
      CertPath certPath = certificateFactory.generateCertPath(certArray);
      certPathValidator.validate(certPath, params);
      logger.info("Peer authenticated by valid certificate chain");
    } catch (InvalidAlgorithmParameterException e) {
      throw new TlsFatalAlert(AlertDescription.internal_error, e);
    } catch (CertPathValidatorException e) {
      StringBuilder diagnostics = new StringBuilder("Failed to verify cert chain:\n");
      for (TlsCertificate cert: chain)
        diagnostics.append(
            "-----BEGIN CERTIFICATE-----\n" 
            + Base64.toBase64String(cert.getEncoded())
            + "\n-----END CERTIFICATE-----\n\n");
      logger.info(diagnostics.toString());
      logger.info("Error at cert #" + e.getIndex());

      throw new TlsFatalAlert (AlertDescription.unknown_ca, e);
    } catch (CertificateException e) {
      logger.log(Level.WARNING, "Invalid certificate presented", e);
      throw new TlsFatalAlert(AlertDescription.bad_certificate, e);
    }
  }
}
