<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, groupCategories, fen } from '@/api/ledger';
import type { BudgetView, BudgetItemView, LedgerCategoryView, BudgetItemPut } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { monthStr } from '@/utils/fmt';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import InputDialog from '@/components/input-dialog.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const month = ref(monthStr());
const budget = ref<BudgetView | null>(null);
const expenseCats = ref<LedgerCategoryView[]>([]);
const saving = ref(false);

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
  if (!kitchenId.value) return;
  try {
    expenseCats.value = groupCategories(await ledgerApi.categories(kitchenId.value)).expense;
  } catch {
    // toast 已统一弹出
  }
});

async function load() {
  if (!kitchenId.value) return;
  try {
    budget.value = await ledgerApi.budgets(kitchenId.value, month.value);
  } catch {
    // toast 已统一弹出
  }
}

function shiftMonth(dir: -1 | 1) {
  const [y, m] = month.value.split('-').map(Number);
  const d = new Date(y, m - 1 + dir, 1);
  month.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  load();
}

function prevMonthStr(): string {
  const [y, m] = month.value.split('-').map(Number);
  const d = new Date(y, m - 2, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

const items = computed(() => budget.value?.items || []);
const total = computed(() => budget.value?.total || { budgetFen: null, usedFen: null, pct: null });

/** 进度条宽度与颜色：超支红色、接近超支橙色、正常绿色 */
function barStyle(pct: number | null): { width: string; background: string } {
  const p = Math.max(0, pct || 0);
  const color = p >= 100 ? theme.danger : p >= 80 ? theme.warning : theme.expense;
  return { width: Math.min(100, p) + '%', background: color };
}

/** 整体覆盖保存（后端 PUT：0=删项） */
function putAll(totalFen: number | null, newItems: BudgetItemPut[]) {
  if (!kitchenId.value || saving.value) return;
  saving.value = true;
  ledgerApi
    .putBudgets(kitchenId.value, { month: month.value, totalFen, items: newItems })
    .then(() => {
      uni.showToast({ title: '已保存', icon: 'none' });
      return load();
    })
    .finally(() => (saving.value = false));
}

// ===== 总预算编辑 =====
const totalDialogVisible = ref(false);

function editTotal() {
  totalDialogVisible.value = true;
}

function onTotalConfirm(value: string) {
  totalDialogVisible.value = false;
  const v = yuanToFen(value);
  if (v == null || v < 0) {
    uni.showToast({ title: '请输入正确的金额', icon: 'none' });
    return;
  }
  putAll(v, items.value.map((it) => ({ category: it.category, amountFen: it.budgetFen || 0 })));
}

// ===== 分类预算：点行 → 改额度 / 删除 =====
const rowSheetVisible = ref(false);
const rowTarget = ref<BudgetItemView | null>(null);
const amountDialogVisible = ref(false);
/** 当前正在输入额度的分类（新增或编辑） */
const inputTarget = ref<string>('');

function onRowTap(it: BudgetItemView) {
  rowTarget.value = it;
  rowSheetVisible.value = true;
}

function onRowAction(key: string) {
  rowSheetVisible.value = false;
  const it = rowTarget.value;
  if (!it) return;
  if (key === 'edit') {
    inputTarget.value = it.category;
    amountDialogTitle.value = `「${it.category}」预算额度`;
    amountDialogDefault.value = (it.budgetFen || 0) > 0 ? ((it.budgetFen || 0) / 100).toFixed(2) : '';
    amountDialogVisible.value = true;
  } else if (key === 'del') {
    uni.showModal({
      title: '删除分类预算',
      content: `删除「${it.category}」的本月预算？`,
      confirmColor: '#E05B4E',
      success: (res) => {
        if (!res.confirm) return;
        putAll(
          total.value.budgetFen ?? null,
          items.value.filter((x) => x.category !== it.category).map((x) => ({ category: x.category, amountFen: x.budgetFen || 0 }))
        );
      },
    });
  }
}

// ===== 添加分类预算：选未设置的支出分类 =====
const addSheetVisible = ref(false);
const amountDialogTitle = ref('预算额度');
const amountDialogDefault = ref('');

function openAdd() {
  if (expenseCats.value.length === 0) {
    uni.showToast({ title: '还没有支出分类', icon: 'none' });
    return;
  }
  addSheetVisible.value = true;
}

const addItems = () => {
  const used = new Set(items.value.map((it) => it.category));
  return expenseCats.value
    .filter((c) => !used.has(c.name))
    .map((c) => ({ key: c.name, title: c.name }));
};

function onAddPick(name: string) {
  addSheetVisible.value = false;
  inputTarget.value = name;
  amountDialogTitle.value = `「${name}」预算额度`;
  amountDialogDefault.value = '';
  amountDialogVisible.value = true;
}

function onAmountConfirm(value: string) {
  amountDialogVisible.value = false;
  const v = yuanToFen(value);
  if (v == null || v < 0) {
    uni.showToast({ title: '请输入正确的金额', icon: 'none' });
    return;
  }
  const rest = items.value
    .filter((x) => x.category !== inputTarget.value)
    .map((x) => ({ category: x.category, amountFen: x.budgetFen || 0 }));
  const next = v > 0 ? [...rest, { category: inputTarget.value, amountFen: v }] : rest;
  putAll(total.value.budgetFen ?? null, next);
}

// ===== 复制上月 =====
function copyLastMonth() {
  if (!kitchenId.value || saving.value) return;
  const prev = prevMonthStr();
  uni.showModal({
    title: '复制上月预算',
    content: `把 ${prev} 的总预算与分类预算复制到 ${month.value}？当前设置将被覆盖。`,
    success: (res) => {
      if (!res.confirm) return;
      saving.value = true;
      ledgerApi
        .budgets(kitchenId.value!, prev)
        .then((pb) => {
          if ((pb.total?.budgetFen ?? null) == null && pb.items.length === 0) {
            uni.showToast({ title: `${prev} 还没有预算设置`, icon: 'none' });
            return;
          }
          return ledgerApi.putBudgets(kitchenId.value!, {
            month: month.value,
            totalFen: pb.total?.budgetFen ?? null,
            items: pb.items.map((it) => ({ category: it.category, amountFen: it.budgetFen || 0 })),
          }).then(() => {
            uni.showToast({ title: '已复制上月预算', icon: 'none' });
            return load();
          });
        })
        .finally(() => (saving.value = false));
    },
  });
}
</script>

<template>
  <view class="page">
    <!-- 月份切换 + 复制上月 -->
    <view class="month-row">
      <text class="arrow" hover-class="press-dim" @tap="shiftMonth(-1)">‹</text>
      <text class="month">{{ month }}</text>
      <text class="arrow" hover-class="press-dim" @tap="shiftMonth(1)">›</text>
      <text class="copy-btn" hover-class="press-dim" @tap="copyLastMonth">复制上月</text>
    </view>

    <!-- 总预算 -->
    <view class="card total-card" hover-class="press-dim" @tap="editTotal">
      <view class="total-head">
        <text class="total-label">月总预算</text>
        <text class="total-edit">点击修改</text>
      </view>
      <view class="total-amount-row">
        <text class="total-amount">¥{{ fen(total.budgetFen || 0) }}</text>
        <text class="total-used">
          已用 ¥{{ fen(total.usedFen || 0) }}
          <text v-if="total.pct != null" :class="{ over: total.pct >= 100 }">（{{ total.pct }}%）</text>
        </text>
      </view>
      <view class="bar">
        <view class="bar-fill" :style="barStyle(total.pct)" />
      </view>
    </view>

    <!-- 分类预算列表 -->
    <view class="card list">
      <view class="list-head">
        <text class="list-title">分类预算</text>
        <text class="add-link" hover-class="press-dim" @tap="openAdd">＋ 添加</text>
      </view>
      <view v-if="items.length === 0" class="empty">
        <text class="empty-tip">还没设置分类预算，点「＋ 添加」开始</text>
      </view>
      <view v-for="it in items" :key="it.category" class="b-row" hover-class="press-dim" @tap="onRowTap(it)">
        <view class="b-top">
          <text class="b-cat">{{ it.category }}</text>
          <text class="b-amount">¥{{ fen(it.budgetFen || 0) }}</text>
        </view>
        <view class="b-mid">
          <view class="bar slim">
            <view class="bar-fill" :style="barStyle(it.pct)" />
          </view>
          <text class="b-used">已用 ¥{{ fen(it.usedFen || 0) }}<text v-if="it.pct != null"> · {{ it.pct }}%</text></text>
        </view>
      </view>
    </view>
    <text class="page-tip">预算为当月整体覆盖保存；分类额度设为 0 即删除该项。</text>

    <InputDialog
      :visible="totalDialogVisible"
      title="月总预算（元）"
      placeholder="如：3000"
      :default-value="total.budgetFen ? (total.budgetFen / 100).toFixed(2) : ''"
      @confirm="onTotalConfirm"
      @close="totalDialogVisible = false"
    />
    <InputDialog
      :visible="amountDialogVisible"
      :title="amountDialogTitle + '（元）'"
      placeholder="如：500；输 0 删除该项"
      :default-value="amountDialogDefault"
      @confirm="onAmountConfirm"
      @close="amountDialogVisible = false"
    />

    <ActionSheet
      :visible="rowSheetVisible"
      :items="[
        { key: 'edit', title: '修改额度' },
        { key: 'del', title: '删除该项' },
      ]"
      @select="onRowAction"
      @close="rowSheetVisible = false"
    />
    <ActionSheet
      :visible="addSheetVisible"
      :items="addItems()"
      @select="onAddPick"
      @close="addSheetVisible = false"
    />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.month-row {
  display: flex; align-items: center; gap: 40rpx;
  padding: 4rpx 8rpx 22rpx;
}
.month { flex: 1; text-align: center; font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.arrow { font-size: 40rpx; color: v-bind('theme.title'); padding: 0 12rpx; line-height: 1; }
.copy-btn {
  font-size: 24rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 999rpx;
  padding: 6rpx 20rpx;
}

.total-card { padding: 28rpx 32rpx; margin-bottom: 24rpx; }
.total-head { display: flex; align-items: baseline; justify-content: space-between; }
.total-label { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.total-edit { font-size: 22rpx; color: v-bind('theme.sub'); }
.total-amount-row {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-top: 14rpx;
}
.total-amount { font-size: 44rpx; font-weight: 700; color: v-bind('theme.title'); }
.total-used { font-size: 24rpx; color: v-bind('theme.sub'); }
.total-used .over { color: v-bind('theme.danger'); font-weight: 600; }

.bar {
  height: 14rpx; border-radius: 8rpx;
  background: v-bind('theme.chipBg'); overflow: hidden;
  margin-top: 18rpx;
}
.bar.slim { flex: 1; height: 10rpx; margin-top: 0; }
.bar-fill { height: 100%; border-radius: 8rpx; transition: width 0.3s; }

.list { padding: 8rpx 32rpx 16rpx; }
.list-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 26rpx 0 8rpx;
}
.list-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.add-link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }
.empty { padding: 30rpx 0; text-align: center; }
.empty-tip { font-size: 24rpx; color: v-bind('theme.sub'); }

.b-row { padding: 22rpx 0; border-top: 2rpx solid v-bind('theme.divider'); }
.b-top { display: flex; align-items: center; justify-content: space-between; }
.b-cat { font-size: 28rpx; font-weight: 500; color: v-bind('theme.title'); }
.b-amount { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.b-mid { display: flex; align-items: center; gap: 16rpx; margin-top: 14rpx; }
.b-used { font-size: 22rpx; color: v-bind('theme.sub'); flex-shrink: 0; }

.page-tip {
  display: block; text-align: center; margin-top: 24rpx;
  font-size: 24rpx; color: v-bind('theme.sub');
}
</style>
