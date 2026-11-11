use std::net::SocketAddr;
use std::time::Duration;

use clap::Parser;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tracing_subscriber::EnvFilter;

use howlongtocook_server::config::{Cli, Command, Config};
use howlongtocook_server::db::Db;
use howlongtocook_server::{AppState, app};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    match cli.command.unwrap_or(Command::Serve) {
        Command::Serve => serve(cli.config).await,
        Command::Healthcheck => healthcheck(&cli.config).await,
    }
}

async fn serve(config: Config) -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| "info,tower_http=info".into()),
        )
        .init();

    let token = config.resolve_token()?;
    let db = Db::connect(&config.database_url).await?;
    let state = AppState::new(db, token, config.secure_cookies);

    let listener = tokio::net::TcpListener::bind(&config.bind).await?;
    tracing::info!(
        version = env!("CARGO_PKG_VERSION"),
        addr = %listener.local_addr()?,
        database = %config.database_url,
        "HowLongToCook server listening (admin UI at /admin)"
    );
    axum::serve(listener, app(state))
        .with_graceful_shutdown(shutdown_signal())
        .await?;
    tracing::info!("shut down cleanly");
    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminate = async {
        if let Ok(mut s) = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        {
            s.recv().await;
        }
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();
    tokio::select! { _ = ctrl_c => {}, _ = terminate => {} }
}

/// Minimal HTTP/1.1 probe so the distroless image needs no curl.
async fn healthcheck(config: &Config) -> anyhow::Result<()> {
    let mut addr: SocketAddr = config.bind.parse()?;
    if addr.ip().is_unspecified() {
        addr.set_ip([127, 0, 0, 1].into());
    }
    let probe = async {
        let mut stream = tokio::net::TcpStream::connect(addr).await?;
        stream
            .write_all(b"GET /healthz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
            .await?;
        let mut buf = Vec::new();
        stream.read_to_end(&mut buf).await?;
        anyhow::Ok(String::from_utf8_lossy(&buf).into_owned())
    };
    let response = tokio::time::timeout(Duration::from_secs(3), probe).await??;
    if response.starts_with("HTTP/1.1 200") {
        println!("healthy");
        Ok(())
    } else {
        anyhow::bail!("unhealthy: {}", response.lines().next().unwrap_or_default())
    }
}
