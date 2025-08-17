package org.metrolink.bas.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "connectors.sim")
public class SimConnectorProperties {
    private double ai1Start = 21.0; // °C initial
    private double ai1Drift = 0.2;  // per tick drift amplitude
    private long periodMs = 1000; // update interval

    // getters & setters
    public double getAi1Start() {
        return ai1Start;
    }

    public void setAi1Start(double v) {
        this.ai1Start = v;
    }

    public double getAi1Drift() {
        return ai1Drift;
    }

    public void setAi1Drift(double v) {
        this.ai1Drift = v;
    }

    public long getPeriodMs() {
        return periodMs;
    }

    public void setPeriodMs(long v) {
        this.periodMs = v;
    }
}
