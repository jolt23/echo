package com.echo.emitter.service;

import com.echo.emitter.config.EmitterProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reloads stub mappings from {@code captures/mappings} without restarting WireMock.
 */
@Service
public class MappingReloadService {

    private static final Logger log = LoggerFactory.getLogger(MappingReloadService.class);

    private final WireMockServer wireMockServer;
    private final EmitterProperties properties;

    public MappingReloadService(WireMockServer wireMockServer, EmitterProperties properties) {
        this.wireMockServer = wireMockServer;
        this.properties = properties;
    }

    public void reloadFromDisk() {
        Path rootDir = Path.of(properties.capturesDir()).toAbsolutePath();
        try {
            Files.createDirectories(rootDir.resolve("mappings"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Same as initial load from --root-dir: re-reads all JSON under mappings/
        wireMockServer.resetToDefaultMappings();
        log.info("Reloaded WireMock mappings from {}", rootDir.resolve("mappings"));
    }
}
