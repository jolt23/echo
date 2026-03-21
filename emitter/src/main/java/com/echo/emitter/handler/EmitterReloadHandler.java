package com.echo.emitter.handler;

import com.echo.emitter.service.MappingReloadService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class EmitterReloadHandler {

    private final MappingReloadService mappingReloadService;

    public EmitterReloadHandler(MappingReloadService mappingReloadService) {
        this.mappingReloadService = mappingReloadService;
    }

    /**
     * Reloads {@code captures/mappings/*.json} into WireMock without restarting.
     */
    public Mono<ServerResponse> reload(ServerRequest request) {
        return Mono.fromRunnable(mappingReloadService::reloadFromDisk)
            .then(ServerResponse.noContent().build())
            .onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue("Failed to reload mappings: " + e.getMessage()));
    }
}
