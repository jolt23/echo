package com.echo.emitter.schedule;

import com.echo.emitter.config.EmitterProperties;
import com.echo.emitter.service.MappingReloadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional periodic reload so new captures appear without restarting the emitter.
 * Enable with {@code echo.emitter.reload-mappings-fixed-delay-ms} (milliseconds between reloads).
 */
@Component
@ConditionalOnProperty(prefix = "echo.emitter", name = "reload-mappings-fixed-delay-ms", matchIfMissing = false)
public class MappingReloadScheduler {

    private final MappingReloadService mappingReloadService;

    public MappingReloadScheduler(MappingReloadService mappingReloadService) {
        this.mappingReloadService = mappingReloadService;
    }

    @Scheduled(fixedDelayString = "${echo.emitter.reload-mappings-fixed-delay-ms}")
    public void reload() {
        mappingReloadService.reloadFromDisk();
    }
}
