-- V1 基础表：用户 / 厨房 / 厨房成员 / 菜谱分类（SQLite 方言，与 postgresql/V1 保持字段一致）
-- 金额字段一律为「分」（INTEGER），时间一律 ISO-8601 文本（UTC）

CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    phone         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    nickname      TEXT NOT NULL,
    avatar        TEXT,
    open_id       TEXT,
    points        INTEGER NOT NULL DEFAULT 0,
    status        INTEGER NOT NULL DEFAULT 1, -- 1 正常 0 封禁
    created_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_users_phone   ON users (phone);
CREATE UNIQUE INDEX uk_users_open_id ON users (open_id);

CREATE TABLE kitchens (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT NOT NULL,
    code           TEXT NOT NULL,
    owner_id       INTEGER NOT NULL REFERENCES users (id),
    level          INTEGER NOT NULL DEFAULT 0,
    dish_quota     INTEGER NOT NULL DEFAULT 50,  -- 免费档 50，会员档 500（后台可调）
    category_quota INTEGER NOT NULL DEFAULT 5,   -- 免费档 5，会员档 50
    vip_expire_at  TEXT,                         -- 厨房会员到期时间，NULL=未开通
    announcement   TEXT NOT NULL DEFAULT '',
    status         INTEGER NOT NULL DEFAULT 1,   -- 1 正常 0 封禁
    created_at     TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_kitchens_code   ON kitchens (code);
CREATE INDEX idx_kitchens_owner        ON kitchens (owner_id);

CREATE TABLE kitchen_members (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    user_id    INTEGER NOT NULL REFERENCES users (id),
    role       TEXT NOT NULL DEFAULT 'MEMBER',   -- OWNER / MEMBER / CUSTOMER
    remark     TEXT NOT NULL DEFAULT '',
    joined_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_member_kitchen_user ON kitchen_members (kitchen_id, user_id);
CREATE INDEX idx_member_user               ON kitchen_members (user_id);

CREATE TABLE categories (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    name       TEXT NOT NULL,
    sort       INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_categories_kitchen ON categories (kitchen_id);
