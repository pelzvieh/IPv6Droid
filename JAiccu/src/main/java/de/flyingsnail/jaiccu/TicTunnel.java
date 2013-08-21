package de.flyingsnail.jaiccu;

/**
 * Created by pelzi on 17.08.13.
 */
public class TicTunnel {
    /** local endpoint */
    private String ipv4Local;

    /** POP endpoint */
    private String ipv4Pop;

    public String getIPv4Pop() {
        return ipv4Pop;
    }

    public void setIPv4Pop(String ipv4Pop) {
        this.ipv4Pop = ipv4Pop;
    }

    public String getIPv4Local() {
        return ipv4Local;
    }

    public void setIPv4Local(String ipv4Local) {
        this.ipv4Local = ipv4Local;
    }

}
