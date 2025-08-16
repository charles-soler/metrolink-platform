package org.metrolink.bas.core.historian;

import org.metrolink.bas.core.model.Value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class InMemoryHistorian implements Historian {
    // per-point ring buffers (unbounded for now; simple)
    private final Map<String, Deque<Value>> store = new ConcurrentHashMap<>();

    @Override
    public void append(Value v) {
        store.computeIfAbsent(v.pointId(), k -> new ConcurrentLinkedDeque<>()).addLast(v);
    }

    @Override
    public List<Value> last(String pointId, int n) {
        var q = store.getOrDefault(pointId, new ConcurrentLinkedDeque<>());
        var out = new ArrayList<Value>(Math.min(n, q.size()));
        var it = q.descendingIterator();
        while (it.hasNext() && out.size() < n) out.add(it.next());
        return out;
    }
}
