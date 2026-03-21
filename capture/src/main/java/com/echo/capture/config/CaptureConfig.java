package com.echo.capture.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Configuration
@EnableConfigurationProperties(CaptureProperties.class)
public class CaptureConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder, CaptureProperties props) {
        WebClient.Builder b = builder.clone().baseUrl(props.backendBaseUrl());
        if (props.insecureSsl()) {
            try {
                var sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
                HttpClient httpClient = HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext));
                return b.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
            } catch (SSLException e) {
                throw new IllegalStateException("Failed to build insecure SSL context", e);
            }
        }
        return b.build();
    }
}
