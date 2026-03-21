package com.echo.emitter.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(EmitterProperties.class)
public class WireMockServerConfig {

    @Bean(destroyMethod = "stop")
    public WireMockServer wireMockServer(EmitterProperties props) {
        Path rootDir = Path.of(props.capturesDir()).toAbsolutePath();
        WireMockConfiguration options = WireMockConfiguration.options()
            .withRootDirectory(rootDir.toString());
        if (props.wireMockPort() == 0) {
            options = options.dynamicPort();
        } else {
            options = options.port(props.wireMockPort());
        }
        WireMockServer server = new WireMockServer(options);
        server.start();
        return server;
    }
}
