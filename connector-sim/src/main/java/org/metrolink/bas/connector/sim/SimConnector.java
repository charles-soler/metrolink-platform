package org.metrolink.bas.connector.sim;

import org.metrolink.bas.core.model.Device;
import org.metrolink.bas.core.model.Point;
import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.ports.*;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

public final class SimConnector implements ConnectorPlugin {
    private final Random rnd = new Random();
    private final Map<String, Double> state = new ConcurrentHashMap<>();
    private final List<Flow.Subscriber<? super Value>> subscribers = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService ses;
    private volatile double drift = 0.2;
    private volatile long periodMs = 1000;

    @Override
    public String id() {
        return "sim";
    }

    // ---- Lifecycle ----
    @Override public void init(Map<String, Object> config) {
        // Defaults if keys missing
        double start  = ((Number) config.getOrDefault("ai1Start", 21.0)).doubleValue();
        this.drift    = ((Number) config.getOrDefault("ai1Drift", 0.2)).doubleValue();
        this.periodMs = ((Number) config.getOrDefault("periodMs", 1000)).longValue();

        state.putIfAbsent("dev1/AI1", start);
        state.putIfAbsent("dev1/AO1", 0.0);
    }

    @Override
    public void start() {
        // seed some state
        state.putIfAbsent("dev1/AI1", 21.0);   // Room Temp
        state.putIfAbsent("dev1/AO1", 0.0);    // Damper Cmd (writable)
        ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(() -> {
            double cur = state.getOrDefault("dev1/AI1", 21.0);
            double next = cur + (rnd.nextDouble() - 0.5) * drift; // use configured drift
            state.put("dev1/AI1", next);
            Value v = new Value("dev1/AI1", next, System.currentTimeMillis());
            for (var s : subscribers) { try { s.onNext(v); } catch (Throwable ignored) {} }
        }, 0, periodMs, TimeUnit.MILLISECONDS); // use configured period
    }

    @Override
    public void stop() {
        if (ses != null) ses.shutdownNow();
    }

    // ---- Ports ----
    @Override
    public DiscoveryPort discovery() {
        return new DiscoveryPort() {
            @Override
            public List<Device> discoverDevices(Duration timeout) {
                return List.of(new Device("dev1", "Sim Device 1", Map.of()));
            }

            @Override
            public List<Point> discoverPoints(Device d, Duration timeout) {
                return List.of(
                        new Point("dev1/AI1", d.id(), "Room Temp", "analogInput", false, Map.of("units", "°C")),
                        new Point("dev1/AO1", d.id(), "Damper Cmd", "analogOutput", true, Map.of("units", "%"))
                );
            }
        };
    }

    @Override
    public ReaderPort reader() {
        return pointIds -> {
            Map<String, Value> out = new HashMap<>();
            long now = System.currentTimeMillis();
            for (String id : pointIds) {
                double v = state.getOrDefault(id, 0.0);
                out.put(id, new Value(id, v, now));
            }
            return out;
        };
    }

    @Override
    public WriterPort writer() {
        return (pointId, value, options) -> {
            if (!pointId.equals("dev1/AO1")) {
                throw new IllegalArgumentException("Only dev1/AO1 is writable in simulator");
            }
            double v = ((Number) value).doubleValue();
            state.put(pointId, v);
        };
    }

    @Override
    public SubscribePort subscribe() {
        return (pointIds, subscriber) -> {
            // basic, backpressure-agnostic subscription
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) { /* no-op */ }

                @Override
                public void cancel() {
                    subscribers.remove(subscriber);
                }
            });
            subscribers.add(subscriber);
            return () -> subscribers.remove(subscriber);
        };
    }

    @Override
    public HealthPort health() {
        return () -> new HealthStatus(true, Map.of(
                "points", state.size(),
                "subscribers", subscribers.size()
        ));
    }
}
