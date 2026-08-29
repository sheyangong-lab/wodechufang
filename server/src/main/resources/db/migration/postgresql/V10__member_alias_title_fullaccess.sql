-- V10 成员账号体系升级（PostgreSQL 方言）
-- 1) 成员支持自定义名字(alias)/职称(title)；主账号可授予成员全权限(full_access)
-- 2) 积分逻辑下线（users.points 列保留但代码不再读写）

ALTER TABLE kitchen_members ADD COLUMN alias VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE kitchen_members ADD COLUMN title VARCHAR(10) NOT NULL DEFAULT '';
ALTER TABLE kitchen_members ADD COLUMN full_access INTEGER NOT NULL DEFAULT 0;
