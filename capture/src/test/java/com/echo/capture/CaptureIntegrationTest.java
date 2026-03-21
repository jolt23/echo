package com.echo.capture;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CaptureIntegrationTest {

    static MockWebServer backend;
    static Path capturesDir;

    @DynamicPropertySource
    static void captureProps(DynamicPropertyRegistry registry) throws IOException {
        if (backend == null) {
            backend = new MockWebServer();
            backend.start();
        }
        if (capturesDir == null) {
            capturesDir = Files.createTempDirectory("echo-capture-it");
        }
        registry.add("echo.capture.backend-base-url", () -> "http://localhost:" + backend.getPort());
        registry.add("echo.capture.captures-dir", () -> capturesDir.toAbsolutePath().toString());
    }

    @BeforeEach
    void enqueueBackend() {
        backend.enqueue(new MockResponse().setBody("OK").setHeader("Content-Type", "text/plain"));
    }

    @AfterAll
    static void shutdownBackend() throws IOException {
        backend.shutdown();
    }

    @Autowired
    WebTestClient webTestClient;

    @Test
    void captureProxiesAndWritesMapping() throws IOException {
        webTestClient
            .get()
            .uri("/echo/capture/hello")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("OK");

        try (var stream = Files.list(capturesDir.resolve("mappings"))) {
            assertThat(stream.count()).isEqualTo(1);
        }
    }
}
