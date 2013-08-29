package de.flyingsnail.ipv6droid.ayiya;

/**
 * Created by pelzi on 21.08.13.
 */
public class ConnectionFailedException extends Exception {
    public ConnectionFailedException(String s, Throwable nested) {
        super (s, nested);
    }
}
