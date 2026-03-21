package com.echo.emitter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echo.emitter")
public record EmitterProperties(
    String capturesDir,
    /**
     * TCP port for embedded WireMock. Default 8090. Use {@code 0} for a dynamic free port (e.g. tests).
     */
    Integer wireMockPort
) {
    public EmitterProperties {
        if (capturesDir == null || capturesDir.isBlank()) {
            capturesDir = "captures";
        }
        if (wireMockPort == null) {
            wireMockPort = 8090;
        }
    }
}
