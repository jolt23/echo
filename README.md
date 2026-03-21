# Echo — API Mock Recording & Replay

![CI](https://github.com/YOUR_USERNAME/echo/workflows/CI/badge.svg)

A Spring Boot WebFlux application for **recording real API interactions** and **replaying them as mocks**. This enables you to capture live API request/response pairs and use them for testing, development, or offline work without dependencies on external services.

## Purpose

**Echo** helps you create realistic API mocks by:

1. **Recording:** Use **Capture** to proxy requests to a real backend API (e.g., Google Maps, Stripe, internal services). Each request/response is automatically saved as a WireMock-compatible JSON mapping.
2. **Replaying:** Use **Emitter** to serve the recorded responses. Your application calls the Emitter instead of the real API, receiving the exact responses that were captured.

**Use cases:**
- **Offline development** – Work without internet or when external APIs are unavailable
- **Testing** – Create deterministic test fixtures from real API responses
- **Cost reduction** – Avoid repeated calls to rate-limited or paid APIs
- **Performance** – Eliminate network latency by serving mocked responses locally
- **Demo environments** – Showcase features without exposing API keys or depending on third-party uptime

## How It Works

Both applications share a **`captures/`** directory containing WireMock-style JSON mapping files:

- **`captures/mappings/`** — JSON stub files created by Capture and served by Emitter

### Workflow

```
1. Point Capture at real API → Make requests → JSON mappings created
2. Point your app at Emitter → Receives recorded responses → No real API calls
```

## Applications

### 1. Capture (`echo-capture`) — Recording Tool

A **pass-through proxy** that captures real API interactions and generates WireMock JSON mappings.

**How to use:**
1. Configure `BACKEND_BASE_URL` to point at the real API (e.g., `https://api.stripe.com`, `https://routes.googleapis.com`)
2. Make requests to `http://<host>:9090/echo/capture/<api-path>`
3. Capture forwards the request to the real backend, records both request and response, and returns the backend's response to you

- **Endpoint:** `http://<host>:9090/echo/capture/*`
- **Behavior:** Strips the `/echo/capture` prefix and forwards to `BACKEND_BASE_URL`
- **Recording:** Saves each request/response as a WireMock-compatible JSON file in `captures/mappings/`
- **Response handling:**
  - **HTTP responses (including errors):** If the backend returns any status (200, 404, 500, etc.), the full status and body are passed through and recorded
  - **Transport failures:** Connection errors, TLS issues, DNS failures, or timeouts return **502** with a JSON error describing the problem

**Example:**
```bash
# Capture a call to Google Routes API
curl -X POST "http://localhost:9090/echo/capture/directions/v2:computeRoutes" \
  -H "Content-Type: application/json" \
  -H "X-Goog-Api-Key: YOUR_KEY" \
  -d '{"origin": {...}, "destination": {...}, "travelMode": "DRIVE"}'

# Creates: captures/mappings/0001-post-directions-v2_computeRoutes.json
```

### 2. Emitter (`echo-emitter`) — Replay Server

A **mock API server** that serves previously captured responses using an embedded WireMock instance.

**How to use:**
1. Ensure you have JSON mappings in `captures/mappings/` (created by Capture)
2. Make requests to `http://<host>:9091/echo/emitter/<api-path>`
3. Emitter matches the request against recorded mappings and returns the captured response

- **Endpoint:** `http://<host>:9091/echo/emitter/*`
- **Behavior:** Strips `/echo/emitter` prefix and matches against recorded request patterns
- **Response:** Returns the recorded response body, status code, and headers from the matching JSON mapping
- **No match:** Returns **500** with message: `Backend is down: no recorded response for this request`

WireMock runs internally on port **8090** within the emitter process.

**Example:**
```bash
# Replay the previously captured call (no real API call made)
curl -X POST "http://localhost:9091/echo/emitter/directions/v2:computeRoutes" \
  -H "Content-Type: application/json" \
  -H "X-Goog-Api-Key: YOUR_KEY" \
  -d '{"origin": {...}, "destination": {...}, "travelMode": "DRIVE"}'

# Returns the captured response instantly, no backend contact
```

### WireMock admin (local dev, no auth)

Use the **emitter HTTP port** so you only need one URL:

- **Proxied admin:** `http://<host>:9091/echo/emitter/__admin/**` → forwarded to WireMock’s `http://localhost:8090/__admin/**`  
  Examples:
  - `GET http://localhost:9091/echo/emitter/__admin/mappings`
  - `POST http://localhost:9091/echo/emitter/__admin/mappings` (import stubs, etc.)

Or hit WireMock **directly** on **8090** (e.g. `http://localhost:8090/__admin/mappings`). Docker Compose maps **`WIREMOCK_EXPOSE_PORT` (default 8090)** on the emitter service for this.

## Quick Start

### Option 1: Docker (Recommended)

1. **Configure your backend API:**
   ```bash
   cp .env.example .env
   # Edit .env and set BACKEND_BASE_URL to your real API
   # Example: BACKEND_BASE_URL=https://routes.googleapis.com
   ```

2. **Start the services:**
   ```bash
   docker compose build
   docker compose up
   ```

3. **Record API calls through Capture:**
   ```bash
   # Make a request through capture (replace with your API endpoint)
   curl "http://localhost:9090/echo/capture/api/users"
   
   # Check that the mapping was created
   ls captures/mappings/
   ```

4. **Replay recorded calls through Emitter:**
   ```bash
   # Same request, but to emitter - returns captured response
   curl "http://localhost:9091/echo/emitter/api/users"
   ```

5. **Reload mappings after recording new endpoints:**
   ```bash
   curl -X POST "http://localhost:9091/echo/emitter/__reload"
   ```

### Option 2: Local Development

**Prerequisites:** Java 17+, Maven

1. **Build both applications:**
   ```bash
   mvn -DskipTests package
   ```

2. **Start Capture (Terminal 1):**
   ```bash
   cd capture
   BACKEND_BASE_URL=https://api.example.com CAPTURES_DIR=../captures mvn spring-boot:run
   ```

3. **Make requests to record them:**
   ```bash
   curl "http://localhost:9090/echo/capture/api/endpoint"
   ```

4. **Start Emitter (Terminal 2):**
   ```bash
   cd emitter
   CAPTURES_DIR=../captures mvn spring-boot:run
   ```

5. **Replay recorded responses:**
   ```bash
   curl "http://localhost:9091/echo/emitter/api/endpoint"
   ```

## Docker

From the repo root:

```bash
cp .env.example .env   # once — edit BACKEND_BASE_URL and other ports as needed
docker compose build
docker compose up
```

Docker Compose reads a **`.env`** file next to `docker-compose.yml` and substitutes variables (no need to edit `docker-compose.yml`). See **`.env.example`** for `BACKEND_BASE_URL`, `CAPTURE_PORT`, `EMITTER_PORT`, and `WIREMOCK_EXPOSE_PORT`.

- Capture: `9090`
- Emitter: `9091` (app) and **`8090` → WireMock admin** (override with `WIREMOCK_EXPOSE_PORT`)
- **Backend URL:** set `BACKEND_BASE_URL` in **`.env`** (default `http://host.docker.internal:8080`).

The **`./captures`** directory is mounted as a bind mount, so JSON mapping files are visible on your host filesystem.

### Reload mappings without restarting Emitter

When you capture new API calls, Emitter needs to reload the mapping files to serve them. You have two options:

**Manual reload (recommended for development):**
```bash
curl -X POST "http://localhost:9091/echo/emitter/__reload"
```

**Automatic reload (optional):**
Set `echo.emitter.reload-mappings-fixed-delay-ms` in `emitter/src/main/resources/application.yml` or via environment variable (e.g., `30000` for 30 seconds) to automatically poll for new mappings.

## WireMock Admin Interface

Access WireMock's admin API to inspect, modify, or manually add mappings:

**Via Emitter proxy (recommended):**
- `GET http://localhost:9091/echo/emitter/__admin/mappings` — List all mappings
- `POST http://localhost:9091/echo/emitter/__admin/mappings` — Manually add a mapping
- `DELETE http://localhost:9091/echo/emitter/__admin/mappings/{id}` — Remove a mapping

**Direct WireMock access:**
- `http://localhost:8090/__admin/**` — Access WireMock directly (Docker: port configured via `WIREMOCK_EXPOSE_PORT`)

## Configuration

| Variable | Application | Default | Description |
|----------|-------------|---------|-------------|
| `BACKEND_BASE_URL` | Capture | `http://localhost:8080` | The real API to capture from (e.g., `https://api.stripe.com`) |
| `CAPTURE_PORT` | Capture | `9090` | Port where Capture listens |
| `EMITTER_PORT` | Emitter | `9091` | Port where Emitter serves mocked responses |
| `CAPTURES_DIR` | Both | `./captures` | Directory for storing/reading JSON mapping files |
| `CAPTURE_INSECURE_SSL` | Capture | `false` | Set `true` in development to skip TLS verification (insecure) |
| `WIREMOCK_PORT` | Emitter | `8090` | Internal WireMock instance port |
| `WIREMOCK_EXPOSE_PORT` | Docker | `8090` | Host port mapped to WireMock admin interface |
| `echo.emitter.reload-mappings-fixed-delay-ms` | Emitter | _(unset)_ | Automatic reload interval in milliseconds (e.g., `30000` = 30 sec) |

## Advanced Usage

### Capturing APIs with Special Characters

Some APIs use colons or other special characters in their paths (e.g., Google's `/v2:computeRoutes`). Echo handles these correctly:

```bash
# Capture Google Routes API
curl -X POST "http://localhost:9090/echo/capture/directions/v2:computeRoutes" \
  -H "Content-Type: application/json" \
  -H "X-Goog-Api-Key: YOUR_KEY" \
  -d '{"origin": {...}, "destination": {...}}'
```

### Working with Authentication

Headers (including API keys, Bearer tokens, etc.) are captured and included in the recorded mappings. When replaying, Emitter will match requests based on the recorded patterns.

### Capturing Error Responses

Capture records ALL responses, including errors:
- **4xx/5xx from backend** → Recorded with actual status and body
- **Network/connection failures** → Recorded as 502 with diagnostic JSON

This allows you to test error handling in your application with realistic error responses.

## Troubleshooting

**No mappings being created:**
- Check that `CAPTURES_DIR` is correctly set and writable
- Verify requests are reaching Capture (check logs)
- Ensure the backend API is responding (Capture will log connection errors)

**Emitter returns 500 "no recorded response":**
- Verify mapping files exist in `captures/mappings/`
- Reload mappings: `curl -X POST http://localhost:9091/echo/emitter/__reload`
- Check that the request path/method matches what was captured

**Capture returning 404 from backend:**
- Verify `BACKEND_BASE_URL` is correct
- Check that the API endpoint exists on the backend
- Review request headers (especially `Host` header should match backend)

## How Mappings Work

Each captured request/response creates a WireMock JSON file like:

```json
{
  "request": {
    "method": "POST",
    "url": "/api/endpoint"
  },
  "response": {
    "status": 200,
    "body": "{\"result\": \"success\"}",
    "headers": {
      "Content-Type": "application/json"
    }
  },
  "priority": 1
}
```

You can manually edit these files to:
- Modify response data
- Add delays (`"fixedDelayMilliseconds": 1000`)
- Create variations (copy and modify for different scenarios)

See [WireMock documentation](http://wiremock.org/docs/stubbing/) for advanced matching and response templating.

## Developing Without Local Maven/Java

Use the **mvnw.sh** wrapper script to run Maven commands in Docker without installing Java or Maven locally.

**Common commands:**
```bash
./mvnw.sh test                    # Run all tests
./mvnw.sh test -pl capture        # Test specific module
./mvnw.sh clean package           # Build JARs
./mvnw.sh package -DskipTests     # Build without tests
./mvnw.sh -it bash                # Interactive shell
```

The script automatically builds a dev image on first use and caches Maven dependencies in a Docker volume for faster subsequent runs.

## Continuous Integration

This project uses GitHub Actions for automated testing and validation. On every push or pull request, the CI pipeline:

1. **Maven Build & Test** - Compiles code, runs unit and integration tests, uploads test results and JAR artifacts
2. **Docker Build** - Builds both capture and emitter Docker images with caching for faster builds
3. **Docker Compose Test** - Validates that both services start correctly and are accessible

View the workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

The CI workflow ensures all changes are validated before merging, maintaining code quality and catching issues early.
