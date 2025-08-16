package org.metrolink.bas.core.ports;

import org.metrolink.bas.core.model.Value;

import java.util.List;
import java.util.Map;

public interface ReaderPort {
    Map<String, Value> read(List<String> pointIds) throws Exception;
}
