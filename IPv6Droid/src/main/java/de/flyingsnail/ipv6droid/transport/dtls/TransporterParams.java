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

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
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
import java.util.concurrent.ExecutionException;

import de.flyingsnail.ipv6droid.transport.TunnelSpec;

public class TransporterParams implements TunnelSpec, Serializable {
    private static final String TAG = TransporterParams.class.getName();
    static final String TUNNEL_TYPE = "DTLSTunnel";
    private TlsCrypto crypto;

    private Inet4Address ipv4Pop;
    private int portPop;
    private int mtu;
    private int heartbeat;
    private String privateKeyEncoded;
    private List<String> certChainEncoded;
    private String tunnelName;
    private String tunnelId;
    private Inet6Address ipv6Endpoint;
    //private Inet6Address ipv6Pop;
    //private int prefixLength;
    private String popName;
    private Certificate certChain;
    private AsymmetricKeyParameter privateKey;
    private AsyncTask<Void, Void, Inet4Address> hostResolver;

    // Serialization
    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        Log.i (TAG, "Serializing");
        out.writeObject(ipv4Pop);
        out.writeInt(portPop);
        out.writeInt(mtu);
        out.writeInt(heartbeat);
        out.writeObject(privateKeyEncoded);
        out.writeObject(certChainEncoded);
        out.writeObject(tunnelName);
        out.writeObject(tunnelId);
        out.writeObject(ipv6Endpoint);
        out.writeObject(popName);
        out.writeObject(hostResolver);
    }

    // Deserialization
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        Log.i(TAG, "Deserializing");
        crypto = new BcTlsCrypto(new SecureRandom());
        ipv4Pop = (Inet4Address)in.readObject();
        portPop = in.readInt();
        mtu = in.readInt();
        heartbeat = in.readInt();
        privateKeyEncoded = (String) in.readObject();
        privateKey = DTLSUtils.parseBcPrivateKeyString(privateKeyEncoded);
        try {
            certChainEncoded = (List<String>) in.readObject();
        } catch (ClassCastException e) {
            throw new IOException(e);
        }
        certChain = DTLSUtils.parseCertificateChain(crypto, certChainEncoded);
        tunnelName = (String) in.readObject();
        tunnelId = (String) in.readObject();
        ipv6Endpoint = (Inet6Address) in.readObject();
        popName = (String) in.readObject();
        try {
            hostResolver = (AsyncTask<Void, Void, Inet4Address>) in.readObject();
        } catch (ClassCastException e) {
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
        // at first call we need to get the resolver's result
        if (hostResolver != null) {
            Log.i (TAG, "Reading IPv4 address from async resolver");
            synchronized (this) {
                if (hostResolver.getStatus() == AsyncTask.Status.PENDING)
                    // this can happen after de-serialization
                    hostResolver.execute();
                int attempt = 0;
                while (hostResolver != null && attempt++ < 2) {
                    try {
                        ipv4Pop = hostResolver.get();
                        hostResolver = null;
                    } catch (ExecutionException e) {
                        Log.i (TAG, "Async resolver didn't resolve, try again", e);
                        hostResolver.execute(); // try resolving again
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while reading resolved address");
                        Thread.currentThread().interrupt();
                        break;
                    }
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

    /*@Override
    public Inet6Address getIpv6Pop() {
        return ipv6Pop;
    }

    @Override
    public void setIpv6Pop(Inet6Address ipv6Pop) {
        this.ipv6Pop = ipv6Pop;
    }

    @Override
    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }*/

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
        return new Date(new Date().getTime() + 1000L*60*60*24*365);
    }

    @Override
    public boolean isEnabled() {
        // todo check valid until date of own certificate
        return true;
    }

    @Override
    public void setIPv4Pop(Inet4Address ipv4Pop) {
        this.ipv4Pop = ipv4Pop;
        hostResolver = null;
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

    public String getPrivateKeyEncoded() {
        return privateKeyEncoded;
    }

    public void setPrivateKeyEncoded(String privateKeyEncoded) {
        this.privateKeyEncoded = privateKeyEncoded;

        try {
            this.privateKey = DTLSUtils.parseBcPrivateKeyString(privateKeyEncoded);
        } catch (IOException e) {
            throw new IllegalStateException("Incorrectly bundled, failure to read private key", e);
        }
    }

    public List<String> getCertChainEncoded() {
        return certChainEncoded;
    }

    public AsymmetricKeyParameter getPrivateKey() {
        return privateKey;
    }

    public Certificate getCertChain() {
        return certChain;
    }

    /**
     * This initializes all attributes from the certificate.
     * @param certChainEncoded a List&lt;String&gt; with the PEM encoded x509 certificate chain of this
     *                  client. The client's cert at position 0, the CA at the end.
     */
    public void setCertChainEncoded(List<String> certChainEncoded) {
        Log.i(TAG, "Setting encoded certificate and reading data from it");
        this.certChainEncoded = certChainEncoded;
        try {
            this.certChain = DTLSUtils.parseCertificateChain(crypto, certChainEncoded);
            if (certChain.getLength() < 2) {
                throw new IllegalArgumentException("Supplied certificate chain is missing the CA certificate");
            }
            // initialise attributes from certificate
            setIpv6Endpoint(DTLSUtils.getIpv6AlternativeName(certChain.getCertificateAt(0)));
            setPopName(DTLSUtils.getIssuerName(certChain.getCertificateAt(0)));
            setTunnelName(DTLSUtils.getSubjectName(certChain.getCertificateAt(0)));
            URL popUrl = DTLSUtils.getIssuerUrl(certChain.getCertificateAt(0));
            if (popUrl == null)
                throw new IllegalArgumentException("No POP URL included in certificate");
            int port = popUrl.getPort();
            if (port <= 0) {
                throw new IllegalArgumentException("No port is included in URL read from certificate");
            }
            setPortPop(port);
            if (hostResolver != null && hostResolver.getStatus() == AsyncTask.Status.RUNNING)
                hostResolver.cancel(true);
            hostResolver = new UrlResolver(popUrl).execute();
        } catch (IOException e) {
            throw new IllegalArgumentException("Incorrectly configured, failure to parse certificates", e);
        }
    }

    /**
     * Helper class for resolving URLs asynchronously. Call execute to start resolving, call
     * get()
     */
    private static class UrlResolver extends AsyncTask<Void, Void, Inet4Address> implements Serializable {
        private final URL popUrl;

        /**
         * Constructor.
         * @param popUrl the URL whose host name should be resolved to Inet4Adress.
         */
        public UrlResolver (URL popUrl) {
            this.popUrl = popUrl;
        }

        @Override
        protected Inet4Address doInBackground(Void ... voids) {
            Log.i(getClass().getName(), "Resolving hostname from URL " + popUrl);
            try {
                for (InetAddress address : InetAddress.getAllByName(popUrl.getHost())) {
                    if (address instanceof Inet4Address) {
                        Log.d(getClass().getName(), "Resolved to " + address);
                        return (Inet4Address)address;
                    }
                }
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Cannot resolve configured issuer URL");
            }
            return null;
        }
    }

    @Override
    public @NonNull String toString() {
        return tunnelName + " (" + tunnelId + "), DTLS\n Your endpoint " + ipv6Endpoint.getHostAddress();
    }

}
