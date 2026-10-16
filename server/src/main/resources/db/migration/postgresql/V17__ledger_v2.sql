-- V17 账本大版本：存钱 + 多币种 + 图片/支付方式
-- 1) 流水加列：图片、支付方式、原币种与原金额（外币自动按汇率折成人民币 amount_fen 入库）
ALTER TABLE ledger_entries ADD COLUMN image_url TEXT;
ALTER TABLE ledger_entries ADD COLUMN payment_method TEXT;
ALTER TABLE ledger_entries ADD COLUMN currency TEXT DEFAULT 'CNY';
ALTER TABLE ledger_entries ADD COLUMN orig_amount_fen INTEGER;

-- 2) 存钱罐（各币种现金额 + 名称）
CREATE TABLE savings (
    id          INTEGER PRIMARY KEY BIGSERIAL,
    kitchen_id  INTEGER NOT NULL,
    name        TEXT NOT NULL,
    currency    TEXT NOT NULL DEFAULT 'CNY',  -- CNY / HKD / USD
    -- 金额一律「分」正数；类型 DEPOSIT=存入 WITHDRAW=取出（存钱罐余额=存入-取出）
    amount_fen  INTEGER NOT NULL,
    kind        TEXT NOT NULL DEFAULT 'DEPOSIT',
    note        TEXT DEFAULT '',
    created_by  INTEGER,
    created_at  TEXT NOT NULL,
    deleted     INTEGER DEFAULT 0
);

-- 3) 支付方式（厨房维度自定义，记账时选择）
CREATE TABLE payment_methods (
    id          INTEGER PRIMARY KEY BIGSERIAL,
    kitchen_id  INTEGER NOT NULL,
    name        TEXT NOT NULL,
    sort        INTEGER DEFAULT 0,
    UNIQUE (kitchen_id, name)
);

-- 默认支付方式（对号已有厨房懒加载：服务端首查时补插，这里先不动数据）
