package org.metrolink.bas.core.model;

import java.util.Map;

public record Point(String id, String deviceId, String name, String kind, boolean writable, Map<String, Object> meta) {
}
