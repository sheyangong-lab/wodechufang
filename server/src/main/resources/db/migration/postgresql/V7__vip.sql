-- V7 会员体系（PostgreSQL 方言）

CREATE TABLE vip_plans (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(20) NOT NULL,
    duration_days INTEGER     NOT NULL,
    price_fen     BIGINT      NOT NULL,
    sort          INTEGER     NOT NULL DEFAULT 0,
    enabled       SMALLINT    NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE redeem_codes (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(30) NOT NULL,
    plan_id          BIGINT      NOT NULL REFERENCES vip_plans (id),
    status           VARCHAR(10) NOT NULL DEFAULT 'UNUSED',
    batch            VARCHAR(20) NOT NULL DEFAULT '',
    used_by          BIGINT,
    used_kitchen_id  BIGINT,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uk_redeem_code ON redeem_codes (code);
CREATE INDEX idx_redeem_status ON redeem_codes (status);

INSERT INTO vip_plans (name, duration_days, price_fen, sort, created_at) VALUES
    ('月卡', 30, 1200, 1, '2026-08-29T00:00:00Z'),
    ('季卡', 90, 3000, 2, '2026-08-29T00:00:00Z'),
    ('年卡', 365, 9800, 3, '2026-08-29T00:00:00Z');

INSERT INTO redeem_codes (code, plan_id, batch, created_at) VALUES
    ('KITCHEN-TEST-MONTH', 1, 'TEST', '2026-08-29T00:00:00Z');
