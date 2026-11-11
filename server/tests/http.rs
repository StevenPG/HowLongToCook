//! End-to-end tests over the full router with an in-memory database.

use axum::body::Body;
use axum::http::{header, Request, StatusCode};
use axum::Router;
use http_body_util::BodyExt;
use serde_json::{json, Value};
use tower::ServiceExt;

use howlongtocook_server::db::Db;
use howlongtocook_server::{app, AppState};

const TOKEN: &str = "test-token-0123456789";

async fn test_app() -> Router {
    let db = Db::connect("sqlite::memory:").await.unwrap();
    app(AppState::new(db, TOKEN.into(), false))
}

fn example() -> Value {
    serde_json::from_str(include_str!("../../api/examples/product.json")).unwrap()
}

async fn send(app: &Router, req: Request<Body>) -> (StatusCode, axum::http::HeaderMap, String) {
    let res = app.clone().oneshot(req).await.unwrap();
    let status = res.status();
    let headers = res.headers().clone();
    let body = res.into_body().collect().await.unwrap().to_bytes();
    (status, headers, String::from_utf8_lossy(&body).into_owned())
}

fn api(method: &str, path: &str, body: Option<Value>) -> Request<Body> {
    let builder = Request::builder()
        .method(method)
        .uri(path)
        .header(header::AUTHORIZATION, format!("Bearer {TOKEN}"));
    match body {
        Some(b) => builder
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(b.to_string()))
            .unwrap(),
        None => builder.body(Body::empty()).unwrap(),
    }
}

fn json(body: &str) -> Value {
    serde_json::from_str(body).unwrap()
}

#[tokio::test]
async fn healthz_is_public() {
    let app = test_app().await;
    let (status, _, body) = send(&app, Request::get("/healthz").body(Body::empty()).unwrap()).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(json(&body)["status"], "ok");
}

#[tokio::test]
async fn api_requires_token() {
    let app = test_app().await;
    let (status, headers, _) = send(&app, Request::get("/api/v1/info").body(Body::empty()).unwrap()).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    assert!(headers.contains_key("www-authenticate"));

    let bad = Request::get("/api/v1/info")
        .header(header::AUTHORIZATION, "Bearer nope")
        .body(Body::empty())
        .unwrap();
    assert_eq!(send(&app, bad).await.0, StatusCode::UNAUTHORIZED);
    assert_eq!(send(&app, api("GET", "/api/v1/info", None)).await.0, StatusCode::OK);
}

#[tokio::test]
async fn sync_flow() {
    let app = test_app().await;
    let p = example();
    let id = p["id"].as_str().unwrap().to_string();

    // Push.
    let (status, _, body) = send(&app, api("PUT", &format!("/api/v1/products/{id}"), Some(p.clone()))).await;
    assert_eq!(status, StatusCode::OK, "{body}");
    let put = json(&body);
    assert_eq!(put["applied"], true);
    assert_eq!(put["revision"], 1);

    // Pull everything, then nothing new.
    let (_, _, body) = send(&app, api("GET", "/api/v1/products?since=0", None)).await;
    let changes = json(&body);
    assert_eq!(changes["revision"], 1);
    assert_eq!(changes["products"].as_array().unwrap().len(), 1);
    assert_eq!(changes["products"][0], p);
    let (_, _, body) = send(&app, api("GET", "/api/v1/products?since=1", None)).await;
    assert!(json(&body)["products"].as_array().unwrap().is_empty());

    // Stale write loses.
    let mut stale = p.clone();
    stale["name"] = json!("Stale");
    stale["updatedAt"] = json!(1);
    let (_, _, body) = send(&app, api("PUT", &format!("/api/v1/products/{id}"), Some(stale))).await;
    let put = json(&body);
    assert_eq!(put["applied"], false);
    assert_eq!(put["product"]["name"], "Chicken Pot Pie");

    // Barcode lookup.
    let (_, _, body) = send(&app, api("GET", "/api/v1/barcodes/012345678905", None)).await;
    assert_eq!(json(&body).as_array().unwrap().len(), 1);

    // Delete makes a tombstone that is visible to sync.
    let (status, _, body) = send(&app, api("DELETE", &format!("/api/v1/products/{id}"), None)).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(json(&body)["product"]["deleted"], true);
    let (_, _, body) = send(&app, api("GET", "/api/v1/products?since=1", None)).await;
    assert_eq!(json(&body)["products"][0]["deleted"], true);
    let (_, _, body) = send(&app, api("GET", "/api/v1/barcodes/012345678905", None)).await;
    assert!(json(&body).as_array().unwrap().is_empty());
}

#[tokio::test]
async fn validation_errors() {
    let app = test_app().await;
    let mut p = example();
    p["rating"] = json!(11);
    let id = p["id"].as_str().unwrap().to_string();
    let (status, _, body) = send(&app, api("PUT", &format!("/api/v1/products/{id}"), Some(p))).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(json(&body)["error"], "bad_request");

    let p = example();
    let (status, _, _) = send(
        &app,
        api("PUT", "/api/v1/products/00000000-0000-4000-8000-000000000000", Some(p)),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);

    let (status, _, _) = send(&app, api("GET", "/api/v1/products/missing", None)).await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn export_import_round_trip() {
    let app = test_app().await;
    let library = json!({ "schemaVersion": 1, "updatedAt": 0, "products": [example()] });
    let (status, _, body) = send(&app, api("POST", "/api/v1/import", Some(library.clone()))).await;
    assert_eq!(status, StatusCode::OK, "{body}");
    assert_eq!(json(&body)["applied"], 1);
    // Re-import is a no-op.
    let (_, _, body) = send(&app, api("POST", "/api/v1/import", Some(library))).await;
    assert_eq!(json(&body)["skipped"], 1);

    let (_, _, body) = send(&app, api("GET", "/api/v1/export", None)).await;
    let export = json(&body);
    assert_eq!(export["schemaVersion"], 1);
    assert_eq!(export["products"][0], example());
}

#[tokio::test]
async fn admin_login_flow() {
    let app = test_app().await;

    // Protected pages redirect to login.
    let (status, headers, _) = send(&app, Request::get("/admin").body(Body::empty()).unwrap()).await;
    assert_eq!(status, StatusCode::SEE_OTHER);
    assert_eq!(headers[header::LOCATION], "/admin/login");

    let (status, _, body) = send(&app, Request::get("/admin/login").body(Body::empty()).unwrap()).await;
    assert_eq!(status, StatusCode::OK);
    assert!(body.contains("Access token"));

    // Wrong token.
    let bad = Request::post("/admin/login")
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from("token=wrong"))
        .unwrap();
    let (_, headers, _) = send(&app, bad).await;
    assert!(headers[header::LOCATION].to_str().unwrap().contains("err="));

    // Right token sets an HttpOnly session cookie that is not the raw token.
    let good = Request::post("/admin/login")
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from(format!("token={TOKEN}")))
        .unwrap();
    let (_, headers, _) = send(&app, good).await;
    let cookie = headers[header::SET_COOKIE].to_str().unwrap().to_string();
    assert!(cookie.contains("HttpOnly"));
    assert!(cookie.contains("SameSite=Strict"));
    assert!(!cookie.contains(TOKEN));
    let session = cookie.split(';').next().unwrap().to_string();

    let get = |path: &str| {
        Request::get(path)
            .header(header::COOKIE, session.clone())
            .body(Body::empty())
            .unwrap()
    };

    // Seed a product through the API, then exercise the pages.
    let p = example();
    let id = p["id"].as_str().unwrap().to_string();
    send(&app, api("PUT", &format!("/api/v1/products/{id}"), Some(p))).await;

    let (status, _, body) = send(&app, get("/admin")).await;
    assert_eq!(status, StatusCode::OK);
    assert!(body.contains("Chicken Pot Pie"));

    let (_, _, body) = send(&app, get("/admin/products?q=pot")).await;
    assert!(body.contains("Microwave · 7 min"));
    let (_, _, body) = send(&app, get("/admin/products?q=zzz")).await;
    assert!(body.contains("No products match"));

    let (status, _, body) = send(&app, get(&format!("/admin/products/{id}"))).await;
    assert_eq!(status, StatusCode::OK);
    assert!(body.contains("Cut slit in film"));
    assert!(body.contains("400°F · Preheat"));

    let (status, _, _) = send(&app, get("/admin/products/new")).await;
    assert_eq!(status, StatusCode::OK);
    let (status, _, _) = send(&app, get("/admin/maintenance")).await;
    assert_eq!(status, StatusCode::OK);
    let (status, headers, body) = send(&app, get("/admin/export")).await;
    assert_eq!(status, StatusCode::OK);
    assert!(headers[header::CONTENT_DISPOSITION].to_str().unwrap().contains("attachment"));
    assert!(body.contains("Chicken Pot Pie"));

    // Edit through the form; server-side edits always win.
    let form = "name=Pot+Pie+Deluxe&brand=&barcodes=012345678905%0A99999&tags=dinner%2C+comfort&rating=5&notes=&methods_json=%5B%5D";
    let edit = Request::post(format!("/admin/products/{id}"))
        .header(header::COOKIE, session.clone())
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from(form))
        .unwrap();
    let (status, _, _) = send(&app, edit).await;
    assert_eq!(status, StatusCode::SEE_OTHER);
    let (_, _, body) = send(&app, api("GET", &format!("/api/v1/products/{id}"), None)).await;
    let stored = json(&body);
    assert_eq!(stored["name"], "Pot Pie Deluxe");
    assert_eq!(stored["barcodes"], json!(["012345678905", "99999"]));
    assert_eq!(stored["tags"], json!(["dinner", "comfort"]));
    assert_eq!(stored["favorite"], false);
    assert_eq!(stored["rating"], 5);
    assert_eq!(stored["brand"], Value::Null);

    // Invalid form re-renders with an error and keeps input.
    let bad = Request::post(format!("/admin/products/{id}"))
        .header(header::COOKIE, session.clone())
        .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
        .body(Body::from("name=Still+here&methods_json=%5B%7B"))
        .unwrap();
    let (status, _, body) = send(&app, bad).await;
    assert_eq!(status, StatusCode::OK);
    assert!(body.contains("methods JSON"));
    assert!(body.contains("Still here"));

    // Delete then purge.
    let del = Request::post(format!("/admin/products/{id}/delete"))
        .header(header::COOKIE, session.clone())
        .body(Body::empty())
        .unwrap();
    send(&app, del).await;
    let (_, _, body) = send(&app, get("/admin/products?deleted=1")).await;
    assert!(body.contains("Pot Pie Deluxe"));
    let purge = Request::post("/admin/maintenance/purge")
        .header(header::COOKIE, session.clone())
        .body(Body::empty())
        .unwrap();
    let (_, headers, _) = send(&app, purge).await;
    assert!(headers[header::LOCATION].to_str().unwrap().contains("Purged+1"));
}

#[tokio::test]
async fn admin_static_assets_are_public() {
    let app = test_app().await;
    let (status, headers, _) =
        send(&app, Request::get("/admin/static/admin.css").body(Body::empty()).unwrap()).await;
    assert_eq!(status, StatusCode::OK);
    assert!(headers[header::CONTENT_TYPE].to_str().unwrap().starts_with("text/css"));
}
