# 家庭厨房 v2 — P2（账本 v2）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 账本升级为三钱包体系（可支配/存款/信用卡）：流水挂账户、TRANSFER 存钱取回还款、预算管理、周期账单自动入账、存款目标、账本首页 v8 改版，删除多币种与旧存钱罐（数据全量迁移兼容）。

**Architecture:** 服务端新增 `module/ledger/account/` 子包（Account/Budget/RecurringBill/SavingGoal 四实体 + AccountService/LedgerBudgetService/RecurringBillJob），V19 一个迁移同时建四表、加两列、做数据迁移（默认账户种子 + 旧 savings 折算人民币生成 TRANSFER 流水）。余额全部由流水推导（期初+流入−流出），不改写历史。前端账本首页换 v8 布局，新增账户/预算/周期三个管理页。

**Tech Stack:** 同 P1（Spring Boot 3.3.5 / uni-app Vue3 / smoke-test.sh + curl + build:h5 + gradle）。

**设计文档:** `docs/superpowers/specs/2026-10-17-family-kitchen-v2-design.md` §3（账本 v2）+ §6（数据模型）。

## Global Constraints

- 分支：`feature/v2-family-kitchen`（继续在 P1 之上，BASE=5ceea19）。
- 后端编译/测试/前端构建/APK 命令与坑同 P1 计划（mvn 全路径、JDK17 出包、`DONE  Build complete` 两空格、pgrep 括号正则、后端启动约 15 秒轮询 health）。
- 金额一律「分」（fen, long）；汇率常量：HKD=0.92、USD=7.24（与 FxService 一致）。
- 流水类型新增 `TRANSFER`；**TRANSFER 不计入收支统计**（summary 的 income/expense/balance 口径不变）。
- 账户余额公式：`balance = initial_fen + Σ(INCOME + TRANSFER_in) − Σ(EXPENSE + TRANSFER_out)`（按 account_id 聚合；REFUND 视同 INCOME 冲减方向相反计入 +refund）。信用卡账户 balance 为负=欠款；剩余额度 = credit_limit_fen + balance。
- 旧 APK（≤1.5.1）兼容：`GET /savings`、`GET /savings/summary`、`GET /fx` 保留为空数据桩（200），`POST/DELETE /savings` 返回 400「请升级 App 到 1.6.0+」。
- 多币种：表字段保留不再读取；所有新接口输入输出统一人民币。
- 每个任务结束跑 `bash scripts/smoke-test.sh` 不允许回归（当前基线 58 项）。

---

### Task 1: V19 迁移 + Account 实体体系 + 账户 API

**Files:**
- Create: `server/src/main/resources/db/migration/sqlite/V19__ledger_accounts.sql`
- Create: `server/src/main/resources/db/migration/postgresql/V19__ledger_accounts.sql`
- Create: `server/src/main/java/com/sharedkitchen/module/ledger/account/Account.java`
- Create: `server/src/main/java/com/sharedkitchen/module/ledger/account/AccountRepository.java`
- Create: `server/src/main/java/com/sharedkitchen/module/ledger/account/AccountService.java`
- Create: `server/src/main/java/com/sharedkitchen/module/ledger/account/AccountController.java`
- Modify: `scripts/smoke-test.sh`（账本段后加账户断言）

**Interfaces:**
- Produces（后续任务与前端依赖）:
  - `Account` 实体：`id/kitchenId/name/type(DISPOSABLE|SAVING|CREDIT)/initialFen/creditLimitFen/sort/deleted/createdAt`
  - `AccountService.AccountView(Long id, String name, String type, Long initialFen, Long creditLimitFen, Integer sort, Long balanceFen)`
  - REST：`GET /api/kitchens/{id}/ledger/accounts`（列表含实时余额）；`POST /accounts`（name,type,initialFen?,creditLimitFen?）；`PUT /accounts/{aid}`（name/sort/initialFen/creditLimitFen）；`DELETE /accounts/{aid}`（软删，要求该账户无流水否则 400）
  - `GET /api/kitchens/{id}/ledger/assets` → `{"disposableFen":..,"savingFen":..,"creditDebtFen":..,"creditLimitFen":..,"creditRemainFen":..,"netAssetFen":..}`（负债为正数；净资产=disposable+saving−creditDebt）

- [ ] **Step 1: 冒烟断言先行（应失败）**

`scripts/smoke-test.sh` 账本段（`check "Excel导出3 Sheet有效"` 后）插入：

```bash
# 4.6 账户体系(P2)
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/accounts -H "$J" -H "$HA" -d '{"name":"零钱通","type":"SAVING","initialFen":50000}')
check "建存款账户" "$R" '"name":"零钱通"'
AID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
R=$(curl -s $BASE/api/kitchens/$KID/ledger/accounts -H "$HA")
check "账户列表含默认现金" "$R" '"name":"现金"'
check "账户余额=期初" "$R" '"balanceFen":50000'
R=$(curl -s $BASE/api/kitchens/$KID/ledger/assets -H "$HA")
check "资产总览含净资产" "$R" '"netAssetFen"'
```

- [ ] **Step 2: 跑冒烟确认失败**

Run: `bash scripts/smoke-test.sh 2>&1 | grep -E "账户|资产总览|结果"`
Expected: 4 条 ✗，总 FAIL。

- [ ] **Step 3: 写 V19 迁移（SQLite 版）**

`V19__ledger_accounts.sql`：

```sql
-- V19 账本v2：三钱包/预算/周期账/存款目标（设计文档 §6）
CREATE TABLE accounts (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id      INTEGER NOT NULL,
    name            TEXT NOT NULL,
    type            TEXT NOT NULL,              -- DISPOSABLE | SAVING | CREDIT
    initial_fen     INTEGER NOT NULL DEFAULT 0,
    credit_limit_fen INTEGER,                    -- 仅 CREDIT
    sort            INTEGER DEFAULT 0,
    deleted         INTEGER DEFAULT 0,
    created_at      TEXT NOT NULL
);
CREATE INDEX idx_accounts_kitchen ON accounts (kitchen_id, deleted);

CREATE TABLE budgets (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    month       TEXT NOT NULL,                  -- YYYY-MM
    category    TEXT,                            -- NULL = 总预算
    amount_fen  INTEGER NOT NULL,
    created_at  TEXT NOT NULL
);
CREATE UNIQUE INDEX uk_budget ON budgets (kitchen_id, month, IFNULL(category,'~总~'));

CREATE TABLE recurring_bills (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    type        TEXT NOT NULL,                  -- EXPENSE | INCOME
    category    TEXT NOT NULL,
    amount_fen  INTEGER NOT NULL,
    account_id  INTEGER,
    cycle       TEXT NOT NULL,                  -- MONTHLY | YEARLY
    next_date   TEXT NOT NULL,                  -- YYYY-MM-DD
    remark      TEXT DEFAULT '',
    last_entry_id INTEGER,
    enabled     INTEGER DEFAULT 1,
    deleted     INTEGER DEFAULT 0,
    created_at  TEXT NOT NULL
);

CREATE TABLE saving_goals (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    kitchen_id  INTEGER NOT NULL,
    name        TEXT NOT NULL,
    target_fen  INTEGER NOT NULL,
    deadline    TEXT,
    deleted     INTEGER DEFAULT 0,
    created_at  TEXT NOT NULL
);

ALTER TABLE ledger_entries ADD COLUMN account_id INTEGER;
ALTER TABLE ledger_entries ADD COLUMN transfer_dir TEXT;   -- 'in'|'out' 仅 TRANSFER

-- 数据迁移1：每个厨房种子四个默认账户
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '现金', 'DISPOSABLE', 0, 1, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '微信', 'DISPOSABLE', 0, 2, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '支付宝', 'DISPOSABLE', 0, 3, datetime('now') FROM kitchens;
INSERT INTO accounts (kitchen_id, name, type, initial_fen, sort, created_at)
SELECT id, '存款', 'SAVING', 0, 10, datetime('now') FROM kitchens;

-- 数据迁移2：旧存钱罐存取记录折算人民币 → 存款账户的 TRANSFER 流水（期初保持0，余额由流水推导）
--   HKD×0.92、USD×7.24 折算（与 FxService 默认一致）；CNY×1
INSERT INTO ledger_entries (kitchen_id, type, source, category, amount_fen, dine_date, remark,
                            account_id, transfer_dir, created_by, created_at)
SELECT s.kitchen_id, 'TRANSFER', 'MANUAL', '存钱迁移',
       CAST(ROUND(s.amount_fen * CASE s.currency WHEN 'HKD' THEN 0.92 WHEN 'USD' THEN 7.24 ELSE 1 END) AS INTEGER),
       substr(s.created_at, 1, 10),
       '旧存钱罐[' || s.name || ']迁移',
       (SELECT a.id FROM accounts a WHERE a.kitchen_id = s.kitchen_id AND a.type = 'SAVING' AND a.deleted = 0 LIMIT 1),
       CASE s.kind WHEN 'DEPOSIT' THEN 'in' ELSE 'out' END,
       s.created_by, s.created_at
FROM savings s WHERE s.deleted = 0;

-- 数据迁移3：历史流水 account_id 保持 NULL（前端显示"未指定"，可批量补）
```

- [ ] **Step 4: PostgreSQL 版**

同文件名建 `postgresql/V19__ledger_accounts.sql`，差异：`INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY`、`datetime('now')`→`to_char(now(),'YYYY-MM-DD"T"HH24:MI:SSZ')`（或 `now()::text`）、`IFNULL`→`COALESCE`、`substr(x,1,10)`→`substring(x from 1 for 10)`、字符串拼接 `||` 相同。

- [ ] **Step 5: 实体与仓储**

`account/Account.java`（字段对应表，getter/setter 风格抄 `LedgerEntry.java`：公共常量 `TYPE_DISPOSABLE/TYPE_SAVING/TYPE_CREDIT`）。`AccountRepository extends JpaRepository<Account, Long>`：

```java
List<Account> findByKitchenIdAndDeletedOrderBySortAscIdAsc(Long kitchenId, Integer deleted);
Optional<Account> findByIdAndDeleted(Long id, Integer deleted);
```

- [ ] **Step 6: AccountService（含余额推导与资产总览）**

核心方法（余额聚合一条 JPQL：

```java
@Query("select e.accountId, sum(case when e.type in ('INCOME','REFUND') or (e.type='TRANSFER' and e.transferDir='in') then e.amountFen when e.type='TRANSFER' then 0 else -e.amountFen end) from LedgerEntry e where e.kitchenId=:kid and e.accountId is not null group by e.accountId")
```
——注意 EXPENSE 记负、TRANSFER 行本身带方向、INCOME/REFUND 记正；此查询放在 `LedgerEntryRepository`）：

```java
public List<AccountView> list(Long userId, Long kitchenId)          // requireMember 后聚合余额
public AccountView create(Long userId, Long kitchenId, String name, String type, Long initialFen, Long creditLimitFen)
public AccountView update(Long userId, Long kitchenId, Long aid, ...) // 名称/排序/期初/额度
public void delete(Long userId, Long kitchenId, Long aid)           // 有流水则 400
public AssetsView assets(Long userId, Long kitchenId)               // 六字段总览
public record AccountView(Long id, String name, String type, Long initialFen, Long creditLimitFen, Integer sort, Long balanceFen) {}
public record AssetsView(Long disposableFen, Long savingFen, Long creditDebtFen, Long creditLimitFen, Long creditRemainFen, Long netAssetFen) {}
```

校验：type ∈ 三值；CREDIT 可带 creditLimitFen≥0；名称 ≤10 字去重；账户上限 20。assets 计算：按 type 分组求 balanceFen（CREDIT 组 debt = −Σbalance，limit/remain 同组求和）。

- [ ] **Step 7: AccountController**

`@RestController @RequestMapping("/api/kitchens/{kitchenId}/ledger")`，端点 `GET /accounts`、`POST /accounts`、`PUT /accounts/{aid}`、`DELETE /accounts/{aid}`、`GET /assets`；`userId(request)` 抄 LedgerController（`JwtAuthFilter.ATTR_USER_ID`）。

- [ ] **Step 8: LedgerEntry 实体加两字段**

`LedgerEntry.java` 加 `@Column(name="account_id") private Long accountId;` 与 `@Column(name="transfer_dir") private String transferDir;` + getter/setter；`LedgerEntryView` record 与 `toView` 同步加（放最后避免破坏现有位置参数——**注意** LedgerEntryView 是位置 record，新增字段追加到末尾，并全局检查所有构造处）。

- [ ] **Step 9: 编译→重启→冒烟→提交**

```bash
# 编译、停起后端同 P1 命令；迁移日志应出现 "to version \"19 - ledger accounts\""
bash scripts/smoke-test.sh 2>&1 | grep -E "账户|资产|结果"   # 期望 4 条 ✓，62 通过
git add server/src/main/resources/db/migration/ server/src/main/java/com/sharedkitchen/module/ledger/ scripts/smoke-test.sh
git commit -m "feat: V19迁移+账户体系——四表/两列/默认账户种子/存钱罐折算迁移+账户与资产总览API(P2)"
```

---

### Task 2: 流水账户化 + TRANSFER（存钱/取回/还款）

**Files:**
- Modify: `server/.../ledger/LedgerController.java`（ManualEntryReq/UpdateReq 加 accountId；新端点）
- Modify: `server/.../ledger/LedgerService.java`（addManual/updateItem 挂账户；transfer 双行；summary 排除 TRANSFER）
- Modify: `scripts/smoke-test.sh`

**Interfaces:**
- Consumes: Task 1 的 AccountRepository（校验账户归属）。
- Produces:
  - `POST /api/kitchens/{id}/ledger/transfers` body `{"fromAccountId":1,"toAccountId":5,"amountFen":100000,"date":"2026-10-17","remark":"存钱"}` → 生成两行 TRANSFER（from 行 dir=out，to 行 dir=in，category 取「转账」）；同厨房、账户存在、金额>0、from≠to 校验。
  - `GET /entries`、`GET /summary` 的响应条目新增 `accountId/transferDir` 字段；**summary 的 income/expense/balance 计算跳过 type=TRANSFER**。
  - 记一笔/编辑可带 `accountId`（EXPENSE/INCOME 时可选，校验归属）。

- [ ] **Step 1: 冒烟断言先行**

```bash
# 4.7 TRANSFER(P2)
SAV=$(curl -s $BASE/api/kitchens/$KID/ledger/accounts -H "$HA" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print([a['id'] for a in d if a['type']=='SAVING'][0])")
DIS=$(curl -s $BASE/api/kitchens/$KID/ledger/accounts -H "$HA" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print([a['id'] for a in d if a['type']=='DISPOSABLE'][0])")
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/transfers -H "$J" -H "$HA" -d "{\"fromAccountId\":$DIS,\"toAccountId\":$SAV,\"amountFen\":10000,\"date\":\"$TODAY\"}")
check "存钱转账" "$R" '"type":"TRANSFER"'
R=$(curl -s "$BASE/api/kitchens/$KID/ledger/assets" -H "$HA")
check "转账后资产总览变动" "$R" '"savingFen":'
R=$(curl -s "$BASE/api/kitchens/$KID/ledger/summary?month=$(date +%Y-%m)" -H "$HA")
check "TRANSFER不计入收支" "$R" '"balance"'
```

（`$TODAY` 若脚本无此变量，用 `$(date +%F)`。）

- [ ] **Step 2: 失败确认** → 同 P1 模式。

- [ ] **Step 3: LedgerService 改造**

- `TYPE_TRANSFER = "TRANSFER"` 常量入 LedgerEntry。
- `addManual`/`updateItem`：req.accountId 非空时校验 `accountRepository.findByIdAndDeleted(aid,0)` 属于本厨房，落 `e.setAccountId(...)`（TRANSFER 类型不允许走此入口，400）。
- 新增：

```java
@Transactional
public List<LedgerEntryView> transfer(Long userId, Long kitchenId, TransferReq req) {
    requireMember(kitchenId, userId);
    if (req.amountFen() == null || req.amountFen() <= 0) throw new BusinessException("金额必须大于 0");
    Account from = accountRepository.findByIdAndDeleted(req.fromAccountId(), 0).orElseThrow(() -> new BusinessException(404, "转出账户不存在"));
    Account to = accountRepository.findByIdAndDeleted(req.toAccountId(), 0).orElseThrow(() -> new BusinessException(404, "转入账户不存在"));
    if (!from.getKitchenId().equals(kitchenId) || !to.getKitchenId().equals(kitchenId)) throw new BusinessException(403, "账户不属于本厨房");
    if (from.getId().equals(to.getId())) throw new BusinessException("转出转入不能是同一账户");
    String date = validDate(req.date());
    // 双行：out 在 from，in 在 to；remark 前缀由调用方语义决定（存钱/取回/还款）
    LedgerEntry out = baseTransfer(kitchenId, userId, req.amountFen(), date, req.remark(), from.getId(), "out");
    LedgerEntry in  = baseTransfer(kitchenId, userId, req.amountFen(), date, req.remark(), to.getId(), "in");
    return List.of(toView(out), toView(in));
}
```

`baseTransfer` 组装实体（category="转账"、source=MANUAL、createdAt=now）。`summary()` 的累计循环：`if (TYPE_TRANSFER.equals(e.getType())) continue;`。`list()` 与 `exportExcel/exportPdf` 不需变（TRANSFER 自然出现在流水中，Excel/PDF 若按类型过滤 EXPENSE/INCOME 则天然排除）。

- [ ] **Step 4: Controller** 加 `@PostMapping("/transfers")` + `record TransferReq(Long fromAccountId, Long toAccountId, Long amountFen, String date, String remark)`；ManualEntryReq/UpdateReq 末尾追加 `Long accountId`。

- [ ] **Step 5: 编译→重启→冒烟→提交**（期望 65 通过）
```bash
git commit -m "feat: 流水账户化+TRANSFER转账——存钱/取回/还款双行入账, 收支统计排除TRANSFER(P2)"
```

---

### Task 3: 预算 + 周期账 + 存款目标 + 旧接口兼容桩

**Files:**
- Create: `server/.../ledger/account/Budget.java`、`RecurringBill.java`、`SavingGoal.java` + 三个 Repository
- Create: `server/.../ledger/account/LedgerBudgetService.java`、`RecurringBillService.java`、`RecurringBillJob.java`、`SavingGoalService.java`
- Modify: `server/.../ledger/LedgerController.java`（新端点 + savings/fx 兼容桩）
- Modify: `server/.../ledger/SavingService.java`（list/summary 返回空、add/remove 400）——或直接在 Controller 改桩，动 Service 更干净
- Modify: `server/.../ledger/LedgerEntryRepository.java`（分类月度聚合查询，供预算用量）
- Modify: `scripts/smoke-test.sh`

**Interfaces:**
- Consumes: Task 1/2 的账户与流水体系；`Notification`（fridge 模块实体：kitchenId/type≤20/title≤60/content/isRead/createdAt）+ `NotificationRepository`；调度样式 `@Scheduled(cron="0 5 8 * * ?", zone="Asia/Shanghai")`（ExpiryScanJob 为 0 0 8，错开 5 分钟）。
- Produces（前端依赖，全部人民币）:
  - `GET /ledger/budgets?month=YYYY-MM` → `{"month":"2026-10","total":{"budgetFen":300000,"usedFen":124000,"pct":41},"items":[{"category":"食材采购","budgetFen":120000,"usedFen":86000,"pct":71}]}`
  - `PUT /ledger/budgets` body `{"month":"2026-10","totalFen":300000,"items":[{"category":"食材采购","amountFen":120000}]}`（整体覆盖式保存；amountFen=0 删除该项；一键复制上月=`GET /budgets?month=上月` 后 PUT 本月）
  - `GET /ledger/recurring-bills` / `POST`（type,category,amountFen,accountId?,cycle,nextDate,remark）/ `PUT /{id}`（enabled 等）/ `DELETE /{id}`
  - `GET /ledger/saving-goals` → `[{"id":1,"name":"春节旅行","targetFen":3000000,"deadline":"2027-02-10","progressFen":1200000,"pct":40}]`（progress=全部 SAVING 账户余额之和）/ `POST` / `DELETE /{id}`
  - 预算超支提醒：RecurringBillJob 同一个定时任务里顺带扫描当月预算，`used≥100%` 且未提醒过（title 去重查 Notification）→ 插入 `type="BUDGET"` 通知；80% 只在账本 assets 响应附 `budgetWarn` 字段（前端展示），不发通知。

- [ ] **Step 1: 冒烟断言先行**

```bash
# 4.8 预算/周期/目标/兼容桩(P2)
M=$(date +%Y-%m)
R=$(curl -s -X PUT $BASE/api/kitchens/$KID/ledger/budgets -H "$J" -H "$HA" -d "{\"month\":\"$M\",\"totalFen\":300000,\"items\":[]}")
check "设总预算" "$R" '"totalFen":300000'
R=$(curl -s "$BASE/api/kitchens/$KID/ledger/budgets?month=$M" -H "$HA")
check "预算用量" "$R" '"usedFen"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/recurring-bills -H "$J" -H "$HA" -d "{\"type\":\"EXPENSE\",\"category\":\"水电燃气\",\"amountFen\":8600,\"cycle\":\"MONTHLY\",\"nextDate\":\"2020-01-01\"}")
check "建周期账" "$R" '"cycle":"MONTHLY"'
check "周期账立即补账" "$R" '"lastEntryId"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/saving-goals -H "$J" -H "$HA" -d '{"name":"春节旅行","targetFen":3000000}')
check "建存款目标" "$R" '"pct"'
R=$(curl -s $BASE/api/kitchens/$KID/ledger/savings -H "$HA")
check "旧存钱罐列表为空桩" "$R" '"data":\[\]'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/savings -H "$J" -H "$HA" -d '{"name":"x","currency":"CNY","amountFen":100}')
check "旧存钱罐写入被拒" "$R" '升级'
```

- [ ] **Step 2: 失败确认** → 同上。

- [ ] **Step 3: 三实体 + 仓储**（字段=V19 表；Repository 方法：`BudgetRepository.findByKitchenIdAndMonth`、`RecurringBillRepository.findByDeletedAndEnabled(0,1)`、`SavingGoalRepository.findByKitchenIdAndDeletedOrderByCreatedAtDesc`）。

- [ ] **Step 4: LedgerBudgetService**

- `get(userId,kitchenId,month)`：查 budgets 行 + `LedgerEntryRepository` 新聚合：

```java
@Query("select e.category, sum(e.amountFen) from LedgerEntry e where e.kitchenId=:kid and e.type='EXPENSE' and e.dineDate like :monthPrefix group by e.category")
List<Object[]> sumExpenseByCategory(Long kid, String monthPrefix);   // '2026-10%'
```

  total.used = Σ所有 EXPENSE；items 只回设了预算的分类。pct 取整。
- `put(...)`：整体删除该月旧行再插入（事务内）；`totalFen≤0` 视为清除总预算。
- `assets` 附带：`AccountService.assets` 返回值加 `budgetTotalFen/budgetUsedFen/budgetWarn(bool used/total≥0.8)`（预算不存在时 null/null/false）——Task 1 的 AssetsView 扩展为可空字段追加（record 末尾三个 `Long budgetTotalFen, Long budgetUsedFen, Boolean budgetWarn`）。

- [ ] **Step 5: RecurringBillService + Job**

- `scanOnce()`：遍历 enabled 且 `next_date <= today` 的账单 → 逐条 `addManual` 语义入账（type/category/amountFen/accountId 落 LedgerEntry，remark=周期账名）→ `lastEntryId` 记回 + next_date 推进（MONTHLY 加一月、YEARLY 加一年，日期非法如 0131→0228 用 `YearMonth.plusMonths(1).atEndOfMonth()` 兜底）→ 循环直至 next_date > today（补齐多月欠账）。
- `create/update/delete/list`；create 时若 nextDate ≤ today 立即触发一次 scan 单条。
- `RecurringBillJob`：`@Scheduled(cron="0 5 8 * * ?", zone="Asia/Shanghai")` 调 `scanOnce()` + 预算超支检查（used≥total 时，`notificationRepository` 查同月同标题未插入过则插入 `type="BUDGET", title="预算超支 2026-10", content="本月支出¥xx 已超过预算¥yy"`）。

- [ ] **Step 6: SavingGoalService**（list 算 progress=Σ SAVING 账户 balanceFen；单一目标建议但允许多个，progress 共用）/ 三个新 Controller 端点组（挂在 LedgerController 或独立 `LedgerExtraController`，按现有风格挂 LedgerController）。

- [ ] **Step 7: 旧接口兼容桩**：SavingService.list→空 List、summary→全 0 SummaryView、add/remove→`throw new BusinessException("存钱罐已升级为存款账户，请更新 App 到 1.6.0+")`；`/fx` 返回固定 `{"CNY":1,"HKD":0.92,"USD":7.24}` 不再调外部（若 FxService 现在也只是静态常量则不动）。

- [ ] **Step 8: 编译→重启→冒烟→提交**（期望 72 通过）
```bash
git commit -m "feat: 预算/周期账自动入账/存款目标+旧存钱罐与fx兼容桩(P2)"
```

---

### Task 4: 前端 api/ledger.ts v2 + 账本首页 v8

**Files:**
- Modify: `client/src/api/ledger.ts`
- Modify: `client/src/pages/ledger/ledger.vue`

**Interfaces:**
- Consumes: Task 1-3 全部 REST；现有 ledger.vue 结构（1.4.x：month-row/cards 含 save-summary/操作行/流水）。
- Produces: `ledgerApi.accounts()/createAccount()/updateAccount()/deleteAccount()/assets()/transfer(data)/budgets(month)/putBudgets(data)/recurringBills()/addRecurringBill()/updateRecurringBill()/removeRecurringBill()/savingGoals()/addSavingGoal()/removeSavingGoal()`；`LedgerEntryView` 增 `accountId: number|null; transferDir: string|null`。

- [ ] **Step 1: ledger.ts 增补**（接口类型 + api 方法，全按 Task1-3 Produces 的 JSON 形状；同时删 `SavingView/SavingSummary` 相关旧类型改为 any 兼容桩——旧方法保留函数签名避免编译错，内部直接调新端点或返回空）。

- [ ] **Step 2: 账本首页 v8 改造**（ledger.vue）：

- **删除**：`CUR_NAME/displayCur/CUR_KEY` 切换逻辑、`savingMoney/savingTotalCny`、save-summary 卡、存钱管理弹层（save-mask 整块）及相关样式；`openSavingManage` 改为跳存款目标页。
- **顶部区换 v8**（对应设计稿线框，v8 压缩版）：

```html
<view class="assets-row">
  <view class="card asset money" hover-class="press-dim" @tap="goAccounts">
    <view class="a-title-row"><text class="a-name">💰 资产</text><text class="a-big">¥{{ fen(assets.disposableFen + assets.savingFen) }}</text></view>
    <view class="a-sub"><text>可支配</text><text>¥{{ fen(assets.disposableFen) }}</text></view>
    <view class="a-sub"><text>存款</text><text>¥{{ fen(assets.savingFen) }}</text></view>
    <view class="goal-bar"><view class="goal-fill" :style="{ width: goalPct + '%' }" /></view>
    <text class="goal-tip">存款目标 {{ goalPct }}%</text>
  </view>
  <view class="card asset credit" hover-class="press-dim" @tap="goAccounts">
    <view class="a-title-row"><text class="a-name">💳 信用卡</text><text class="a-big neg">-¥{{ fen(assets.creditDebtFen) }}</text></view>
    <view class="a-sub"><text>总额度</text><text>¥{{ fen(assets.creditLimitFen) }}</text></view>
    <view class="a-sub"><text>剩余</text><text>¥{{ fen(assets.creditRemainFen) }}</text></view>
  </view>
</view>
<view class="cards">
  <view class="card summary"><text class="s-label">结余</text><text class="s-value">¥{{ fen(summary.balance) }}</text></view>
  <view class="card summary"><text class="s-label">收入</text><text class="s-value">¥{{ fen(summary.income) }}</text></view>
  <view class="card summary"><text class="s-label">支出</text><text class="s-value">¥{{ fen(summary.expense) }}</text></view>
</view>
<view class="acts card">
  <text class="act" @tap="goAdd">＋记一笔</text>
  <text class="act" @tap="goBudgets">📊预算{{ assets.budgetWarn ? '⚠️' : '' }}</text>
  <text class="act" @tap="goRecurring">🔁周期</text>
  <text class="act" @tap="saveMoney">🐖存钱</text>
  <text class="act" @tap="goMore">⋯</text>
</view>
```

- 样式按 v8 线框：双资产卡各 46% 宽居中、标题金额同行、分项行距 1.45、目标条 3px；月度三数单行卡；操作行五项。
- `saveMoney`：`uni.showModal` 输入金额（或简化 ActionSheet 选存钱/取回/还款→跳转账户页操作）；`goMore` 弹 ActionSheet（导出PDF/分类管理/存款目标）。
- **流水行**：`accountName(e.accountId)` 小字标签（accounts 列表缓存 Map）；TRANSFER 行显示 `↔ 存钱/取回/还款` 类别与不带 +/−（金额中性色）。
- onShow 并行拉 `assets + summary + entries + accounts + budgets`（budgets 用于预算角标）。
- **记一笔入口传账户**：`pages/ledger/add.vue` 下一任务改，本任务不动它（编译不破即可：add.vue 现有调用不变）。

- [ ] **Step 3: 构建验证**：`npm run build:h5` → DONE。
- [ ] **Step 4: 浏览器验证**（dev server + 登录态注入同 P1 流程）：资产双卡数字与 curl assets 一致；存钱按钮走通转账（assets.savingFen 变化）；流水出现 `↔` 行；旧存钱罐 UI 无残留。
- [ ] **Step 5: Commit** `feat: 账本首页v8——资产双卡/三数/五操作/流水账户标签, 删多币种与存钱罐UI(P2)`

---

### Task 5: 账户/预算/周期/目标 管理页 + 记一笔带账户

**Files:**
- Create: `client/src/pages/ledger/accounts.vue`（账户管理：分组列表 可支配/存款/信用卡，增删改、期初/额度编辑、每户余额；信用卡行含「还款」按钮→弹层选可支配账户+金额→transfer）
- Create: `client/src/pages/ledger/budgets.vue`（月预算编辑：总预算+分类预算行、用量进度条、一键复制上月）
- Create: `client/src/pages/ledger/recurring.vue`（周期账列表/新建/启停/删除）
- Create: `client/src/pages/ledger/goals.vue`（存款目标：进度环/条+新建删除）
- Modify: `client/src/pages.json`（注册四页）
- Modify: `client/src/pages/ledger/add.vue`（表单加「账户」行——ActionSheet 选账户/未指定）
- Modify: `client/src/pages/ledger/ledger.vue`（v8 的四个 go* 跳转指向新页）

**Interfaces:**
- Consumes: Task 4 的 ledgerApi 新方法；add.vue 现有结构（分费用/AI识别/日期/备注行）。
- Produces: 四个新页面路由；add.vue 提交体带 `accountId`（Task 2 已支持）。

- [ ] **Step 1-4: 四个页面逐个写**（每个页面：script 拉 ledgerApi 对应方法 + 列表/表单 UI，风格抄 `pages/fridge/categories.vue` 的列表管理模式与 theme 用色；页面注册进 pages.json，标题分别为 账户管理/预算管理/周期账单/存款目标，均 navigationBarBackgroundColor #FFF8F2）。
- [ ] **Step 5: add.vue 加账户选择行**（`账户 | 未指定 ›` ActionSheet 列出 accounts by type 分组；提交 `accountId: selectedAccountId ?? undefined`）。
- [ ] **Step 6: 构建+浏览器验证**：四页可达可增删；记一笔选微信后流水带「微信」标签且 assets 对应账户余额变动。
- [ ] **Step 7: Commit** `feat: 账户/预算/周期/目标四管理页+记一笔挂账户(P2)`

---

### Task 6: 全量验证 + APK 136/1.6.0

**Files:** Modify `client/android/app/build.gradle:10-11`（136 / "1.6.0"）

- [ ] Step 1: `bash scripts/smoke-test.sh` 全绿（72 项）。
- [ ] Step 2: 出包三步（build:h5 / cap sync / gradlew assembleDebug，JDK17）。
- [ ] Step 3: 包校验：`136 1.6.0`；`unzip -p app-debug.apk assets/public/assets/pages-ledger-accounts.*.js | grep -c 账户管理` ≥1。
- [ ] Step 4: 数据迁移抽查（本地 dev 库已有旧存钱罐数据的，重启后 `GET /ledger/accounts` 的存款余额 = 旧 CNY+折算外币净额）。
- [ ] Step 5: Commit `chore: P2出包136(1.6.0)——账本v2三钱包`。发布由用户确认后另行执行。

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 三钱包/存钱/还款→T1/T2；§3.2 v8 首页→T4；§3.3 预算/周期/多账户→T3/T5；§3.4 兼容迁移→T1(V19 SQL)+T3(桩)；§6 四表+两列+TRANSFER→T1/T2。预算 80% 预警=assets.budgetWarn 前端角标、100%=通知，与 spec「80% 预警、100% 提醒」对齐。
- **占位符**：无 TBD；T5 四页面因结构同构给模式引用（抄 categories.vue）+字段清单，属可执行粒度。
- **类型一致性**：AssetsView 在 T1 定义、T3 末尾扩三可空字段（budget*），T4 前端按含 budgetWarn 使用——T3 先行保证 T4 可用；transferDir 'in'/'out' 在 SQL/实体/Service/前端一致；LedgerEntryView 末尾追加字段避免位置参数破坏。
