-- V3 订单表（SQLite 方言）
-- 状态机: PENDING 未完成 → COMPLETED 已完成；PENDING/COMPLETED? → REFUND_REQUESTED 申请退单 → REFUNDED 已退单
-- 金额一律「分」；订单项存菜品快照（名称/规格/单价），价格以服务端当前价为准

CREATE TABLE orders (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL REFERENCES kitchens (id),
    buyer_id    INTEGER NOT NULL REFERENCES users (id),
    status      TEXT NOT NULL DEFAULT 'PENDING', -- PENDING/COMPLETED/REFUND_REQUESTED/REFUNDED
    remark      TEXT NOT NULL DEFAULT '',
    total_fen   INTEGER NOT NULL DEFAULT 0,
    dine_date   TEXT NOT NULL,                   -- YYYY-MM-DD，按日期筛选用
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);
CREATE INDEX idx_orders_kitchen ON orders (kitchen_id, dine_date, status);
CREATE INDEX idx_orders_buyer   ON orders (buyer_id, status);

CREATE TABLE order_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id   INTEGER NOT NULL REFERENCES orders (id),
    dish_id    INTEGER,
    dish_name  TEXT NOT NULL,
    spec_name  TEXT,
    price_fen  INTEGER NOT NULL,
    quantity   INTEGER NOT NULL
);
CREATE INDEX idx_items_order ON order_items (order_id);
