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

package de.flyingsnail.ipv6droid.android.googlesubscription;

import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Deserialize InetAddresses simply as an IPv6 address as defined by RFC.
 * This is as Jackson 1 did things and sounds very sensible to do.
 * 
 * @author pelzi
 *
 */
//@Provider
//@Consumes(MediaType.APPLICATION_JSON)
public class SimpleInetDeserializer implements JsonDeserializer<Inet4Address> {
  private final static String TAG = SimpleInetDeserializer.class.getName();

  SimpleInetDeserializer() {
    Log.i(TAG, "Constructed SimpleInet6Serializer");
  }


  /**
   * Gson invokes this call-back method during deserialization when it encounters a field of the
   * specified type.
   * <p>In the implementation of this call-back method, you should consider invoking
   * {@link JsonDeserializationContext#deserialize(JsonElement, Type)} method to create objects
   * for any non-trivial field of the returned object. However, you should never invoke it on the
   * the same type passing {@code json} since that will cause an infinite loop (Gson will call your
   * call-back method again).
   *
   * @param json    The Json data being deserialized
   * @param typeOfT The type of the Object to deserialize to
   * @param context the JsonDeserializationContext that could be used to deserialize attributes
   * @return a deserialized object of the specified type typeOfT which is a subclass of {@code T}
   * @throws JsonParseException if json is not in the expected format of {@code typeofT}
   */
  @Override
  public Inet4Address deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
    if (Inet4Address.class.equals(typeOfT)) {
      String value = json.getAsString();
      InetAddress address;
      try {
        address = Inet4Address.getByName(value);
      } catch (UnknownHostException e) {
        throw new JsonParseException("Value not parseable to InetAdress", e);
      }
      if (address instanceof Inet4Address) {
        Log.d(TAG, "Deserialization successfull");
        return (Inet4Address) address;
      } else {
        throw new JsonParseException("Value did not represent an Inet4Address but a different InetAddress");
      }
    }
    Log.w(TAG, "We're called for an incompatible type " + typeOfT);
    return null;
  }
}
