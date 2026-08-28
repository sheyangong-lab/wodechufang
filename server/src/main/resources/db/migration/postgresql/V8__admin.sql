-- V8 后台管理员（PostgreSQL 方言）

CREATE TABLE admin_users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uk_admin_username ON admin_users (username);

INSERT INTO admin_users (username, password_hash, created_at) VALUES
    ('admin', '5f4ccfee8b8a5f6e8a9e6a4d17b6ca2b1a9e3e17b6ca2b1a9e3e17b6d0f4e2a9c8b7d6e5f4a3b2', '2026-08-29T00:00:00Z');
