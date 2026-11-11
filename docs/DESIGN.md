# HowLongToCook — Design Document

> Status: **v0.1 (first iteration)** · License: GPL-3.0-or-later

## 1. Problem

Frozen/packaged meals ship with cooking instructions printed on an outer box that
usually ends up in the recycling, while the individually wrapped item lives on in the
freezer. HowLongToCook stores those instructions per product, keyed by **name** and/or
**barcode**, so they can be retrieved later by searching or scanning.

## 2. Goals and non-goals

**Goals**

- Fast capture: scan a barcode (or type a name), enter structured cooking steps, done.
- Fast retrieval: scan or search and see the instructions immediately — offline.
- User-owned storage: sync to an **S3 bucket**, a **Cloudflare R2 bucket**, or a
  **self-hosted Rust server**; or keep data on-device only.
- Professional, consistent Material 3 UI with a custom brand palette (light + dark),
  optional dynamic color.
- Single repository containing the Android app and the Rust server, buildable together.

**Non-goals (v1)**

- Photos / OCR of instruction panels.
- Microwave wattage scaling.
- Cooking timers (a large-text "Cook mode" is provided instead).
- Multi-user accounts or sharing. One person, possibly multiple devices.
- CI/CD and published releases (layout is ready for them; see §11).

## 3. Decisions log

| Topic | Decision |
|---|---|
| Instructions | Structured: one or more *cooking methods* per product, each with ordered steps + notes |
| Methods | Microwave, Oven/Toaster oven, Air fryer, Stovetop |
| Product fields | name, brand, barcodes (0..n), tags, favorite, rating (1–5), notes |
| Barcode scanner | **ZXing** core + CameraX (fully open source, F-Droid friendly) |
| Product lookup | Open Food Facts prefills name/brand for unknown barcodes; opt-out in Settings |
| Users | Single user |
| Source of truth | **Local-first** (Room); background sync; last-write-wins per product |
| Remote options | None (local only), S3, R2 (and any S3-compatible e.g. MinIO), self-hosted server |
| Server | Rust: axum + sqlx (SQLite) + askama admin UI; single shared bearer token |
| Server deploy | x86_64 Docker image; Docker Compose + Kubernetes examples |
| Portability | Local JSON export/import on device; JSON export/import on server admin UI |
| Theme | Custom warm palette, light/dark/system, opt-in dynamic color |
| Home | Searchable library list + prominent Scan FAB |
| Android stack | Kotlin, Compose, Hilt, Room, Ktor, Coroutines/Flow, Navigation (type-safe), CameraX, WorkManager, DataStore |
| minSdk / target | 31 (Android 12) / 36 |
| Package | `com.stevenpg.howlongtocook` |
| Units | Temperatures stored as entered (value + unit), displayed in user's unit (locale default, overridable) |
| Testing | Pragmatic: JVM unit tests (sync merge, SigV4, serialization, formatting), Compose UI tests, Rust integration tests, RustFS via compose for S3 testing |
| License | GPL-3.0-or-later |

## 4. Repository layout

```
HowLongToCook/
├── android/                 # Gradle project (open this folder in Android Studio)
│   ├── app/
│   ├── gradle/libs.versions.toml
│   └── ...
├── server/                  # Cargo project (Rust server + admin UI)
│   ├── src/
│   ├── templates/           # askama templates for the admin UI
│   ├── static/              # admin UI CSS (embedded into the binary)
│   ├── migrations/
│   └── tests/
├── api/
│   ├── openapi.yaml         # REST contract between app and server
│   └── examples/            # canonical JSON examples (shared by both test suites)
├── deploy/
│   ├── docker/              # Dockerfile usage + compose.yaml (server only)
│   └── kubernetes/          # kustomize base: Deployment, Service, PVC, Secret, Ingress
├── compose.dev.yaml         # local dev: server + MinIO (+ bucket bootstrap)
├── justfile                 # task runner tying both builds together
├── docs/
└── LICENSE
```

## 5. Data model

The canonical JSON representation (used for sync, export/import and the server API):

```json
{
  "id": "6f1c2a8e-8a8e-4c55-9b2c-0c6f0f7f2d10",
  "name": "Chicken Pot Pie",
  "brand": "Example Foods",
  "barcodes": ["012345678905"],
  "tags": ["dinner"],
  "favorite": true,
  "rating": 4,
  "notes": "Let it rest — filling is lava.",
  "methods": [
    {
      "type": "MICROWAVE",
      "power": "High",
      "temperature": null,
      "preheat": false,
      "steps": [
        { "action": "VENT", "durationSeconds": null, "note": "Cut slit in film" },
        { "action": "COOK", "durationSeconds": 240, "note": null },
        { "action": "STIR", "durationSeconds": null, "note": null },
        { "action": "COOK", "durationSeconds": 120, "note": null },
        { "action": "STAND", "durationSeconds": 60, "note": null }
      ],
      "notes": null
    },
    {
      "type": "OVEN",
      "power": null,
      "temperature": { "value": 400, "unit": "F" },
      "preheat": true,
      "steps": [{ "action": "COOK", "durationSeconds": 2700, "note": "On baking sheet, center rack" }],
      "notes": null
    }
  ],
  "createdAt": 1762851000000,
  "updatedAt": 1762851000000,
  "deleted": false
}
```

- `id`: client-generated UUID v4.
- `methods`: ordered; **the first method is the preferred one** and is shown first.
- `type`: `MICROWAVE | OVEN | AIR_FRYER | STOVETOP`.
- `action`: `COOK | STIR | FLIP | SHAKE | STAND | VENT | REMOVE | OTHER`. Steps render as
  e.g. "Cook 4:00", "Stir", "Stand 1:00", with an optional note.
- `temperature`: stored exactly as printed on the box (`unit` is `F` or `C`), converted for
  display (snapped to conventional dial steps: 25 °F / 10 °C, so 400 °F shows as 200 °C) — no lossy round-trips.
- `power`: free text (microwave "High"/"50%", stovetop "Medium-high").
- `createdAt`/`updatedAt`: epoch millis, set by the client that made the change.
- `deleted`: tombstone. Deletes are soft so they propagate through sync; tombstones are
  purged from the server via the admin UI.
- **Device-local only** (not synced): `lastViewedAt`, sync bookkeeping.

Validation (enforced by app and server): `name` 1–200 chars; ≤ 20 barcodes of 4–64
chars `[0-9A-Za-z-]`; ≤ 20 tags; rating null or 1–5; ≤ 10 methods; ≤ 50 steps per method;
`durationSeconds` 0–86 400.

## 6. Android app

### 6.1 Architecture

Single-module app with package-by-layer:

```
com.stevenpg.howlongtocook
├── data/
│   ├── local/        Room DB, entities, DAOs, converters
│   ├── model/        Domain + serializable DTOs (the §5 schema)
│   ├── repository/   ProductRepository (UI-facing, Flow-based)
│   ├── settings/     DataStore preferences + SecretStore (Keystore AES-GCM)
│   ├── remote/
│   │   ├── RemoteStore.kt          interface
│   │   ├── s3/  SigV4Signer, S3Client, S3RemoteStore   (AWS S3, R2, MinIO, …)
│   │   ├── server/ ServerRemoteStore                   (Rust backend)
│   │   └── openfoodfacts/ OpenFoodFactsClient
│   ├── sync/         SyncEngine (merge), SyncWorker, SyncScheduler
│   └── transfer/     JSON export/import
├── di/               Hilt modules
├── ui/
│   ├── theme/        Color, Type, Shape, Theme
│   ├── components/   shared composables
│   ├── library/      home list + search
│   ├── detail/       product detail + cook mode
│   ├── editor/       add/edit product
│   ├── scanner/      CameraX + ZXing
│   └── settings/     settings screens
└── MainActivity, HowLongToCookApp, navigation
```

MVVM with unidirectional data flow: each screen has a `@HiltViewModel` exposing a
`StateFlow<UiState>`; composables are stateless where practical and previewable.

### 6.2 Local storage

Room tables:

- `products` — scalar fields, `methods` as JSON (kotlinx.serialization TypeConverter),
  `tags` as JSON, `deleted`, `dirty` (pending upload), `lastViewedAt`.
- `barcodes` — (`code` PK, `productId` FK, cascade). A barcode maps to exactly one
  product on a device; assigning an existing code to another product moves it (with a
  confirmation in the editor).

Search: case-insensitive `LIKE` over name, brand and tags, plus exact barcode match.
Personal-scale data (hundreds of rows) makes FTS unnecessary.

### 6.3 Sync

`RemoteStore` abstraction:

```kotlin
interface RemoteStore {
    suspend fun testConnection(): Result<String>
    suspend fun sync(local: List<ProductDto>, cursor: String?): SyncResult
}
```

`SyncEngine` performs a merge that is identical for every backend:

1. Read all local products (including tombstones).
2. Pull remote state (see below).
3. Per product id, keep the version with the greater `updatedAt` (ties → remote).
4. Write winners locally; push local winners that differ remotely.
5. Clear `dirty` flags for what was pushed; persist cursor + last-sync status.

**S3 / R2 layout.** One object: `<prefix>/library.json`
(`{"schemaVersion":1,"updatedAt":…,"products":[…]}`). One object keeps a sync to two
HTTP requests and fits easily in a single-user library. Writes use optimistic
concurrency: `PUT` with `If-Match: <etag>` (or `If-None-Match: *` if it did not exist).
On `412 Precondition Failed` the engine re-fetches and re-merges (max 3 attempts). If the
provider rejects conditional headers (`501`), it falls back to an unconditional put.
AWS S3 and Cloudflare R2 enforce `If-Match`; some S3-compatible servers (e.g. RustFS,
used by the dev stack) accept but ignore it, in which case concurrent writes from two
devices in the same instant fall back to last-writer-wins at the object level.

Requests are signed with a small in-house **AWS SigV4** signer (unit-tested against the
AWS published example) over Ktor — avoiding the multi-MB AWS SDK. Presets:

| Preset | Endpoint | Region | Addressing |
|---|---|---|---|
| AWS S3 | `https://s3.<region>.amazonaws.com` | user | virtual-hosted |
| Cloudflare R2 | `https://<accountId>.r2.cloudflarestorage.com` | `auto` | path |
| S3-compatible | user | user (default `us-east-1`) | path |

Settings collect: access key ID, secret access key, bucket, (endpoint / account ID,
region), optional prefix.

**Self-hosted server.** Settings collect base URL + token. Sync uses the server's
revision cursor: `GET /api/v1/products?since=<rev>` returns changed products and the new
revision; dirty local products are pushed with `PUT /api/v1/products/{id}`. The server
applies the same last-write-wins rule, so concurrent devices converge.

**Scheduling** (WorkManager, network-constrained, unique work):
- On app start and 5 s after any local edit (debounced).
- Periodically every 6 h.
- Manual "Sync now" in Settings.

### 6.4 Secrets

Secrets (S3 secret key, server token) are encrypted with an AES-256-GCM key held in the
**Android Keystore** and stored in a dedicated DataStore file, which is excluded from cloud
backup and device transfer (`data_extraction_rules.xml`), because the Keystore key would
not survive anyway. Non-secret settings live in a normal Preferences DataStore.

### 6.5 Barcode scanning

CameraX `Preview` + `ImageAnalysis` (YUV, keep-only-latest) feeding ZXing
`MultiFormatReader` with retail + common formats (EAN-13/8, UPC-A/E, Code 128/39/93,
ITF, QR, Data Matrix). Includes a torch toggle, a scan reticle, haptic feedback on
detection, and "Enter manually". Camera permission is requested contextually with a
rationale screen.

Scan result flow: known barcode → Detail; unknown → Editor prefilled with the barcode and,
if enabled, Open Food Facts name/brand (`GET https://world.openfoodfacts.org/api/v2/product/{code}?fields=product_name,brands`
with a generic `User-Agent: HowLongToCook-Android/<version>`).

### 6.6 Screens

- **Library (home):** large top app bar, search field, filter chips (All · Favorites ·
  per tag), "Recently viewed" row, alphabetical product cards (name, brand, preferred
  method summary e.g. "Microwave · 7 min"), sync status icon, extended **Scan** FAB, and
  an "Add manually" action. Empty state with illustration and call to action.
- **Detail:** header (name, brand, rating, favorite, tags, barcodes), method selector
  (segmented buttons), method card (temperature/power, preheat badge, total time), numbered
  step list with action icons, notes. Actions: **Cook mode**, Edit, Delete (with undo).
- **Cook mode:** full-screen, large type, screen kept on, one method at a time.
- **Editor:** name, brand, barcodes (chips; add via scan or typing), tags (chip input),
  rating, notes, methods (add/remove/reorder; per method: power or temperature + unit,
  preheat, steps with action picker, duration mm:ss, note; reorder via up/down).
  Validation + unsaved-changes guard.
- **Settings:** Storage (Local only · S3 · R2 · Self-hosted, with forms, *Test connection*,
  *Sync now*, last sync result); Appearance (System/Light/Dark, Dynamic color); Units
  (Auto/°F/°C); Product lookup (Open Food Facts on/off); Data (Export JSON, Import JSON via
  Storage Access Framework); About (version, GPL-3.0, source link).

### 6.7 Theme

Material 3 with a custom warm palette ("toasted" orange primary, herb green secondary,
warm neutral surfaces), generated tonal roles for light and dark, a consistent type
scale and shape set (rounded 12–28 dp). Dynamic color is opt-in. Edge-to-edge, Android 12
splash screen API, adaptive (monochrome-capable) launcher icon.

## 7. Rust server

### 7.1 Stack

`axum` (HTTP), `tokio`, `sqlx` (SQLite, runtime queries + embedded migrations),
`askama` (server-rendered admin templates), `rust-embed` (static assets), `tower-http`
(tracing, timeouts, body limits), `clap` (CLI/env config), `tracing`.

### 7.2 Configuration

| Env / flag | Default | Meaning |
|---|---|---|
| `HLTC_TOKEN` / `--token` | *(required)* | Shared bearer token for API and admin login |
| `HLTC_TOKEN_FILE` | — | Read the token from a file (Docker/K8s secrets) |
| `HLTC_DATABASE_URL` | `sqlite://data/howlongtocook.db` | SQLite path (created if missing) |
| `HLTC_BIND` | `0.0.0.0:8080` | Listen address |
| `HLTC_SECURE_COOKIES` | `false` | Set `Secure` on the admin cookie (enable behind TLS) |
| `RUST_LOG` | `info` | Log filter |

Subcommands: `serve` (default), `healthcheck` (used by Docker HEALTHCHECK on a distroless
image).

### 7.3 Storage

```sql
CREATE TABLE products (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  updated_at  INTEGER NOT NULL,   -- client clock, LWW key
  deleted     INTEGER NOT NULL DEFAULT 0,
  revision    INTEGER NOT NULL,   -- server-assigned, monotonically increasing
  data        TEXT NOT NULL       -- canonical JSON (§5)
);
CREATE INDEX idx_products_revision ON products(revision);
```

Barcode lookups use SQLite's `json_each` over `data`. Each write is a transaction that
assigns `revision = max(revision) + 1`. SQLite runs in WAL mode.

### 7.4 REST API (`/api/v1`, `Authorization: Bearer <token>`)

| Method | Path | Description |
|---|---|---|
| GET | `/healthz` | Liveness (no auth) |
| GET | `/api/v1/info` | Server version, product count, current revision |
| GET | `/api/v1/products?since=<rev>&includeDeleted=true` | Changes since revision → `{revision, products}` |
| GET | `/api/v1/products/{id}` | One product |
| PUT | `/api/v1/products/{id}` | Upsert (LWW by `updatedAt`); returns stored product + `revision` + `applied` |
| DELETE | `/api/v1/products/{id}` | Tombstone |
| GET | `/api/v1/barcodes/{code}` | Products with that barcode |
| GET | `/api/v1/export` | Full library JSON (same format as the S3 `library.json`) |
| POST | `/api/v1/import` | Merge a library JSON (LWW) |

Errors use `{"error": "<code>", "message": "<human text>"}`. Full contract in
`api/openapi.yaml`.

### 7.5 Admin UI

Lightweight, server-rendered (askama + one embedded stylesheet, no JavaScript framework,
no build step) under `/admin`:

- **Login** with the shared token → `HttpOnly`, `SameSite=Strict` cookie holding an
  HMAC-derived session value (never the raw token).
- **Dashboard:** product count, tombstone count, revision, DB size, version, uptime.
- **Products:** searchable table; view; edit core fields (name, brand, barcodes, tags,
  favorite, rating, notes) and methods (validated JSON editor); delete; restore.
- **Maintenance:** purge tombstones; export JSON (download); import JSON (upload).

The admin UI uses the same palette as the app for a consistent look.

## 8. Deployment

- **Docker:** multi-stage build (`rust:*-slim` builder → `gcr.io/distroless/cc-debian12:nonroot`),
  data volume at `/data`, `HEALTHCHECK` via the `healthcheck` subcommand.
  `deploy/docker/compose.yaml` shows a production-style setup with a secret file.
- **Kubernetes:** kustomize base with Namespace, Secret (example), PVC (RWO),
  Deployment (`replicas: 1`, `strategy: Recreate` — SQLite has a single writer;
  non-root, read-only root FS, probes, resource limits), Service, and an example
  Ingress (TLS via cert-manager annotation, commented).
- **Local dev:** `compose.dev.yaml` runs the server plus RustFS (an Apache-2.0, MinIO-compatible
  S3 server; MinIO no longer publishes images) and creates a bucket so
  both remote modes can be tested from an emulator (`http://10.0.2.2:8080`,
  `http://10.0.2.2:9000`).

## 9. Security considerations

- Everything is user-owned; no telemetry. Only third party contacted is Open Food Facts
  (opt-out), sending just the barcode.
- Token comparison is constant-time. Request bodies are size-limited (1 MiB API, 10 MiB
  import).
- Cleartext HTTP is allowed by the app only because self-hosted LAN servers are a
  legitimate use case; the settings screen warns when a non-HTTPS URL is used.
- The admin cookie is `HttpOnly` + `SameSite=Strict`; state-changing admin routes are POST
  only (SameSite blocks cross-site form posts).

## 10. Testing strategy

- **Android JVM tests:** sync merge rules, SigV4 against AWS test vector, S3 store
  against Ktor `MockEngine` (ETag / 412 retry), server store, two-device sync convergence
  (Robolectric + Room), Roborazzi screenshot renders, opt-in live tests against a real
  S3 store and the real Rust server (`LiveBackendsTest`), JSON (de)serialization,
  temperature/duration formatting, editor validation.
- **Android instrumented tests:** Room DAO, Compose UI flows (library → editor → detail).
  Run from Android Studio.
- **Rust:** unit tests (LWW, validation) and integration tests over the router
  (in-memory SQLite) for API + admin auth.
- **Manual end-to-end:** `just dev-up` then point an emulator at the server / MinIO.

## 11. Future (release-ready layout)

- GitHub Actions: `android` (lint, test, assembleRelease, signed APK on tag), `server`
  (fmt, clippy, test, multi-arch image to GHCR on tag).
- F-Droid metadata (ZXing choice already keeps the app FOSS-only).
- Possible later features: cook timers, photo of instructions + OCR, wattage scaling.

## 12. Phasing

1. **Local app** — data model, Room, library/detail/editor/settings, scanner, theme,
   export/import.
2. **S3/R2 sync** — SigV4, S3 store, sync engine, WorkManager.
3. **Rust server** — API, admin UI, Docker + Kubernetes examples; server remote store.
4. **Lookup + polish** — Open Food Facts, empty states, accessibility pass.

All four phases are delivered in the first iteration (this PR); subsequent PRs refine.
