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

import androidx.annotation.NonNull;

import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import de.flyingsnail.ipv6droid.android.dtlsrequest.AndroidBackedKeyPair;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class TransporterParams implements TunnelSpec, Serializable {
    private static final String TAG = TransporterParams.class.getName();
    private static ExecutorService resolverPool = Executors.newCachedThreadPool();
    static final String TUNNEL_TYPE = "DTLSTunnel";
    private TlsCrypto crypto;

    private Inet4Address ipv4Pop;
    private int portPop;
    private int mtu;
    private int heartbeat;
    private String privateKeyAlias;
    private List<String> certChainEncoded;
    private String tunnelName;
    private String tunnelId;
    private Inet6Address ipv6Endpoint;
    private String popName;
    private Certificate certChain;
    private AndroidBackedKeyPair keyPair;
    private Date expiryDate;

    // Serialization
    private static final long serialVersionUID = 4L;
    private String dnsPop;
    private Future<Inet4Address> resolvedIp;

    private void writeObject(ObjectOutputStream out)
            throws IOException {
        Log.i (TAG, "Serializing");
        out.writeObject(ipv4Pop);
        out.writeInt(mtu);
        out.writeInt(heartbeat);
        out.writeObject(privateKeyAlias);
        out.writeObject(certChainEncoded);
    }

    // Deserialization
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        Log.i(TAG, "Deserializing");
        crypto = new BcTlsCrypto(new SecureRandom());
        ipv4Pop = (Inet4Address)in.readObject();
        mtu = in.readInt();
        heartbeat = in.readInt();
        setPrivateKeyAlias ((String) in.readObject());
        try {
            setCertChainEncoded ((List<String>) in.readObject());
        } catch (ClassCastException|IllegalArgumentException e) {
            throw new IOException(e);
        }
    }

    private void readObjectNoData()
            throws ObjectStreamException {
        throw new ObjectStreamException(getClass().getName()) {
        };
    }


    public TransporterParams() {
        Log.i (TAG, "Constructing");
        crypto = new BcTlsCrypto(new SecureRandom());
    }

    @Override
    public Inet4Address getIPv4Pop() {
        if (ipv4Pop == null && resolvedIp == null && dnsPop != null) {
            resolvedIp = resolverPool.submit(new HostResolver(dnsPop));
        }
        // at first call we need to get the resolver's result if available. If there's a ipv4Pop
        // address set and the resolver did not finish, we can use the old one (de-serialization).
        if (resolvedIp != null &&
                (ipv4Pop == null || resolvedIp.isDone())) {
            Log.i (TAG, "Reading IPv4 address from async resolver");
            synchronized (this) {
                try {
                    ipv4Pop = resolvedIp.get();
                    resolvedIp = null;
                } catch (ExecutionException| CancellationException e) {
                    Log.i (TAG, "Async resolver didn't resolve", e);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while reading resolved address");
                }
            }
        }

        return ipv4Pop;
    }

    @Override
    public String getTunnelName() {
        return tunnelName;
    }

    @Override
    public void setTunnelName(String tunnelName) {
        this.tunnelName = tunnelName;
    }

    @Override
    public String getTunnelId() {
        return tunnelId;
    }

    @Override
    public void setTunnelId(String tunnelId) {
        this.tunnelId = tunnelId;
    }

    @Override
    public String getType() {
        return TUNNEL_TYPE;
    }

    @Override
    public Inet6Address getIpv6Endpoint() {
        return ipv6Endpoint;
    }

    @Override
    public void setIpv6Endpoint(Inet6Address ipv6Endpoint) {
        this.ipv6Endpoint = ipv6Endpoint;
    }

    @Override
    public String getPopName() {
        return popName;
    }

    @Override
    public void setPopName(String popName) {
        this.popName = popName;
    }

    @Override
    public Date getExpiryDate() {
        return expiryDate;
    }

    @Override
    public boolean isEnabled() {
        return getExpiryDate().after(new Date());
    }

    @Override
    public void setIPv4Pop(Inet4Address ipv4Pop) {
        this.ipv4Pop = ipv4Pop;
        if (resolvedIp != null && !resolvedIp.isDone())
            resolvedIp.cancel(true);
        resolvedIp = null;
    }

    public int getPortPop() {
        return portPop;
    }

    public void setPortPop(int portPop) {
        this.portPop = portPop;
    }

    @Override
    public int getMtu() {
        return mtu;
    }

    @Override
    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    @Override
    public int getHeartbeatInterval() {
        return heartbeat;
    }

    @Override
    public void setHeartbeatInterval(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    public String getPrivateKeyAlias() {
        return privateKeyAlias;
    }

    /**
     * Set and verify the alias of the entry in the Android key store that holds the private
     * key to be used with this tunnel.
     *
     * @param privateKeyAlias a String to identify the private key in Android key store
     * @throws IOException in case no key with that alias can be retrieved
     * @throws IllegalStateException in case the device does not provide the algorithms we use
     * @throws IllegalArgumentException in case the alias refers to an entry of unsuitable type
     */
    public void setPrivateKeyAlias(String privateKeyAlias) throws IOException, IllegalStateException, IllegalArgumentException {
        this.privateKeyAlias = privateKeyAlias;
        keyPair = new AndroidBackedKeyPair (privateKeyAlias);
    }

    public List<String> getCertChainEncoded() {
        return certChainEncoded;
    }

    public Certificate getCertChain() {
        return certChain;
    }

    /**
     * This initializes all attributes from the certificate.
     * @param certChainEncoded a List&lt;String&gt; with the PEM encoded x509 certificate chain of this
     *                  client. The client's cert at position 0, the CA at the end.
     */
    public void setCertChainEncoded(List<String> certChainEncoded) throws IllegalArgumentException {
        Log.i(TAG, "Setting encoded certificate and reading data from it");
        this.certChainEncoded = certChainEncoded;
        try {
            this.certChain = DTLSUtils.parseCertificateChain(crypto, certChainEncoded);
            if (certChain.getLength() < 2) {
                throw new IllegalArgumentException("Supplied certificate chain is missing the CA certificate");
            }
            // initialise attributes from certificate
            TlsCertificate myCert = certChain.getCertificateAt(0);
            Inet6Address myIpv6 = DTLSUtils.getIpv6AlternativeName(myCert);
            if (myIpv6 == null)
                throw new IllegalArgumentException("Supplied certificate is missing the IPv6 IP as alternative name");
            setIpv6Endpoint(myIpv6);
            setPopName(DTLSUtils.getIssuerName(myCert));
            setTunnelName(DTLSUtils.getSubjectCommonName(myCert));
            URL popUrl = DTLSUtils.getIssuerUrl(myCert);
            if (popUrl == null)
                throw new IllegalArgumentException("No POP URL included in certificate");
            int port = popUrl.getPort();
            if (port <= 0) {
                throw new IllegalArgumentException("No port is included in URL read from certificate");
            }
            setPortPop(port);
            setExpiryDate(DTLSUtils.getExpiryDate(myCert));
            setTunnelId(myCert.getSerialNumber().toString(16));
            dnsPop = popUrl.getHost();
            if (resolvedIp != null && !resolvedIp.isDone())
                resolvedIp.cancel(true);
            resolvedIp = resolverPool.submit(new HostResolver(dnsPop));
        } catch (IOException e) {
            throw new IllegalArgumentException("Incorrectly configured, failure to parse certificates", e);
        }
    }

    private void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public AndroidBackedKeyPair getKeyPair() {
        return keyPair;
    }

    public String getDnsPop() {
        return dnsPop;
    }

    /**
     * Helper class for resolving URLs asynchronously. Call execute to start resolving, call
     * get()
     */
    private static class HostResolver implements Callable<Inet4Address>, Serializable {
        private final String popHost;
        /**
         * Constructor.
         * @param popHost a String representing the host name that should be resolved to Inet4Adress.
         */
        public HostResolver(String popHost) {
            this.popHost = popHost;
        }

        @Override
        public Inet4Address call() throws UnknownHostException {
            Log.i(getClass().getName(), "Resolving hostname from URL " + popHost);
            for (InetAddress address : InetAddress.getAllByName(popHost)) {
                    if (address instanceof Inet4Address) {
                        Log.d(getClass().getName(), "Resolved to " + address);
                        return (Inet4Address)address;
                    }
            }
            throw new UnknownHostException("No IPv4 address for " + popHost);
        }
    }

    @Override
    public @NonNull String toString() {
        return tunnelName + " (" + tunnelId + "), DTLS\n Your endpoint " + (ipv6Endpoint == null ? "-" : ipv6Endpoint.getHostAddress());
    }

}
