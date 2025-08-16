package org.metrolink.bas.core.ports;

import java.util.Map;

public interface LifecyclePort {
    void init(Map<String, Object> config);

    void start() throws Exception;

    void stop() throws Exception;
}
