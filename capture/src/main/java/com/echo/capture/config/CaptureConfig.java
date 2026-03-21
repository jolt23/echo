package com.echo.capture.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Configuration
@EnableConfigurationProperties(CaptureProperties.class)
public class CaptureConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder, CaptureProperties props) {
        // Don't set baseUrl - we'll build full URLs in the service to avoid encoding issues with :
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        
        WebClient.Builder b = builder.clone().uriBuilderFactory(factory);
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
