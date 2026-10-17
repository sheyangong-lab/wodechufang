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
      planApi.day(kitchenId.value, todayStr()).catch(() => null),
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
  // switchTab 只能进 tabBar 页；失败（非 tab 页）再 navigateTo 兜底
  uni.switchTab({
    url,
    fail: () => uni.navigateTo({ url, fail: () => undefined }),
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
