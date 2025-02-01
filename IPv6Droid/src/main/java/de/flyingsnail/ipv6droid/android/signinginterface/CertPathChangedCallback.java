/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.android.signinginterface;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableList;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.Tunnels;
import de.flyingsnail.ipv6droid.android.dtlsrequest.CertificateToTunnel;
import de.flyingsnail.ipv6droid.android.vpnrun.VpnStatusReport;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * A class handling a change to a certificate path. A certificate path is represented by an
 * (ordered) list of string. When IPv6Droid queries a supplier app for certificates, the result
 * will be stored in an ObservableList&lt;String&gt;. This class can be used to convert this and
 * write it back to an observable Tunnel object.
 */
public class CertPathChangedCallback extends ObservableList.OnListChangedCallback<ObservableList<String>> {
    private static final Logger logger = Logger.getLogger(CertPathChangedCallback.class.getName());
    private final ObservableField<TunnelSpec> receiver;
    private final CertificateToTunnel certHelper;
    private final VpnStatusReport statusReport;

    /**
     * Constructor.
     * @param receiver an ObservableField&lt;TunnelSpec&gt; that is going to be set to
     *                 the TunnelSpec resulting from a received update to the cert path.
     * @param certHelper the CertificatToTunnel instance that was used to create the CSR.
     */
    public CertPathChangedCallback(@NonNull ObservableField<TunnelSpec> receiver,
                                   @NonNull CertificateToTunnel certHelper,
                                   @NonNull Context context) {
        this.receiver = receiver;
        this.certHelper = certHelper;
        statusReport = new VpnStatusReport(context);
    }

    @Override
    public void onChanged(ObservableList<String> sender) {
        if (!sender.isEmpty()) {
            try {
                TunnelSpec spec = certHelper.createTunnelSpec(sender);
                receiver.set(spec);
                Tunnels tunnels = new Tunnels(1);
                tunnels.add(spec);
                tunnels.setActiveTunnel(spec);
                statusReport.setTunnels(tunnels);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to convert cert path to tunnel spec", e);
            }
        }
    }

    @Override
    public void onItemRangeChanged(ObservableList<String> sender, int positionStart, int itemCount) {
        onChanged(sender);
    }

    @Override
    public void onItemRangeInserted(ObservableList<String> sender, int positionStart, int itemCount) {
        onChanged(sender);
    }

    @Override
    public void onItemRangeMoved(ObservableList<String> sender, int fromPosition, int toPosition, int itemCount) {
        onChanged(sender);
    }

    @Override
    public void onItemRangeRemoved(ObservableList<String> sender, int positionStart, int itemCount) {
        onChanged(sender);
    }
}
