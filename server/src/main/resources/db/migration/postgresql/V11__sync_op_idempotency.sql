-- V11 设备直连同步：操作幂等记录（SQLite 方言）
-- 两台设备离线互同步后各自回写服务器，同一条操作(X-Op-Id)可能被重复提交，
-- 以此表去重并回放缓存的响应。

CREATE TABLE sync_ops (
    op_id         TEXT PRIMARY KEY,
    kitchen_id    INTEGER,
    status        INTEGER NOT NULL,
    response_body TEXT NOT NULL,
    created_at    TEXT NOT NULL
);
