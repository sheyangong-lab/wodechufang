-- V7 会员体系（SQLite 方言）：套餐 / 兑换码
-- 兑换码状态: UNUSED 未使用 / USED 已核销 / DISABLED 已作废
-- 种子: 3 档套餐 + 1 个测试兑换码（月卡，码 KITCHEN-TEST-MONTH，仅开发用，生产由后台批量生成）

CREATE TABLE vip_plans (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    duration_days INTEGER NOT NULL,
    price_fen     INTEGER NOT NULL,
    sort          INTEGER NOT NULL DEFAULT 0,
    enabled       INTEGER NOT NULL DEFAULT 1,
    created_at    TEXT NOT NULL
);

CREATE TABLE redeem_codes (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    code             TEXT NOT NULL,
    plan_id          INTEGER NOT NULL REFERENCES vip_plans (id),
    status           TEXT NOT NULL DEFAULT 'UNUSED',
    batch            TEXT NOT NULL DEFAULT '',
    used_by          INTEGER,
    used_kitchen_id  INTEGER,
    used_at          TEXT,
    created_at       TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_redeem_code ON redeem_codes (code);
CREATE INDEX idx_redeem_status ON redeem_codes (status);

INSERT INTO vip_plans (name, duration_days, price_fen, sort, created_at) VALUES
    ('月卡', 30, 1200, 1, '2026-08-29T00:00:00Z'),
    ('季卡', 90, 3000, 2, '2026-08-29T00:00:00Z'),
    ('年卡', 365, 9800, 3, '2026-08-29T00:00:00Z');

INSERT INTO redeem_codes (code, plan_id, batch, created_at) VALUES
    ('KITCHEN-TEST-MONTH', 1, 'TEST', '2026-08-29T00:00:00Z');
