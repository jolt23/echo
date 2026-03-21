package com.echo.capture.service;

import com.echo.capture.config.CaptureProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CaptureService {

    private static final String MAPPINGS_SUBDIR = "mappings";
    private final WebClient webClient;
    private final CaptureProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicInteger counter = new AtomicInteger(0);

    public CaptureService(WebClient webClient, CaptureProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Mono<ProxyResponse> proxyAndCapture(
            HttpMethod method,
            String path,
            String queryString,
            HttpHeaders requestHeaders,
            Flux<DataBuffer> body
    ) {
        String backendPath = path.startsWith("/") ? path : "/" + path;
        String url = backendPath + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");

        return body
            .collectList()
            .flatMap(buffers -> {
                byte[] requestBody = buffers.stream()
                    .reduce(
                        new byte[0],
                        (acc, b) -> {
                            byte[] bytes = new byte[b.readableByteCount()];
                            b.read(bytes);
                            return concat(acc, bytes);
                        },
                        (a, b) -> concat(a, b)
                    );
                return proxyRequest(method, url, requestHeaders, requestBody)
                    .flatMap(response -> {
                        byte[] responseBody = response.body();
                        int status = response.status();
                        HttpHeaders responseHeaders = response.headers();
                        return writeMapping(method, backendPath, queryString,
                                status, responseHeaders, responseBody)
                            .thenReturn(response);
                    });
            });
    }

    private Mono<ProxyResponse> proxyRequest(
            HttpMethod method,
            String url,
            HttpHeaders requestHeaders,
            byte[] requestBody
    ) {
        // Build full URL string to preserve special characters like : in path
        String fullUrl = properties.backendBaseUrl() + url;
        
        WebClient.RequestBodySpec spec = webClient
            .method(method)
            .uri(fullUrl)
            .headers(h -> requestHeaders.forEach((name, values) -> {
                if (!isHopByHop(name)) {
                    h.addAll(name, values);
                }
            }));

        // Use retrieve() with onStatus to handle all status codes without throwing exceptions
        WebClient.ResponseSpec responseSpec = requestBody.length > 0
            ? spec.bodyValue(requestBody).retrieve()
            : spec.retrieve();
        
        Mono<ProxyResponse> response = responseSpec
            .onStatus(status -> true, clientResponse -> Mono.empty()) // Don't throw on any status
            .toEntity(byte[].class)
            .map(entity -> {
                HttpHeaders headers = new HttpHeaders();
                entity.getHeaders().forEach(headers::addAll);
                byte[] body = entity.getBody() != null ? entity.getBody() : new byte[0];
                return new ProxyResponse(entity.getStatusCode().value(), headers, body);
            });

        return response
            .onErrorResume(WebClientResponseException.class, e -> Mono.fromCallable(() ->
                new ProxyResponse(
                    e.getStatusCode().value(),
                    e.getHeaders(),
                    e.getResponseBodyAsByteArray() != null ? e.getResponseBodyAsByteArray() : new byte[0]
                )))
            .onErrorResume(e -> Mono.fromCallable(() -> transportErrorResponse(method, url, e)));
    }

    /**
     * No HTTP response from backend (DNS, TLS, connection reset, timeout). Returns JSON for debugging.
     */
    private ProxyResponse transportErrorResponse(HttpMethod method, String relativeUri, Throwable e) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("error", "BACKEND_TRANSPORT_ERROR");
        root.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        root.put("exceptionType", e.getClass().getName());
        Throwable c = e.getCause();
        int depth = 0;
        List<String> causes = new ArrayList<>();
        while (c != null && depth < 5) {
            causes.add(c.getClass().getName() + ": " + (c.getMessage() != null ? c.getMessage() : ""));
            c = c.getCause();
            depth++;
        }
        if (!causes.isEmpty()) {
            root.put("causes", String.join(" | ", causes));
        }
        root.put("method", method.name());
        root.put("relativeUri", relativeUri);
        root.put("backendBaseUrl", properties.backendBaseUrl());
        root.put(
            "hint",
            "No HTTP response from backend; verify BACKEND_BASE_URL, TLS (CAPTURE_INSECURE_SSL), DNS, and network. "
                + "If the backend returned 403/401, you should see that status and body on success — "
                + "this payload means the TCP/TLS layer failed before any HTTP response."
        );

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
            bytes = ("{\"error\":\"BACKEND_TRANSPORT_ERROR\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        }
        return new ProxyResponse(502, h, bytes);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Mono<Void> writeMapping(
            HttpMethod method,
            String path,
            String queryString,
            int responseStatus,
            HttpHeaders responseHeaders,
            byte[] responseBody
    ) {
        return Mono.fromCallable(() -> {
            Path root = Path.of(properties.capturesDir()).toAbsolutePath();
            Path mappingsDir = root.resolve(MAPPINGS_SUBDIR);
            Files.createDirectories(mappingsDir);

            String safePath = path.replaceAll("[^a-zA-Z0-9/_-]", "_");
            if (safePath.length() > 100) {
                safePath = safePath.substring(0, 100);
            }
            String filename = String.format("%04d-%s-%s.json", counter.incrementAndGet(), method.name().toLowerCase(), safePath.replace("/", "-"));
            Path file = mappingsDir.resolve(filename);

            // WireMock-compatible request
            ObjectNode request = objectMapper.createObjectNode();
            request.put("method", method.name());
            String url = path + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
            request.put("url", url);

            // WireMock-compatible response
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", responseStatus);
            if (responseBody.length > 0) {
                if (isPrintable(responseBody)) {
                    response.put("body", new String(responseBody, StandardCharsets.UTF_8));
                } else {
                    response.put("base64Body", Base64.getEncoder().encodeToString(responseBody));
                }
            }
            Map<String, Object> headers = new HashMap<>();
            responseHeaders.forEach((name, values) -> {
                if (!isHopByHop(name) && !values.isEmpty()) {
                    headers.put(name, values.size() == 1 ? values.get(0) : values);
                }
            });
            if (!headers.isEmpty()) {
                response.set("headers", objectMapper.valueToTree(headers));
            }

            ObjectNode mapping = objectMapper.createObjectNode();
            mapping.set("request", request);
            mapping.set("response", response);
            mapping.put("priority", 1);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), mapping);
            return null;
        });
    }

    private static boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return "connection".equals(lower) || "keep-alive".equals(lower)
            || "proxy-authenticate".equals(lower) || "proxy-authorization".equals(lower)
            || "te".equals(lower) || "trailers".equals(lower) || "transfer-encoding".equals(lower)
            || "upgrade".equals(lower) || "host".equals(lower); // Host must be set by WebClient based on target URI
    }

    private static boolean isPrintable(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 32 && b != '\n' && b != '\r' && b != '\t') {
                return false;
            }
            if (b > 126) {
                return false;
            }
        }
        return true;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    public record ProxyResponse(int status, HttpHeaders headers, byte[] body) {}
}
