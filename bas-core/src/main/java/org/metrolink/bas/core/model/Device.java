package org.metrolink.bas.core.model;

import java.util.Map;

public record Device(String id, String name, Map<String, Object> meta) {
}
