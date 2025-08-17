package org.metrolink.bas.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectors.bacnet")
public class BacnetConnectorProperties {

    // Defaults you can override in application.yml
    private int deviceInstance = 12345;
    private int apduTimeoutMs = 3000;
    private int covRenewSec = 120;
    private double defaultCovIncrement = 0.1;
    private boolean bbmdEnabled = false;

    // getters/setters
    public int getDeviceInstance() {
        return deviceInstance;
    }

    public void setDeviceInstance(int deviceInstance) {
        this.deviceInstance = deviceInstance;
    }

    public int getApduTimeoutMs() {
        return apduTimeoutMs;
    }

    public void setApduTimeoutMs(int apduTimeoutMs) {
        this.apduTimeoutMs = apduTimeoutMs;
    }

    public int getCovRenewSec() {
        return covRenewSec;
    }

    public void setCovRenewSec(int covRenewSec) {
        this.covRenewSec = covRenewSec;
    }

    public double getDefaultCovIncrement() {
        return defaultCovIncrement;
    }

    public void setDefaultCovIncrement(double defaultCovIncrement) {
        this.defaultCovIncrement = defaultCovIncrement;
    }

    public boolean isBbmdEnabled() {
        return bbmdEnabled;
    }

    public void setBbmdEnabled(boolean bbmdEnabled) {
        this.bbmdEnabled = bbmdEnabled;
    }
}
