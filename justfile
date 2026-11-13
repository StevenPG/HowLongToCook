# Task runner for the whole repository. Install: https://just.systems
# Run `just` to list recipes.

set shell := ["bash", "-euo", "pipefail", "-c"]

default:
    @just --list

# ---- Everything ------------------------------------------------------------

# Build the server and the Android debug APK
build: server-build android-build

# Run all tests (server + Android JVM unit tests)
test: server-test android-test

# Lint/format checks for both projects
check: server-check android-lint

# ---- Rust server -----------------------------------------------------------

server-build:
    cd server && cargo build --release

server-test:
    cd server && cargo test

server-check:
    cd server && cargo fmt --check && cargo clippy --all-targets -- -D warnings

# Run the server locally (token "dev-token-change-me", data in server/data)
server-run:
    cd server && HLTC_TOKEN=dev-token-change-me HLTC_DATABASE_URL=sqlite://data/dev.db cargo run

server-image tag="howlongtocook-server:dev":
    docker build -t {{tag}} server

# ---- Android ---------------------------------------------------------------

android-build:
    cd android && ./gradlew assembleDebug

android-test:
    cd android && ./gradlew testDebugUnitTest

android-lint:
    cd android && ./gradlew lintDebug

# Install the debug build on a connected device/emulator
android-install:
    cd android && ./gradlew installDebug

# ---- Local stack (server + MinIO) -----------------------------------------

dev-up:
    docker compose -f compose.dev.yaml up -d --build

dev-down:
    docker compose -f compose.dev.yaml down

dev-logs:
    docker compose -f compose.dev.yaml logs -f
