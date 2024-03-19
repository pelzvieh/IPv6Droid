/*
 *
 *  * Copyright (c) 2024 Dr. Andreas Feldner.
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

import android.os.IBinder;

import java.util.List;

/**
 * The interface that a certificate provider for IPv6Droid must implement and
 * return on service binding.
 */
public interface IPv6DroidCertRequest extends IBinder {
    /** A String used to identify the Intent action when binding to a certificat issuing service */
    String ACTION = "de.flyingsnail.ipv6droid.REQUEST_TUNNEL";
    /** A String giving the key of a Bundle, to which the certificate signing request is put. */
    String REQUEST_KEY="CSR";
    /**
     * A String giving the key of a Bundle, where to find the List&lt;String&gt; with the elements of
     * the cert path.
     */
    String CERTPATH_KEY="CERT";
    List<String> getCertificateChain(String csrPemEncoded);
}
