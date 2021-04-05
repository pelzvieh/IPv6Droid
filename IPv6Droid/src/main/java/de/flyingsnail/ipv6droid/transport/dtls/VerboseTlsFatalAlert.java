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

import org.bouncycastle.tls.TlsFatalAlert;

public class VerboseTlsFatalAlert extends TlsFatalAlert {
    public VerboseTlsFatalAlert(short alertDescription, Throwable alertCause) {
        super(alertDescription, alertCause);
    }

    @NonNull
    @Override
    public String toString() {
        Throwable cause = getCause();
        return cause == null ? super.toString() : (
                super.toString() + ": " + cause.getClass().getSimpleName() + " \"" + cause.getMessage() + "\""
        );
    }
}
