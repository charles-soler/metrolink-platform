package org.metrolink.bas.connector.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.model.Point;
import org.metrolink.bas.core.ports.*;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BacnetConnector implements ConnectorPlugin {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(BacnetConnector.class);
    private final ConcurrentMap<Integer, RemoteDevice> discovered = new ConcurrentHashMap<>();
    // ---- config from init(cfg) ----
    private Map<String, Object> cfg = Map.of();
    private int deviceInstance = 12345;
    private int apduTimeoutMs = 3000;
    private int apduSegTimeoutMs = 2000;
    private int apduRetries = 1;
    private int udpPort = 0xBAC0;            // 47808
    private String bindAddress = null;       // e.g. "192.168.1.7"
    private String broadcastAddress = null;  // e.g. "192.168.1.255"
    private boolean bbmdEnabled = false;     // reserved
    private double defaultCovIncrement = 0.1;// reserved
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
        this.apduSegTimeoutMs = has("apduSegmentTimeoutMs")
                ? getInt("apduSegmentTimeoutMs", 2000)
                : getInt("apduSegTimeoutMs", 2000);
        this.apduRetries = getInt("apduRetries", 1);

        this.udpPort = getInt("udpPort", 0xBAC0);
        this.bindAddress = getString("bindAddress", null);
        this.broadcastAddress = getString("broadcast", null);

        this.bbmdEnabled = getBool("bbmdEnabled", false);
        this.defaultCovIncrement = getDouble("defaultCovIncrement", 0.1);
    }

    @Override
    public synchronized void start() throws Exception {
        if (initialized) return;

        var nb = new IpNetworkBuilder()
                .withPort(udpPort)
                .withReuseAddress(true);

        if (bindAddress != null && !bindAddress.isBlank()) {
            nb.withLocalBindAddress(bindAddress);
        }
        String bcast = (broadcastAddress != null && !broadcastAddress.isBlank())
                ? broadcastAddress : "255.255.255.255";
        nb.withBroadcast(bcast, udpPort);

        var net = nb.build();

        var tx = new DefaultTransport(net);
        tx.setTimeout(apduTimeoutMs);
        tx.setSegTimeout(apduSegTimeoutMs);
        tx.setRetries(apduRetries);

        var ld = new LocalDevice(deviceInstance, tx);
        ld.initialize();

        // Listener updates our map immediately when I-Am arrives
        ld.getEventHandler().addListener(new com.serotonin.bacnet4j.event.DeviceEventAdapter() {
            @Override
            public void iAmReceived(RemoteDevice d) {
                discovered.put(d.getInstanceNumber(), d);
                LOG.info("I-Am received: instance={} addr={}", d.getInstanceNumber(), d.getAddress());
            }
        });

        this.transport = tx;
        this.localDevice = ld;
        this.initialized = true;

        LOG.info("BACnet LocalDevice up: instance={} bind={} broadcast={} port={}",
                deviceInstance, bindAddress, bcast, udpPort);
    }

    @Override
    public synchronized void stop() {
        initialized = false;
        discovered.clear(); // tidy
        var ld = this.localDevice;
        this.localDevice = null;
        if (ld != null) {
            try {
                ld.terminate();
            } catch (Exception ignore) {
            }
        }
    }

    // -------- Ports --------

    @Override
    public ReaderPort reader() {
        return pointIds -> {
            throw new UnsupportedOperationException("BACnet read not implemented (devices-only)");
        };
    }

    @Override
    public WriterPort writer() {
        return (pointId, value, options) -> {
            throw new UnsupportedOperationException("BACnet write not implemented (devices-only)");
        };
    }

    @Override
    public DiscoveryPort discovery() {
        return new DiscoveryPort() {
            @Override
            public java.util.List<Device> discoverDevices(java.time.Duration timeout) throws Exception {
                if (!(initialized && localDevice != null)) {
                    throw new IllegalStateException("BACnet LocalDevice not initialized");
                }

                // fresh run
                discovered.clear();

                long waitMs = Math.max(1500, Math.min(
                        (timeout != null ? timeout.toMillis() : 3000), 5000));

                // Send Who-Is (global)
                try {
                    localDevice.sendGlobalBroadcast(new com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest());
                    LOG.debug("Sent Who-Is (global)");
                } catch (Exception e) {
                    LOG.warn("Global Who-Is failed: {}", e.toString());
                }

                // Also send directed Who-Is to the broadcast IP we’re using
                try {
                    var addr = com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils.toAddress(
                            (broadcastAddress != null && !broadcastAddress.isBlank()) ? broadcastAddress : "255.255.255.255",
                            udpPort);
                    localDevice.send(addr, new com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest());
                    LOG.debug("Sent Who-Is (directed) to {}", addr);
                } catch (Exception e) {
                    LOG.warn("Directed Who-Is failed: {}", e.toString());
                }

                // Wait up to waitMs, but return early if something arrives
                long end = System.currentTimeMillis() + waitMs;
                while (System.currentTimeMillis() < end) {
                    if (!discovered.isEmpty()) break;
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignore) {
                    }
                }

                var remotes = discovered.values();
                LOG.info("discoverDevices: after {} ms, remote count={}", waitMs, remotes.size());

                var out = new java.util.ArrayList<Device>(remotes.size());
                for (var rd : remotes) {
                    String id = "device:" + rd.getInstanceNumber();
                    String name = "BACnet Device " + rd.getInstanceNumber();
                    var meta = new LinkedHashMap<String, Object>();
                    meta.put("address", String.valueOf(rd.getAddress()));
                    out.add(new Device(id, name, meta));
                }
                return out;
            }

            @Override
            public java.util.List<Point> discoverPoints(Device device, java.time.Duration timeout) {
                return java.util.List.of();
            }
        };
    }

    @Override
    public SubscribePort subscribe() {
        return (pointIds, subscriber) -> {
            throw new UnsupportedOperationException("BACnet subscribe not implemented (devices-only)");
        };
    }

    @Override
    public HealthPort health() {
        return () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("implemented", "localDevice-lifecycle + whois/devices");
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

    // -------- helpers --------
    private boolean has(String key) {
        return cfg.containsKey(key) && cfg.get(key) != null;
    }

    private int getInt(String key, int def) {
        Object v = cfg.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
        }
        return def;
    }

    private double getDouble(String key, double def) {
        Object v = cfg.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) try {
            return Double.parseDouble(s.trim());
        } catch (Exception ignored) {
        }
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
