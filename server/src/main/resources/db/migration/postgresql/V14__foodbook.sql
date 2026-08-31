-- V14 食本（PostgreSQL 方言）

CREATE TABLE foodbook_items (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT NOT NULL REFERENCES kitchens (id),
    page_date  VARCHAR(10) NOT NULL,
    image_url  TEXT NOT NULL,
    x          DOUBLE PRECISION NOT NULL DEFAULT 10,
    y          DOUBLE PRECISION NOT NULL DEFAULT 10,
    width      DOUBLE PRECISION NOT NULL DEFAULT 40,
    z_index    INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_foodbook_kitchen_date ON foodbook_items (kitchen_id, page_date);
