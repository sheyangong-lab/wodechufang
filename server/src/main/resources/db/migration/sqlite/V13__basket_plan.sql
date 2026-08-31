-- V13 菜篮 + 饮食计划（SQLite 方言）
-- 菜篮：下单用料与冰箱比对后的采购清单（checked=已买到）
-- 饮食计划：按日期/餐段挂菜谱或自定义菜单；餐段名每厨房可自定义（JSON）

CREATE TABLE basket_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    name       TEXT NOT NULL,
    quantity   TEXT NOT NULL DEFAULT '',
    checked    INTEGER NOT NULL DEFAULT 0,
    source     TEXT NOT NULL DEFAULT 'MANUAL',   -- AUTO 从下单生成 / MANUAL 手动
    created_at TEXT NOT NULL
);
CREATE INDEX idx_basket_kitchen ON basket_items (kitchen_id, checked);

CREATE TABLE plan_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id INTEGER NOT NULL REFERENCES kitchens (id),
    plan_date  TEXT NOT NULL,                    -- YYYY-MM-DD
    slot_index INTEGER NOT NULL,                 -- 餐段序号（对应 plan_config 里的数组下标）
    item_type  TEXT NOT NULL DEFAULT 'DISH',     -- DISH 厨房菜谱 / CUSTOM 自定义菜单
    dish_id    INTEGER,
    name       TEXT NOT NULL,
    image_url  TEXT NOT NULL DEFAULT '',
    remark     TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL
);
CREATE INDEX idx_plan_kitchen_date ON plan_items (kitchen_id, plan_date);

CREATE TABLE plan_config (
    kitchen_id INTEGER PRIMARY KEY REFERENCES kitchens (id),
    slots_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
