# syntax=docker/dockerfile:1
# Build context: the server/ directory.  docker build -t howlongtocook-server server/

FROM rust:1-slim-trixie AS build
WORKDIR /src
# Cache dependencies separately from sources.
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo 'fn main() {}' > src/main.rs && touch src/lib.rs \
    && cargo build --release --locked \
    && rm -rf src target/release/deps/howlongtocook* target/release/.fingerprint/howlongtocook*
COPY migrations migrations
COPY templates templates
COPY static static
COPY src src
RUN cargo build --release --locked \
    && mkdir -p /out/data \
    && cp target/release/howlongtocook-server /out/

FROM gcr.io/distroless/cc-debian13:nonroot
LABEL org.opencontainers.image.title="howlongtocook-server" \
      org.opencontainers.image.description="Self-hosted sync backend and admin UI for HowLongToCook" \
      org.opencontainers.image.licenses="GPL-3.0-or-later" \
      org.opencontainers.image.source="https://github.com/StevenPG/HowLongToCook"
COPY --from=build /out/howlongtocook-server /usr/local/bin/howlongtocook-server
COPY --from=build --chown=nonroot:nonroot /out/data /data
ENV HLTC_DATABASE_URL=sqlite:///data/howlongtocook.db \
    HLTC_BIND=0.0.0.0:8080 \
    RUST_LOG=info
VOLUME ["/data"]
EXPOSE 8080
USER nonroot:nonroot
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/howlongtocook-server", "healthcheck"]
ENTRYPOINT ["/usr/local/bin/howlongtocook-server"]
CMD ["serve"]
