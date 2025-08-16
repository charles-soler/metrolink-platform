package org.metrolink.bas.core.model;

import java.util.Map;

public record Node(String id, String deviceId, String name, String type, boolean writable, Map<String, Object> meta) {
}
