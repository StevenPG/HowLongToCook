//! JSON REST API used by the Android app (see api/openapi.yaml).

use axum::extract::{DefaultBodyLimit, Path, Query, Request, State};
use axum::http::header::AUTHORIZATION;
use axum::middleware::{self, Next};
use axum::response::Response;
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};

use crate::AppState;
use crate::error::ApiError;
use crate::model::{Library, Product, SCHEMA_VERSION, now_millis};

pub const API_BODY_LIMIT: usize = 1024 * 1024;
pub const IMPORT_BODY_LIMIT: usize = 10 * 1024 * 1024;

pub fn router(state: AppState) -> Router<AppState> {
    Router::new()
        .route("/info", get(info))
        .route("/products", get(list_products))
        .route(
            "/products/{id}",
            get(get_product).put(put_product).delete(delete_product),
        )
        .route("/barcodes/{code}", get(by_barcode))
        .route("/export", get(export))
        .route(
            "/import",
            post(import).layer(DefaultBodyLimit::max(IMPORT_BODY_LIMIT)),
        )
        .layer(DefaultBodyLimit::max(API_BODY_LIMIT))
        .route_layer(middleware::from_fn_with_state(state, require_token))
}

async fn require_token(
    State(state): State<AppState>,
    request: Request,
    next: Next,
) -> Result<Response, ApiError> {
    let header = request
        .headers()
        .get(AUTHORIZATION)
        .and_then(|v| v.to_str().ok());
    if !state.auth.check_bearer(header) {
        return Err(ApiError::Unauthorized);
    }
    Ok(next.run(request).await)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct InfoResponse {
    name: &'static str,
    version: &'static str,
    schema_version: u32,
    product_count: i64,
    revision: i64,
    server_time: i64,
}

async fn info(State(state): State<AppState>) -> Result<Json<InfoResponse>, ApiError> {
    let stats = state.db.stats().await?;
    Ok(Json(InfoResponse {
        name: "howlongtocook-server",
        version: env!("CARGO_PKG_VERSION"),
        schema_version: SCHEMA_VERSION,
        product_count: stats.products,
        revision: stats.revision,
        server_time: now_millis(),
    }))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ListQuery {
    #[serde(default)]
    since: i64,
    #[serde(default = "default_true")]
    include_deleted: bool,
}

fn default_true() -> bool {
    true
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ChangesResponse {
    pub revision: i64,
    pub products: Vec<Product>,
}

async fn list_products(
    State(state): State<AppState>,
    Query(q): Query<ListQuery>,
) -> Result<Json<ChangesResponse>, ApiError> {
    // Read the revision first: anything written concurrently is picked up next time.
    let revision = state.db.current_revision().await?;
    let products = state.db.list_since(q.since, q.include_deleted).await?;
    Ok(Json(ChangesResponse { revision, products }))
}

async fn get_product(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Product>, ApiError> {
    state.db.get(&id).await?.map(Json).ok_or(ApiError::NotFound)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PutResponse {
    pub product: Product,
    pub applied: bool,
    pub revision: i64,
}

async fn put_product(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(product): Json<Product>,
) -> Result<Json<PutResponse>, ApiError> {
    if product.id != id {
        return Err(ApiError::BadRequest("id in path and body differ".into()));
    }
    let product = product.normalized().map_err(ApiError::BadRequest)?;
    let out = state.db.upsert(product).await?;
    Ok(Json(PutResponse {
        product: out.product,
        applied: out.applied,
        revision: out.revision,
    }))
}

async fn delete_product(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<PutResponse>, ApiError> {
    let out = state.db.soft_delete(&id).await?.ok_or(ApiError::NotFound)?;
    Ok(Json(PutResponse {
        product: out.product,
        applied: out.applied,
        revision: out.revision,
    }))
}

async fn by_barcode(
    State(state): State<AppState>,
    Path(code): Path<String>,
) -> Result<Json<Vec<Product>>, ApiError> {
    Ok(Json(state.db.find_by_barcode(code.trim()).await?))
}

async fn export(State(state): State<AppState>) -> Result<Json<Library>, ApiError> {
    Ok(Json(export_library(&state).await?))
}

pub async fn export_library(state: &AppState) -> Result<Library, ApiError> {
    Ok(Library {
        schema_version: SCHEMA_VERSION,
        updated_at: now_millis(),
        products: state.db.all(true).await?,
    })
}

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ImportResponse {
    pub received: usize,
    pub applied: usize,
    pub skipped: usize,
    pub revision: i64,
}

async fn import(
    State(state): State<AppState>,
    Json(library): Json<Library>,
) -> Result<Json<ImportResponse>, ApiError> {
    Ok(Json(import_library(&state, library).await?))
}

/// Merge a library document using the normal last-write-wins rule.
/// The whole import is validated up front so a bad file changes nothing.
pub async fn import_library(
    state: &AppState,
    library: Library,
) -> Result<ImportResponse, ApiError> {
    if library.schema_version > SCHEMA_VERSION {
        return Err(ApiError::BadRequest(format!(
            "unsupported schemaVersion {} (max {SCHEMA_VERSION})",
            library.schema_version
        )));
    }
    let received = library.products.len();
    let products = library
        .products
        .into_iter()
        .map(|p| {
            let name = p.name.clone();
            p.normalized()
                .map_err(|e| ApiError::BadRequest(format!("product '{name}': {e}")))
        })
        .collect::<Result<Vec<_>, _>>()?;
    let mut res = ImportResponse {
        received,
        ..Default::default()
    };
    for p in products {
        let out = state.db.upsert(p).await?;
        if out.applied {
            res.applied += 1;
        } else {
            res.skipped += 1;
        }
        res.revision = out.revision;
    }
    res.revision = state.db.current_revision().await?;
    Ok(res)
}
