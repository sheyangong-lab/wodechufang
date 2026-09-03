-- V15 账本流水支持「总费用 + 分费用」
-- sub_items 存 JSON 数组文本：[{"name":"蔬菜","amountFen":3000},...]
-- 总费用 = amount_fen（由分费用求和，客户端写入前已求和，服务端校验一致）
ALTER TABLE ledger_entries ADD COLUMN sub_items TEXT;
