-- V18 冰箱分层：食材存放位置（上冷藏 / 下冷冻）
-- storage: FRIDGE=冷藏（默认，兼容存量数据） / FREEZER=冷冻
ALTER TABLE fridge_items ADD COLUMN storage TEXT NOT NULL DEFAULT 'FRIDGE';
