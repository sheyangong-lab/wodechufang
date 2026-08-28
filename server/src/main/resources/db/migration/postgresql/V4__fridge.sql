-- V4 冰箱（PostgreSQL 方言）

CREATE TABLE fridge_categories (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT      NOT NULL REFERENCES kitchens (id),
    name       VARCHAR(20) NOT NULL,
    sort       INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_fridge_cat_kitchen ON fridge_categories (kitchen_id);

CREATE TABLE fridge_items (
    id               BIGSERIAL PRIMARY KEY,
    kitchen_id       BIGINT      NOT NULL REFERENCES kitchens (id),
    category_id      BIGINT      REFERENCES fridge_categories (id),
    name             VARCHAR(30) NOT NULL,
    produced_date    VARCHAR(10),
    shelf_life_value INTEGER     NOT NULL DEFAULT 1,
    shelf_life_unit  VARCHAR(6)  NOT NULL DEFAULT 'DAY',
    quantity         VARCHAR(30) NOT NULL DEFAULT '',
    remark           VARCHAR(50) NOT NULL DEFAULT '',
    deleted          SMALLINT    NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_fridge_item_kitchen ON fridge_items (kitchen_id, deleted);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT      NOT NULL REFERENCES kitchens (id),
    type       VARCHAR(20) NOT NULL,
    title      VARCHAR(60) NOT NULL,
    content    VARCHAR(300) NOT NULL DEFAULT '',
    is_read    SMALLINT    NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_kitchen ON notifications (kitchen_id, is_read);
