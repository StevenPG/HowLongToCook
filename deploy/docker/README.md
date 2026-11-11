# Docker

## Build the image

```sh
docker build -t howlongtocook-server server/
```

The image is a multi-stage build ending on `gcr.io/distroless/cc-debian13:nonroot`
(~50 MB). It runs as a non-root user, keeps its SQLite database in the `/data`
volume, and has a built-in `HEALTHCHECK` (`howlongtocook-server healthcheck`).

## Run it

```sh
docker run -d --name howlongtocook \
  -p 8080:8080 \
  -e HLTC_TOKEN="$(openssl rand -hex 32)" \
  -v howlongtocook-data:/data \
  howlongtocook-server
```

Then open <http://localhost:8080/admin> and sign in with the token. Use the same
token and URL in the app under **Settings → Storage → Self-hosted server**.

## Compose

`compose.yaml` in this folder is a production-style example that reads the token
from a secret file and runs the container with a read-only root filesystem and all
capabilities dropped:

```sh
cd deploy/docker
mkdir -p secrets && openssl rand -hex 32 > secrets/hltc_token
docker compose up -d --build
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `HLTC_TOKEN` | — | Shared bearer token (min. 16 chars). Required unless `HLTC_TOKEN_FILE` is set |
| `HLTC_TOKEN_FILE` | — | Path to a file containing the token |
| `HLTC_DATABASE_URL` | `sqlite:///data/howlongtocook.db` | SQLite database location |
| `HLTC_BIND` | `0.0.0.0:8080` | Listen address |
| `HLTC_SECURE_COOKIES` | `false` | Mark the admin cookie `Secure`; enable behind HTTPS |
| `RUST_LOG` | `info` | Log level filter |

## Backups

Use **Admin → Maintenance → Download export** (or `GET /api/v1/export`) for a
portable JSON backup, or copy the volume's `howlongtocook.db*` files while the
container is stopped.
