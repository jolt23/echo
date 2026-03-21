# Echo — WireMock capture & emitter

Two Spring Boot **WebFlux** apps that share a **`captures/`** tree (WireMock-style layout):

- **`captures/mappings/`** — JSON stub files (WireMock format) produced by Capture and read by Emitter.

## Applications

### 1. Capture (`echo-capture`)

Pass-through proxy:

- **Endpoint:** `http://<host>:9090/echo/capture/*`
- Strips the `/echo/capture` prefix and forwards the remainder to the real backend (`BACKEND_BASE_URL`).
- Records each call as a **WireMock-compatible** mapping under `captures/mappings/`.
- **Backend HTTP responses:** if the backend returns **4xx/5xx**, that status and body are passed through (so e.g. **403** with a JSON error body from Google is visible).  
   **Transport failures** (no connection, TLS handshake, DNS, timeout) return **502** with a JSON **`BACKEND_TRANSPORT_ERROR`** payload describing the exception and `backendBaseUrl`.

### 2. Emitter (`echo-emitter`)

Replay server (embedded **WireMock**):

- **Public API:** `http://<host>:9091/echo/emitter/*`
- Strips `/echo/emitter` and matches the same URL path/query that was stored during capture (e.g. capture `GET /api/foo` → replay `GET /echo/emitter/api/foo`).
- If **no stub matches**, WireMock returns **404** and the app responds with **500** and body:  
  `Backend is down: no recorded response for this request`.

WireMock itself listens on **8090** inside the emitter process.

### WireMock admin (local dev, no auth)

Use the **emitter HTTP port** so you only need one URL:

- **Proxied admin:** `http://<host>:9091/echo/emitter/__admin/**` → forwarded to WireMock’s `http://localhost:8090/__admin/**`  
  Examples:
  - `GET http://localhost:9091/echo/emitter/__admin/mappings`
  - `POST http://localhost:9091/echo/emitter/__admin/mappings` (import stubs, etc.)

Or hit WireMock **directly** on **8090** (e.g. `http://localhost:8090/__admin/mappings`). Docker Compose maps **`WIREMOCK_EXPOSE_PORT` (default 8090)** on the emitter service for this.

## Build (local)

```bash
mvn -pl capture -am -DskipTests package   # capture JAR
mvn -pl emitter -am -DskipTests package   # emitter JAR
```

Or from the parent:

```bash
mvn -DskipTests package
```

## Run (local)

Terminal 1 — backend (or any HTTP server on 8080).

Terminal 2 — capture:

```bash
cd capture
BACKEND_BASE_URL=http://localhost:8080 CAPTURES_DIR=../captures mvn spring-boot:run
```

Terminal 3 — emitter (after you have at least one mapping):

```bash
cd emitter
CAPTURES_DIR=../captures mvn spring-boot:run
```

Example:

```bash
# Through capture (records mapping)
curl "http://localhost:9090/echo/capture/api/users"

# Replay from emitter
curl "http://localhost:9091/echo/emitter/api/users"
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

Both services mount the **`captures`** named volume so mappings recorded by Capture are visible to Emitter.

### Reload mappings without restarting Emitter

WireMock loads stubs at startup. To pick up **new** JSON files from Capture:

```bash
curl -X POST http://localhost:9091/echo/emitter/__reload
```

Or set **`echo.emitter.reload-mappings-fixed-delay-ms`** (e.g. `30000`) to poll the `mappings/` folder on an interval.

## Configuration

| Variable | App | Default |
|----------|-----|---------|
| `CAPTURE_PORT` | Capture | `9090` |
| `EMITTER_PORT` | Emitter | `9091` |
| `BACKEND_BASE_URL` | Capture | `http://localhost:8080` |
| `CAPTURE_INSECURE_SSL` | Capture | `false` — set `true` **only in dev** to skip TLS verification to the backend (insecure) |
| `CAPTURES_DIR` | Both | `./captures` |
| `WIREMOCK_PORT` | Emitter (internal WireMock) | `8090` |
| `WIREMOCK_EXPOSE_PORT` | Docker: host port → container 8090 | `8090` |
| `echo.emitter.reload-mappings-fixed-delay-ms` | Emitter | _(unset — manual reload only)_ |

`POST /echo/emitter/__reload` uses WireMock’s **`resetToDefaultMappings()`** to re-read JSON files under `captures/mappings/`.
