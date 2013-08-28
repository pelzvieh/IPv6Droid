package de.flyingsnail.jaiccu;

public class RoutingConfiguration implements Cloneable {
    /**
     * a flag if the ipv6 default route should be set to the tunnel
     */
    private boolean setDefaultRoute;
    /**
     * a String that gives a different route specification if default route is not to be set.
     */
    private String specificRoute;

    public RoutingConfiguration(boolean setDefaultRoute, String specificRoute) {
        this.setDefaultRoute = setDefaultRoute;
        this.specificRoute = specificRoute;
    }

    public boolean isSetDefaultRoute() {
        return setDefaultRoute;
    }

    public void setSetDefaultRoute(boolean setDefaultRoute) {
        this.setDefaultRoute = setDefaultRoute;
    }

    public String getSpecificRoute() {
        return specificRoute;
    }

    public void setSpecificRoute(String specificRoute) {
        this.specificRoute = specificRoute;
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