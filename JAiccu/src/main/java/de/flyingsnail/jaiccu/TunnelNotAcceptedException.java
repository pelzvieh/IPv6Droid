package de.flyingsnail.jaiccu;

/**
 * Created by pelzi on 21.08.13.
 */
public class TunnelNotAcceptedException extends Exception {
    public TunnelNotAcceptedException(String s, Exception e) {
        super (s, e);
    }
}
