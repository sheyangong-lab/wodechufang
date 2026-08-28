-- V3 订单表（PostgreSQL 方言）

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    kitchen_id  BIGINT      NOT NULL REFERENCES kitchens (id),
    buyer_id    BIGINT      NOT NULL REFERENCES users (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    remark      VARCHAR(200) NOT NULL DEFAULT '',
    total_fen   BIGINT      NOT NULL DEFAULT 0,
    dine_date   VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_orders_kitchen ON orders (kitchen_id, dine_date, status);
CREATE INDEX idx_orders_buyer   ON orders (buyer_id, status);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT      NOT NULL REFERENCES orders (id),
    dish_id    BIGINT,
    dish_name  VARCHAR(60) NOT NULL,
    spec_name  VARCHAR(30),
    price_fen  BIGINT      NOT NULL,
    quantity   INTEGER     NOT NULL
);
CREATE INDEX idx_items_order ON order_items (order_id);
