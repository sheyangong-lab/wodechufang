-- V6 账本（PostgreSQL 方言）
-- type: INCOME / EXPENSE / REFUND；金额一律「分」正数

CREATE TABLE ledger_entries (
    id          BIGSERIAL PRIMARY KEY,
    kitchen_id  BIGINT      NOT NULL REFERENCES kitchens (id),
    type        VARCHAR(10) NOT NULL,
    source      VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    order_id    BIGINT,
    category    VARCHAR(20) NOT NULL DEFAULT '',
    amount_fen  BIGINT      NOT NULL,
    dine_date   VARCHAR(10) NOT NULL,
    remark      VARCHAR(100) NOT NULL DEFAULT '',
    created_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_ledger_order ON ledger_entries (order_id, type) WHERE order_id IS NOT NULL;
CREATE INDEX idx_ledger_kitchen ON ledger_entries (kitchen_id, dine_date);
