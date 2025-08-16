package org.metrolink.bas.app;

import org.metrolink.bas.core.Kernel;
import org.metrolink.bas.core.historian.Historian;
import org.metrolink.bas.core.historian.InMemoryHistorian;
import org.metrolink.bas.core.model.Node;
import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.spi.ConnectorPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

public class SmokeMain {

    // Simple Flow.Subscriber that appends values to a Historian and stops after N
    static final class ValueCollector implements Flow.Subscriber<Value> {
        private final Historian hist;
        private final String pointIdFilter;
        private final CountDownLatch latch;
        private Flow.Subscription sub;

        ValueCollector(Historian hist, String pointIdFilter, int count) {
            this.hist = hist;
            this.pointIdFilter = pointIdFilter;
            this.latch = new CountDownLatch(count);
        }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.sub = subscription;
            // request "infinite" demand; our sim ignores backpressure anyway
            subscription.request(Long.MAX_VALUE);
        }

        @Override public void onNext(Value item) {
            if (pointIdFilter == null || pointIdFilter.equals(item.pointId())) {
                hist.append(item);
                latch.countDown();
            }
        }

        @Override public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }

        @Override public void onComplete() { /* no-op */ }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        void cancel() {
            if (sub != null) sub.cancel();
        }
    }

    public static void main(String[] args) throws Exception {
        var loader = ServiceLoader.load(ConnectorPlugin.class);
        var plugin = loader.findFirst().orElseThrow(() ->
                new IllegalStateException("No ConnectorPlugin found on classpath"));

        plugin.init(Map.of());
        plugin.start();

        try {
            var kernel = new Kernel(
                    plugin.discovery(), plugin.reader(), plugin.writer(),
                    plugin.subscribe(), plugin.health()
            );

            var nodes = kernel.discoverAndRegister();
            System.out.println("Discovered nodes:");
            for (Node n : nodes) {
                System.out.println("  - " + n.id() + " | " + n.name() + " (" + n.type() + ")");
            }
            if (nodes.isEmpty()) return;

            // ---- Subscribe demo (no polling) ----
            var tempId = "dev1/AI1";
            Historian hist = new InMemoryHistorian();

            var collector = new ValueCollector(hist, tempId, /*collect*/ 5);
            AutoCloseable handle = plugin.subscribe().subscribe(List.of(tempId), collector);

            // wait up to ~10s for 5 updates from the simulator
            collector.await(10, TimeUnit.SECONDS);
            handle.close();     // unsubscribe
            collector.cancel(); // be tidy

            var last = hist.last(tempId, 5);
            System.out.println("\nCOV-like samples for " + tempId + ":");
            for (int i = last.size() - 1; i >= 0; i--) {
                var v = last.get(i);
                System.out.println("  " + v.tsEpochMs() + " -> " + v.value());
            }

        } finally {
            plugin.stop();
        }
    }
}
