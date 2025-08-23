package org.metrolink.bas.core.scheduler;

import org.metrolink.bas.core.model.Value;
import org.metrolink.bas.core.ports.ReaderPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PollScheduler implements AutoCloseable {
    private final ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "poll-scheduler");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> task;

    public AutoCloseable start(ReaderPort reader,
                               List<String> pointIds,
                               Duration interval,
                               Consumer<Map<String, Value>> onBatch) {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(pointIds);
        Objects.requireNonNull(interval);
        Objects.requireNonNull(onBatch);

        task = ses.scheduleAtFixedRate(() -> {
            try {
                onBatch.accept(reader.read(pointIds));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);

        return this::stop;
    }

    private void stop() {
        if (task != null) task.cancel(true);
    }

    @Override
    public void close() {
        stop();
        ses.shutdownNow();
    }
}
