# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
The Android app and the server share one version number.

## [Unreleased]

### Added
- Android app (Kotlin, Jetpack Compose, Material 3):
  - library with search, favorite and tag filters, and recently viewed products
  - barcode scanner (ZXing + CameraX) with a torch toggle and manual entry
  - structured instructions for microwave, oven, air fryer and stovetop
  - Cook mode: large text, screen stays on
  - Open Food Facts name and brand lookup (opt-out)
  - JSON export and import
  - light and dark themes, optional colors from the wallpaper; °F/°C preference
- Data stays on the device and syncs to Amazon S3, Cloudflare R2, S3-compatible
  stores, or the self-hosted server (last-write-wins, tombstones for deletes).
- Credentials encrypted with the Android Keystore and excluded from backups.
- Rust server (axum, SQLite): sync API with revision cursor, shared-token auth.
- Server admin UI: dashboard, product search and editing, delete and restore,
  export and import, permanent removal of deleted products.
- Docker image (distroless, non-root, healthcheck), Docker Compose and Kubernetes examples.
- OpenAPI contract, design document, and test suites for both projects.
- All user-facing text in Android string resources (with plurals), ready for translation;
  validation, sync and connection errors are typed and mapped to localized messages.

### Fixed
- The barcode scanner no longer leaks a background thread each time it is opened.
- Edits made in the product editor before it finished loading (such as a barcode returned
  by the scanner) are no longer lost.

[Unreleased]: https://github.com/StevenPG/HowLongToCook/commits/main
