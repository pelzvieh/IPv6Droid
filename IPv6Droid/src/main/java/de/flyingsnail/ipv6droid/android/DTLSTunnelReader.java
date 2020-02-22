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

package de.flyingsnail.ipv6droid.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.transport.ConnectionFailedException;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;
import de.flyingsnail.ipv6droid.transport.dtls.TransporterParams;

public class DTLSTunnelReader implements TunnelReader {
    private final Context context;

    private final static String TAG = DTLSTunnelReader.class.getSimpleName();

    private final String keyConfig;

    private final List<String> certConfig;
    private final int heartbeat;
    private final Inet4Address ipv4PoP;
    private final Inet6Address ipv6PoP;
    private final int portPoP;
    private final Inet6Address myIpv6;
    private final int mtu;
    private final String popName;
    private final int prefixLength;
    private final String tunnelId;
    private final String tunnelName;

    public DTLSTunnelReader (Context context) throws ConnectionFailedException {
        this.context = context;

        // load DTLS Configuration
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        keyConfig = myPreferences.getString("dtls_key", "");
        String concattedCerts = myPreferences.getString("dtls_certs", "");

        if (keyConfig.isEmpty() || concattedCerts.isEmpty())
            throw new ConnectionFailedException("No DTLS credentials configured", null);
        String[] certStrings = concattedCerts.split("-----BEGIN CERTIFICATE-----");
        certConfig = new ArrayList<>(certStrings.length);
        for (String certString: certStrings) {
            certString = certString.trim();
            if (!certString.isEmpty()) {
                certConfig.add("-----BEGIN CERTIFICATE-----\n" + certString);
            }
        }

        // todo build params and perform parsing here

        heartbeat = 10000; // todo probably OK to turn into a constant
        try {
            ipv4PoP = (Inet4Address)Inet4Address.getByName("192.168.1.161"); // todo read from certificate
            ipv6PoP = (Inet6Address)Inet6Address.getByName("2a06:1c41:c1::1"); // todo read from certificate
            portPoP = 5072; // todo read from certificate
            myIpv6 = (Inet6Address)Inet6Address.getByName("2a06:1c41:c1::6"); // todo read from certificate
        } catch (UnknownHostException e) {
            throw new ConnectionFailedException("Could not resolve host name", e);
        }
        mtu = 1300; // todo check, probably OK as constant
        popName = "flyingsnail"; // todo read from certificate issuer
        prefixLength = 64; // todo check what this is required for
        tunnelId = "1"; // todo read from certificate
        tunnelName = "my DTLS"; // todo read from certificate subject name
    }

    @Override
    public List<? extends TunnelSpec> queryTunnels() throws ConnectionFailedException, IOException {
        TransporterParams params = new TransporterParams();
        params.setCertChain(certConfig);
        params.setPrivateKey(keyConfig);
        params.setHeartbeatInterval(heartbeat);
        params.setIPv4Pop(ipv4PoP);
        params.setIpv6Pop(ipv6PoP);
        params.setPortPop(portPoP);
        params.setIpv6Endpoint(myIpv6);
        params.setMtu(mtu);
        params.setPopName(popName);
        params.setPrefixLength(prefixLength);
        params.setTunnelId(tunnelId);
        params.setTunnelName(tunnelName);
        List<TunnelSpec> myTunnels = new ArrayList<TunnelSpec>(1);
        myTunnels.add(params);
        return myTunnels;
    }
}
