package com.echo.emitter.router;

import com.echo.emitter.handler.EmitterAdminProxyHandler;
import com.echo.emitter.handler.EmitterHandler;
import com.echo.emitter.handler.EmitterReloadHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class EmitterRouter {

    @Bean
    public RouterFunction<ServerResponse> emitterRoutes(
            EmitterReloadHandler reloadHandler,
            EmitterAdminProxyHandler adminProxyHandler,
            EmitterHandler handler
    ) {
        return RouterFunctions.route(
            RequestPredicates.POST("/echo/emitter/__reload"),
            reloadHandler::reload
        ).andRoute(
            RequestPredicates.path("/echo/emitter/__admin/**")
                .or(RequestPredicates.path("/echo/emitter/__admin")),
            adminProxyHandler::proxyAdmin
        ).andRoute(
            RequestPredicates.path("/echo/emitter/**").and(RequestPredicates.accept(MediaType.ALL)),
            handler::replay
        );
    }
}
