package com.echo.emitter.handler;

import com.echo.emitter.client.WireMockForwardClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Exposes WireMock's admin API through the emitter HTTP port at
 * {@code /echo/emitter/__admin/**} → {@code http://wiremock/__admin/**}.
 * Intended for local development only (no authentication).
 */
@Component
public class EmitterAdminProxyHandler {

    private static final String ADMIN_PREFIX = "/echo/emitter/__admin";

    private final WireMockForwardClient wireMockForwardClient;

    public EmitterAdminProxyHandler(WireMockForwardClient wireMockForwardClient) {
        this.wireMockForwardClient = wireMockForwardClient;
    }

    public Mono<ServerResponse> proxyAdmin(ServerRequest request) {
        String path = request.path();
        if (!path.startsWith(ADMIN_PREFIX)) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST).build();
        }
        String suffix = path.length() > ADMIN_PREFIX.length()
            ? path.substring(ADMIN_PREFIX.length())
            : "";
        if (suffix.isEmpty() || "/".equals(suffix)) {
            suffix = "";
        } else if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        String wireMockUri = suffix.isEmpty() ? "/__admin" : "/__admin" + suffix;

        String query = request.uri().getRawQuery();
        String uri = query != null && !query.isBlank() ? wireMockUri + "?" + query : wireMockUri;

        HttpMethod method = request.method();
        HttpHeaders headers = request.headers().asHttpHeaders();
        var body = request.bodyToFlux(DataBuffer.class);

        return wireMockForwardClient.forward(method, uri, headers, body)
            .flatMap(response -> {
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
            .switchIfEmpty(ServerResponse.status(HttpStatus.BAD_GATEWAY).build())
            .onErrorResume(e -> ServerResponse.status(HttpStatus.BAD_GATEWAY).build());
    }

    private static boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return "connection".equals(lower) || "keep-alive".equals(lower)
            || "transfer-encoding".equals(lower);
    }
}
