package com.echo.capture.router;

import com.echo.capture.handler.CaptureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class CaptureRouter {

    @Bean
    public RouterFunction<ServerResponse> captureRoute(CaptureHandler handler) {
        return RouterFunctions.route(
            RequestPredicates.path("/echo/capture/**").and(RequestPredicates.accept(MediaType.ALL)),
            handler::proxyAndCapture
        );
    }
}
