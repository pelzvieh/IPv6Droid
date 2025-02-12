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

package de.flyingsnail.ipv6droid.transport;

import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.transport.ayiya.Ayiya;
import de.flyingsnail.ipv6droid.transport.ayiya.TicTunnel;
import de.flyingsnail.ipv6droid.transport.dtls.DTLSTransporter;
import de.flyingsnail.ipv6droid.transport.dtls.TransporterParams;

public class TransporterBuilder {
  private static final Map<Class<? extends TunnelSpec>, Class<? extends Transporter>> registry;
  private static final Logger logger = Logger.getLogger(TransporterBuilder.class.getName());

  static {
    registry = new HashMap<> (Map.of(
            TicTunnel.class, Ayiya.class,
            TransporterParams.class, DTLSTransporter.class
    ));
  }

  static public void register(Class<? extends TunnelSpec> input, Class<? extends Transporter> output) {
    registry.put(input, output);
  }

  public static Transporter createTransporter(TunnelSpec spec) throws NoSuchAlgorithmException, ConnectionFailedException {
    for (Class<? extends TunnelSpec> probeInput: registry.keySet()) {
      if (probeInput.isInstance(spec)) {
          try {
              Class<? extends Transporter> transporterClass = Objects.requireNonNull(registry.get(probeInput));
              logger.info("Constructing instance of " + transporterClass.getName() + " for spec type " + probeInput.getName());
              return transporterClass.getConstructor(probeInput).newInstance(spec);
          } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
              throw new RuntimeException(e);
          }
      }
    }

    throw new NoSuchAlgorithmException("No transport builder registered for " + spec.getType());
  }
}
