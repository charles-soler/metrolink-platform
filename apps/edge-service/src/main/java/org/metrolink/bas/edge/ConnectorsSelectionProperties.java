package org.metrolink.bas.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectors")
public class ConnectorsSelectionProperties {
    /**
     * Which connector to use at runtime: "sim" or "bacnet". Default is "sim".
     */
    private String active = "sim";

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }
}
