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

import org.bouncycastle.tls.AbstractTlsClient;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.AlertLevel;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DefaultTlsHeartbeat;
import org.bouncycastle.tls.HeartbeatMode;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsHeartbeat;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bouncycastle.tls.CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256;

/**
 * A TlsClient as defined by the Bouncy Castle low level TLS API, sub-class-configured to serve
 * for the IPv6Droid DTLS implementation.
 */
class IPv6DTlsClient extends AbstractTlsClient {

    private final int heartbeat;

    private final TlsCertificate trustedCA;

    private Logger logger = Logger.getLogger(DTLSUtils.class.getName());

    private Certificate certChain;

    private PrivateKey privateKey;

    /**
     * Constructor.
     * @param crypto the TlsCrypto object to use with this TLS client.
     * @param heartbeat the heartbeat interval in milliseconds.
     * @param certChain a Certificate object carrying the complete certificate chain.
     * @param privateKey a AsymmetricKeyParameter giving the corresponding private key.
     */
    public IPv6DTlsClient(TlsCrypto crypto, int heartbeat, Certificate certChain, PrivateKey privateKey) {
        super(crypto);
        this.heartbeat = heartbeat;
        this.certChain = certChain;
        this.privateKey = privateKey;

        trustedCA = certChain.getCertificateAt(certChain.getLength()-1);
    }

    /**
     * This implementation allows peer to send heartbeat.
     * @return the constant short to allow peer's heartbeat.
     */
    @Override
    public short getHeartbeatPolicy() {
        return HeartbeatMode.peer_allowed_to_send;
    }

    @Override
    public TlsHeartbeat getHeartbeat() {
        return new DefaultTlsHeartbeat(heartbeat, 10000);
    }

    /**
     * This implementation checks the server certificate against a pinned root CA bundled with
     * its jar.
     * @return the TlsAuthentication object for this client.
     */
    @Override
    public TlsAuthentication getAuthentication() {
        return new TlsAuthentication() {
            @Override
            public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws IOException {
                TlsCertificate[] chain = serverCertificate.getCertificate().getCertificateList();
                logger.info("Cert chain received of "+chain.length);
                if (chain.length == 0)
                    throw new TlsFatalAlert(AlertDescription.certificate_required);
                for (int i = 0; i < chain.length; i++) {
                    org.bouncycastle.asn1.x509.Certificate entry = org.bouncycastle.asn1.x509.Certificate.getInstance(chain[i].getEncoded());
                    logger.info(" Cert["+i+"] subject: " + entry.getSubject());
                }

                if (!DTLSUtils.areSameCertificate(chain[chain.length-1], trustedCA)) {
                    throw new TlsFatalAlert(AlertDescription.certificate_unknown);
                }
            }

            @Override
            public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) throws IOException {
                logger.info("Client credentials requested");
                short[] certificateTypes = certificateRequest.getCertificateTypes();
                if (certificateTypes == null || !Arrays.contains(certificateTypes, ClientCertificateType.rsa_sign)) {
                    return null;
                }
                Vector<SignatureAndHashAlgorithm> clientSigAlgs = (Vector<SignatureAndHashAlgorithm>)context.getSecurityParametersHandshake().getClientSigAlgs();

                try {
                    return DTLSUtils.loadSignerCredentials(context, clientSigAlgs,
                            SignatureAlgorithm.rsa, certChain, privateKey);
                } catch (NoSupportedAlgorithm noSupportedAlgorithm) {
                    throw new IOException(noSupportedAlgorithm);
                }
            }
        };
    }

    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        logger.log((alertLevel == AlertLevel.fatal) ? Level.WARNING : Level.INFO, "DTLS client raised alert: " + AlertLevel.getText(alertLevel)
                + ", " + AlertDescription.getText(alertDescription), cause);
    }

    @Override
    public void notifyAlertReceived(short alertLevel, short alertDescription) {
        logger.log((alertLevel == AlertLevel.fatal) ? Level.WARNING : Level.INFO, "DTLS client received alert: " + AlertLevel.getText(alertLevel)
                        + ", " + AlertDescription.getText(alertDescription));
    }

    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10);
    }

    @Override
    protected int[] getSupportedCipherSuites() {
        return new int[]{TLS_DHE_RSA_WITH_AES_128_CBC_SHA256};
    }
}
