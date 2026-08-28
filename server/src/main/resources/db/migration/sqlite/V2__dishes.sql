-- V2 菜谱表（SQLite 方言）
-- 价格一律「分」整数；specs 为 JSON 文本 [{"name":"小份","priceFen":800}]

CREATE TABLE dishes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id      INTEGER NOT NULL REFERENCES kitchens (id),
    category_id     INTEGER REFERENCES categories (id),
    name            TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    image_url       TEXT,
    price_fen       INTEGER NOT NULL DEFAULT 0,
    specs_json      TEXT,
    recommend_stars INTEGER NOT NULL DEFAULT 0,
    materials       TEXT NOT NULL DEFAULT '',
    steps           TEXT NOT NULL DEFAULT '',
    servings        TEXT NOT NULL DEFAULT '',
    cook_minutes    INTEGER,
    difficulty      TEXT NOT NULL DEFAULT '',
    calories        TEXT NOT NULL DEFAULT '',
    share_square    INTEGER NOT NULL DEFAULT 0,
    status          INTEGER NOT NULL DEFAULT 1,   -- 1 上架 0 下架
    deleted         INTEGER NOT NULL DEFAULT 0,   -- 1 在回收站
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);
CREATE INDEX idx_dishes_kitchen  ON dishes (kitchen_id, deleted, status);
CREATE INDEX idx_dishes_category ON dishes (category_id);
