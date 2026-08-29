-- V9 全功能账本 + 去 VIP（PostgreSQL 方言）
-- 1) 账本分类改为每厨房可自定义（首次访问懒加载默认分类，见 LedgerService）
-- 2) 食材支持照片
-- 3) VIP 功能下线：全功能免费，删除会员表

CREATE TABLE ledger_categories (
    id         BIGSERIAL PRIMARY KEY,
    kitchen_id BIGINT NOT NULL REFERENCES kitchens (id),
    type       VARCHAR(10) NOT NULL,
    name       VARCHAR(10) NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_ledger_cat_kitchen ON ledger_categories (kitchen_id);

ALTER TABLE fridge_items ADD COLUMN image_url TEXT NOT NULL DEFAULT '';

DROP TABLE IF EXISTS redeem_codes;
DROP TABLE IF EXISTS vip_plans;
