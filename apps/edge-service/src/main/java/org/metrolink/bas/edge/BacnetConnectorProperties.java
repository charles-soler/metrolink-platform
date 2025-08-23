package org.metrolink.bas.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectors.bacnet")
public class BacnetConnectorProperties {

    // Defaults you can override in application.yml
    private Integer udpPort = 47808;
    private String bindAddress;        // e.g. "192.168.1.6" (your PC's LAN IP)
    private String broadcast;          // e.g. "192.168.1.255"
    private Integer apduRetries = 1;
    private Integer apduSegTimeoutMs = 2000;
    private int deviceInstance = 12345;
    private int apduTimeoutMs = 3000;
    private int covRenewSec = 120;
    private double defaultCovIncrement = 0.1;
    private boolean bbmdEnabled = false;

    // getters/setters
    public Integer getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(Integer v) {
        this.udpPort = v;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String v) {
        this.bindAddress = v;
    }

    public String getBroadcast() {
        return broadcast;
    }

    public void setBroadcast(String v) {
        this.broadcast = v;
    }

    public Integer getApduRetries() {
        return apduRetries;
    }

    public void setApduRetries(Integer v) {
        this.apduRetries = v;
    }

    public Integer getApduSegTimeoutMs() {
        return apduSegTimeoutMs;
    }

    public void setApduSegTimeoutMs(Integer v) {
        this.apduSegTimeoutMs = v;
    }

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
