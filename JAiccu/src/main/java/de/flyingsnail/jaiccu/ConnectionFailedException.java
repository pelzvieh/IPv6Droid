package de.flyingsnail.jaiccu;

/**
 * Created by pelzi on 21.08.13.
 */
public class ConnectionFailedException extends Exception {
    public ConnectionFailedException(String s, Throwable nested) {
        super (s, nested);
    }
}
