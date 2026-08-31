-- V12 修复 sync_ops 缺 id 列（V11 建表遗漏；表为纯缓存，直接重建）

DROP TABLE IF EXISTS sync_ops;
CREATE TABLE sync_ops (
    id            BIGSERIAL PRIMARY KEY,
    op_id         TEXT NOT NULL UNIQUE,
    kitchen_id    BIGINT,
    status        INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at    TEXT NOT NULL
);
