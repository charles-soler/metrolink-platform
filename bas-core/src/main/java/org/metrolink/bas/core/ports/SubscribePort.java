package org.metrolink.bas.core.ports;

import org.metrolink.bas.core.model.Value;

import java.util.List;
import java.util.concurrent.Flow;

public interface SubscribePort {
    AutoCloseable subscribe(List<String> pointIds, Flow.Subscriber<Value> subscriber) throws Exception;
}
