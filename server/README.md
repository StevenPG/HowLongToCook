# HowLongToCook server

A self-hosted sync backend for the HowLongToCook Android app, with a lightweight
admin UI. Rust (axum + sqlx/SQLite + askama). Everything ships as one binary;
templates and CSS are compiled in.

```sh
HLTC_TOKEN="$(openssl rand -hex 32)" cargo run
# API:   http://localhost:8080/api/v1  (Authorization: Bearer <token>)
# Admin: http://localhost:8080/admin   (sign in with the same token)
```

- API contract: [`../api/openapi.yaml`](../api/openapi.yaml)
- Design: [`../docs/DESIGN.md`](../docs/DESIGN.md) §7
- Docker / Compose: [`../deploy/docker`](../deploy/docker)
- Kubernetes: [`../deploy/kubernetes`](../deploy/kubernetes)

## Admin UI

| Page | What it does |
|---|---|
| Dashboard | Counts (products, favorites, barcodes, deleted), revision, DB size, recently updated products |
| Products | Search by name/brand/tag/barcode, view instructions, edit fields and methods (JSON), create, delete, restore |
| Maintenance | Download a JSON export, import an export (last-write-wins merge), purge deleted products |

Sign-in uses the shared token and sets an `HttpOnly`, `SameSite=Strict` cookie derived
from it (HMAC). Set `HLTC_SECURE_COOKIES=true` when served over HTTPS.

## Development

```sh
cargo test                                   # unit + HTTP integration tests (in-memory SQLite)
cargo clippy --all-targets -- -D warnings
cargo fmt
```

Layout:

```
src/
  main.rs     CLI (serve, healthcheck), startup, graceful shutdown
  lib.rs      router assembly, shared state
  config.rs   flags / env vars
  model.rs    canonical product model + validation (mirrors the Android DTOs)
  db.rs       SQLite access, last-write-wins upsert, revision counter
  api.rs      /api/v1 JSON endpoints
  admin.rs    /admin pages
  auth.rs     token + session checks (constant time)
templates/    askama HTML templates
static/       admin.css, logo.svg (embedded)
migrations/   sqlx migrations (embedded)
tests/        HTTP-level integration tests
```
