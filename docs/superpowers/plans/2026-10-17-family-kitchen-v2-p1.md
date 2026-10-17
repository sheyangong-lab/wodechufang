# 家庭厨房 v2 — P1（骨架+中枢）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 v2 第一步——底部导航切为「中枢/账本/冰箱/菜谱/我」五 Tab，新增中枢首页（问候+四宫格+周计划+最近账单），下线广场，食本/订单入口移位。

**Architecture:** 后端新增一个纯聚合接口 `/hub/overview`（复用 Ledger/Fridge/Basket/Plan 四个现有 Service，无新表）；前端新增中枢页 + 重排 custom-tabbar 与 pages.json 的原生 tabBar，其余页面不动。

**Tech Stack:** Spring Boot 3.3.5（Java 21, Maven）/ uni-app Vue3 + Vite / 测试走 `scripts/smoke-test.sh` + curl + `npm run build:h5` + gradle assembleDebug。

**设计文档:** `docs/superpowers/specs/2026-10-17-family-kitchen-v2-design.md`（本计划只做 §7 的 P1 行）

## Global Constraints

- 分支：`feature/v2-family-kitchen`（已存在，基于 main@3d2aa0b），所有提交落此分支。
- 后端编译：`cd server && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ~/.local/opt/apache-maven-3.9.11/bin/mvn -f $PWD/pom.xml -B -q -DskipTests package`（本机无 `mvn` 命令）。
- 后端测试载体：`bash scripts/smoke-test.sh`（55 项存量，不允许回归失败）；API 实测用 curl（后端跑在 localhost:8080，测试账号注册随机手机号 smsCode=1234）。
- 前端构建：`cd client && npm run build:h5`（成功标准：输出 `DONE  Build complete`，注意是两个空格）。
- 图标只能复用 `client/src/static/icons/` 现有 png，不新增设计资产。
- 所有金额单位是「分」（fen, long）。
- 提交信息用中文，格式 `feat|fix|docs|chore: 摘要`。

---

### Task 1: 后端 /hub/overview 聚合接口

**Files:**
- Create: `server/src/main/java/com/sharedkitchen/module/hub/HubController.java`
- Create: `server/src/main/java/com/sharedkitchen/module/hub/HubService.java`
- Modify: `scripts/smoke-test.sh`（账本段之后加 hub 断言）

**Interfaces:**
- Consumes（已存在，签名实测自源码）:
  - `LedgerService.summary(Long userId, Long kitchenId, String month)` → `MonthSummary(long income, long refund, long expense, long balance, List<DayPoint> days)`
  - `FridgeService.listItems(Long userId, Long kitchenId, Long categoryId, String keyword, String state)` → `List<FridgeItemView>`，其中 `FridgeItemView(... String name, ..., String expireDate, int daysLeft, String state)`，state ∈ `fresh|expiring|expired`
  - `BasketService.list(Long userId, Long kitchenId)` → `List<BasketItemView(Long id, String name, String quantity, Integer checked, String source)>`，`checked==0` 为待买
  - `PlanService.day(Long userId, Long kitchenId, String date)` → `PlanDay(String date, List<String> slots, List<PlanItemView> items)`，`PlanItemView(Long id, Integer slotIndex, String itemType, Long dishId, String name, String imageUrl, String remark)`
  - `userId(request)` 模式：`return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);`
- Produces（Task 2 的前端依赖）: `GET /api/kitchens/{id}/hub/overview` → `HubOverviewView` JSON：
  ```json
  { "month": {"income":0,"refund":0,"expense":0,"balance":0},
    "fridge": {"total":12,"expiring":2,"expiringNames":["青菜","酸奶"]},
    "tonight": {"name":"番茄炒蛋","imageUrl":"/files/x.png"},
    "basket": {"pendingCount":3,"pendingNames":["番茄","鸡蛋","葱"]} }
  ```
  `tonight` 可为 `null`（今天没排菜）。`expiringNames`/`pendingNames` 最多 3 个。

- [ ] **Step 1: 先加冒烟断言（此时应失败）**

`scripts/smoke-test.sh` 在「账本」段（`check "Excel导出3 Sheet有效"` 附近）之后插入：

```bash
# 4.5 中枢聚合接口
R=$(curl -s $BASE/api/kitchens/$KID/hub/overview -H "$HA")
check "中枢总览-结余字段" "$R" '"balance"'
check "中枢总览-冰箱字段" "$R" '"expiringNames"'
check "中枢总览-买菜字段" "$R" '"pendingCount"'
```

- [ ] **Step 2: 跑冒烟确认失败**

Run: `cd /home/syg/桌面/共享厨房 && bash scripts/smoke-test.sh 2>&1 | grep -E "中枢|结果"`
Expected: 3 条 `✗ 中枢总览-...`（404 无 balance 等字段），总结果 FAIL。

- [ ] **Step 3: 写 HubService**

`server/src/main/java/com/sharedkitchen/module/hub/HubService.java`：

```java
package com.sharedkitchen.module.hub;

import com.sharedkitchen.module.basket.BasketService;
import com.sharedkitchen.module.basket.BasketService.BasketItemView;
import com.sharedkitchen.module.fridge.FridgeService;
import com.sharedkitchen.module.fridge.FridgeService.FridgeItemView;
import com.sharedkitchen.module.ledger.LedgerService;
import com.sharedkitchen.module.ledger.LedgerService.MonthSummary;
import com.sharedkitchen.module.plan.PlanService;
import com.sharedkitchen.module.plan.PlanService.PlanDay;
import com.sharedkitchen.module.plan.PlanService.PlanItemView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** 中枢首页聚合：一次请求拉齐 钱/冰箱/今晚/买菜 四格数据（纯读，无新表）。 */
@Service
public class HubService {

    private final LedgerService ledgerService;
    private final FridgeService fridgeService;
    private final BasketService basketService;
    private final PlanService planService;

    public HubService(LedgerService ledgerService, FridgeService fridgeService,
                      BasketService basketService, PlanService planService) {
        this.ledgerService = ledgerService;
        this.fridgeService = fridgeService;
        this.basketService = basketService;
        this.planService = planService;
    }

    public HubOverviewView overview(Long userId, Long kitchenId) {
        String month = LocalDate.now().toString().substring(0, 7);
        MonthSummary ms = ledgerService.summary(userId, kitchenId, month);

        List<FridgeItemView> items = fridgeService.listItems(userId, kitchenId, null, null, "all");
        List<String> expiringNames = items.stream()
                .filter(i -> "expiring".equals(i.state()))
                .map(FridgeItemView::name)
                .limit(3)
                .toList();
        FridgeOverview fridge = new FridgeOverview(items.size(),
                (int) items.stream().filter(i -> "expiring".equals(i.state())).count(),
                expiringNames);

        List<BasketItemView> basket = basketService.list(userId, kitchenId);
        List<String> pendingNames = basket.stream()
                .filter(b -> b.checked() == 0)
                .map(BasketItemView::name)
                .limit(3)
                .toList();
        BasketOverview basketOverview = new BasketOverview(
                (int) basket.stream().filter(b -> b.checked() == 0).count(), pendingNames);

        return new HubOverviewView(ms, fridge, tonight(userId, kitchenId), basketOverview);
    }

    /** 今晚吃什么：取今天餐计划里槽位名含"晚"的最后一项，否则取最后一项，没排菜返回 null。 */
    private TonightView tonight(Long userId, Long kitchenId) {
        try {
            PlanDay day = planService.day(userId, kitchenId, LocalDate.now().toString());
            if (day == null || day.items().isEmpty()) return null;
            List<String> slots = day.slots();
            PlanItemView pick = null;
            for (PlanItemView it : day.items()) {
                String slotName = it.slotIndex() != null && it.slotIndex() < slots.size()
                        ? slots.get(it.slotIndex()) : "";
                if (slotName != null && slotName.contains("晚")) pick = it;
            }
            if (pick == null) pick = day.items().get(day.items().size() - 1);
            return new TonightView(pick.name(), pick.imageUrl());
        } catch (Exception e) {
            return null; // 餐段未配置等场景不阻塞中枢
        }
    }

    public record HubOverviewView(MonthSummary month, FridgeOverview fridge,
                                  TonightView tonight, BasketOverview basket) {}
    public record FridgeOverview(int total, int expiring, List<String> expiringNames) {}
    public record TonightView(String name, String imageUrl) {}
    public record BasketOverview(int pendingCount, List<String> pendingNames) {}
}
```

注意：若 `PlanItemView` 的字段名与上面引用（`slotIndex()/name()/imageUrl()`）编译不过，以 `PlanService.java:160` 的 record 定义为准修正引用。

- [ ] **Step 4: 写 HubController**

`server/src/main/java/com/sharedkitchen/module/hub/HubController.java`：

```java
package com.sharedkitchen.module.hub;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens/{kitchenId}/hub")
public class HubController {

    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/overview")
    public ApiResponse<HubService.HubOverviewView> overview(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        Long userId = (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        return ApiResponse.ok(hubService.overview(userId, kitchenId));
    }
}
```

- [ ] **Step 5: 编译并重启**

```bash
cd /home/syg/桌面/共享厨房/server && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ~/.local/opt/apache-maven-3.9.11/bin/mvn -f $PWD/pom.xml -B -q -DskipTests package
# 停旧实例（注意用括号正则避免误杀自身）
for p in $(pgrep -f "server-0[.]1[.]0[.]jar"); do kill $p; done; sleep 3
# 启动（后台）后等 health
```
Expected: 编译无 ERROR；`curl -s localhost:8080/api/health` 返回 `"status":"UP"`。

- [ ] **Step 6: 跑冒烟确认通过**

Run: `bash scripts/smoke-test.sh 2>&1 | grep -E "中枢|结果"`
Expected: 3 条 `✓ 中枢总览-...`，`55+3 通过, 0 失败`。

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/com/sharedkitchen/module/hub/ scripts/smoke-test.sh
git commit -m "feat: 中枢聚合接口/hub/overview——结余+冰箱临期+今晚+买菜一次拉齐(P1)"
```

---

### Task 2: 客户端 hub API 与中枢页面

**Files:**
- Create: `client/src/api/hub.ts`
- Create: `client/src/pages/hub/hub.vue`
- Modify: `client/src/pages.json`（注册页面，tabBar 改动留给 Task 3 一起做）

**Interfaces:**
- Consumes: Task 1 的 `/hub/overview` 响应结构（见 Task 1 Produces）；`ledgerApi.list`、`planApi.day`（周计划条用，均已在 `client/src/api/` 存在）；`CustomTabbar` 组件（props: `current: string`）；主题 `theme`（`@/styles/theme`）。
- Produces: `hubApi.overview(kitchenId): Promise<HubOverview>`；页面路由 `/pages/hub/hub`（Task 3 的 tabBar 第一项）。

- [ ] **Step 1: 写 `client/src/api/hub.ts`**

```ts
import { request } from '@/utils/request';

export interface HubOverview {
  month: { income: number; refund: number; expense: number; balance: number };
  fridge: { total: number; expiring: number; expiringNames: string[] };
  tonight: { name: string; imageUrl: string } | null;
  basket: { pendingCount: number; pendingNames: string[] };
}

export const hubApi = {
  overview: (kitchenId: number) =>
    request<HubOverview>({ url: `/api/kitchens/${kitchenId}/hub/overview` }),
};
```

- [ ] **Step 2: 写 `client/src/pages/hub/hub.vue`**

```vue
<script setup lang="ts">
import { theme } from '@/styles/theme';
import { hubApi } from '@/api/hub';
import { ledgerApi } from '@/api/ledger';
import { planApi } from '@/api/plan';
import { getCurrentKitchenId } from '@/api/kitchen';
import { fullUrl } from '@/api/dish';
import { todayStr } from '@/utils/fmt';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import CustomTabbar from '@/components/custom-tabbar.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const data = ref<Awaited<ReturnType<typeof hubApi.overview>> | null>(null);
const recent = ref<{ id: number; title: string; amountFen: number; type: string }[]>([]);
const weekItems = ref<{ name: string; date: string }[]>([]);
const loading = ref(true);

const greet = (() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
})();

onShow(load);

async function load() {
  kitchenId.value = getCurrentKitchenId();
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    const [hub, entries, plan] = await Promise.all([
      hubApi.overview(kitchenId.value),
      ledgerApi.list(kitchenId.value, todayStr().slice(0, 7)).catch(() => []),
      planApi.day(kitchenId.value).catch(() => null),
    ]);
    data.value = hub;
    recent.value = (entries || []).slice(0, 3).map((e) => ({
      id: e.id,
      title: e.category || (e.type === 'INCOME' ? '收入' : '支出'),
      amountFen: e.amountFen,
      type: e.type,
    }));
    weekItems.value = (plan?.items || []).map((i) => ({ name: i.name, date: plan.date }));
  } finally {
    loading.value = false;
  }
}

function fmt(fen: number) {
  return (fen / 100).toFixed(2);
}

function go(url: string) {
  uni.switchTab({
    fail: () => uni.navigateTo({ url }),
    success: () => undefined,
  } as never);
  // switchTab 只对 tab 页生效；非 tab 页直接 navigateTo
  uni.navigateTo({
    url,
    fail: () => undefined,
  });
}
</script>

<template>
  <view class="page">
    <view class="page-scroll">
      <view class="greet">
        <text class="greet-main">👋 {{ greet }}，今天吃点好的</text>
        <text class="greet-sub">{{ todayStr() }}</text>
      </view>

      <view class="grid">
        <view class="card cell" hover-class="press-dim" @tap="go('/pages/ledger/ledger')">
          <text class="cell-title">💰 本月结余</text>
          <text class="cell-num">¥{{ fmt(data?.month.balance || 0) }}</text>
          <text class="cell-sub">支出 ¥{{ fmt(data?.month.expense || 0) }}</text>
        </view>
        <view class="card cell" hover-class="press-dim" @tap="go('/pages/fridge/fridge')">
          <text class="cell-title">🧊 冰箱 {{ data?.fridge.total ?? 0 }} 样</text>
          <text class="cell-num">{{ data?.fridge.expiring ?? 0 }} 样临期</text>
          <text class="cell-sub">{{ (data?.fridge.expiringNames || []).join('·') || '都很新鲜' }}</text>
        </view>
        <view class="card cell" hover-class="press-dim" @tap="go('/pages/plan/plan')">
          <text class="cell-title">🍳 今晚吃什么</text>
          <text class="cell-num">{{ data?.tonight?.name || '还没定' }}</text>
          <text class="cell-sub">{{ data?.tonight ? '去排菜 ›' : '点我排菜 ›' }}</text>
        </view>
        <view class="card cell" hover-class="press-dim" @tap="go('/pages/basket/basket')">
          <text class="cell-title">🧺 买菜清单</text>
          <text class="cell-num">{{ data?.basket.pendingCount ?? 0 }} 样待买</text>
          <text class="cell-sub">{{ (data?.basket.pendingNames || []).join('·') || '清单是空的' }}</text>
        </view>
      </view>

      <view v-if="weekItems.length" class="card strip" hover-class="press-dim" @tap="go('/pages/plan/plan')">
        <text class="strip-main">📅 本周餐计划</text>
        <text class="strip-sub">{{ weekItems.map((w) => w.name).join(' / ') }}</text>
      </view>

      <view class="card recent">
        <text class="recent-title">最近账单</text>
        <view v-for="r in recent" :key="r.id" class="recent-row">
          <text class="recent-name">{{ r.title }}</text>
          <text class="recent-amt" :class="r.type === 'EXPENSE' ? 'out' : 'in'">
            {{ r.type === 'EXPENSE' ? '−' : '+' }}¥{{ fmt(r.amountFen) }}
          </text>
        </view>
        <text v-if="recent.length === 0" class="recent-empty">本月还没有记录</text>
      </view>
    </view>
    <CustomTabbar current="hub" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex; flex-direction: column;
  height: calc(var(--sk-vh, 100vh) - var(--window-top, 0px));
  overflow: hidden;
}
.page-scroll { flex: 1; overflow-y: auto; min-height: 0; padding: 24rpx; box-sizing: border-box; }
.card { background: v-bind('theme.card'); border-radius: 24rpx; box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1); }
.greet { padding: 12rpx 8rpx 20rpx; }
.greet-main { display: block; font-size: 36rpx; font-weight: 700; color: v-bind('theme.title'); }
.greet-sub { display: block; font-size: 24rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.grid { display: flex; flex-wrap: wrap; gap: 16rpx; }
.cell { width: calc(50% - 8rpx); box-sizing: border-box; padding: 22rpx; min-height: 170rpx; display: flex; flex-direction: column; justify-content: space-between; }
.cell-title { font-size: 24rpx; color: v-bind('theme.sub'); }
.cell-num { font-size: 34rpx; font-weight: 700; color: v-bind('theme.title'); }
.cell-sub { font-size: 22rpx; color: v-bind('theme.sub'); }
.strip { margin-top: 16rpx; padding: 20rpx 24rpx; }
.strip-main { display: block; font-size: 26rpx; font-weight: 600; color: v-bind('theme.title'); }
.strip-sub { display: block; font-size: 24rpx; color: v-bind('theme.sub'); margin-top: 6rpx; }
.recent { margin-top: 16rpx; padding: 20rpx 24rpx; }
.recent-title { display: block; font-size: 26rpx; font-weight: 600; color: v-bind('theme.title'); }
.recent-row { display: flex; justify-content: space-between; padding: 12rpx 0; }
.recent-name { font-size: 26rpx; color: v-bind('theme.title'); }
.recent-amt { font-size: 26rpx; font-weight: 600; }
.recent-amt.out { color: v-bind('theme.expense'); }
.recent-amt.in { color: v-bind('theme.income'); }
.recent-empty { display: block; font-size: 24rpx; color: v-bind('theme.sub'); padding: 12rpx 0; }
</style>
```

注意 `go()` 的实现是简化版：先用 `uni.navigateTo`，失败（目标是 tab 页）再由 `switchTab` 兜底——实现时按 uni-app 实际行为调通即可，验收标准是四格点击能到对应页面。

- [ ] **Step 3: 注册页面（只加页面，不动 tabBar）**

`client/src/pages.json` 的 `pages` 数组第一项前插入：

```json
{
  "path": "pages/hub/hub",
  "style": { "navigationBarTitleText": "家庭中枢", "navigationBarBackgroundColor": "#FFF8F2", "navigationBarTextStyle": "black" }
}
```

- [ ] **Step 4: 构建验证**

Run: `cd client && npm run build:h5 2>&1 | tail -3`
Expected: `DONE  Build complete`。

- [ ] **Step 5: Commit**

```bash
git add client/src/api/hub.ts client/src/pages/hub/hub.vue client/src/pages.json
git commit -m "feat: 中枢首页——问候+四宫格+周计划+最近账单(P1)"
```

---

### Task 3: Tab 骨架切换 + 广场下线 + 入口移位

**Files:**
- Modify: `client/src/components/custom-tabbar.vue`（tabs 数组，约 7-15 行）
- Modify: `client/src/pages.json`（tabBar.list 5 项 + 删 square 页注册）
- Modify: `client/src/pages/order/order.vue:215`（去 Tabbar 或改 current）
- Modify: `client/src/pages/kitchen/kitchen.vue:170-181`（删广场入口、页头加「点单」快捷入口）
- Modify: `client/src/pages/profile/profile.vue`（加食本入口）
- Delete: `client/src/pages/square/square.vue`
- Create: `client/src/static/icons/tab-hub.png`、`tab-hub-active.png`（由现有图标复制）

**Interfaces:**
- Consumes: Task 2 的 `/pages/hub/hub` 路由。
- Produces: 新 Tab 键名 `hub`（custom-tabbar current="hub"）；菜谱 Tab 键名仍为 `kitchen`（文案「菜谱」）。

- [ ] **Step 1: 图标准备**

```bash
cd client/src/static/icons
cp pot.png tab-hub.png && cp pan-active.png tab-hub-active.png
```

- [ ] **Step 2: 改 custom-tabbar 的 tabs 数组**

`custom-tabbar.vue` 中整个 `const tabs: TabDef[] = [...]` 替换为：

```ts
const tabs: TabDef[] = [
  { key: 'hub', text: '中枢', icon: '/static/icons/tab-hub.png', iconActive: '/static/icons/tab-hub-active.png', path: '/pages/hub/hub', isTab: true },
  { key: 'ledger', text: '账本', icon: '/static/icons/tab-ledger.png', iconActive: '/static/icons/tab-ledger-active.png', path: '/pages/ledger/ledger', isTab: true },
  { key: 'fridge', text: '冰箱', icon: '/static/icons/tab-fridge.png', iconActive: '/static/icons/tab-fridge-active.png', path: '/pages/fridge/fridge', isTab: true },
  { key: 'kitchen', text: '菜谱', icon: '/static/icons/pan.png', iconActive: '/static/icons/pan-active.png', path: '/pages/kitchen/kitchen', isTab: true },
  { key: 'me', text: '我', icon: '/static/icons/tab-me.png', iconActive: '/static/icons/tab-me-active.png', path: '/pages/profile/profile', isTab: true },
];
```

（原数组里的 orders 与 foodbook 两项删除；组件注释同步更新为 5 个入口。）

- [ ] **Step 3: 改 pages.json 的 tabBar.list**

原生 tabBar `list` 整体替换（顺序=中枢/账本/冰箱/菜谱/我）：

```json
"list": [
  { "pagePath": "pages/hub/hub", "text": "中枢",
    "iconPath": "static/icons/tab-hub.png", "selectedIconPath": "static/icons/tab-hub-active.png" },
  { "pagePath": "pages/ledger/ledger", "text": "账本",
    "iconPath": "static/icons/tab-ledger.png", "selectedIconPath": "static/icons/tab-ledger-active.png" },
  { "pagePath": "pages/fridge/fridge", "text": "冰箱",
    "iconPath": "static/icons/tab-fridge.png", "selectedIconPath": "static/icons/tab-fridge-active.png" },
  { "pagePath": "pages/kitchen/kitchen", "text": "菜谱",
    "iconPath": "static/icons/pan.png", "selectedIconPath": "static/icons/pan-active.png" },
  { "pagePath": "pages/profile/profile", "text": "我",
    "iconPath": "static/icons/tab-me.png", "selectedIconPath": "static/icons/tab-me-active.png" }
]
```

同时删除 `pages` 数组里 `pages/square/square` 那一项（约 88-89 行）。

- [ ] **Step 4: 删广场页面与入口**

```bash
rm client/src/pages/square/square.vue
rmdir client/src/pages/square
```

`kitchen.vue:170` 附近：删除 `{ key: 'square', title: '广场偷菜', ... }` 菜单项与 `key === 'square'` 两个分支（178-181 行）。页面里如有其他 `/pages/square` 引用一并删除（`grep -rn "square" client/src` 应只剩 pages.json 历史无引用）。

- [ ] **Step 5: 订单页去 Tab 化 + 厨房页加点单入口**

- `order/order.vue:215`：`<CustomTabbar current="orders" />` 整行删除（订单不再是 tab 页，从别处进入）。
- `kitchen.vue` 页头操作区（发布菜谱按钮旁）加一行入口：

```html
<view class="quick-row">
  <text class="quick" hover-class="press-dim" @tap="() => uni.navigateTo({ url: '/pages/order/order' })">📋 点单</text>
  <text class="quick" hover-class="press-dim" @tap="() => uni.navigateTo({ url: '/pages/basket/basket' })">🧺 买菜</text>
  <text class="quick" hover-class="press-dim" @tap="() => uni.navigateTo({ url: '/pages/plan/plan' })">📅 排菜</text>
</view>
```

样式（加进 kitchen.vue 的 style 段）：

```scss
.quick-row { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.quick {
  flex: 1; text-align: center; font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 12rpx; padding: 12rpx 0;
}
```

- [ ] **Step 6: profile 加食本入口**

`profile.vue` 的功能列表（已有「服务器设置」等 entry 的列表结构）里加同款一项，点击 `uni.navigateTo({ url: '/pages/foodbook/foodbook' })`，标题「📖 我的食本」。`foodbook.vue:493` 的 `current="foodbook"` 改为 `current="me"`。

- [ ] **Step 7: 构建验证**

```bash
cd client && grep -rn "square" src/ | grep -v node_modules | head; npm run build:h5 2>&1 | tail -2
```
Expected: 无 square 残留引用（或仅注释）；`DONE  Build complete`。

- [ ] **Step 8: 浏览器冒烟（dev server）**

```bash
cd client && nohup npm run dev:h5 > /tmp/sk-h5.log 2>&1 &
```
浏览器（或 curl）验证：`http://localhost:5173/#/pages/hub/hub` 打开有四宫格；五个 tab 互相切换无 404；点「点单/买菜/排菜」可达。截图留档。

- [ ] **Step 9: Commit**

```bash
git add -A client/src
git commit -m "feat: v2骨架——5Tab(中枢/账本/冰箱/菜谱/我)+广场下线+订单/食本入口移位(P1)"
```

---

### Task 4: 全量验证 + APK 出包

**Files:**
- Modify: `client/android/app/build.gradle:10-11`（版本号 134 / 1.5.0）

**Interfaces:**
- Consumes: Task 1-3 全部产物。
- Produces: `client/android/app/build/outputs/apk/debug/app-debug.apk`（134/1.5.0）。

- [ ] **Step 1: 后端全量冒烟**

Run: `bash scripts/smoke-test.sh 2>&1 | tail -2`
Expected: `58 通过, 0 失败`（55 存量 + 3 hub）。

- [ ] **Step 2: 升版本 + 出包**

```bash
cd client && sed -i 's/versionCode 133/versionCode 134/; s/versionName "1.4.3"/versionName "1.5.0"/' android/app/build.gradle
npm run build:h5 2>&1 | tail -1
npx cap sync android 2>&1 | tail -1
cd android && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug 2>&1 | grep BUILD
```
Expected: 三步分别输出 `DONE  Build complete` / `Sync finished` / `BUILD SUCCESSFUL`。

- [ ] **Step 3: 包内校验**

```bash
cd app/build/outputs/apk/debug && python3 -c "
import json; d=json.load(open('output-metadata.json'))
print(d['elements'][0]['versionCode'], d['elements'][0]['versionName'])"
unzip -p app-debug.apk assets/public/assets/pages-hub-*.js | grep -c "本月结余"
```
Expected: `134 1.5.0`；grep ≥1。

- [ ] **Step 4: Commit**

```bash
git add client/android/app/build.gradle
git commit -m "chore: P1出包134(1.5.0)——v2骨架+中枢首页"
```

发布到生产（192.168.3.78，走 /tmp/sshx.py）由用户确认后另行执行，不在本计划内。

---

## Self-Review 记录

- **Spec 覆盖**：§1 骨架/广场/食本移位→Task 3；§2 中枢→Task 1+2；§7 P1 行→Task 4。预算百分比显示属 P2（有 budgets 表后），P1 结余格以「支出」小字占位——与 spec §2 数据源说明一致。
- **占位符**：无 TBD/TODO；kitchen.vue quick-row 的插入位置描述为「页头操作区」，因该文件无稳定行号，执行者需按「发布菜谱按钮旁」语义定位。
- **类型一致**：`hubApi.overview` 返回类型与 Task 1 JSON 字段一一对应（month/fridge/tonight/basket）；tab 键名 `hub` 在 Task 2（current="hub"）与 Task 3（tabs 数组）一致。
