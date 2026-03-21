package com.echo.emitter.handler;

import com.echo.emitter.client.WireMockForwardClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class EmitterHandler {

    private static final String EMITTER_PREFIX = "/echo/emitter";
    private static final String BACKEND_DOWN_MESSAGE = "Backend is down: no recorded response for this request";

    private final WireMockForwardClient wireMockForwardClient;

    public EmitterHandler(WireMockForwardClient wireMockForwardClient) {
        this.wireMockForwardClient = wireMockForwardClient;
    }

    public Mono<ServerResponse> replay(ServerRequest request) {
        String path = request.path();
        if (!path.startsWith(EMITTER_PREFIX)) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST).build();
        }
        String stubPath = path.substring(EMITTER_PREFIX.length());
        if (stubPath.isEmpty()) {
            stubPath = "/";
        }
        String query = request.uri().getRawQuery();
        String uri = query != null && !query.isBlank() ? stubPath + "?" + query : stubPath;
        HttpMethod method = request.method();
        HttpHeaders headers = request.headers().asHttpHeaders();
        var body = request.bodyToFlux(DataBuffer.class);

        return wireMockForwardClient.forward(method, uri, headers, body)
            .flatMap(response -> {
                if (response.statusCode() == HttpStatus.NOT_FOUND) {
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(BACKEND_DOWN_MESSAGE);
                }
                var resp = ServerResponse.status(response.statusCode())
                    .headers(h -> response.headers().asHttpHeaders().forEach((name, values) -> {
                        if (!isHopByHop(name)) {
                            h.addAll(name, values);
                        }
                    }));
                return response.bodyToMono(byte[].class)
                    .defaultIfEmpty(new byte[0])
                    .flatMap(bytes -> bytes.length > 0 ? resp.bodyValue(bytes) : resp.build());
            })
            .switchIfEmpty(ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(BACKEND_DOWN_MESSAGE))
            .onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(BACKEND_DOWN_MESSAGE));
    }

    private static boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return "connection".equals(lower) || "keep-alive".equals(lower)
            || "transfer-encoding".equals(lower);
    }
}
