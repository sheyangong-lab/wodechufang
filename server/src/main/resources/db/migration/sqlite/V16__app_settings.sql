-- V16 运行时配置：后台可改的应用设置（AI识别/JWT等），DB 值覆盖 application.yml 默认
CREATE TABLE app_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

INSERT INTO app_settings (key, value, updated_at) VALUES
  ('glm.model', 'glm-5.3-flash', datetime('now')),
  ('glm.api-key', '', datetime('now')),
  ('jwt.ttl-hours', '72', datetime('now')),
  ('admin.allowed-cidrs', '192.168.0.0/16,10.0.0.0/8,172.16.0.0/12,127.0.0.1', datetime('now'));
