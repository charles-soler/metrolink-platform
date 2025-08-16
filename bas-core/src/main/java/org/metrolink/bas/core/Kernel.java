package org.metrolink.bas.core;

import org.metrolink.bas.core.model.HealthStatus;
import org.metrolink.bas.core.model.Node;
import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.ports.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Kernel {
    private final DiscoveryPort discovery;
    private final ReaderPort reader;
    private final WriterPort writer;
    private final SubscribePort subscribe;
    private final HealthPort health;

    private final Map<String, Node> nodes = new ConcurrentHashMap<>();

    public Kernel(DiscoveryPort d, ReaderPort r, WriterPort w, SubscribePort s, HealthPort h) {
        this.discovery = d;
        this.reader = r;
        this.writer = w;
        this.subscribe = s;
        this.health = h;
    }

    public List<Node> discoverAndRegister() throws Exception {
        var devices = discovery.discoverDevices(Duration.ofSeconds(2));
        var out = new ArrayList<Node>();

        for (var dev : devices) {
            for (var p : discovery.discoverPoints(dev, Duration.ofSeconds(2))) {
                var n = new Node(p.id(), dev.id(), p.name(), p.kind(), p.writable(), p.meta());
                nodes.put(n.id(), n);
                out.add(n);
            }
        }

        return out;
    }

    public Map<String, Value> readNow(List<String> ids) throws Exception {
        return reader.read(ids);
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public HealthStatus health() {
        return health.health();
    }
}
