<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi } from '@/api/ledger';
import type { LedgerEntryView, MonthSummary, SavingSummary } from '@/api/ledger';
import { getCurrentKitchenId } from '@/api/kitchen';
import { getApiBase } from '@/api/config';
import { onShow } from '@dcloudio/uni-app';
import { computed, nextTick, ref, watch } from 'vue';
import CalendarPicker from '@/components/calendar-picker.vue';
import CustomTabbar from '@/components/custom-tabbar.vue';
import InputDialog from '@/components/input-dialog.vue';
import { monthStr } from '@/utils/fmt';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const month = ref(monthStr());
const summary = ref<MonthSummary>({ income: 0, refund: 0, expense: 0, balance: 0, days: [] });
const entries = ref<LedgerEntryView[]>([]);
const showStats = ref(false);
const loading = ref(true);
const loadFailed = ref(false);
const calVisible = ref(false);
const calSelected = computed(() => `${month.value}-01`);

// ===== 显示货币切换（金额按汇率换算显示，数据始终以人民币分存储） =====
type Cur = 'CNY' | 'HKD' | 'USD';
const CUR_KEY = 'ledgerDisplayCur';
const SYMBOL: Record<Cur, string> = { CNY: '¥', HKD: 'HK$', USD: '$' };
const CUR_NAME: Record<Cur, string> = { CNY: '人民币', HKD: '港币', USD: '美元' };
const displayCur = ref<Cur>((uni.getStorageSync(CUR_KEY) as Cur) || 'CNY');
const fx = ref<{ HKD: number; USD: number }>({ HKD: 0.92, USD: 7.24 });

function rateOf(cur: Cur): number {
  return cur === 'HKD' ? fx.value.HKD : cur === 'USD' ? fx.value.USD : 1;
}
/** 人民币分 → 显示货币元字符串（含符号） */
function money(cnyFen: number): string {
  const c = displayCur.value;
  const val = c === 'CNY' ? cnyFen / 100 : (cnyFen / 100) / rateOf(c);
  return SYMBOL[c] + val.toFixed(2);
}
function switchCur() {
  const order: Cur[] = ['CNY', 'HKD', 'USD'];
  displayCur.value = order[(order.indexOf(displayCur.value) + 1) % order.length];
  uni.setStorageSync(CUR_KEY, displayCur.value);
  uni.showToast({ title: '按' + CUR_NAME[displayCur.value] + '显示', icon: 'none' });
}

// ===== 存钱罐 =====
const saving = ref<SavingSummary>({ balanceCnyFen: 0, balanceHkdFen: 0, balanceUsdFen: 0, recent: [] });
const savingVisible = ref(false);
const savingCurrency = ref<Cur>('CNY');
const savingAmount = ref('');
const savingKind = ref<'DEPOSIT' | 'WITHDRAW'>('DEPOSIT');
const savingName = ref('');

function savingTotalCny(): number {
  const s = saving.value;
  return s.balanceCnyFen + Math.round(s.balanceHkdFen * fx.value.HKD) + Math.round(s.balanceUsdFen * fx.value.USD);
}
function cycleSavingCur() {
  const order: Cur[] = ['CNY', 'HKD', 'USD'];
  savingCurrency.value = order[(order.indexOf(savingCurrency.value) + 1) % order.length];
}

function openSaving(kind: 'DEPOSIT' | 'WITHDRAW') {
  savingKind.value = kind;
  savingAmount.value = '';
  savingVisible.value = true;
}
function onSavingSave(v: string) {
  // v: InputDialog 输入的金额
  savingVisible.value = false;
  const fen = Math.round(parseFloat(v || savingAmount.value) * 100);
  if (!fen || fen <= 0 || !kitchenId.value) {
    uni.showToast({ title: '请输入正确金额', icon: 'none' });
    return;
  }
  ledgerApi
    .savingAdd(kitchenId.value, {
      name: savingName.value.trim() || (savingKind.value === 'DEPOSIT' ? '存入' : '取出'),
      currency: savingCurrency.value,
      amountFen: fen,
      kind: savingKind.value,
    })
    .then(() => {
      uni.showToast({ title: savingKind.value === 'DEPOSIT' ? '已存入' : '已取出', icon: 'none' });
      loadSaving();
    });
}

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
    loadFailed.value = false;
    ledgerApi.fx(kitchenId.value).then((f) => (fx.value = f)).catch(() => {});
    loadSaving();
  } catch {
    // 网络失败且无缓存：清空并给出可见的失败态，
    // 避免"月份文字变了但数据还是上个月的"这种看似切换失灵的误导
    summary.value = { income: 0, refund: 0, expense: 0, balance: 0, days: [] };
    entries.value = [];
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

function loadSaving() {
  if (!kitchenId.value) return;
  ledgerApi.savingsSummary(kitchenId.value).then((s) => (saving.value = s)).catch(() => {});
}

function goAdd() {
  uni.navigateTo({ url: `/pages/ledger/add?month=${month.value}` });
}

function goCategories() {
  uni.navigateTo({ url: '/pages/ledger/categories' });
}

function exportPdf() {
  if (!kitchenId.value) return;
  uni.showLoading({ title: '生成PDF…' });
  const url = getApiBase() + ledgerApi.exportPdfUrl(kitchenId.value, month.value);
  const token = uni.getStorageSync('token');
  // #ifdef H5
  fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
    .then((r) => {
      if (!r.ok) throw new Error('' + r.status);
      return r.blob();
    })
    .then((b) => {
      uni.hideLoading();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(b);
      a.download = `共享厨房账单-${month.value}.pdf`;
      a.click();
      URL.revokeObjectURL(a.href);
    })
    .catch(() => {
      uni.hideLoading();
      uni.showToast({ title: '导出失败', icon: 'none' });
    });
  // #endif
  // #ifndef H5
  uni.downloadFile({
    url,
    header: token ? { Authorization: `Bearer ${token}` } : {},
    success: (d) => {
      uni.hideLoading();
      uni.openDocument({ filePath: d.tempFilePath, showMenu: true });
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: '下载失败', icon: 'none' });
    },
  });
  // #endif
}

function typeLabel(e: LedgerEntryView) {
  if (e.type === 'EXPENSE') return e.category || '支出';
  if (e.type === 'REFUND') return '退款冲销';
  return e.category || '收入';
}

// ----- 分费用展开：点带分项的流水展开/收起明细 -----
const expanded = ref<Record<number, boolean>>({});

function hasSubItems(e: LedgerEntryView) {
  return !!(e.subItems && e.subItems.length);
}

// ----- 条目左滑（像冰箱食材卡）：露出「修改 / 删除」 -----
const BTN_W = uni.upx2px(240);
const openedId = ref<number | null>(null);
const dragId = ref<number | null>(null);
const dragDx = ref(0);
let touchStartX = 0;
let touchStartY = 0;
let touchBaseX = 0;
let dragging = false;
let decided = false;

function entryStyle(id: number) {
  if (dragId.value === id) {
    return { transform: `translateX(${dragDx.value}px)`, transition: 'none' };
  }
  if (openedId.value === id) {
    return { transform: `translateX(${-BTN_W}px)` };
  }
  return { transform: 'translateX(0px)' };
}

function onEntryTouchStart(e: TouchEvent, id: number) {
  const t = e.touches[0];
  touchStartX = t.clientX;
  touchStartY = t.clientY;
  touchBaseX = openedId.value === id ? -BTN_W : 0;
  dragging = false;
  decided = false;
  dragId.value = null;
}

function onEntryTouchMove(e: TouchEvent, id: number) {
  const t = e.touches[0];
  const dx = t.clientX - touchStartX;
  const dy = t.clientY - touchStartY;
  if (!decided) {
    if (Math.abs(dx) < 8) return;
    if (Math.abs(dx) > Math.abs(dy)) {
      decided = true;
      dragging = true;
      dragId.value = id;
    } else {
      decided = true;
    }
  }
  if (dragging) {
    dragDx.value = Math.max(-BTN_W, Math.min(0, touchBaseX + dx));
  }
}

function onEntryTouchEnd(id: number) {
  if (dragging && dragId.value === id) {
    openedId.value = dragDx.value < -BTN_W / 2 ? id : null;
  }
  dragging = false;
  dragId.value = null;
}

function onEntryTap(e: LedgerEntryView) {
  if (openedId.value !== null) {
    openedId.value = null;
    return;
  }
  // 点击 = 展开明细（分项 + 图片 + 支付方式）
  expanded.value = { ...expanded.value, [e.id]: !expanded.value[e.id] };
}

function editEntry(e: LedgerEntryView) {
  openedId.value = null;
  uni.navigateTo({ url: `/pages/ledger/add?id=${e.id}` });
}

function delEntrySwipe(e: LedgerEntryView) {
  openedId.value = null;
  delEntry(e);
}

function previewEntryImage(e: LedgerEntryView) {
  if (e.imageUrl) {
    uni.previewImage({ urls: [getApiBase() + e.imageUrl] });
  }
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
    <view class="page-scroll">
    <!-- 月份切换（点月份打开日历直接跳月） -->
    <view class="month-row">
      <text class="arrow" hover-class="press-dim" @tap="prevMonth">‹</text>
      <text class="month" hover-class="press-dim" @tap="calVisible = true">{{ month }} ▾</text>
      <text class="arrow" hover-class="press-dim" @tap="nextMonth">›</text>
    </view>

    <!-- 三卡汇总（金额随显示货币换算；卡片可点切换货币） -->
    <view class="cards" @tap="switchCur">
      <view v-for="c in cards" :key="c.label" class="card summary">
        <text class="s-label">{{ c.label }}</text>
        <text class="s-value" :style="{ color: c.color }">{{ money(c.value) }}</text>
      </view>
      <view class="card summary cur-hint">
        <text class="s-label">显示货币</text>
        <text class="s-value">{{ CUR_NAME[displayCur] }} ⇄</text>
      </view>
    </view>

    <!-- 存钱罐 -->
    <view class="card save-card">
      <view class="save-main">
        <text class="s-label">存钱罐 · 合计约 {{ money(savingTotalCny()) }} · 存入币种: <text class="cur-pick" @tap.stop="cycleSavingCur">{{ CUR_NAME[savingCurrency] }} ⇄</text></text>
        <view class="save-balances">
          <text v-if="saving.balanceCnyFen" class="save-b">¥{{ (saving.balanceCnyFen / 100).toFixed(2) }}</text>
          <text v-if="saving.balanceHkdFen" class="save-b">HK${{ (saving.balanceHkdFen / 100).toFixed(2) }}</text>
          <text v-if="saving.balanceUsdFen" class="save-b">${{ (saving.balanceUsdFen / 100).toFixed(2) }}</text>
          <text v-if="!saving.balanceCnyFen && !saving.balanceHkdFen && !saving.balanceUsdFen" class="save-b muted">还没有存款</text>
        </view>
      </view>
      <view class="save-ops">
        <text class="save-op dep" hover-class="press-dim" @tap="openSaving('DEPOSIT')">存入</text>
        <text class="save-op wd" hover-class="press-dim" @tap="openSaving('WITHDRAW')">取出</text>
      </view>
    </view>

    <!-- 操作行 -->
    <view class="card actions">
      <text class="act add" hover-class="press-dim" @tap="goAdd">＋ 记一笔</text>
      <text class="act toggle" hover-class="press-dim" @tap="showStats = !showStats">
        {{ showStats ? '收起总统计' : '总统计' }}
      </text>
      <text class="act cats" hover-class="press-dim" @tap="goCategories">分类管理</text>
      <text class="act export" hover-class="press-dim" @tap="exportPdf">导出PDF</text>
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

    <!-- 流水列表（按日分组；条目左滑=修改/删除，点击=展开明细） -->
    <view v-if="loading" class="loading-tip"><text>加载中…</text></view>
    <view v-for="([date, list]) in grouped" :key="date" class="day-group">
      <text class="day-head">{{ Number(date.slice(5, 7)) }}月{{ Number(date.slice(8, 10)) }}日</text>
      <view v-for="e in list" :key="e.id" class="swipe-cell">
        <view class="swipe-actions">
          <text class="swipe-btn edit" hover-class="press-dim" @tap.stop="editEntry(e)">修改</text>
          <text class="swipe-btn del" hover-class="press-dim" @tap.stop="delEntrySwipe(e)">删除</text>
        </view>
        <view
          class="card entry swipe-content"
          :style="entryStyle(e.id)"
          @touchstart="onEntryTouchStart($event, e.id)"
          @touchmove.stop.prevent="onEntryTouchMove($event, e.id)"
          @touchend="onEntryTouchEnd(e.id)"
          @touchcancel="onEntryTouchEnd(e.id)"
          @tap="onEntryTap(e)"
        >
          <view class="entry-row">
            <view class="entry-icon" :class="e.type.toLowerCase()">
              <text>{{ e.type === 'EXPENSE' ? '支' : e.type === 'REFUND' ? '退' : '收' }}</text>
            </view>
            <view class="entry-info">
              <text class="entry-title">
                {{ typeLabel(e) }}
                <text v-if="e.paymentMethod" class="pm-tag">{{ e.paymentMethod }}</text>
                <text v-if="e.currency && e.currency !== 'CNY'" class="cur-tag">{{ e.currency }}</text>
                <text v-if="hasSubItems(e)" class="sub-tag">分{{ e.subItems!.length }}项</text>
              </text>
              <text v-if="e.remark" class="entry-remark">{{ e.remark }}</text>
            </view>
            <view class="entry-right">
              <text class="entry-amount" :class="e.type === 'EXPENSE' ? 'out' : 'in'">
                {{ e.type === 'EXPENSE' ? '−' : '+' }}{{ money(e.amountFen) }}
              </text>
              <text v-if="hasSubItems(e) || e.imageUrl" class="expand-arrow" :class="{ open: expanded[e.id] }">▸</text>
            </view>
          </view>
          <!-- 展开：原币种金额 / 支付方式 / 分项 / 账单图片 -->
          <view v-if="expanded[e.id]" class="entry-subs">
            <view v-if="e.currency !== 'CNY' && e.origAmountFen" class="entry-sub">
              <text class="entry-sub-name">原币种金额</text>
              <text class="entry-sub-amount">{{ SYMBOL[e.currency as 'CNY'|'HKD'|'USD'] }}{{ (e.origAmountFen / 100).toFixed(2) }}</text>
            </view>
            <view v-for="(s, si) in (e.subItems || [])" :key="si" class="entry-sub">
              <text class="entry-sub-name">{{ s.name }}</text>
              <text class="entry-sub-amount">¥{{ (s.amountFen / 100).toFixed(2) }}</text>
            </view>
            <view v-if="e.imageUrl" class="entry-photo-row" @tap.stop="previewEntryImage(e)">
              <image class="entry-photo" :src="getApiBase() + e.imageUrl" mode="aspectFill" />
              <text class="entry-photo-tip">点击查看账单大图</text>
            </view>
          </view>
        </view>
      </view>
    </view>
    <view v-if="loadFailed && !loading" class="card empty" @tap="load">
      <image class="empty-img" src="/static/icons/empty-ledger.png" mode="aspectFit" />
      <text class="empty-tip">{{ month }} 数据加载失败，点此重试</text>
    </view>
    <view v-if="!loadFailed && !loading && entries.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-ledger.png" mode="aspectFit" />
      <text class="empty-tip">{{ month }} 还没有收支记录</text>
    </view>

    <!-- 存钱弹窗：输入金额；币种/名目用当前选择 -->
    <InputDialog
      :visible="savingVisible"
      :title="(savingKind === 'DEPOSIT' ? '存入 ' : '取出 ') + CUR_NAME[savingCurrency] + '（存入币种点存钱卡上的币种标签切换）'"
      :default-value="savingAmount"
      placeholder="金额，如 100.00"
      :maxlength="12"
      @confirm="onSavingSave"
      @close="savingVisible = false"
    />

    <CalendarPicker
      :visible="calVisible"
      :selected="calSelected"
      @select="onCalPick"
      @close="calVisible = false"
    />
  
        </view>
    <CustomTabbar current="ledger" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex; flex-direction: column;
  /* WebView 的 vh 计算不稳（部分机型 100vh > 可视区导致顶部裁切/底栏溢出），
     App.vue 启动时 JS 写入实际屏幕 px */
  height: calc(var(--sk-vh, 100vh) - var(--window-top, 0px)); /* 减去fixed导航栏高度，配合uni-page-body的padding-top让位 */
  overflow: hidden;
}
.page-scroll {
  flex: 1; overflow-y: auto;
  min-height: 0;
  padding: 24rpx;
  padding-bottom: 60rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.swipe-cell { position: relative; margin-bottom: 16rpx; border-radius: 24rpx; overflow: hidden; }
.swipe-actions {
  position: absolute; top: 0; right: 0; bottom: 0;
  display: flex; align-items: stretch; z-index: 1;
}
.swipe-btn {
  width: 120rpx; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 26rpx; font-weight: 600;
}
.swipe-btn.edit { background: v-bind('theme.success'); }
.swipe-btn.del { background: v-bind('theme.danger'); }
.swipe-content { position: relative; z-index: 2; will-change: transform; }

.save-card { display: flex; align-items: center; gap: 20rpx; padding: 22rpx 26rpx; margin-bottom: 16rpx; }
.save-main { flex: 1; min-width: 0; }
.save-balances { display: flex; gap: 18rpx; margin-top: 6rpx; flex-wrap: wrap; }
.save-b { font-size: 28rpx; font-weight: 700; color: v-bind('theme.title'); }
.save-b.muted { color: v-bind('theme.sub'); font-weight: 400; }
.save-ops { display: flex; gap: 14rpx; }
.save-op {
  padding: 12rpx 28rpx; border-radius: 40rpx; font-size: 26rpx; font-weight: 600;
}
.save-op.dep { background: v-bind('theme.primaryBtn'); color: #fff; }
.save-op.wd { color: v-bind('theme.primaryBtn'); border: 2rpx solid v-bind('theme.primaryBtn'); }
.cur-pick { color: v-bind('theme.primaryBtn'); font-weight: 600; }
.cur-hint { background: v-bind('theme.primaryLight'); }
.pm-tag {
  font-size: 20rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 8rpx;
  padding: 2rpx 10rpx; margin-left: 8rpx;
}
.cur-tag {
  font-size: 20rpx; color: #B8860B;
  border: 2rpx solid #D4AF37; border-radius: 8rpx;
  padding: 2rpx 10rpx; margin-left: 8rpx;
}
.entry-photo-row { display: flex; align-items: center; gap: 16rpx; padding-top: 14rpx; }
.entry-photo { width: 160rpx; height: 160rpx; border-radius: 14rpx; background: v-bind('theme.primaryLight'); }
.entry-photo-tip { font-size: 22rpx; color: v-bind('theme.sub'); }

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
  padding: 22rpx 28rpx; margin-bottom: 12rpx;
}
.entry-row {
  display: flex; align-items: center; gap: 20rpx;
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
.sub-tag {
  display: inline-block; margin-left: 10rpx;
  font-size: 18rpx; color: v-bind('theme.expense');
  border: 2rpx solid v-bind('theme.expense'); border-radius: 6rpx;
  padding: 0 8rpx;
}
.entry-remark { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.entry-right { display: flex; flex-direction: column; align-items: flex-end; }
.entry-amount { font-size: 30rpx; font-weight: 700; }
.entry-amount.in { color: v-bind('theme.income'); }
.entry-amount.out { color: v-bind('theme.expense'); }
.expand-arrow {
  font-size: 20rpx; color: v-bind('theme.sub'); margin-top: 2rpx;
  transition: transform 0.2s; transform: rotate(90deg);
}
.expand-arrow.open { transform: rotate(270deg); }
.entry-subs {
  margin-top: 16rpx; padding-top: 14rpx;
  border-top: 2rpx dashed v-bind('theme.divider');
}
.entry-sub { display: flex; align-items: center; padding: 8rpx 0 8rpx 84rpx; }
.entry-sub-name { flex: 1; font-size: 25rpx; color: v-bind('theme.sub'); }
.entry-sub-amount { font-size: 25rpx; color: v-bind('theme.title'); }
.empty { padding: 90rpx 0; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.empty-img { width: 160rpx; height: 160rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
</style>
