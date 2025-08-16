package org.metrolink.bas.core.model;

import java.util.Map;

public record HealthStatus(boolean up, Map<String, Object> metrics) {
}
