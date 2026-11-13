-- Products are stored as canonical JSON (see docs/DESIGN.md §5) alongside the
-- columns the server needs for querying and last-write-wins sync.
CREATE TABLE IF NOT EXISTS products (
    id          TEXT PRIMARY KEY NOT NULL,
    name        TEXT NOT NULL,
    updated_at  INTEGER NOT NULL,
    deleted     INTEGER NOT NULL DEFAULT 0,
    revision    INTEGER NOT NULL,
    data        TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_products_revision ON products (revision);
CREATE INDEX IF NOT EXISTS idx_products_name ON products (name COLLATE NOCASE);

-- Monotonic revision counter. Kept separately so purging tombstones never causes
-- revision numbers to be reused (which would make clients miss changes).
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY NOT NULL,
    value INTEGER NOT NULL
);

INSERT OR IGNORE INTO meta (key, value) VALUES ('revision', 0);
