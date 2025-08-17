package org.metrolink.bas.edge;

import org.metrolink.bas.core.ports.HealthPort;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

@Component
public class ConnectorHealthIndicator implements HealthIndicator {
    private final HealthPort health;
    public ConnectorHealthIndicator(HealthPort health) { this.health = health; }

    @Override public Health health() {
        var s = health.health();
        var builder = s.up() ? Health.up() : Health.down();
        if (s.metrics() != null) builder.withDetails(s.metrics());
        return builder.build();
    }
}
