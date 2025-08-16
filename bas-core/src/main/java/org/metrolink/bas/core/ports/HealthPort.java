package org.metrolink.bas.core.ports;

import org.metrolink.bas.core.model.HealthStatus;

public interface HealthPort {
    HealthStatus health();
}
