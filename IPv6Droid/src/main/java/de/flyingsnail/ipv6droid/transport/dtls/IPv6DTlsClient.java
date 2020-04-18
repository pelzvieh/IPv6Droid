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
import org.bouncycastle.tls.DefaultTlsHeartbeat;
import org.bouncycastle.tls.HeartbeatMode;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsHeartbeat;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.dtlsrequest.AndroidBackedKeyPair;

import static org.bouncycastle.tls.CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256;

/**
 * A TlsClient as defined by the Bouncy Castle low level TLS API, sub-class-configured to serve
 * for the IPv6Droid DTLS implementation.
 */
class IPv6DTlsClient extends AbstractTlsClient {

    private final int heartbeat;

    private final TlsCertificate trustedCA;

    private final AndroidBackedKeyPair androidBackedKeyPair;
    private final Certificate myCertChain;

    private final Logger logger = Logger.getLogger(DTLSUtils.class.getName());
    private final String dnsName;

    /**
     * Constructor.
     * @param crypto the TlsCrypto object to use with this TLS client.
     * @param heartbeat the heartbeat interval in milliseconds.
     * @param certChain a Certificate object carrying the complete client certificate chain.
     * @param androidBackedKeyPair an AndroidBackedKeyPair object referring to the RSA keypair to use
     * @param dnsName a String giving the host name of the server, used for cert verification
     */
    public IPv6DTlsClient(final TlsCrypto crypto,
                          final int heartbeat,
                          final Certificate certChain,
                          final AndroidBackedKeyPair androidBackedKeyPair,
                          final String dnsName) {
        super(crypto);
        this.heartbeat = heartbeat;
        this.androidBackedKeyPair = androidBackedKeyPair;
        this.trustedCA = certChain.getCertificateAt(certChain.getLength()-1);
        this.myCertChain = certChain;
        this.dnsName = dnsName;
    }

    /**
     * This implementation allows peer to send heartbeat.
     * @return the constant short to allow peer's heartbeat.
     */
    @Override
    public short getHeartbeatPolicy() {
        return HeartbeatMode.peer_allowed_to_send;
    }

    /**
     * Query for a Heartbeat object configured to the heartbeat interval of this client.
     * @return a TlsHeartbeat object configured for this client.
     */
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
        return new IPv6TlsAuthentication(trustedCA,
                (Vector<SignatureAndHashAlgorithm>)context.getSecurityParametersHandshake().getClientSigAlgs(),
                androidBackedKeyPair.getTlsCredentialedSigner(myCertChain),
                dnsName);
    }

    @Override
    protected Vector getSupportedSignatureAlgorithms() {
        Vector supportedAlgorithm = new Vector(1);
        supportedAlgorithm.add(androidBackedKeyPair.getSignatureAndHashAlgorithm());
        return supportedAlgorithm;
    }

    /**
     * Extension point of Bouncycastle: log a received TLS alert message
     * @param alertLevel a short from AlertLevel.... definitions indication the level of TLS alert
     * @param alertDescription a short from AlertDescription... definitions indicating what the alert is about
     * @param message a String giving a textual message
     * @param cause a Throwable giving the Exception that caused the alert
     */
    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        logger.log((alertLevel == AlertLevel.fatal) ? Level.WARNING : Level.INFO, "DTLS client raised alert: " + AlertLevel.getText(alertLevel)
                + ", " + AlertDescription.getText(alertDescription) + ": " + message, cause);
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
