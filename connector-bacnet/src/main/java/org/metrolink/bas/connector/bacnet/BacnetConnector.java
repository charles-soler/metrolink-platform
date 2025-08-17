package org.metrolink.bas.connector.bacnet;

import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.Point;
import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.ports.*;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Flow;

public final class BacnetConnector implements ConnectorPlugin {
    private Map<String, Object> cfg = Map.of();

    @Override public String id() { return "bacnet"; }

    @Override public void init(Map<String, Object> config) {
        this.cfg = (config != null) ? Map.copyOf(config) : Map.of();
    }

    @Override public void start() { /* no-op skeleton */ }

    @Override public void stop() { /* no-op skeleton */ }

    @Override public DiscoveryPort discovery() {
        return new DiscoveryPort() {
            @Override public List<Device> discoverDevices(Duration timeout) {
                throw new UnsupportedOperationException("BACnet discovery not implemented (skeleton)");
            }
            @Override public List<Point> discoverPoints(Device device, Duration timeout) {
                throw new UnsupportedOperationException("BACnet point discovery not implemented (skeleton)");
            }
        };
    }

    @Override public ReaderPort reader() {
        return pointIds -> { throw new UnsupportedOperationException("BACnet read not implemented (skeleton)"); };
    }

    @Override public WriterPort writer() {
        return (pointId, value, opts) -> { throw new UnsupportedOperationException("BACnet write not implemented (skeleton)"); };
    }

    @Override public SubscribePort subscribe() {
        return (pointIds, subscriber) -> {
            throw new UnsupportedOperationException("BACnet subscribe not implemented (skeleton)");
        };
    }

    @Override public HealthPort health() {
        return () -> new HealthStatus(false, Map.of(
                "implemented", "skeleton",
                "cfgKeys", String.join(",", cfg.keySet())
        ));
    }
}
