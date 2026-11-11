# HowLongToCook

Keep the cooking instructions from frozen and packaged meals after the box is gone.
Scan the barcode (or type a name), save the microwave / oven / air fryer / stovetop
steps, and find them again in seconds — even offline in the garage.

This repository holds both halves of the project:

| Path | What |
|---|---|
| [`android/`](android) | Android app — Kotlin, Jetpack Compose (Material 3), Room, Hilt, Ktor, CameraX + ZXing |
| [`server/`](server) | Optional self-hosted sync server with a lightweight admin UI — Rust, axum, SQLite |
| [`api/`](api) | OpenAPI contract and shared JSON examples used by both test suites |
| [`deploy/`](deploy) | Docker Compose and Kubernetes examples for the server |
| [`docs/DESIGN.md`](docs/DESIGN.md) | Design document and decisions log |

## Storage options

Products always live on the phone first (works offline) and sync in the background to
one of:

- **This device only** — no sync; JSON export/import for backups
- **Amazon S3** — your bucket + IAM access key
- **Cloudflare R2** — your bucket + R2 S3 API token
- **Any S3-compatible store** — MinIO, RustFS, Garage, B2, Wasabi…
- **Self-hosted server** — the Rust server in `server/`, with a shared token

Credentials are encrypted with the Android Keystore and never included in backups.

## Quick start

Requirements: JDK 17+, Android SDK (API 37 platform), Rust 1.85+, Docker (optional),
[`just`](https://just.systems) (optional).

```sh
# Android — or open android/ in Android Studio
cd android && ./gradlew assembleDebug testDebugUnitTest

# Server
cd server && HLTC_TOKEN="$(openssl rand -hex 32)" cargo run
# admin UI: http://localhost:8080/admin

# Both backends locally (server + S3-compatible RustFS), for testing from an emulator
just dev-up
```

From the emulator use `http://10.0.2.2:8080` (server, token `dev-token-change-me`) or
endpoint `http://10.0.2.2:9000`, bucket `howlongtocook`, key `hltc` / `hltc-dev-secret`
(choose **S3-compatible**).

## Testing

```sh
just test                                         # server + Android JVM tests
cd android && ./gradlew testDebugUnitTest -Pscreenshots   # also writes PNGs to app/build/screenshots
```

Opt-in tests against real backends are described in
`android/app/src/test/.../data/remote/LiveBackendsTest.kt`.

## License

[GPL-3.0-or-later](LICENSE)
