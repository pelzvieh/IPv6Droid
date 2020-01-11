/*
 *
 *  * Copyright (c) 2019 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.transport;

import java.security.NoSuchAlgorithmException;

import de.flyingsnail.ipv6droid.transport.ayiya.Ayiya;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;
import de.flyingsnail.ipv6droid.transport.dtls.DTLSTransporter;

public class TransporterBuilder {
  public static Transporter createTransporter(TicTunnel spec) throws NoSuchAlgorithmException, ConnectionFailedException {
    if (Ayiya.TUNNEL_TYPE.equals(spec.getType())) {
      return new Ayiya(spec);
    }
    if (DTLSTransporter.TUNNEL_TYPE.equals(spec.getType())) {
      return new DTLSTransporter(spec);
    }
    throw new NoSuchAlgorithmException("No transport builder registered for " + spec.getType());
  }
}
