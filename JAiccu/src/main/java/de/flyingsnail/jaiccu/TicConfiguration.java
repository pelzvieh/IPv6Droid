package de.flyingsnail.jaiccu;

import android.util.Log;

/**
 * Created by pelzi on 19.08.13.
 */
public class TicConfiguration implements Cloneable {
    /** the username to login to TIC */
    private String username;
    /** the password to login to TIC */
    private String password;
    /** the TIC server */
    private String server;

    /** Constructor initializing all fields */
    public TicConfiguration(String username, String password, String server) {
        this.username = username;
        this.password = password;
        this.server = server;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Cloning failed", e);
        }
    }
}
