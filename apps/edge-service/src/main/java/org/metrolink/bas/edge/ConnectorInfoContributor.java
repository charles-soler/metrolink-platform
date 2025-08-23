package org.metrolink.bas.edge;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConnectorInfoContributor implements InfoContributor {
    private final ConnectorRuntimeInfo rt;

    public ConnectorInfoContributor(ConnectorRuntimeInfo rt) {
        this.rt = rt;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("connector", Map.of(
                "id", rt.id(),
                "cfg", rt.cfg()
        ));
    }
}
