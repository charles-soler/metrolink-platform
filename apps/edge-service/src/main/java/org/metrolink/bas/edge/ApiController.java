package org.metrolink.bas.edge;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.metrolink.bas.core.Kernel;
import org.metrolink.bas.core.model.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Kernel kernel;

    // metrics
    private final Counter discoverCounter;
    private final Counter readCounter;
    private final Counter writeCounter;
    private final Timer readTimer;

    public ApiController(Kernel kernel, MeterRegistry registry) {
        this.kernel = kernel;

        // counters/timer
        this.discoverCounter = registry.counter("bas_discover");
        this.readCounter = registry.counter("bas_reads");
        this.writeCounter = registry.counter("bas_writes");
        // name includes _seconds so Prometheus will export *_seconds_* series
        this.readTimer = registry.timer("bas_read_latency_seconds");

        // gauge node count; Micrometer will call this function when scraping
        registry.gauge("bas_nodes_total", this.kernel, k -> (double) k.nodes().size());
    }

    @PostMapping("/discover")
    public List<?> discover() throws Exception {
        var nodes = kernel.discoverAndRegister();
        discoverCounter.increment();
        return nodes;
    }

    @PostMapping("/devices")
    public List<Map<String, Object>> listDevices() throws Exception {
        var devices = kernel.discoverDevices(Duration.ofSeconds(2)); // short wait for I-Am replies
        return devices.stream()
                .map(d -> Map.of(
                        "id", d.id(),
                        "name", d.name(),
                        "meta", d.meta()))
                .toList();
    }

    @GetMapping("/nodes")
    public List<?> nodes() {
        return kernel.nodes().stream().toList();
    }

    @GetMapping("/read")
    public Map<String, Value> read(@RequestParam List<String> ids) throws Exception {
        readCounter.increment();
        var sample = Timer.start();
        try {
            return kernel.readNow(ids);
        } finally {
            sample.stop(readTimer);
        }
    }

    @PostMapping("/write")
    public ResponseEntity<Void> write(@RequestParam String id, @RequestParam double value) throws Exception {
        kernel.writeNow(id, value);
        writeCounter.increment();
        return ResponseEntity.noContent().build();
    }
}
