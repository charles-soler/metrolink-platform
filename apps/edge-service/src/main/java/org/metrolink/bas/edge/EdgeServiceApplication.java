package org.metrolink.bas.edge;

import org.metrolink.bas.core.Kernel;
import org.metrolink.bas.core.ports.HealthPort;
import org.metrolink.bas.core.spi.ConnectorPlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.ServiceLoader;

@SpringBootApplication
public class EdgeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdgeServiceApplication.class, args);
    }

    // EdgeServiceApplication.java
    @Bean(destroyMethod = "stop")
    public ConnectorPlugin connectorPlugin() throws Exception {
        var plugin = ServiceLoader.load(ConnectorPlugin.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No ConnectorPlugin found"));
        plugin.init(Map.of());     // later: pass real config
        plugin.start();
        return plugin;
    }

    @Bean
    public Kernel kernel(ConnectorPlugin plugin) {
        return new Kernel(
                plugin.discovery(),
                plugin.reader(),
                plugin.writer(),
                plugin.subscribe(),
                plugin.health()
        );
    }

    @Bean
    public HealthPort healthPort(ConnectorPlugin plugin) {
        return plugin.health();
    }
}