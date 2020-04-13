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

import android.util.Log;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.DefaultTlsCredentialedSigner;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsContext;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.TlsSigner;
import org.bouncycastle.tls.crypto.TlsStreamSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

/**
 * This is a collection of static methods to help loading standard PEM resources into the somewhat
 * enigmatic low-level objects of Bouncy Castle DTLS implementation. Because DTLS is only available
 * from the low-level, not the JCA standard, familiar resources like java keystores cannot be easily
 * used.
 *
 * Refer to the @link{https://github.com/bcgit/bc-java/blob/master/tls/src/test/java/org/bouncycastle/tls/test/TlsTestUtils.java} BC implementation.
 */
class DTLSUtils {
    private static String TAG = DTLSUtils.class.getName();

    private DTLSUtils() {}


    public static TlsCredentialedDecryptor loadEncryptionCredentials(TlsContext context, Certificate certificate, AsymmetricKeyParameter privateKey) {
        TlsCrypto crypto = context.getCrypto();

        return new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto)crypto, certificate, privateKey);
    }

    private static AsymmetricKeyParameter loadBcPrivateKeyResource(String keyResource) throws IOException {
        PemObject pem = loadPemResource(keyResource);
        switch (pem.getType()) {
            case "PRIVATE KEY":
                return PrivateKeyFactory.createKey(pem.getContent());
            case "RSA PRIVATE KEY":
                // this is the relevant case with the bundled resources
                RSAPrivateKey rsa = RSAPrivateKey.getInstance(pem.getContent());
                return new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(),
                        rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(),
                        rsa.getExponent2(), rsa.getCoefficient());
            case "EC PRIVATE KEY":
                ECPrivateKey pKey = ECPrivateKey.getInstance(pem.getContent());
                AlgorithmIdentifier algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, pKey.getParameters());
                PrivateKeyInfo privInfo = new PrivateKeyInfo(algId, pKey);
                return PrivateKeyFactory.createKey(privInfo);
            case "ENCRYPTED PRIVATE KEY":
                throw new UnsupportedOperationException("Encrypted PKCS#8 keys not supported");
        }
        throw new IllegalArgumentException("No supported private key in resource " + keyResource);
    }

    static AsymmetricKeyParameter parseBcPrivateKeyString(String keyString) throws IOException {
        PemObject pem = parsePemString(keyString);
        switch (pem.getType()) {
            case "PRIVATE KEY":
                return PrivateKeyFactory.createKey(pem.getContent());
            case "RSA PRIVATE KEY":
                // this is the relevant case with the bundled resources
                RSAPrivateKey rsa = RSAPrivateKey.getInstance(pem.getContent());
                return new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(),
                        rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(),
                        rsa.getExponent2(), rsa.getCoefficient());
            case "EC PRIVATE KEY":
                ECPrivateKey pKey = ECPrivateKey.getInstance(pem.getContent());
                AlgorithmIdentifier algId = new AlgorithmIdentifier(X9ObjectIdentifiers.id_ecPublicKey, pKey.getParameters());
                PrivateKeyInfo privInfo = new PrivateKeyInfo(algId, pKey);
                return PrivateKeyFactory.createKey(privInfo);
            case "ENCRYPTED PRIVATE KEY":
                throw new UnsupportedOperationException("Encrypted PKCS#8 keys not supported");
        }
        throw new IllegalArgumentException("No supported private key encoded in supplied string");
    }

    private static Certificate loadCertificateChain (TlsContext context, String[] certResources) throws IOException {
        return loadCertificateChain(context.getCrypto(), certResources);
    }

    private static Certificate loadCertificateChain(TlsCrypto crypto, String[] certResources) throws IOException {
        TlsCertificate[] chain = new TlsCertificate[certResources.length];
        for (int i = 0; i < certResources.length; ++i)
        {
            chain[i] = loadCertificateResource(crypto, certResources[i]);
        }
        return new Certificate(chain);
    }

    /**
     * Parse the supplied List of PEM encoded strings into a Certificate object representing
     * the whole chain.
     * @param crypto the TlsCrypto object to use.
     * @param certStrings a List&lt;String&gt; giving this instance's certificate at position 0
     *                    and the trusted CA's certificate as the last element. All strings are
     *                    expected to be PEM encoded X509 certificates.
     * @return a Certificate representing the certificate chain.
     * @throws IOException in case of parsing errors
     */
    static Certificate parseCertificateChain(TlsCrypto crypto, List<String> certStrings) throws IOException {
        ArrayList<TlsCertificate> chain = new ArrayList<>(certStrings.size());
        for (String certString: certStrings) {
            chain.add(parseCertificateString(crypto, certString));
        }
        return new Certificate(chain.toArray(new TlsCertificate[0]));
    }

    private static TlsCertificate loadCertificateResource(TlsCrypto crypto, String certResource) throws IOException {
        PemObject pem = loadPemResource(certResource);
        if (pem != null && pem.getType().endsWith("CERTIFICATE")) {
            return crypto.createCertificate(pem.getContent());
        }
        throw new IllegalArgumentException("'resource' doesn't specify a valid certificate");
    }

    private static TlsCertificate parseCertificateString(TlsCrypto crypto, String certString) throws IOException {
        PemObject pem = parsePemString(certString);
        if (pem != null && pem.getType().endsWith("CERTIFICATE")) {
            return crypto.createCertificate(pem.getContent());
        }
        throw new IllegalArgumentException("Supplied PEM string doesn't specify a valid certificate");
    }

    private static PemObject loadPemResource(String pemResource) throws IOException {
        InputStream s = DTLSUtils.class.getResourceAsStream(pemResource);
        if (s == null) {
            throw new IOException("Resource "+ pemResource + " not found in classpath");
        }
        PemReader p = new PemReader(new InputStreamReader(s));
        PemObject o = p.readPemObject();
        p.close();
        return o;
    }

    private static PemObject parsePemString(String pemString) throws IOException {
        PemReader p = new PemReader(new StringReader(pemString));
        PemObject o = p.readPemObject();
        p.close();
        if (o == null)
            throw new IllegalArgumentException("Supplied string is not valid PEM");
        return o;
    }

    private static SignatureAndHashAlgorithm algorithmForCode (Vector<SignatureAndHashAlgorithm> supportedSignatureAlgorithms, short signatureAlgorithm)  throws NoSupportedAlgorithm {
        if (supportedSignatureAlgorithms == null) {
            supportedSignatureAlgorithms = (Vector<SignatureAndHashAlgorithm>)TlsUtils.getDefaultSignatureAlgorithms(signatureAlgorithm);
        }

        SignatureAndHashAlgorithm signatureAndHashAlgorithm = null;
        for (SignatureAndHashAlgorithm alg: supportedSignatureAlgorithms) {
            if (alg.getSignature() == signatureAlgorithm) {
                // Just grab the first one we find
                signatureAndHashAlgorithm = alg;
                break;
            }
        }

        if (signatureAndHashAlgorithm == null) {
            throw new NoSupportedAlgorithm("None of the supported signature algorithms generates the required signature type");
        }
        return signatureAndHashAlgorithm;
    }


    static TlsCredentialedSigner loadSignerCredentials(TlsContext context,
                                                       Vector<SignatureAndHashAlgorithm> supportedSignatureAlgorithms,
                                                       short signatureAlgorithm,
                                                       Certificate certificate,
                                                       PrivateKey privateKey) throws NoSupportedAlgorithm {
        TlsCrypto crypto = context.getCrypto();
        TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);
        SignatureAndHashAlgorithm signatureAndHashAlgorithm = algorithmForCode(supportedSignatureAlgorithms, signatureAlgorithm);

        TlsSigner signer = new TlsSigner() {
            @Override
            public byte[] generateRawSignature(SignatureAndHashAlgorithm algorithm, byte[] hash) throws IOException {
                Signature s = null;
                if (algorithm.getSignature() != SignatureAlgorithm.rsa)
                    throw new IOException("Signature algorithm of algorithm  not supported: " + algorithm.toString());
                try {
                    s = Signature.getInstance("SHA256withRSA"); // todo derive from algorithm, or at least check it to be the one supported algorithm
                    s.initSign(privateKey);
                    s.update(hash);
                    byte[] signature = s.sign();
                    return signature;
                } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
                    throw new IOException("Cannot create requested signature", e);
                }
            }

            @Override
            public TlsStreamSigner getStreamSigner(SignatureAndHashAlgorithm algorithm) throws IOException {
                return null; // this is actually the same implementation as used with BcTlsRSASigner.... wtf?!
            }
        };
        return new DefaultTlsCredentialedSigner(
                cryptoParams,
                signer,
                certificate,
                signatureAndHashAlgorithm);
    }

    static boolean areSameCertificate(TlsCertificate a, TlsCertificate b) throws IOException {
        return Arrays.areEqual(a.getEncoded(), b.getEncoded());
    }


    /**
     * Examines the SubjectAlternateNames extensions of the supplied certificate and probes for one
     * of type IPAdress. Reconstructs the IP address from the hexdump representation and returns
     * the first name found to be an IPv6Adress.
     * @param cert the TlsCertificate to read a subjectAlternativeName IPv6 address from
     * @return null if no matching extension was found or the Inet6Adress reconstructed from cert
     */
    static Inet6Address getIpv6AlternativeName(TlsCertificate cert)  {
        try {
            Extensions extensions = org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()).getTBSCertificate().getExtensions();
            if (extensions == null) {
                Log.i(TAG, "No certificate extensions presented");
                return null;
            }
            GeneralNames generalNames = GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);
            for (GeneralName generalName : generalNames.getNames()) {
                if (generalName.getTagNo() == GeneralName.iPAddress) {
                    InetAddress inetAddress = InetAddress.getByAddress(
                            new BigInteger(generalName.getName().toString().substring(1), 16).toByteArray());
                    if (inetAddress instanceof Inet6Address) {
                        Log.i(TAG, "Supplied cert contains IPv6 subject alternative name: " + inetAddress);
                        return (Inet6Address) inetAddress;
                    } else {
                        Log.d(TAG, "Found subject alternative name IP address, but not IPv6: " + inetAddress);
                    }
                } else {
                    Log.d(TAG, "Found subject alternative name which is not IP: " + generalName.getName());
                }
            }
            Log.d(TAG, "Supplied cert did not contain an IPv6 subject alternative name");
        } catch (Throwable t) {
            Log.e(TAG, "severe problem occurred", t);
        }
        return null;
    }

    /**
     * Returns the issuer name of the supplied TlsCertificate.
     * @param cert the TlsCertificate to read a subjectAlternativeName from
     * @return null if no matching extension was found or the Inet6Adress reconstructed from cert
     * @throws IOException on encoding errors on the ASN 1 level
     */
    static String getIssuerName(TlsCertificate cert) throws IOException {
        return org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()).getTBSCertificate().getIssuer().toString();
    }

    /**
     * Returns the expiry date of the supplied TlsCertificate.
     * @param cert the TlsCertificate to read the expiryDate from
     * @return the Date of expiry of this certificate, or null if no such attribute encoded
     * @throws IOException on encoding errors on the ASN 1 level
     */
    static Date getExpiryDate(TlsCertificate cert) throws IOException {
        Time endDate = org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()).getTBSCertificate().getEndDate();
        return (endDate == null) ?
                new Date(new Date().getTime() + 1000L*3600L*24L*365L) : // in one year
                endDate.getDate();
    }

    /**
     * Examines the IssuerAlternateNames extensions of the supplied certificate and probes for one
     * of type otherName.
     * @param cert the TlsCertificate to read a subjectAlternativeName from
     * @return null if no matching extension was found or the Inet6Adress reconstructed from cert
     * @throws IOException on encoding errors on the ASN 1 level
     */
    static URL getIssuerUrl(TlsCertificate cert) throws IOException {
        Extensions extensions = org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()).getTBSCertificate().getExtensions();
        if (extensions == null) {
            Log.i(TAG, "No certificate extensions presented");
            return null;
        }
        GeneralNames generalNames = GeneralNames.fromExtensions(extensions, Extension.issuerAlternativeName);
        for (GeneralName generalName: generalNames.getNames()) {
            if (generalName.getTagNo() == GeneralName.uniformResourceIdentifier) {
                return new URL(generalName.getName().toString());
            } else {
                Log.d(TAG, "Found issuer alternative name which is not otherName: "+ generalName.getName());
            }
        }
        Log.d(TAG, "Supplied cert did not contain an otherName issuer alternative name");
        return null;
    }

    /**
     * Reads the subject name from the supplied certificate.
     * @param cert the TlsCertificate to read a subjectName from
     * @return null if no matching extension was found or the X500Name read from cert's subject name
     * @throws IOException on encoding errors on the ASN 1 level
     */
    static X500Name getSubjectName(TlsCertificate cert) throws IOException {
        return org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()).getTBSCertificate().getSubject();
    }

    /**
     * Reads the common name part of the subject name from the supplied certificate.
     * @param cert the TlsCertificate to read the subjectName from
     * @return null if no matching extension was found, or no common name part is in the subjectName,
     *          or the String representing the common name (without CN= prefix).
     * @throws IOException on encoding errors on the ASN 1 level
     */
    static String getSubjectCommonName(TlsCertificate cert) throws IOException {
        X500Name x500Name = getSubjectName(cert);
        if (x500Name == null)
            return null;
        RDN[] rdns = x500Name.getRDNs(X509ObjectIdentifiers.commonName);
        if (rdns == null || rdns.length == 0)
            return null;
        return rdns[0].getFirst().getValue().toString();
    }

}
