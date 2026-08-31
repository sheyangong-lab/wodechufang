<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi } from '@/api/ledger';
import type { LedgerEntryView, MonthSummary } from '@/api/ledger';
import { getCurrentKitchenId } from '@/api/kitchen';
import { getApiBase } from '@/api/config';
import { onShow } from '@dcloudio/uni-app';
import { computed, nextTick, ref, watch } from 'vue';
import CalendarPicker from '@/components/calendar-picker.vue';
import { monthStr } from '@/utils/fmt';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const month = ref(monthStr());
const summary = ref<MonthSummary>({ income: 0, refund: 0, expense: 0, balance: 0, days: [] });
const entries = ref<LedgerEntryView[]>([]);
const showStats = ref(false);
const loading = ref(true);
const calVisible = ref(false);
const calSelected = computed(() => `${month.value}-01`);

function onCalPick(date: string | null) {
  calVisible.value = false;
  if (!date) return;
  const m = date.slice(0, 7);
  if (m !== month.value) {
    month.value = m;
    load();
  }
}

const cards = computed(() => [
  { label: '收入', value: summary.value.income, color: theme.income },
  { label: '支出', value: summary.value.expense, color: theme.expense },
  { label: '结余', value: summary.value.balance, color: theme.title },
]);

const grouped = computed(() => {
  const map = new Map<string, LedgerEntryView[]>();
  entries.value.forEach((e) => {
    const list = map.get(e.date) || [];
    list.push(e);
    map.set(e.date, list);
  });
  return [...map.entries()];
});

// ===== 总统计：支出构成（饼图数据） =====
const PIE_COLORS = ['#EFA63C', '#E8836F', '#5BA47C', '#E8A23D', '#7BA6C9', '#B08BC9', '#C96B6B', '#8C7F6A'];

const pieSlices = computed(() => {
  const byCat = new Map<string, number>();
  entries.value
    .filter((e) => e.type === 'EXPENSE')
    .forEach((e) => byCat.set(e.category, (byCat.get(e.category) || 0) + e.amountFen));
  const total = [...byCat.values()].reduce((a, b) => a + b, 0);
  const list = [...byCat.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([name, fen], i) => ({
      name,
      fen,
      pct: total > 0 ? (fen / total) * 100 : 0,
      color: PIE_COLORS[i % PIE_COLORS.length],
    }));
  return { total, list };
});

const pieGradient = computed(() => {
  const { list, total } = pieSlices.value;
  if (total === 0) return theme.chipBg;
  let acc = 0;
  const stops = list.map((s) => {
    const from = (acc / total) * 360;
    acc += s.fen;
    const to = (acc / total) * 360;
    return `${s.color} ${from}deg ${to}deg`;
  });
  return `conic-gradient(${stops.join(', ')})`;
});

// ===== 总统计：近7日消费（平滑曲线数据） =====
const trend = computed(() => {
  const [y, m] = month.value.split('-').map(Number);
  const now = new Date();
  const isCurrentMonth = month.value === monthStr(now);
  const endDate = isCurrentMonth ? new Date() : new Date(y, m, 0); // 非当前月取该月最后一天
  const byDay = new Map<string, number>();
  entries.value
    .filter((e) => e.type === 'EXPENSE')
    .forEach((e) => byDay.set(e.date, (byDay.get(e.date) || 0) + e.amountFen));
  const points: { label: string; fen: number }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    points.push({ label: `${d.getMonth() + 1}/${d.getDate()}`, fen: byDay.get(key) || 0 });
  }
  return points;
});

const trendMax = computed(() => Math.max(1, ...trend.value.map((p) => p.fen)));

watch([showStats, trend, entries], () => {
  if (showStats.value) nextTick(() => setTimeout(drawTrend, 60));
});

/** 平滑消费曲线：Catmull-Rom 转 Bezier，H5 直接操作 canvas 保证清晰度 */
function drawTrend() {
  // #ifdef H5
  const host = document.getElementById('trend-canvas');
  const canvas = host ? (host.querySelector('canvas') as HTMLCanvasElement | null) : null;
  if (!canvas) return;
  const dpr = window.devicePixelRatio || 1;
  const cssW = canvas.clientWidth || canvas.parentElement!.clientWidth;
  const cssH = canvas.clientHeight || 180;
  canvas.width = cssW * dpr;
  canvas.height = cssH * dpr;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, cssW, cssH);

  const pts = trend.value;
  const padL = 10, padR = 10, padT = 18, padB = 22;
  const w = cssW - padL - padR;
  const h = cssH - padT - padB;
  const max = trendMax.value;
  const px = (i: number) => padL + (w * i) / (pts.length - 1);
  const py = (fen: number) => padT + h - (h * fen) / max;
  const xy = pts.map((p, i) => ({ x: px(i), y: py(p.fen) }));

  // 网格基线
  ctx.strokeStyle = theme.divider;
  ctx.lineWidth = 1;
  [0.5, 1].forEach((f) => {
    const y = padT + h * f;
    ctx.beginPath();
    ctx.moveTo(padL, y);
    ctx.lineTo(cssW - padR, y);
    ctx.stroke();
  });

  // 曲线下渐变填充
  const grad = ctx.createLinearGradient(0, padT, 0, padT + h);
  grad.addColorStop(0, 'rgba(239, 166, 60, 0.32)');
  grad.addColorStop(1, 'rgba(239, 166, 60, 0.02)');
  ctx.beginPath();
  ctx.moveTo(xy[0].x, xy[0].y);
  for (let i = 0; i < xy.length - 1; i++) {
    const p0 = xy[Math.max(0, i - 1)];
    const p1 = xy[i];
    const p2 = xy[i + 1];
    const p3 = xy[Math.min(xy.length - 1, i + 2)];
    ctx.bezierCurveTo(
      p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
      p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
      p2.x, p2.y
    );
  }
  ctx.lineTo(xy[xy.length - 1].x, padT + h);
  ctx.lineTo(xy[0].x, padT + h);
  ctx.closePath();
  ctx.fillStyle = grad;
  ctx.fill();

  // 曲线描边
  ctx.beginPath();
  ctx.moveTo(xy[0].x, xy[0].y);
  for (let i = 0; i < xy.length - 1; i++) {
    const p0 = xy[Math.max(0, i - 1)];
    const p1 = xy[i];
    const p2 = xy[i + 1];
    const p3 = xy[Math.min(xy.length - 1, i + 2)];
    ctx.bezierCurveTo(
      p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
      p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
      p2.x, p2.y
    );
  }
  ctx.strokeStyle = theme.primaryBtn;
  ctx.lineWidth = 2.5;
  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.stroke();

  // 数据点
  xy.forEach((p) => {
    ctx.beginPath();
    ctx.arc(p.x, p.y, 3, 0, Math.PI * 2);
    ctx.fillStyle = theme.card;
    ctx.fill();
    ctx.strokeStyle = theme.primaryBtn;
    ctx.lineWidth = 2;
    ctx.stroke();
  });

  // 日期标签
  ctx.fillStyle = theme.chartText;
  ctx.font = '10px system-ui, sans-serif';
  ctx.textAlign = 'center';
  pts.forEach((p, i) => ctx.fillText(p.label, xy[i].x, cssH - 6));
  // #endif
}

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
    const [s, list] = await Promise.all([
      ledgerApi.summary(kitchenId.value, month.value),
      ledgerApi.list(kitchenId.value, month.value),
    ]);
    summary.value = s;
    entries.value = list;
  } catch {
    // toast 已统一弹出
  } finally {
    loading.value = false;
  }
}

function goAdd() {
  uni.navigateTo({ url: `/pages/ledger/add?month=${month.value}` });
}

function goCategories() {
  uni.navigateTo({ url: '/pages/ledger/categories' });
}

function exportExcel() {
  if (!kitchenId.value) return;
  uni.showLoading({ title: '生成中…' });
  ledgerApi
    .export(kitchenId.value, month.value)
    .then((res) => {
      const full = getApiBase() + res.url;
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
    <!-- 月份切换（点月份打开日历直接跳月） -->
    <view class="month-row">
      <text class="arrow" hover-class="press-dim" @tap="prevMonth">‹</text>
      <text class="month" hover-class="press-dim" @tap="calVisible = true">{{ month }} ▾</text>
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
        {{ showStats ? '收起总统计' : '总统计' }}
      </text>
      <text class="act cats" hover-class="press-dim" @tap="goCategories">分类管理</text>
      <text class="act export" hover-class="press-dim" @tap="exportExcel">导出Excel</text>
    </view>

    <!-- 总统计 -->
    <view v-if="showStats" class="card chart">
      <text class="sec-title">支出构成 · {{ month }}</text>
      <view v-if="pieSlices.total > 0" class="pie-row">
        <view class="pie-box">
          <view class="pie" :style="{ background: pieGradient }">
            <view class="pie-hole">
              <text class="pie-total">¥{{ (pieSlices.total / 100).toFixed(0) }}</text>
              <text class="pie-total-label">总支出</text>
            </view>
          </view>
        </view>
        <view class="legend">
          <view v-for="s in pieSlices.list" :key="s.name" class="legend-row">
            <view class="legend-dot" :style="{ background: s.color }" />
            <text class="legend-name">{{ s.name }}</text>
            <text class="legend-pct">{{ s.pct.toFixed(0) }}%</text>
            <text class="legend-fen">¥{{ (s.fen / 100).toFixed(0) }}</text>
          </view>
        </view>
      </view>
      <view v-else class="chart-empty"><text>本月暂无支出记录</text></view>

      <text class="sec-title trend-title">近 7 天消费趋势</text>
      <view class="trend-box">
        <canvas id="trend-canvas" class="trend-canvas" />
      </view>
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

    <CalendarPicker
      :visible="calVisible"
      :selected="calSelected"
      @select="onCalPick"
      @close="calVisible = false"
    />
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
.actions { display: flex; padding: 20rpx 24rpx; margin-bottom: 16rpx; }
.act { font-size: 27rpx; flex: 1; text-align: center; }
.act.add { color: v-bind('theme.primaryBtn'); font-weight: 700; }
.act.toggle { color: v-bind('theme.title'); }
.act.cats { color: v-bind('theme.title'); }
.act.export { color: v-bind('theme.title'); }

.chart { padding: 28rpx 32rpx; margin-bottom: 16rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.trend-title { display: block; margin-top: 32rpx; }
.stats-title { display: block; margin-top: 32rpx; }

.pie-row { display: flex; align-items: center; gap: 32rpx; margin-top: 24rpx; }
.pie-box { flex-shrink: 0; }
.pie {
  width: 260rpx; height: 260rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.pie-hole {
  width: 160rpx; height: 160rpx; border-radius: 50%;
  background: v-bind('theme.card');
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.pie-total { font-size: 30rpx; font-weight: 700; color: v-bind('theme.title'); }
.pie-total-label { font-size: 20rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.legend { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 14rpx; }
.legend-row { display: flex; align-items: center; gap: 12rpx; }
.legend-dot { width: 18rpx; height: 18rpx; border-radius: 6rpx; flex-shrink: 0; }
.legend-name { flex: 1; font-size: 24rpx; color: v-bind('theme.title'); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.legend-pct { font-size: 24rpx; font-weight: 700; color: v-bind('theme.title'); width: 64rpx; text-align: right; }
.legend-fen { font-size: 22rpx; color: v-bind('theme.sub'); width: 90rpx; text-align: right; }

.trend-box { margin-top: 20rpx; }
.trend-canvas { width: 100%; height: 360rpx; }

.chart-empty { padding: 20rpx 0; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.stat-row { display: flex; align-items: center; gap: 16rpx; margin-top: 18rpx; }
.stat-rank {
  width: 36rpx; height: 36rpx; border-radius: 10rpx;
  background: v-bind('theme.chipBg'); color: v-bind('theme.sub');
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
.entry-icon.income, .entry-icon.refund { background: v-bind('theme.primaryLight'); color: v-bind('theme.income'); }
.entry-icon.expense { background: v-bind('theme.successLight'); color: v-bind('theme.expense'); }
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
