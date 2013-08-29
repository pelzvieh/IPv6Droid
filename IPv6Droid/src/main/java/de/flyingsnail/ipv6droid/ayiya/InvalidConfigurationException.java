package de.flyingsnail.ipv6droid.ayiya;

/**
 * Created by pelzi on 17.08.13.
 */
public class InvalidConfigurationException extends IllegalArgumentException {
    public InvalidConfigurationException(String confKey, String confValue, Throwable cause) {
        super("Configuration " + confKey + " set to value " + confValue + " is invalid because " + cause.getMessage(), cause);
    }
}
