package org.metrolink.bas.core.ports;

import java.util.Map;

public interface WriterPort {
    void write(String pointId, Object value, Map<String, Object> options) throws Exception;
}
