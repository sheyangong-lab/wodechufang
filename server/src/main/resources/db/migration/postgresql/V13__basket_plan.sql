-- V13 菜篮 + 饮食计划（PostgreSQL 方言）

CREATE TABLE basket_items (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT NOT NULL REFERENCES kitchens (id),
    name       VARCHAR(30) NOT NULL,
    quantity   VARCHAR(30) NOT NULL DEFAULT '',
    checked    INTEGER NOT NULL DEFAULT 0,
    source     VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    created_at TEXT NOT NULL
);
CREATE INDEX idx_basket_kitchen ON basket_items (kitchen_id, checked);

CREATE TABLE plan_items (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT NOT NULL REFERENCES kitchens (id),
    plan_date  VARCHAR(10) NOT NULL,
    slot_index INTEGER NOT NULL,
    item_type  VARCHAR(10) NOT NULL DEFAULT 'DISH',
    dish_id    BIGINT,
    name       VARCHAR(50) NOT NULL,
    image_url  TEXT NOT NULL DEFAULT '',
    remark     VARCHAR(100) NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);
CREATE INDEX idx_plan_kitchen_date ON plan_items (kitchen_id, plan_date);

CREATE TABLE plan_config (
    kitchen_id BIGINT PRIMARY KEY REFERENCES kitchens (id),
    slots_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
