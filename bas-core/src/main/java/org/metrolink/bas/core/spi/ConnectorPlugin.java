package org.metrolink.bas.core.spi;

import org.metrolink.bas.core.ports.*;

public interface ConnectorPlugin extends LifecyclePort {
    String id();

    DiscoveryPort discovery();

    ReaderPort reader();

    WriterPort writer();

    SubscribePort subscribe();

    HealthPort health();
}
