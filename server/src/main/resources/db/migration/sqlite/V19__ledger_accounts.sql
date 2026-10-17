-- V19 账本v2：三钱包/预算/周期账/存款目标（设计文档 §6）
CREATE TABLE accounts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id      INTEGER NOT NULL,
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,              -- DISPOSABLE | SAVING | CREDIT
    initial_fen     INTEGER NOT NULL DEFAULT 0,
    credit_limit_fen INTEGER,                    -- 仅 CREDIT
    sort            INTEGER DEFAULT 0,
    deleted         INTEGER DEFAULT 0,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_accounts_kitchen ON accounts (kitchen_id, deleted);

CREATE TABLE budgets (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    month       TEXT NOT NULL,                  -- YYYY-MM
    category    TEXT,                            -- NULL = 总预算
    amount_fen  INTEGER NOT NULL,
    created_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_budget ON budgets (kitchen_id, month, IFNULL(category,'~总~'));

CREATE TABLE recurring_bills (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    type        TEXT NOT NULL,                  -- EXPENSE | INCOME
    category    TEXT NOT NULL,
    amount_fen  INTEGER NOT NULL,
    account_id  INTEGER,
    cycle       TEXT NOT NULL,                  -- MONTHLY | YEARLY
    next_date   TEXT NOT NULL,                  -- YYYY-MM-DD
    remark      TEXT DEFAULT '',
    last_entry_id INTEGER,
    enabled     INTEGER DEFAULT 1,
    deleted     INTEGER DEFAULT 0,
    created_at  TEXT NOT NULL
);

CREATE TABLE saving_goals (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    name        TEXT NOT NULL,
    target_fen  INTEGER NOT NULL,
    deadline    TEXT,
    deleted     INTEGER DEFAULT 0,
    created_at  TEXT NOT NULL
);

ALTER TABLE ledger_entries ADD COLUMN account_id INTEGER;
ALTER TABLE ledger_entries ADD COLUMN transfer_dir TEXT;   -- 'in'|'out' 仅 TRANSFER

-- 数据迁移1：每个厨房种子四个默认账户
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '现金', 'DISPOSABLE', 0, 1, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '微信', 'DISPOSABLE', 0, 2, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '支付宝', 'DISPOSABLE', 0, 3, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '存款', 'SAVING', 0, 10, datetime('now') FROM kitchens;

-- 数据迁移2：旧存钱罐存取记录折算人民币 → 存款账户的 TRANSFER 流水（期初保持0，余额由流水推导）
--   HKD×0.92、USD×7.24 折算（与 FxService 默认一致）；CNY×1
INSERT INTO ledger_entries (kitchen_id, type, source, category, amount_fen, dine_date, remark,
                            account_id, transfer_dir, created_by, created_at)
SELECT s.kitchen_id, 'TRANSFER', 'MANUAL', '存钱迁移',
       CAST(ROUND(s.amount_fen * CASE s.currency WHEN 'HKD' THEN 0.92 WHEN 'USD' THEN 7.24 ELSE 1 END) AS INTEGER),
       substr(s.created_at, 1, 10),
       '旧存钱罐[' || s.name || ']迁移',
       (SELECT a.id FROM accounts a WHERE a.kitchen_id = s.kitchen_id AND a.type = 'SAVING' AND a.deleted = 0 LIMIT 1),
       CASE s.kind WHEN 'DEPOSIT' THEN 'in' ELSE 'out' END,
       s.created_by, s.created_at
FROM savings s WHERE s.deleted = 0;

-- 数据迁移3：历史流水 account_id 保持 NULL（前端显示"未指定"，可批量补）
