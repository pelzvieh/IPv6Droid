package de.flyingsnail.jaiccu;

import java.net.UnknownHostException;

/**
 * Created by pelzi on 17.08.13.
 */
public class InvalidConfigurationException extends IllegalArgumentException {
    public InvalidConfigurationException(String confKey, String confValue, Throwable cause) {
        super("Configuration " + confKey + " set to value " + confValue + " is invalid because " + cause.getMessage(), cause);
    }
}
