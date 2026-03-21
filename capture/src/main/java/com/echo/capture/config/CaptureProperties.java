package com.echo.capture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "echo.capture")
public record CaptureProperties(
    String backendBaseUrl,
    String capturesDir,
    /**
     * When {@code true}, TLS connections to the backend trust any certificate (no hostname/CN check).
     * <strong>Development only</strong> — never enable in production.
     */
    Boolean insecureSsl
) {
    public CaptureProperties {
        if (backendBaseUrl == null || backendBaseUrl.isBlank()) {
            backendBaseUrl = "http://localhost:8080";
        }
        if (capturesDir == null || capturesDir.isBlank()) {
            capturesDir = "captures";
        }
        if (insecureSsl == null) {
            insecureSsl = false;
        }
    }
}
