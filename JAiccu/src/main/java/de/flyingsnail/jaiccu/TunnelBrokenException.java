package de.flyingsnail.jaiccu;

/**
 * Created by pelzi on 22.08.13.
 */
public class TunnelBrokenException extends Exception {
    public TunnelBrokenException (String msg, Throwable cause) {
        super (msg, cause);
    }
}
