package org.metrolink.bas.core.ports;

import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.Point;

import java.time.Duration;
import java.util.List;

public interface DiscoveryPort {
    List<Device> discoverDevices(Duration timeout) throws Exception;

    List<Point> discoverPoints(Device device, Duration timeout) throws Exception;
}
