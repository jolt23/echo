package com.echo.capture.handler;

import com.echo.capture.service.CaptureService;
import com.echo.capture.service.CaptureService.ProxyResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class CaptureHandler {

    private static final String CAPTURE_PREFIX = "/echo/capture";

    private final CaptureService captureService;

    public CaptureHandler(CaptureService captureService) {
        this.captureService = captureService;
    }

    public Mono<ServerResponse> proxyAndCapture(ServerRequest request) {
        String path = request.path();
        if (!path.startsWith(CAPTURE_PREFIX)) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST).build();
        }
        String backendPath = path.substring(CAPTURE_PREFIX.length());
        if (backendPath.isEmpty()) {
            backendPath = "/";
        }
        String queryString = request.uri().getRawQuery();
        HttpMethod method = request.method();
        var headers = request.headers().asHttpHeaders();
        var body = request.bodyToFlux(org.springframework.core.io.buffer.DataBuffer.class);

        return captureService.proxyAndCapture(method, backendPath, queryString, headers, body)
            .flatMap((ProxyResponse r) -> {
                var resp = ServerResponse.status(r.status())
                    .headers(h -> r.headers().forEach((name, values) -> {
                        if (!isHopByHop(name)) {
                            h.addAll(name, values);
                        }
                    }));
                if (r.body().length > 0) {
                    return resp.bodyValue(r.body());
                }
                return resp.build();
            })
            .onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    private static boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return "connection".equals(lower) || "keep-alive".equals(lower)
            || "transfer-encoding".equals(lower);
    }
}
