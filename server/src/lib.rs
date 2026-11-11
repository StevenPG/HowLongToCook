//! HowLongToCook self-hosted server: sync API + admin UI over SQLite.

pub mod admin;
pub mod api;
pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod model;

use std::sync::Arc;
use std::time::{Duration, Instant};

use axum::response::Redirect;
use axum::routing::get;
use axum::{Json, Router};
use serde_json::json;
use tower_http::timeout::TimeoutLayer;
use tower_http::trace::TraceLayer;

use crate::auth::Auth;
use crate::db::Db;

#[derive(Clone)]
pub struct AppState {
    pub db: Db,
    pub auth: Arc<Auth>,
    pub secure_cookies: bool,
    pub started: Instant,
}

impl AppState {
    pub fn new(db: Db, token: String, secure_cookies: bool) -> AppState {
        AppState {
            db,
            auth: Arc::new(Auth::new(token)),
            secure_cookies,
            started: Instant::now(),
        }
    }
}

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/", get(|| async { Redirect::to("/admin") }))
        .route("/healthz", get(healthz))
        .nest("/api/v1", api::router(state.clone()))
        .nest("/admin", admin::router(state.clone()))
        .layer(TraceLayer::new_for_http())
        .layer(TimeoutLayer::with_status_code(
            axum::http::StatusCode::REQUEST_TIMEOUT,
            Duration::from_secs(30),
        ))
        .with_state(state)
}

async fn healthz(
    axum::extract::State(state): axum::extract::State<AppState>,
) -> (axum::http::StatusCode, Json<serde_json::Value>) {
    if state.db.ping().await {
        (axum::http::StatusCode::OK, Json(json!({ "status": "ok" })))
    } else {
        (
            axum::http::StatusCode::SERVICE_UNAVAILABLE,
            Json(json!({ "status": "database unavailable" })),
        )
    }
}
