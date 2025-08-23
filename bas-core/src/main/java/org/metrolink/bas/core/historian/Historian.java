package org.metrolink.bas.core.historian;

import org.metrolink.bas.core.model.Value;

import java.util.List;

public interface Historian {
    void append(Value v);                       // store one sample

    List<Value> last(String pointId, int n);    // last N samples for a point
}
