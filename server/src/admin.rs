//! Lightweight server-rendered admin UI (askama templates, no JavaScript framework).

use askama::Template;
use axum::extract::{Multipart, Path, Query, Request, State};
use axum::http::{StatusCode, header};
use axum::middleware::{self, Next};
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use axum::{Form, Router};
use axum_extra::extract::cookie::{Cookie, CookieJar, SameSite};
use serde::Deserialize;

use crate::AppState;
use crate::api::{IMPORT_BODY_LIMIT, export_library, import_library};
use crate::model::{CookingMethod, Library, Product, now_millis};

const SESSION_COOKIE: &str = "hltc_session";
const ADMIN_CSS: &str = include_str!("../static/admin.css");
const LOGO_SVG: &str = include_str!("../static/logo.svg");

pub fn router(state: AppState) -> Router<AppState> {
    let protected = Router::new()
        .route("/", get(dashboard))
        .route("/products", get(products))
        .route("/products/new", get(new_product).post(create_product))
        .route("/products/{id}", get(edit_product).post(update_product))
        .route("/products/{id}/delete", post(delete_product))
        .route("/products/{id}/restore", post(restore_product))
        .route("/maintenance", get(maintenance))
        .route("/maintenance/purge", post(purge))
        .route("/export", get(export_download))
        .route(
            "/import",
            post(import_upload).layer(axum::extract::DefaultBodyLimit::max(IMPORT_BODY_LIMIT)),
        )
        .route("/logout", post(logout))
        .route_layer(middleware::from_fn_with_state(state, require_session));

    Router::new()
        .route("/login", get(login_page).post(login))
        .route("/static/admin.css", get(css))
        .route("/static/logo.svg", get(logo))
        .merge(protected)
}

async fn require_session(
    State(state): State<AppState>,
    jar: CookieJar,
    request: Request,
    next: Next,
) -> Response {
    let ok = jar
        .get(SESSION_COOKIE)
        .is_some_and(|c| state.auth.check_session(c.value()));
    if ok {
        next.run(request).await
    } else {
        Redirect::to("/admin/login").into_response()
    }
}

// ---------------------------------------------------------------------------
// Rendering helpers

#[derive(Default, Deserialize)]
struct Flash {
    msg: Option<String>,
    err: Option<String>,
}

struct HtmlTemplate<T>(T);

impl<T: Template> IntoResponse for HtmlTemplate<T> {
    fn into_response(self) -> Response {
        match self.0.render() {
            Ok(html) => Html(html).into_response(),
            Err(e) => {
                tracing::error!(error = %e, "template render failed");
                (StatusCode::INTERNAL_SERVER_ERROR, "template error").into_response()
            }
        }
    }
}

fn redirect_with(path: &str, key: &str, message: &str) -> Redirect {
    Redirect::to(&format!("{path}?{key}={}", url_encode(message)))
}

fn url_encode(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            b' ' => "+".to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

fn internal(e: impl std::fmt::Display) -> Response {
    tracing::error!(error = %e, "admin error");
    (StatusCode::INTERNAL_SERVER_ERROR, "Internal error").into_response()
}

pub fn human_bytes(bytes: i64) -> String {
    let b = bytes as f64;
    if b >= 1024.0 * 1024.0 {
        format!("{:.1} MiB", b / (1024.0 * 1024.0))
    } else if b >= 1024.0 {
        format!("{:.1} KiB", b / 1024.0)
    } else {
        format!("{bytes} B")
    }
}

pub fn human_uptime(secs: u64) -> String {
    let (d, h, m) = (secs / 86_400, (secs % 86_400) / 3600, (secs % 3600) / 60);
    if d > 0 {
        format!("{d}d {h}h")
    } else if h > 0 {
        format!("{h}h {m}m")
    } else {
        format!("{m}m")
    }
}

pub fn format_timestamp(millis: i64) -> String {
    // Minimal UTC formatter (civil-from-days) to avoid a date/time dependency.
    let secs = millis.div_euclid(1000);
    let days = secs.div_euclid(86_400);
    let rem = secs.rem_euclid(86_400);
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + if m <= 2 { 1 } else { 0 };
    format!(
        "{y:04}-{m:02}-{d:02} {:02}:{:02} UTC",
        rem / 3600,
        (rem % 3600) / 60
    )
}

// ---------------------------------------------------------------------------
// Static assets

async fn css() -> impl IntoResponse {
    (
        [
            (header::CONTENT_TYPE, "text/css; charset=utf-8"),
            (header::CACHE_CONTROL, "public, max-age=3600"),
        ],
        ADMIN_CSS,
    )
}

async fn logo() -> impl IntoResponse {
    (
        [
            (header::CONTENT_TYPE, "image/svg+xml"),
            (header::CACHE_CONTROL, "public, max-age=86400"),
        ],
        LOGO_SVG,
    )
}

// ---------------------------------------------------------------------------
// Login / logout

#[derive(Template)]
#[template(path = "login.html")]
struct LoginTemplate {
    error: Option<String>,
}

async fn login_page(Query(flash): Query<Flash>) -> impl IntoResponse {
    HtmlTemplate(LoginTemplate { error: flash.err })
}

#[derive(Deserialize)]
struct LoginForm {
    token: String,
}

async fn login(
    State(state): State<AppState>,
    jar: CookieJar,
    Form(form): Form<LoginForm>,
) -> Response {
    if !state.auth.check_token(form.token.trim()) {
        tracing::warn!("failed admin login attempt");
        return redirect_with("/admin/login", "err", "Invalid token").into_response();
    }
    let cookie = Cookie::build((SESSION_COOKIE, state.auth.session_value().to_string()))
        .path("/admin")
        .http_only(true)
        .same_site(SameSite::Strict)
        .secure(state.secure_cookies)
        .build();
    (jar.add(cookie), Redirect::to("/admin")).into_response()
}

async fn logout(jar: CookieJar) -> Response {
    let removal = Cookie::build((SESSION_COOKIE, "")).path("/admin").build();
    (jar.remove(removal), Redirect::to("/admin/login")).into_response()
}

// ---------------------------------------------------------------------------
// Dashboard

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    nav: &'static str,
    version: &'static str,
    products: i64,
    tombstones: i64,
    favorites: i64,
    barcodes: i64,
    revision: i64,
    db_size: String,
    uptime: String,
    recent: Vec<ProductRow>,
}

struct ProductRow {
    id: String,
    name: String,
    brand: String,
    summary: String,
    barcodes: String,
    tags: Vec<String>,
    favorite: bool,
    updated: String,
}

impl From<&Product> for ProductRow {
    fn from(p: &Product) -> Self {
        ProductRow {
            id: p.id.clone(),
            name: p.name.clone(),
            brand: p.brand.clone().unwrap_or_default(),
            summary: p.summary(),
            barcodes: p.barcodes.join(", "),
            tags: p.tags.clone(),
            favorite: p.favorite,
            updated: format_timestamp(p.updated_at),
        }
    }
}

async fn dashboard(State(state): State<AppState>) -> Response {
    let stats = match state.db.stats().await {
        Ok(s) => s,
        Err(e) => return internal(e),
    };
    let mut all = match state.db.all(false).await {
        Ok(a) => a,
        Err(e) => return internal(e),
    };
    all.sort_by_key(|p| std::cmp::Reverse(p.updated_at));
    HtmlTemplate(DashboardTemplate {
        nav: "dashboard",
        version: env!("CARGO_PKG_VERSION"),
        products: stats.products,
        tombstones: stats.tombstones,
        favorites: stats.favorites,
        barcodes: stats.barcodes,
        revision: stats.revision,
        db_size: human_bytes(stats.db_size_bytes),
        uptime: human_uptime(state.started.elapsed().as_secs()),
        recent: all.iter().take(8).map(ProductRow::from).collect(),
    })
    .into_response()
}

// ---------------------------------------------------------------------------
// Products

#[derive(Template)]
#[template(path = "products.html")]
struct ProductsTemplate {
    nav: &'static str,
    q: String,
    show_deleted: bool,
    rows: Vec<ProductRow>,
    msg: Option<String>,
    err: Option<String>,
}

#[derive(Deserialize)]
struct ProductsQuery {
    #[serde(default)]
    q: String,
    #[serde(default)]
    deleted: Option<String>,
    msg: Option<String>,
    err: Option<String>,
}

async fn products(State(state): State<AppState>, Query(query): Query<ProductsQuery>) -> Response {
    let show_deleted = query.deleted.as_deref() == Some("1");
    match state.db.search(&query.q, show_deleted).await {
        Ok(list) => HtmlTemplate(ProductsTemplate {
            nav: "products",
            q: query.q,
            show_deleted,
            rows: list.iter().map(ProductRow::from).collect(),
            msg: query.msg,
            err: query.err,
        })
        .into_response(),
        Err(e) => internal(e),
    }
}

#[derive(Template)]
#[template(path = "product.html")]
struct ProductTemplate {
    nav: &'static str,
    is_new: bool,
    id: String,
    deleted: bool,
    created: String,
    updated: String,
    methods: Vec<CookingMethod>,
    form: ProductForm,
    msg: Option<String>,
    err: Option<String>,
}

#[derive(Deserialize, Default, Clone)]
struct ProductForm {
    name: String,
    #[serde(default)]
    brand: String,
    #[serde(default)]
    barcodes: String,
    #[serde(default)]
    tags: String,
    #[serde(default)]
    favorite: Option<String>,
    #[serde(default)]
    rating: String,
    #[serde(default)]
    notes: String,
    #[serde(default)]
    methods_json: String,
}

impl ProductForm {
    fn from_product(p: &Product) -> ProductForm {
        ProductForm {
            name: p.name.clone(),
            brand: p.brand.clone().unwrap_or_default(),
            barcodes: p.barcodes.join("\n"),
            tags: p.tags.join(", "),
            favorite: p.favorite.then(|| "on".to_string()),
            rating: p.rating.map(|r| r.to_string()).unwrap_or_default(),
            notes: p.notes.clone().unwrap_or_default(),
            methods_json: serde_json::to_string_pretty(&p.methods).unwrap_or_else(|_| "[]".into()),
        }
    }

    fn is_favorite(&self) -> bool {
        self.favorite.is_some()
    }

    /// Apply the form onto a base product (new or existing) and validate.
    fn apply(&self, mut base: Product) -> Result<Product, String> {
        base.name = self.name.clone();
        base.brand = Some(self.brand.clone());
        base.barcodes = self
            .barcodes
            .split(['\n', ',', '\r'])
            .map(str::to_string)
            .collect();
        base.tags = self.tags.split(',').map(str::to_string).collect();
        base.favorite = self.is_favorite();
        base.rating = match self.rating.trim() {
            "" => None,
            r => Some(
                r.parse::<u8>()
                    .map_err(|_| "rating must be a number 1-5".to_string())?,
            ),
        };
        base.notes = Some(self.notes.clone());
        let methods_json = self.methods_json.trim();
        base.methods = if methods_json.is_empty() {
            Vec::new()
        } else {
            serde_json::from_str(methods_json).map_err(|e| format!("methods JSON: {e}"))?
        };
        base.normalized()
    }
}

fn blank_product() -> Product {
    let now = now_millis();
    Product {
        id: uuid::Uuid::new_v4().to_string(),
        name: String::new(),
        brand: None,
        barcodes: vec![],
        tags: vec![],
        favorite: false,
        rating: None,
        notes: None,
        methods: vec![],
        created_at: now,
        updated_at: now,
        deleted: false,
    }
}

const NEW_METHODS_TEMPLATE: &str = r#"[
  {
    "type": "MICROWAVE",
    "power": "High",
    "temperature": null,
    "preheat": false,
    "steps": [
      { "action": "COOK", "durationSeconds": 180, "note": null }
    ],
    "notes": null
  }
]"#;

async fn new_product(Query(flash): Query<Flash>) -> Response {
    let p = blank_product();
    let mut form = ProductForm::from_product(&p);
    form.methods_json = NEW_METHODS_TEMPLATE.to_string();
    render_product(&p, form, true, flash.msg, flash.err)
}

fn render_product(
    p: &Product,
    form: ProductForm,
    is_new: bool,
    msg: Option<String>,
    err: Option<String>,
) -> Response {
    HtmlTemplate(ProductTemplate {
        nav: "products",
        is_new,
        id: p.id.clone(),
        deleted: p.deleted,
        created: format_timestamp(p.created_at),
        updated: format_timestamp(p.updated_at),
        methods: p.methods.clone(),
        form,
        msg,
        err,
    })
    .into_response()
}

async fn create_product(State(state): State<AppState>, Form(form): Form<ProductForm>) -> Response {
    let base = blank_product();
    match form.apply(base.clone()) {
        Ok(p) => match state.db.save_authoritative(p).await {
            Ok(out) => redirect_with(
                &format!("/admin/products/{}", out.product.id),
                "msg",
                "Product created",
            )
            .into_response(),
            Err(e) => internal(e),
        },
        Err(e) => render_product(&base, form, true, None, Some(e)),
    }
}

async fn edit_product(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Query(flash): Query<Flash>,
) -> Response {
    match state.db.get(&id).await {
        Ok(Some(p)) => render_product(
            &p,
            ProductForm::from_product(&p),
            false,
            flash.msg,
            flash.err,
        ),
        Ok(None) => redirect_with("/admin/products", "err", "Product not found").into_response(),
        Err(e) => internal(e),
    }
}

async fn update_product(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Form(form): Form<ProductForm>,
) -> Response {
    let existing = match state.db.get(&id).await {
        Ok(Some(p)) => p,
        Ok(None) => {
            return redirect_with("/admin/products", "err", "Product not found").into_response();
        }
        Err(e) => return internal(e),
    };
    match form.apply(existing.clone()) {
        Ok(p) => match state.db.save_authoritative(p).await {
            Ok(_) => {
                redirect_with(&format!("/admin/products/{id}"), "msg", "Saved").into_response()
            }
            Err(e) => internal(e),
        },
        Err(e) => render_product(&existing, form, false, None, Some(e)),
    }
}

async fn delete_product(State(state): State<AppState>, Path(id): Path<String>) -> Response {
    match state.db.soft_delete(&id).await {
        Ok(Some(out)) => redirect_with(
            "/admin/products",
            "msg",
            &format!(
                "Deleted '{}'. It can be restored until tombstones are purged.",
                out.product.name
            ),
        )
        .into_response(),
        Ok(None) => redirect_with("/admin/products", "err", "Product not found").into_response(),
        Err(e) => internal(e),
    }
}

async fn restore_product(State(state): State<AppState>, Path(id): Path<String>) -> Response {
    match state.db.restore(&id).await {
        Ok(Some(_)) => {
            redirect_with(&format!("/admin/products/{id}"), "msg", "Restored").into_response()
        }
        Ok(None) => redirect_with("/admin/products", "err", "Product not found").into_response(),
        Err(e) => internal(e),
    }
}

// ---------------------------------------------------------------------------
// Maintenance: purge, export, import

#[derive(Template)]
#[template(path = "maintenance.html")]
struct MaintenanceTemplate {
    nav: &'static str,
    tombstones: i64,
    products: i64,
    msg: Option<String>,
    err: Option<String>,
}

async fn maintenance(State(state): State<AppState>, Query(flash): Query<Flash>) -> Response {
    match state.db.stats().await {
        Ok(stats) => HtmlTemplate(MaintenanceTemplate {
            nav: "maintenance",
            tombstones: stats.tombstones,
            products: stats.products,
            msg: flash.msg,
            err: flash.err,
        })
        .into_response(),
        Err(e) => internal(e),
    }
}

async fn purge(State(state): State<AppState>) -> Response {
    match state.db.purge_tombstones().await {
        Ok(n) => redirect_with(
            "/admin/maintenance",
            "msg",
            &format!("Purged {n} deleted product(s)"),
        )
        .into_response(),
        Err(e) => internal(e),
    }
}

async fn export_download(State(state): State<AppState>) -> Response {
    match export_library(&state).await {
        Ok(lib) => {
            let body = serde_json::to_string_pretty(&lib).unwrap_or_default();
            let filename = format!(
                "howlongtocook-export-{}.json",
                &format_timestamp(lib.updated_at)[..10]
            );
            (
                [
                    (header::CONTENT_TYPE, "application/json".to_string()),
                    (
                        header::CONTENT_DISPOSITION,
                        format!("attachment; filename=\"{filename}\""),
                    ),
                ],
                body,
            )
                .into_response()
        }
        Err(e) => internal(e),
    }
}

async fn import_upload(State(state): State<AppState>, mut multipart: Multipart) -> Response {
    let mut bytes = None;
    while let Ok(Some(field)) = multipart.next_field().await {
        if field.name() == Some("file") {
            match field.bytes().await {
                Ok(b) => bytes = Some(b),
                Err(e) => {
                    return redirect_with(
                        "/admin/maintenance",
                        "err",
                        &format!("Upload failed: {e}"),
                    )
                    .into_response();
                }
            }
        }
    }
    let Some(bytes) = bytes.filter(|b| !b.is_empty()) else {
        return redirect_with("/admin/maintenance", "err", "Choose a JSON file to import")
            .into_response();
    };
    let library: Library = match serde_json::from_slice(&bytes) {
        Ok(l) => l,
        Err(e) => {
            return redirect_with(
                "/admin/maintenance",
                "err",
                &format!("Invalid export file: {e}"),
            )
            .into_response();
        }
    };
    match import_library(&state, library).await {
        Ok(r) => redirect_with(
            "/admin/maintenance",
            "msg",
            &format!(
                "Imported {} product(s): {} applied, {} already up to date",
                r.received, r.applied, r.skipped
            ),
        )
        .into_response(),
        Err(e) => redirect_with("/admin/maintenance", "err", &e.to_string()).into_response(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timestamps_format_in_utc() {
        assert_eq!(format_timestamp(0), "1970-01-01 00:00 UTC");
        assert_eq!(format_timestamp(1_762_851_000_000), "2025-11-11 08:50 UTC");
    }

    #[test]
    fn form_round_trip() {
        let p: Product =
            serde_json::from_str(include_str!("../../api/examples/product.json")).unwrap();
        let form = ProductForm::from_product(&p);
        let back = form.apply(p.clone()).unwrap();
        assert_eq!(back, p);
    }

    #[test]
    fn form_rejects_bad_methods_json() {
        let p = blank_product();
        let form = ProductForm {
            name: "X".into(),
            methods_json: "[{".into(),
            ..Default::default()
        };
        assert!(form.apply(p).unwrap_err().contains("methods JSON"));
    }
}
