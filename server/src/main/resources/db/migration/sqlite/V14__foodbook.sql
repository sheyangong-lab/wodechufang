-- V14 食本（SQLite 方言）：每天一页手账，贴图自由摆放
-- x/y/width 为页面宽度/高度的百分比（0-100），保存贴纸位置

CREATE TABLE foodbook_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    page_date  TEXT NOT NULL,
    image_url  TEXT NOT NULL,
    x          REAL NOT NULL DEFAULT 10,
    y          REAL NOT NULL DEFAULT 10,
    width      REAL NOT NULL DEFAULT 40,
    z_index    INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_foodbook_kitchen_date ON foodbook_items (kitchen_id, page_date);
