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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    public String id() { return "bacnet"; }

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

        // Either withSubnet(...) or withBroadcast(...)
        if (bindAddress != null && !bindAddress.isBlank()) {
            nb.withLocalBindAddress(bindAddress);
            // mask 24 is common for 192.168.x.x; we can expose later as config
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

        // Log I-Am events (good sanity signal)
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

                // Tunables (could be made configurable later)
                long idleWindowMs = 1500;        // stop when no new I-Am for this long
                long maxDurationMs = 12_000;     // hard cap overall
                if (timeout != null && timeout.toMillis() > 0) {
                    maxDurationMs = Math.min(maxDurationMs, timeout.toMillis());
                }

                // Collect instances & last-seen addresses directly from I-Am events
                Map<Integer, String> seen = new ConcurrentHashMap<>();
                AtomicLong lastSeen = new AtomicLong(System.currentTimeMillis());

                DeviceEventAdapter listener = new DeviceEventAdapter() {
                    @Override public void iAmReceived(RemoteDevice d) {
                        seen.put(d.getInstanceNumber(), String.valueOf(d.getAddress()));
                        lastSeen.set(System.currentTimeMillis());
                        LOG.debug("I-Am (cache): {} @ {}", d.getInstanceNumber(), d.getAddress());
                    }
                };
                localDevice.getEventHandler().addListener(listener);

                long start = System.currentTimeMillis();
                try {
                    // Who-Is bursts: t≈0s, 1s, 3s (helps across routed segments / slow stacks)
                    sendWhoIsBurst(0);
                    // spin-wait with idle window
                    boolean sentAt1s = false, sentAt3s = false;
                    for (;;) {
                        Thread.sleep(100);
                        long now = System.currentTimeMillis();
                        long sinceLast = now - lastSeen.get();

                        if (!sentAt1s && now - start >= 1000) {
                            sendWhoIsBurst(1);
                            sentAt1s = true;
                        }
                        if (!sentAt3s && now - start >= 3000) {
                            sendWhoIsBurst(3);
                            sentAt3s = true;
                        }

                        if (sinceLast >= idleWindowMs) break;      // idle long enough
                        if (now - start >= maxDurationMs) break;    // or hit cap
                    }

                    // Build Devices from what we actually saw (don’t rely on cache timing)
                    var out = new java.util.ArrayList<Device>(seen.size());
                    seen.forEach((inst, addr) -> out.add(
                            new Device("device:" + inst, "BACnet Device " + inst, Map.of("address", addr))
                    ));
                    LOG.info("Discovery complete: {} device(s) in ~{} ms",
                            out.size(), System.currentTimeMillis() - start);
                    return out;
                } finally {
                    localDevice.getEventHandler().removeListener(listener);
                }
            }

            private void sendWhoIsBurst(int markSec) {
                try {
                    localDevice.sendGlobalBroadcast(new WhoIsRequest());
                    LOG.debug("Who-Is burst@{}s: global", markSec);
                } catch (Exception e) {
                    LOG.warn("Who-Is (global) failed: {}", e.toString());
                }
                try {
                    String bcast = (broadcastAddress != null && !broadcastAddress.isBlank())
                            ? broadcastAddress : "255.255.255.255";
                    var addr = IpNetworkUtils.toAddress(bcast, udpPort);
                    localDevice.send(addr, new WhoIsRequest());
                    LOG.debug("Who-Is burst@{}s: directed {}", markSec, addr);
                } catch (Exception e) {
                    LOG.warn("Who-Is (directed) failed: {}", e.toString());
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
        return pointIds -> { throw new UnsupportedOperationException("BACnet read not implemented (devices-only)"); };
    }

    @Override
    public WriterPort writer() {
        return (pointId, value, options) -> { throw new UnsupportedOperationException("BACnet write not implemented (devices-only)"); };
    }

    @Override
    public SubscribePort subscribe() {
        return (pointIds, subscriber) -> { throw new UnsupportedOperationException("BACnet subscribe not implemented (devices-only)"); };
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
