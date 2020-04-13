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

package de.flyingsnail.ipv6droid.android.dtlsrequest;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class AndroidBackedKeyPair {

    private static final String TAG = AndroidBackedKeyPair.class.getName();

    private final KeyStore keyStore;

    private final KeyPair keyPair;

    private final String alias;

    /*
     * Generate a new RSA key pair entry in the Android Keystore by
     * using the KeyPairGenerator API. The private key can only be
     * used for signing, decryption, and verification and only with SHA-256 or
     * SHA-512 as the message digest.
     */

    public static KeyPair create(String alias) {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");

            kpg.initialize(
                    new KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1, KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .build());
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new IllegalStateException("Standard algorithms not available on this device", e);
        }

        KeyPair kp = kpg.generateKeyPair();
        Log.i(TAG, "Created new keypair for alias " + alias);
        return kp;
    }

    /**
     * List the entries of the Android key store
     * @return a List&lt;String&gt; giving all aliases in keystore.
     */
    public static List<String> listAliases() throws IOException {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Cannot access AndroidKeyStore", e);
        }
        Enumeration<String> aliases;
        try {
            ks.load(null);
            aliases = ks.aliases();
        } catch (CertificateException | KeyStoreException e) {
            throw new IOException(e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm for elements of Keystore not available", e);
        }

        List<String> retval = new ArrayList<>(5);
        while (aliases.hasMoreElements()) {
            retval.add(aliases.nextElement());
        }
        Log.i(TAG, "Found " + retval.size() + " elements in AndroidKeyStore");
        return retval;
    }

    public AndroidBackedKeyPair(final String alias) throws IOException {
        this.alias = alias;
        KeyStore.Entry entry = null;

        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            // read keypair for alias
            entry = keyStore.getEntry(alias, null);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | UnrecoverableEntryException e) {
            throw new IllegalStateException("Cannot access AndroidKeyStore", e);
        }
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new IllegalArgumentException("Not an instance of PrivateKeyEntry");
        }
        keyPair = new KeyPair(((KeyStore.PrivateKeyEntry) entry).getCertificate().getPublicKey(),
                ((KeyStore.PrivateKeyEntry) entry).getPrivateKey());
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }


    public AsymmetricKeyParameter getEncoidedPrivateKey() {
        // todo conversion of conversion of conversion, is it really the efficient way to go?
        byte[] rawBytes = keyPair.getPrivate().getEncoded();
        if (rawBytes == null)
            throw new IllegalStateException("Cannot get encoded representation of private key");
        PKCS8EncodedKeySpec pkcs8 = new PKCS8EncodedKeySpec(rawBytes);
        if (pkcs8 == null)
            throw new IllegalStateException("Could not generate PKCS#8 representation of private key");
        RSAPrivateKey rsa = RSAPrivateKey.getInstance(pkcs8.getEncoded());
        if (rsa == null)
            throw new IllegalStateException("Could not instatiate RSA private key representation of private key");
        return new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsa.getPublicExponent(),
                rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(),
                rsa.getExponent2(), rsa.getCoefficient());
/*
        RSAPublicKey rsaP = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateCrtKeyParameters parms = new RSAPrivateCrtKeyParameters(rsa.getModulus(), rsaP.getPublicExponent(),
                rsa.getPrivateExponent(), rsa.getPrime1(), rsa.getPrime2(), rsa.getExponent1(),
                rsa.getExponent2(), rsa.getCoefficient());
        RSAPrivateKey.getInstance()
        new AsymmetricKeyParameter()*/
    }

    public String getCertificationRequest() throws IOException {
        JcaPKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(new X500Name("CN="+alias), getPublicKey());
        PKCS10CertificationRequest request = builder.build(new ContentSigner() {
            private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.840.113549.1.1.11"));
            }

            @Override
            public OutputStream getOutputStream() {
                return outputStream;
            }

            @Override
            public byte[] getSignature() {
                Signature s;
                try {
                    s = Signature.getInstance("SHA256withRSA");
                    s.initSign(getPrivateKey());
                    s.update(outputStream.toByteArray());
                    byte[] signature = s.sign();
                    outputStream.reset();
                    return signature;
                } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
                    throw new IllegalStateException("Cannot calculate a signature", e);
                }
            }
        });
        CharArrayWriter charWriter = new CharArrayWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(charWriter);
        pemWriter.writeObject(request);
        pemWriter.close();
        return charWriter.toString();
    }

}
