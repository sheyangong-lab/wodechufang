-- V8 后台管理员（SQLite 方言）
-- 种子: admin / admin123@sk （SHA-256 hex），生产必须改密

CREATE TABLE admin_users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_admin_username ON admin_users (username);

INSERT INTO admin_users (username, password_hash, created_at) VALUES
    ('admin', 'efe1706eac70b9f42472d8f9c14dd6fb264e7ba657fa356a64466326f082e092', '2026-08-29T00:00:00Z');
