package com.echo.emitter;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmitterIntegrationTest {

    static Path capturesDir;

    @DynamicPropertySource
    static void emitterProps(DynamicPropertyRegistry registry) throws IOException {
        if (capturesDir == null) {
            capturesDir = Files.createTempDirectory("echo-emitter-it");
            Path mappings = capturesDir.resolve("mappings");
            Files.createDirectories(mappings);
            Files.writeString(
                mappings.resolve("hello.json"),
                """
                    {
                      "request": { "method": "GET", "url": "/api/hello" },
                      "response": { "status": 200, "body": "hi" }
                    }
                    """
            );
        }
        registry.add("echo.emitter.captures-dir", () -> capturesDir.toAbsolutePath().toString());
        registry.add("echo.emitter.wire-mock-port", () -> 0);
    }

    @Autowired
    WebTestClient webTestClient;

    @Test
    @Order(1)
    void emitterReplaysRecordedStub() {
        webTestClient
            .get()
            .uri("/echo/emitter/api/hello")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("hi");
    }

    @Test
    @Order(2)
    void unknownStubReturns500() {
        webTestClient
            .get()
            .uri("/echo/emitter/api/missing")
            .exchange()
            .expectStatus().isEqualTo(500)
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("Backend is down"));
    }

    @Test
    @Order(3)
    void wireMockAdminExposedThroughEmitterPort() {
        webTestClient
            .get()
            .uri("/echo/emitter/__admin/mappings")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("mappings"));
    }

    @Test
    @Order(4)
    void reloadEndpointReturns204() {
        webTestClient
            .post()
            .uri("/echo/emitter/__reload")
            .exchange()
            .expectStatus().isNoContent();
    }
}
