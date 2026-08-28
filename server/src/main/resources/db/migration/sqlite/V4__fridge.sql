-- V4 冰箱（SQLite 方言）：食材类别 / 食材 / 站内通知
-- 保质期 = 数值 + 单位(DAY/WEEK/MONTH/YEAR)；到期日 = 生产日期(缺省取入库日) + 保质期
-- 状态由服务端计算：剩余<0 已过期；0-3天 快过期；>3 新鲜

CREATE TABLE fridge_categories (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    name       TEXT NOT NULL,
    sort       INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_fridge_cat_kitchen ON fridge_categories (kitchen_id);

CREATE TABLE fridge_items (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id       INTEGER NOT NULL REFERENCES kitchens (id),
    category_id      INTEGER REFERENCES fridge_categories (id),
    name             TEXT NOT NULL,
    produced_date    TEXT,
    shelf_life_value INTEGER NOT NULL DEFAULT 1,
    shelf_life_unit  TEXT NOT NULL DEFAULT 'DAY',
    quantity         TEXT NOT NULL DEFAULT '',
    remark           TEXT NOT NULL DEFAULT '',
    deleted          INTEGER NOT NULL DEFAULT 0,   -- 清仓/删除=软删
    created_at       TEXT NOT NULL
);
CREATE INDEX idx_fridge_item_kitchen ON fridge_items (kitchen_id, deleted);

CREATE TABLE notifications (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL REFERENCES kitchens (id),
    type        TEXT NOT NULL,        -- FRIDGE_EXPIRY / ORDER / SYSTEM
    title       TEXT NOT NULL,
    content     TEXT NOT NULL DEFAULT '',
    is_read     INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL
);
CREATE INDEX idx_notifications_kitchen ON notifications (kitchen_id, is_read);
