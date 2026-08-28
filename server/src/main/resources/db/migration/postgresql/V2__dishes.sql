-- V2 菜谱表（PostgreSQL 方言）

CREATE TABLE dishes (
    id              BIGSERIAL PRIMARY KEY,
    kitchen_id      BIGINT      NOT NULL REFERENCES kitchens (id),
    category_id     BIGINT      REFERENCES categories (id),
    name            VARCHAR(60)  NOT NULL,
    description     VARCHAR(500) NOT NULL DEFAULT '',
    image_url       VARCHAR(300),
    price_fen       BIGINT      NOT NULL DEFAULT 0,
    specs_json      TEXT,
    recommend_stars INTEGER     NOT NULL DEFAULT 0,
    materials       TEXT        NOT NULL DEFAULT '',
    steps           TEXT        NOT NULL DEFAULT '',
    servings        VARCHAR(50) NOT NULL DEFAULT '',
    cook_minutes    INTEGER,
    difficulty      VARCHAR(20) NOT NULL DEFAULT '',
    calories        VARCHAR(50) NOT NULL DEFAULT '',
    share_square    SMALLINT    NOT NULL DEFAULT 0,
    status          SMALLINT    NOT NULL DEFAULT 1,
    deleted         SMALLINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_dishes_kitchen  ON dishes (kitchen_id, deleted, status);
CREATE INDEX idx_dishes_category ON dishes (category_id);
