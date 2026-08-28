-- V5 账本（SQLite 方言）
-- type: INCOME 收入 / EXPENSE 支出 / REFUND 退款冲销
-- source: ORDER 订单自动 / MANUAL 手动记账
-- 金额一律「分」正数；结余 = INCOME - REFUND - EXPENSE
-- 唯一约束 (order_id, type) 防同一订单重复入账/重复冲销（手动单 order_id 为 NULL 不受约束）

CREATE TABLE ledger_entries (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL REFERENCES kitchens (id),
    type        TEXT NOT NULL,
    source      TEXT NOT NULL DEFAULT 'MANUAL',
    order_id    INTEGER,
    category    TEXT NOT NULL DEFAULT '',
    amount_fen  INTEGER NOT NULL,
    dine_date   TEXT NOT NULL,
    remark      TEXT NOT NULL DEFAULT '',
    created_by  INTEGER,
    created_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_ledger_order ON ledger_entries (order_id, type) WHERE order_id IS NOT NULL;
CREATE INDEX idx_ledger_kitchen ON ledger_entries (kitchen_id, dine_date);
