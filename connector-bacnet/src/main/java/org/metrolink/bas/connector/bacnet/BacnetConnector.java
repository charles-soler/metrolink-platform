package org.metrolink.bas.connector.bacnet;

import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.model.Point;
import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.ports.DiscoveryPort;
import org.metrolink.bas.core.ports.HealthPort;
import org.metrolink.bas.core.ports.ReaderPort;
import org.metrolink.bas.core.ports.SubscribePort;
import org.metrolink.bas.core.ports.WriterPort;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Flow;

public final class BacnetConnector implements ConnectorPlugin {

    // ---- config (populated in init(cfg)) ----
    private Map<String, Object> cfg = Map.of();

    private int deviceInstance = 12345;
    private int apduTimeoutMs = 3000;
    private int apduSegTimeoutMs = 2000;  // optional additional timeout
    private int apduRetries = 1;          // optional retries
    private boolean bbmdEnabled = false;  // reserved for future use
    private double defaultCovIncrement = 0.1;

    private int udpPort = 0xBAC0;         // 47808
    private String bindAddress = null;    // optional local NIC to bind
    private String broadcastAddress = null; // optional broadcast override

    // ---- runtime ----
    private volatile LocalDevice localDevice;
    private volatile DefaultTransport transport;
    private volatile boolean initialized = false;

    @Override
    public String id() {
        return "bacnet";
    }

    @Override
    public void init(Map<String, Object> config) {
        this.cfg = (config != null) ? Map.copyOf(config) : Map.of();

        this.deviceInstance = getInt("deviceInstance", 12345);
        this.apduTimeoutMs = getInt("apduTimeoutMs", 3000);
        // accept either key name for segment timeout
        this.apduSegTimeoutMs = has("apduSegmentTimeoutMs")
                ? getInt("apduSegmentTimeoutMs", 2000)
                : getInt("apduSegTimeoutMs", 2000);
        this.apduRetries = getInt("apduRetries", 1);

        this.defaultCovIncrement = getDouble("defaultCovIncrement", 0.1);
        this.bbmdEnabled = getBool("bbmdEnabled", false);

        this.udpPort = getInt("udpPort", 0xBAC0);
        this.bindAddress = getString("bindAddress", null);
        this.broadcastAddress = getString("broadcast", null);
    }

    @Override
    public synchronized void start() throws Exception {
        if (initialized) return;

        IpNetworkBuilder nb = new IpNetworkBuilder().withPort(udpPort);

        if (bindAddress != null && !bindAddress.isBlank()) {
            nb.withLocalBindAddress(bindAddress);
        }

        // REQUIRED: either broadcast or subnet. Use provided broadcast or fallback to limited broadcast.
        String bcast = (broadcastAddress != null && !broadcastAddress.isBlank())
                ? broadcastAddress
                : "255.255.255.255";
        nb.withBroadcast(bcast, udpPort);

        IpNetwork net = nb.build();

        DefaultTransport tx = new DefaultTransport(net);
        tx.setTimeout(apduTimeoutMs);
        tx.setSegTimeout(apduSegTimeoutMs);
        tx.setRetries(apduRetries);

        LocalDevice ld = new LocalDevice(deviceInstance, tx);
        ld.initialize();

        this.transport = tx;
        this.localDevice = ld;
        this.initialized = true;
    }


    @Override
    public synchronized void stop() {
        this.initialized = false;
        LocalDevice ld = this.localDevice;
        this.localDevice = null;
        if (ld != null) {
            try { ld.terminate(); } catch (Exception ignore) {}
        }
    }


    // -------- Ports --------

    @Override
    public DiscoveryPort discovery() {
        return new DiscoveryPort() {
            @Override
            public List<Device> discoverDevices(Duration timeout) throws Exception {
                // Next step: implement Who-Is/I-Am collection into Device records
                throw new UnsupportedOperationException("BACnet discovery not implemented (Who-Is/I-Am stub pending)");
            }

            @Override
            public List<Point> discoverPoints(Device device, Duration timeout) throws Exception {
                throw new UnsupportedOperationException("BACnet point discovery not implemented (skeleton)");
            }
        };
    }

    @Override
    public ReaderPort reader() {
        return pointIds -> {
            throw new UnsupportedOperationException("BACnet read not implemented (skeleton)");
        };
    }

    @Override
    public WriterPort writer() {
        return (pointId, value, opts) -> {
            throw new UnsupportedOperationException("BACnet write not implemented (skeleton)");
        };
    }

    @Override
    public SubscribePort subscribe() {
        return (pointIds, subscriber) -> {
            throw new UnsupportedOperationException("BACnet subscribe not implemented (skeleton)");
        };
    }

    @Override
    public HealthPort health() {
        return () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("implemented", "localDevice-lifecycle");
            m.put("deviceInstance", deviceInstance);
            m.put("apduTimeoutMs", apduTimeoutMs);
            m.put("apduSegTimeoutMs", apduSegTimeoutMs);
            m.put("apduRetries", apduRetries);
            m.put("udpPort", udpPort);
            m.put("bindAddress", bindAddress);
            m.put("broadcast", broadcastAddress);
            m.put("bbmdEnabled", bbmdEnabled);
            m.put("covIncrement", defaultCovIncrement);
            m.put("cfgKeys", String.join(",", cfg.keySet()));

            boolean up = initialized && localDevice != null;
            m.put("localDevice", up ? "initialized" : "not-initialized");
            return new HealthStatus(up, m);
        };
    }

    // -------- helpers to read typed config --------

    private boolean has(String key) {
        return cfg.containsKey(key) && cfg.get(key) != null;
    }

    private int getInt(String key, int def) {
        Object v = cfg.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        return def;
    }

    private double getDouble(String key, double def) {
        Object v = cfg.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) try { return Double.parseDouble(s.trim()); } catch (Exception ignored) {}
        return def;
    }

    private boolean getBool(String key, boolean def) {
        Object v = cfg.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    private String getString(String key, String def) {
        Object v = cfg.get(key);
        return (v != null) ? String.valueOf(v) : def;
    }
}
