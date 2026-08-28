-- V1 基础表：用户 / 厨房 / 厨房成员 / 菜谱分类（PostgreSQL 方言）
-- 注意：user 为 PG 保留字，表名使用 users；金额为「分」整数

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    avatar        TEXT,
    open_id       VARCHAR(64),
    points        INTEGER     NOT NULL DEFAULT 0,
    status        SMALLINT    NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_users_phone   ON users (phone);
CREATE UNIQUE INDEX uk_users_open_id ON users (open_id);

CREATE TABLE kitchens (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(50) NOT NULL,
    code           VARCHAR(64) NOT NULL,
    owner_id       BIGINT      NOT NULL REFERENCES users (id),
    level          INTEGER     NOT NULL DEFAULT 0,
    dish_quota     INTEGER     NOT NULL DEFAULT 50,
    category_quota INTEGER     NOT NULL DEFAULT 5,
    vip_expire_at  TIMESTAMPTZ,
    announcement   VARCHAR(200) NOT NULL DEFAULT '',
    status         SMALLINT    NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_kitchens_code ON kitchens (code);
CREATE INDEX idx_kitchens_owner      ON kitchens (owner_id);

CREATE TABLE kitchen_members (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT      NOT NULL REFERENCES kitchens (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    role       VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    remark     VARCHAR(50) NOT NULL DEFAULT '',
    joined_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_member_kitchen_user ON kitchen_members (kitchen_id, user_id);
CREATE INDEX idx_member_user               ON kitchen_members (user_id);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT      NOT NULL REFERENCES kitchens (id),
    name       VARCHAR(20) NOT NULL,
    sort       INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_categories_kitchen ON categories (kitchen_id);
