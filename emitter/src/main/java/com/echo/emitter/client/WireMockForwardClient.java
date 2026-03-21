package com.echo.emitter.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Forwards HTTP requests to the embedded {@link WireMockServer} (uses its bound port, including dynamic ports).
 */
@Component
public class WireMockForwardClient {

    private final WebClient webClient;

    public WireMockForwardClient(WebClient.Builder webClientBuilder, WireMockServer wireMockServer) {
        this.webClient = webClientBuilder
            .baseUrl("http://127.0.0.1:" + wireMockServer.port())
            .build();
    }

    public Mono<ClientResponse> forward(
            HttpMethod method,
            String uriPathAndQuery,
            HttpHeaders headers,
            Flux<DataBuffer> body
    ) {
        WebClient.RequestBodySpec spec = webClient
            .method(method)
            .uri(uriPathAndQuery)
            .headers(h -> headers.forEach((name, values) -> {
                if (!isHopByHop(name)) {
                    h.addAll(name, values);
                }
            }));

        return body.collectList()
            .flatMap(buffers -> {
                byte[] b = buffers.stream()
                    .reduce(
                        new byte[0],
                        (acc, buf) -> {
                            byte[] bytes = new byte[buf.readableByteCount()];
                            buf.read(bytes);
                            return concat(acc, bytes);
                        },
                        (a, b2) -> concat(a, b2)
                    );
                if (b.length > 0) {
                    return spec.bodyValue(b).exchange();
                }
                return spec.exchange();
            })
            .onErrorResume(e -> Mono.error(new IllegalStateException("WireMock unreachable", e)));
    }

    private static boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return "connection".equals(lower) || "keep-alive".equals(lower)
            || "transfer-encoding".equals(lower);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
