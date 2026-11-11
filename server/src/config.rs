use clap::{Args, Parser, Subcommand};

#[derive(Debug, Parser)]
#[command(
    name = "howlongtocook-server",
    version,
    about = "HowLongToCook sync server and admin UI"
)]
pub struct Cli {
    #[command(subcommand)]
    pub command: Option<Command>,
    #[command(flatten)]
    pub config: Config,
}

#[derive(Debug, Subcommand)]
pub enum Command {
    /// Run the HTTP server (default).
    Serve,
    /// Probe the local server's /healthz endpoint; exits non-zero when unhealthy.
    /// Intended for Docker HEALTHCHECK in images without curl/wget.
    Healthcheck,
}

#[derive(Debug, Clone, Args)]
pub struct Config {
    /// Shared bearer token for the API and admin UI login.
    #[arg(long, env = "HLTC_TOKEN", hide_env_values = true)]
    pub token: Option<String>,

    /// Read the shared token from a file (e.g. a Docker or Kubernetes secret).
    #[arg(long, env = "HLTC_TOKEN_FILE")]
    pub token_file: Option<std::path::PathBuf>,

    /// SQLite database URL.
    #[arg(
        long,
        env = "HLTC_DATABASE_URL",
        default_value = "sqlite://data/howlongtocook.db"
    )]
    pub database_url: String,

    /// Address to listen on.
    #[arg(long, env = "HLTC_BIND", default_value = "0.0.0.0:8080")]
    pub bind: String,

    /// Mark the admin session cookie as Secure (enable when served over HTTPS).
    #[arg(long, env = "HLTC_SECURE_COOKIES", default_value_t = false)]
    pub secure_cookies: bool,
}

pub const MIN_TOKEN_LEN: usize = 16;

impl Config {
    pub fn resolve_token(&self) -> anyhow::Result<String> {
        let token = match (&self.token, &self.token_file) {
            (Some(t), _) => t.clone(),
            (None, Some(path)) => std::fs::read_to_string(path)
                .map_err(|e| anyhow::anyhow!("reading token file {}: {e}", path.display()))?,
            (None, None) => anyhow::bail!(
                "no token configured: set HLTC_TOKEN or HLTC_TOKEN_FILE (generate one with `openssl rand -hex 32`)"
            ),
        };
        let token = token.trim().to_string();
        if token.len() < MIN_TOKEN_LEN {
            anyhow::bail!("token must be at least {MIN_TOKEN_LEN} characters");
        }
        Ok(token)
    }
}
