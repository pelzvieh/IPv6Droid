package de.flyingsnail.ipv6droid.ayiya;

/**
 * Created by pelzi on 21.08.13.
 */
public class TunnelNotAcceptedException extends Exception {
    public TunnelNotAcceptedException(String s, Exception e) {
        super (s, e);
    }
}
