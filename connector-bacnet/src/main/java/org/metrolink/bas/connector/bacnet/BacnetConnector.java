package org.metrolink.bas.connector.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.model.Point;
import org.metrolink.bas.core.ports.*;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class BacnetConnector implements ConnectorPlugin {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(BacnetConnector.class);

    // ---- config from init(cfg) ----
    private Map<String, Object> cfg = Map.of();
    private int deviceInstance = 12345;
    private int apduTimeoutMs = 3000;
    private int apduSegTimeoutMs = 2000;
    private int apduRetries = 1;

    private int udpPort = 0xBAC0;            // 47808
    private String bindAddress = null;       // e.g. "192.168.1.6"
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

        IpNetworkBuilder nb = new IpNetworkBuilder()
                .withPort(udpPort)
                .withReuseAddress(true);

        if (bindAddress != null && !bindAddress.isBlank()) {
            nb.withLocalBindAddress(bindAddress);
            // Common home LAN mask; expose as config later if needed
            nb.withSubnet(bindAddress, 24);
        } else {
            String bcast = (broadcastAddress != null && !broadcastAddress.isBlank())
                    ? broadcastAddress : "255.255.255.255";
            nb.withBroadcast(bcast, udpPort);
        }

        IpNetwork net = nb.build();

        DefaultTransport tx = new DefaultTransport(net);
        tx.setTimeout(apduTimeoutMs);
        tx.setSegTimeout(apduSegTimeoutMs);
        tx.setRetries(apduRetries);

        LocalDevice ld = new LocalDevice(deviceInstance, tx);
        ld.initialize();

        // (Keep a global log-only listener so we can see I-Am chatter at startup)
        ld.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override public void iAmReceived(RemoteDevice d) {
                LOG.info("I-Am received: instance={} addr={}", d.getInstanceNumber(), d.getAddress());
            }
        });

        this.transport = tx;
        this.localDevice = ld;
        this.initialized = true;

        LOG.info("BACnet LocalDevice up: instance={} bind={} port={} ({} discovery mode)",
                deviceInstance, bindAddress, udpPort,
                (bindAddress != null && !bindAddress.isBlank()) ? "subnet" : "broadcast");
    }

    @Override
    public synchronized void stop() {
        initialized = false;
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
            public java.util.List<Device> discoverDevices(java.time.Duration timeout) throws Exception {
                if (!(initialized && localDevice != null)) {
                    throw new IllegalStateException("BACnet LocalDevice not initialized");
                }

                // Per-call collection of I-Ams (don’t rely solely on LocalDevice cache timing)
                final ConcurrentMap<Integer, RemoteDevice> seen = new ConcurrentHashMap<>();
                final AtomicLong lastIAmAt = new AtomicLong(0);

                DeviceEventAdapter collector = new DeviceEventAdapter() {
                    @Override public void iAmReceived(RemoteDevice d) {
                        seen.put(d.getInstanceNumber(), d);
                        lastIAmAt.set(System.currentTimeMillis());
                    }
                };

                // Attach temporary listener for this discovery window
                localDevice.getEventHandler().addListener(collector);
                try {
                    // Send Who-Is (global + directed)
                    final long maxWindowMs = Math.max(1500, Math.min(timeout != null ? timeout.toMillis() : 3000, 8000));

                    try {
                        localDevice.sendGlobalBroadcast(new WhoIsRequest());
                        LOG.debug("Sent Who-Is (global)");
                    } catch (Exception e) {
                        LOG.warn("Global Who-Is failed: {}", e.toString());
                    }

                    try {
                        String bcast = (broadcastAddress != null && !broadcastAddress.isBlank())
                                ? broadcastAddress : "255.255.255.255";
                        var addr = IpNetworkUtils.toAddress(bcast, udpPort);
                        localDevice.send(addr, new WhoIsRequest());
                        LOG.debug("Sent Who-Is (directed) to {}", addr);
                    } catch (Exception e) {
                        LOG.warn("Directed Who-Is failed: {}", e.toString());
                    }

                    // Debounce: wait until no new I-Am for quietGap, but cap at maxWindowMs
                    final long quietGapMs = 600; // a hair longer than previous 500ms
                    final long t0 = System.currentTimeMillis();
                    // small warm-up
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}

                    while (true) {
                        long now = System.currentTimeMillis();
                        long sinceLast = (lastIAmAt.get() == 0) ? (now - t0) : (now - lastIAmAt.get());

                        if (sinceLast >= quietGapMs && !seen.isEmpty()) break; // settled with at least one device
                        if ((now - t0) >= maxWindowMs) break;                  // absolute cap
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    }

                    // Merge what we saw with LocalDevice cache (in case some were added just after last event)
                    Collection<RemoteDevice> cached = localDevice.getRemoteDevices();
                    for (RemoteDevice rd : cached) seen.putIfAbsent(rd.getInstanceNumber(), rd);

                    var out = new ArrayList<Device>(seen.size());
                    for (RemoteDevice rd : seen.values()) {
                        String id = "device:" + rd.getInstanceNumber();
                        var meta = new LinkedHashMap<String, Object>();
                        meta.put("address", String.valueOf(rd.getAddress()));
                        out.add(new Device(id, "BACnet Device " + rd.getInstanceNumber(), meta));
                    }

                    LOG.info("discoverDevices: returning {} devices", out.size());
                    return out;
                } finally {
                    // always detach our per-call listener
                    try { localDevice.getEventHandler().removeListener(collector); } catch (Exception ignore) {}
                }
            }

            @Override
            public java.util.List<Point> discoverPoints(Device device, java.time.Duration timeout) {
                return java.util.List.of(); // not yet
            }
        };
    }

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
