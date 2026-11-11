//! SQLite persistence with last-write-wins semantics and a monotonic revision cursor.

use std::str::FromStr;

use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions};
use sqlx::{Row, SqlitePool};

use crate::model::{Product, now_millis};

#[derive(Clone)]
pub struct Db {
    pool: SqlitePool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UpsertOutcome {
    /// The version stored after the call (either the incoming one or the newer existing one).
    pub product: Product,
    /// Whether the incoming product replaced what was stored.
    pub applied: bool,
    pub revision: i64,
}

#[derive(Debug, Clone, Default)]
pub struct Stats {
    pub products: i64,
    pub tombstones: i64,
    pub favorites: i64,
    pub barcodes: i64,
    pub revision: i64,
    pub db_size_bytes: i64,
}

impl Db {
    pub async fn connect(url: &str) -> anyhow::Result<Db> {
        let in_memory = url.contains(":memory:");
        if !in_memory
            && let Some(path) = url
                .strip_prefix("sqlite://")
                .or_else(|| url.strip_prefix("sqlite:"))
        {
            let path = path.split('?').next().unwrap_or(path);
            if let Some(parent) = std::path::Path::new(path).parent()
                && !parent.as_os_str().is_empty()
            {
                std::fs::create_dir_all(parent)?;
            }
        }
        let mut options = SqliteConnectOptions::from_str(url)?
            .create_if_missing(true)
            .foreign_keys(true);
        if !in_memory {
            options = options.journal_mode(SqliteJournalMode::Wal);
        }
        let pool = SqlitePoolOptions::new()
            // An in-memory database exists per connection, so keep exactly one.
            .max_connections(if in_memory { 1 } else { 4 })
            .connect_with(options)
            .await?;
        sqlx::migrate!("./migrations").run(&pool).await?;
        Ok(Db { pool })
    }

    pub async fn ping(&self) -> bool {
        sqlx::query("SELECT 1").execute(&self.pool).await.is_ok()
    }

    pub async fn current_revision(&self) -> sqlx::Result<i64> {
        sqlx::query_scalar("SELECT value FROM meta WHERE key = 'revision'")
            .fetch_one(&self.pool)
            .await
    }

    pub async fn get(&self, id: &str) -> sqlx::Result<Option<Product>> {
        let row = sqlx::query("SELECT data FROM products WHERE id = ?")
            .bind(id)
            .fetch_optional(&self.pool)
            .await?;
        Ok(row.map(|r| decode(r.get::<String, _>("data"))))
    }

    /// Products changed after `since`, in revision order.
    pub async fn list_since(
        &self,
        since: i64,
        include_deleted: bool,
    ) -> sqlx::Result<Vec<Product>> {
        let rows = sqlx::query(
            "SELECT data FROM products WHERE revision > ? AND (? OR deleted = 0) ORDER BY revision",
        )
        .bind(since)
        .bind(include_deleted)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(|r| decode(r.get("data"))).collect())
    }

    /// Admin search across name, brand, tags and barcodes.
    pub async fn search(&self, query: &str, show_deleted: bool) -> sqlx::Result<Vec<Product>> {
        let pattern = format!("%{}%", query.trim().to_lowercase());
        let rows = sqlx::query(
            "SELECT data FROM products
             WHERE deleted = ? AND (? = '%%' OR lower(name) LIKE ? OR lower(data) LIKE ?)
             ORDER BY name COLLATE NOCASE",
        )
        .bind(show_deleted)
        .bind(&pattern)
        .bind(&pattern)
        .bind(&pattern)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(|r| decode(r.get("data"))).collect())
    }

    pub async fn find_by_barcode(&self, code: &str) -> sqlx::Result<Vec<Product>> {
        let rows = sqlx::query(
            "SELECT p.data FROM products p, json_each(p.data, '$.barcodes') b
             WHERE b.value = ? AND p.deleted = 0
             ORDER BY p.name COLLATE NOCASE",
        )
        .bind(code)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(|r| decode(r.get("data"))).collect())
    }

    /// Last-write-wins upsert. Equal timestamps keep the stored version so repeated
    /// pushes are idempotent and do not bump the revision.
    pub async fn upsert(&self, incoming: Product) -> sqlx::Result<UpsertOutcome> {
        let mut tx = self.pool.begin().await?;
        let existing: Option<String> = sqlx::query_scalar("SELECT data FROM products WHERE id = ?")
            .bind(&incoming.id)
            .fetch_optional(&mut *tx)
            .await?;
        if let Some(existing) = existing.map(decode)
            && existing.updated_at >= incoming.updated_at
        {
            let revision = current_revision_tx(&mut tx).await?;
            tx.commit().await?;
            return Ok(UpsertOutcome {
                product: existing,
                applied: false,
                revision,
            });
        }
        let revision: i64 = sqlx::query_scalar(
            "UPDATE meta SET value = value + 1 WHERE key = 'revision' RETURNING value",
        )
        .fetch_one(&mut *tx)
        .await?;
        let data = serde_json::to_string(&incoming).expect("product serializes");
        sqlx::query(
            "INSERT INTO products (id, name, updated_at, deleted, revision, data)
             VALUES (?, ?, ?, ?, ?, ?)
             ON CONFLICT(id) DO UPDATE SET
               name = excluded.name, updated_at = excluded.updated_at,
               deleted = excluded.deleted, revision = excluded.revision, data = excluded.data",
        )
        .bind(&incoming.id)
        .bind(&incoming.name)
        .bind(incoming.updated_at)
        .bind(incoming.deleted)
        .bind(revision)
        .bind(&data)
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(UpsertOutcome {
            product: incoming,
            applied: true,
            revision,
        })
    }

    /// Apply a server-side edit (admin UI / delete endpoint): the new version always wins,
    /// so its timestamp is forced past the stored one even if clocks disagree.
    pub async fn save_authoritative(&self, mut product: Product) -> sqlx::Result<UpsertOutcome> {
        let stored = self.get(&product.id).await?;
        let floor = stored.map(|p| p.updated_at + 1).unwrap_or(0);
        product.updated_at = now_millis().max(floor);
        self.upsert(product).await
    }

    /// Tombstone a product. Returns `None` if it does not exist.
    pub async fn soft_delete(&self, id: &str) -> sqlx::Result<Option<UpsertOutcome>> {
        match self.get(id).await? {
            None => Ok(None),
            Some(mut p) => {
                p.deleted = true;
                Ok(Some(self.save_authoritative(p).await?))
            }
        }
    }

    pub async fn restore(&self, id: &str) -> sqlx::Result<Option<UpsertOutcome>> {
        match self.get(id).await? {
            None => Ok(None),
            Some(mut p) => {
                p.deleted = false;
                Ok(Some(self.save_authoritative(p).await?))
            }
        }
    }

    /// Permanently remove tombstones. Returns the number of rows removed.
    pub async fn purge_tombstones(&self) -> sqlx::Result<u64> {
        let res = sqlx::query("DELETE FROM products WHERE deleted = 1")
            .execute(&self.pool)
            .await?;
        Ok(res.rows_affected())
    }

    pub async fn all(&self, include_deleted: bool) -> sqlx::Result<Vec<Product>> {
        self.list_since(i64::MIN, include_deleted).await
    }

    pub async fn stats(&self) -> sqlx::Result<Stats> {
        let row = sqlx::query(
            "SELECT
               COALESCE(SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END), 0) AS products,
               COALESCE(SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END), 0) AS tombstones,
               COALESCE(SUM(CASE WHEN deleted = 0 AND json_extract(data, '$.favorite') THEN 1 ELSE 0 END), 0) AS favorites,
               COALESCE(SUM(CASE WHEN deleted = 0 THEN json_array_length(data, '$.barcodes') ELSE 0 END), 0) AS barcodes
             FROM products",
        )
        .fetch_one(&self.pool)
        .await?;
        let page_count: i64 = sqlx::query_scalar("PRAGMA page_count")
            .fetch_one(&self.pool)
            .await?;
        let page_size: i64 = sqlx::query_scalar("PRAGMA page_size")
            .fetch_one(&self.pool)
            .await?;
        Ok(Stats {
            products: row.get("products"),
            tombstones: row.get("tombstones"),
            favorites: row.get("favorites"),
            barcodes: row.get("barcodes"),
            revision: self.current_revision().await?,
            db_size_bytes: page_count * page_size,
        })
    }
}

async fn current_revision_tx(tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>) -> sqlx::Result<i64> {
    sqlx::query_scalar("SELECT value FROM meta WHERE key = 'revision'")
        .fetch_one(&mut **tx)
        .await
}

fn decode(data: String) -> Product {
    // Only validated products are ever written, so this cannot fail in practice.
    serde_json::from_str(&data).expect("stored product JSON is valid")
}

#[cfg(test)]
mod tests {
    use super::*;

    fn product(updated_at: i64) -> Product {
        let mut p: Product =
            serde_json::from_str(include_str!("../../api/examples/product.json")).unwrap();
        p.updated_at = updated_at;
        p
    }

    #[tokio::test]
    async fn last_write_wins() {
        let db = Db::connect("sqlite::memory:").await.unwrap();
        let first = db.upsert(product(100)).await.unwrap();
        assert!(first.applied);
        assert_eq!(first.revision, 1);

        // Older write is rejected and returns the stored version.
        let mut older = product(50);
        older.name = "Old".into();
        let out = db.upsert(older).await.unwrap();
        assert!(!out.applied);
        assert_eq!(out.product.name, "Chicken Pot Pie");
        assert_eq!(out.revision, 1);

        // Same timestamp is idempotent.
        assert!(!db.upsert(product(100)).await.unwrap().applied);

        // Newer write wins and bumps the revision.
        let mut newer = product(200);
        newer.name = "New".into();
        let out = db.upsert(newer).await.unwrap();
        assert!(out.applied);
        assert_eq!(out.revision, 2);
        assert_eq!(db.get(&out.product.id).await.unwrap().unwrap().name, "New");
    }

    #[tokio::test]
    async fn revisions_survive_purge() {
        let db = Db::connect("sqlite::memory:").await.unwrap();
        let p = product(1);
        db.upsert(p.clone()).await.unwrap();
        db.soft_delete(&p.id).await.unwrap().unwrap();
        assert_eq!(db.current_revision().await.unwrap(), 2);
        assert_eq!(db.purge_tombstones().await.unwrap(), 1);
        assert_eq!(db.current_revision().await.unwrap(), 2);
        let mut q = product(5);
        q.id = uuid::Uuid::new_v4().to_string();
        assert_eq!(db.upsert(q).await.unwrap().revision, 3);
    }

    #[tokio::test]
    async fn barcode_lookup_and_since() {
        let db = Db::connect("sqlite::memory:").await.unwrap();
        db.upsert(product(1)).await.unwrap();
        assert_eq!(db.find_by_barcode("012345678905").await.unwrap().len(), 1);
        assert!(db.find_by_barcode("999").await.unwrap().is_empty());
        assert_eq!(db.list_since(0, true).await.unwrap().len(), 1);
        assert!(db.list_since(1, true).await.unwrap().is_empty());
        let stats = db.stats().await.unwrap();
        assert_eq!((stats.products, stats.favorites, stats.barcodes), (1, 1, 1));
    }
}
