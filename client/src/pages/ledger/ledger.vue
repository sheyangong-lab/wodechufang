<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi } from '@/api/ledger';
import type { LedgerEntryView, MonthSummary, DishStat } from '@/api/ledger';
import { getCurrentKitchenId } from '@/api/kitchen';
import { API_BASE } from '@/api/config';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const month = ref(new Date().toISOString().slice(0, 7));
const summary = ref<MonthSummary>({ income: 0, refund: 0, expense: 0, balance: 0, days: [] });
const entries = ref<LedgerEntryView[]>([]);
const stats = ref<DishStat[]>([]);
const showStats = ref(false);
const loading = ref(true);

const cards = computed(() => [
  { label: '收入', value: summary.value.income, color: theme.income },
  { label: '支出', value: summary.value.expense, color: theme.expense },
  { label: '结余', value: summary.value.balance, color: theme.title },
]);

const maxDay = computed(() =>
  Math.max(1, ...summary.value.days.map((d) => Math.max(d.incomeFen, d.expenseFen)))
);

const grouped = computed(() => {
  const map = new Map<string, LedgerEntryView[]>();
  entries.value.forEach((e) => {
    const list = map.get(e.date) || [];
    list.push(e);
    map.set(e.date, list);
  });
  return [...map.entries()];
});

onShow(load);

function prevMonth() {
  const [y, m] = month.value.split('-').map(Number);
  const d = new Date(y, m - 2, 1);
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  load();
}

function nextMonth() {
  const [y, m] = month.value.split('-').map(Number);
  const d = new Date(y, m, 1);
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  load();
}

async function load() {
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    const [s, list, stats] = await Promise.all([
      ledgerApi.summary(kitchenId.value, month.value),
      ledgerApi.list(kitchenId.value, month.value),
      ledgerApi.dishStats(kitchenId.value, month.value).catch(() => []),
    ]);
    summary.value = s;
    entries.value = list;
    stats.value = stats;
  } catch {
    // toast 已统一弹出
  } finally {
    loading.value = false;
  }
}

function goAdd() {
  uni.navigateTo({ url: `/pages/ledger/add?month=${month.value}` });
}

function exportExcel() {
  if (!kitchenId.value) return;
  uni.showLoading({ title: '生成中…' });
  ledgerApi
    .export(kitchenId.value, month.value)
    .then((res) => {
      const full = API_BASE + res.url;
      // #ifdef H5
      window.open(full);
      // #endif
      // #ifndef H5
      uni.downloadFile({
        url: full,
        success: (d) => uni.openDocument({ filePath: d.tempFilePath, showMenu: true }),
        fail: () => uni.showToast({ title: '下载失败', icon: 'none' }),
      });
      // #endif
      uni.showToast({ title: '已生成 Excel', icon: 'none' });
    })
    .finally(() => uni.hideLoading());
}

function typeLabel(e: LedgerEntryView) {
  if (e.type === 'EXPENSE') return e.category || '支出';
  if (e.type === 'REFUND') return '退款冲销';
  return e.category || '收入';
}

function delEntry(e: LedgerEntryView) {
  if (e.source === 'ORDER') {
    uni.showToast({ title: '订单流水不可删，退款走退单流程', icon: 'none' });
    return;
  }
  uni.showModal({
    title: '删除流水',
    content: '确定删除这条手动流水？',
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      ledgerApi.remove(kitchenId.value!, e.id).then(load);
    },
  });
}
</script>

<template>
  <view class="page">
    <!-- 月份切换 -->
    <view class="month-row">
      <text class="arrow" hover-class="press-dim" @tap="prevMonth">‹</text>
      <text class="month">{{ month }}</text>
      <text class="arrow" hover-class="press-dim" @tap="nextMonth">›</text>
    </view>

    <!-- 三卡汇总 -->
    <view class="cards">
      <view v-for="c in cards" :key="c.label" class="card summary">
        <text class="s-label">{{ c.label }}</text>
        <text class="s-value" :style="{ color: c.color }">¥{{ (c.value / 100).toFixed(2) }}</text>
      </view>
    </view>

    <!-- 操作行 -->
    <view class="card actions">
      <text class="act add" hover-class="press-dim" @tap="goAdd">＋ 记一笔</text>
      <text class="act toggle" hover-class="press-dim" @tap="showStats = !showStats">
        {{ showStats ? '收起统计' : '经营统计' }}
      </text>
      <text class="act export" hover-class="press-dim" @tap="exportExcel">导出 Excel</text>
    </view>

    <!-- 每日收支柱状图 -->
    <view v-if="showStats" class="card chart">
      <text class="sec-title">每日收支</text>
      <view v-for="d in summary.days" :key="d.date" class="bar-row">
        <text class="bar-date">{{ d.date.slice(8) }}日</text>
        <view class="bars">
          <view class="bar income-bar" :style="{ width: (d.incomeFen / maxDay) * 100 + '%' }" />
          <view class="bar expense-bar" :style="{ width: (d.expenseFen / maxDay) * 100 + '%' }" />
        </view>
        <text class="bar-val">¥{{ (d.incomeFen / 100).toFixed(0) }} / ¥{{ (d.expenseFen / 100).toFixed(0) }}</text>
      </view>
      <view v-if="summary.days.length === 0" class="chart-empty"><text>本月暂无收支</text></view>

      <text class="sec-title stats-title">菜品销售排行</text>
      <view v-for="(s, i) in stats" :key="s.name" class="stat-row">
        <text class="stat-rank" :class="{ top: i < 3 }">{{ i + 1 }}</text>
        <text class="stat-name">{{ s.name }}</text>
        <text class="stat-qty">×{{ s.quantity }}</text>
        <text class="stat-sales">¥{{ (s.salesFen / 100).toFixed(2) }}</text>
      </view>
      <view v-if="stats.length === 0" class="chart-empty"><text>本月暂无完成订单</text></view>
    </view>

    <!-- 流水列表（按日分组） -->
    <view v-if="loading" class="loading-tip"><text>加载中…</text></view>
    <view v-for="([date, list]) in grouped" :key="date" class="day-group">
      <text class="day-head">{{ date }}</text>
      <view
        v-for="e in list"
        :key="e.id"
        class="card entry"
        hover-class="press-dim"
        @longpress="delEntry(e)"
      >
        <view class="entry-icon" :class="e.type.toLowerCase()">
          <text>{{ e.type === 'EXPENSE' ? '支' : e.type === 'REFUND' ? '退' : '收' }}</text>
        </view>
        <view class="entry-info">
          <text class="entry-title">{{ typeLabel(e) }}<text v-if="e.source === 'ORDER'" class="order-tag">订单</text></text>
          <text v-if="e.remark" class="entry-remark">{{ e.remark }}</text>
        </view>
        <text class="entry-amount" :class="e.type === 'EXPENSE' ? 'out' : 'in'">
          {{ e.type === 'EXPENSE' ? '−' : '+' }}¥{{ (e.amountFen / 100).toFixed(2) }}
        </text>
      </view>
    </view>
    <view v-if="!loading && entries.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-ledger.png" mode="aspectFit" />
      <text class="empty-tip">{{ month }} 还没有收支记录</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 60rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.month-row {
  display: flex; align-items: center; justify-content: center; gap: 64rpx;
  padding: 8rpx 0 20rpx;
}
.month { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.arrow { font-size: 40rpx; color: v-bind('theme.title'); padding: 0 16rpx; line-height: 1; }
.cards { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.summary { flex: 1; padding: 24rpx 0; display: flex; flex-direction: column; align-items: center; }
.s-label { font-size: 22rpx; color: v-bind('theme.sub'); }
.s-value { font-size: 30rpx; font-weight: 700; margin-top: 8rpx; }
.actions { display: flex; padding: 20rpx 32rpx; gap: 40rpx; margin-bottom: 16rpx; }
.act { font-size: 28rpx; }
.act.add { color: v-bind('theme.primaryBtn'); font-weight: 700; }
.act.toggle { color: v-bind('theme.title'); margin-left: auto; }
.act.export { color: v-bind('theme.title'); }

.chart { padding: 28rpx 32rpx; margin-bottom: 16rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.stats-title { display: block; margin-top: 32rpx; }
.bar-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; }
.bar-date { width: 80rpx; font-size: 22rpx; color: v-bind('theme.sub'); }
.bars { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.bar { height: 14rpx; border-radius: 7rpx; }
.income-bar { background: v-bind('theme.income'); }
.expense-bar { background: v-bind('theme.expense'); }
.bar-val { font-size: 20rpx; color: v-bind('theme.sub'); width: 150rpx; text-align: right; }
.chart-empty { padding: 20rpx 0; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.stat-row { display: flex; align-items: center; gap: 16rpx; margin-top: 18rpx; }
.stat-rank {
  width: 36rpx; height: 36rpx; border-radius: 10rpx;
  background: #f2f0ea; color: v-bind('theme.sub');
  font-size: 22rpx; text-align: center; line-height: 36rpx;
}
.stat-rank.top { background: v-bind('theme.primaryLight'); color: v-bind('theme.primaryBtn'); font-weight: 700; }
.stat-name { flex: 1; font-size: 26rpx; color: v-bind('theme.title'); }
.stat-qty { font-size: 24rpx; color: v-bind('theme.sub'); }
.stat-sales { font-size: 26rpx; font-weight: 600; color: v-bind('theme.income'); }

.loading-tip { padding: 40rpx 0; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.day-head { display: block; font-size: 24rpx; color: v-bind('theme.sub'); margin: 20rpx 0 12rpx; }
.entry {
  display: flex; align-items: center; gap: 20rpx;
  padding: 22rpx 28rpx; margin-bottom: 12rpx;
}
.entry-icon {
  width: 64rpx; height: 64rpx; border-radius: 18rpx;
  display: flex; align-items: center; justify-content: center;
  font-size: 26rpx; font-weight: 700;
}
.entry-icon.income, .entry-icon.refund { background: #fdefe2; color: v-bind('theme.income'); }
.entry-icon.expense { background: #e9f3ec; color: v-bind('theme.expense'); }
.entry-info { flex: 1; min-width: 0; }
.entry-title { font-size: 28rpx; font-weight: 500; color: v-bind('theme.title'); }
.order-tag {
  display: inline-block; margin-left: 10rpx;
  font-size: 18rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 6rpx;
  padding: 0 8rpx;
}
.entry-remark { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.entry-amount { font-size: 30rpx; font-weight: 700; }
.entry-amount.in { color: v-bind('theme.income'); }
.entry-amount.out { color: v-bind('theme.expense'); }
.empty { padding: 90rpx 0; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.empty-img { width: 160rpx; height: 160rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
</style>
