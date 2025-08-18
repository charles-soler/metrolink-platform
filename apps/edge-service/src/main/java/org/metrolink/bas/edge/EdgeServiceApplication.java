package org.metrolink.bas.edge;

import org.metrolink.bas.core.Kernel;
import org.metrolink.bas.core.ports.HealthPort;
import org.metrolink.bas.core.spi.ConnectorPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.ServiceLoader;

@SpringBootApplication
@EnableConfigurationProperties({
        SimConnectorProperties.class,
        BacnetConnectorProperties.class,
        ConnectorsSelectionProperties.class
})
public class EdgeServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(EdgeServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EdgeServiceApplication.class, args);
    }

    @Bean
    public ConnectorRuntimeInfo connectorRuntimeInfo(
            ConnectorsSelectionProperties selection,
            SimConnectorProperties simProps,
            BacnetConnectorProperties bacnetProps
    ) {
        String id = selection.getActive(); // "sim" or "bacnet"
        Map<String,Object> cfg = "sim".equals(id)
                ? Map.of(
                "ai1Start", simProps.getAi1Start(),
                "ai1Drift", simProps.getAi1Drift(),
                "periodMs", simProps.getPeriodMs()
        )
                : Map.of(
                "deviceInstance", bacnetProps.getDeviceInstance(),
                "apduTimeoutMs", bacnetProps.getApduTimeoutMs(),
                "covRenewSec", bacnetProps.getCovRenewSec(),
                "defaultCovIncrement", bacnetProps.getDefaultCovIncrement(),
                "bbmdEnabled", bacnetProps.isBbmdEnabled()
        );
        return new ConnectorRuntimeInfo(id, cfg);
    }

    @Bean(destroyMethod = "stop")
    public ConnectorPlugin connectorPlugin(ConnectorRuntimeInfo rt) throws Exception {
        var plugin = ServiceLoader.load(ConnectorPlugin.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p.id().equals(rt.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConnectorPlugin found with id=" + rt.id()));

        log.info("Starting connector id={} with cfg={}", rt.id(), rt.cfg());
        plugin.init(rt.cfg());
        plugin.start();
        return plugin;
    }

    @Bean
    public Kernel kernel(ConnectorPlugin plugin) {
        return new Kernel(plugin.discovery(), plugin.reader(), plugin.writer(), plugin.subscribe(), plugin.health());
    }

    @Bean
    public HealthPort healthPort(ConnectorPlugin plugin) {
        return plugin.health();
    }
}
