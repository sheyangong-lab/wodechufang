<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, groupCategories, fen } from '@/api/ledger';
import type { BillView, LedgerCategoryView, AccountView } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const bills = ref<BillView[]>([]);
const cats = ref<{ expense: LedgerCategoryView[]; income: LedgerCategoryView[] }>({
  expense: [],
  income: [],
});
const accounts = ref<AccountView[]>([]);
const savingForm = ref(false);

const TYPE_LABEL: Record<string, string> = { EXPENSE: '支出', INCOME: '收入' };
const CYCLE_LABEL: Record<string, string> = { MONTHLY: '每月', YEARLY: '每年' };
const ACCT_TYPE_LABEL: Record<string, string> = { DISPOSABLE: '可支配', SAVING: '存款', CREDIT: '信用卡' };

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
  if (!kitchenId.value) return;
  ledgerApi.categories(kitchenId.value).then((c) => (cats.value = groupCategories(c))).catch(() => {});
  ledgerApi.accounts(kitchenId.value).then((a) => (accounts.value = a)).catch(() => {});
});

async function load() {
  if (!kitchenId.value) return;
  try {
    bills.value = await ledgerApi.recurringBills(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

function accountName(id: number | null): string {
  if (id == null) return '';
  return accounts.value.find((a) => a.id === id)?.name || '';
}

/** 启停开关：PUT 只传 enabled（其余字段回传原值，后端按非 null 才更新） */
function toggle(b: BillView, e: any) {
  const enabled = e.detail.value ? 1 : 0;
  ledgerApi
    .updateRecurringBill(kitchenId.value!, b.id, {
      type: b.type,
      category: b.category,
      amountFen: b.amountFen,
      enabled,
    })
    .then(() => {
      uni.showToast({ title: enabled ? '已启用' : '已停用', icon: 'none' });
      load();
    })
    .catch(() => load());
}

function remove(b: BillView) {
  uni.showModal({
    title: '删除周期账单',
    content: `删除「${b.category}」的周期设置？已生成的历史流水保留。`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      ledgerApi.removeRecurringBill(kitchenId.value!, b.id).then(() => {
        uni.showToast({ title: '已删除', icon: 'none' });
        load();
      });
    },
  });
}

// ===== 新建弹层 =====
const sheetVisible = ref(false);
const form = ref({
  type: 'EXPENSE' as 'EXPENSE' | 'INCOME',
  category: '',
  amount: '',
  accountId: null as number | null,
  cycle: 'MONTHLY' as 'MONTHLY' | 'YEARLY',
  nextDate: todayStr(),
  remark: '',
});
const typeSheetVisible = ref(false);
const catSheetVisible = ref(false);
const acctSheetVisible = ref(false);
const cycleSheetVisible = ref(false);
const dateCalVisible = ref(false);

const formCats = computed(() =>
  form.value.type === 'EXPENSE' ? cats.value.expense : cats.value.income
);

function openCreate() {
  form.value = {
    type: 'EXPENSE',
    category: '',
    amount: '',
    accountId: null,
    cycle: 'MONTHLY',
    nextDate: todayStr(),
    remark: '',
  };
  sheetVisible.value = true;
}

const catItems = () =>
  formCats.value.map((c) => ({ key: c.name, title: c.name }));

const acctItems = () => {
  const list: { key: string; title: string; desc?: string }[] = [
    { key: '', title: '未指定', desc: '不关联账户' },
  ];
  (['DISPOSABLE', 'SAVING', 'CREDIT'] as const).forEach((t) => {
    accounts.value
      .filter((a) => a.type === t)
      .forEach((a) => list.push({ key: String(a.id), title: a.name, desc: ACCT_TYPE_LABEL[t] }));
  });
  return list;
};

function onTypePick(key: string) {
  typeSheetVisible.value = false;
  form.value.type = key as 'EXPENSE' | 'INCOME';
  if (form.value.category && !formCats.value.some((c) => c.name === form.value.category)) {
    form.value.category = '';
  }
}

function onCatPick(key: string) {
  catSheetVisible.value = false;
  form.value.category = key;
}

function onAcctPick(key: string) {
  acctSheetVisible.value = false;
  form.value.accountId = key === '' ? null : Number(key);
}

function onCyclePick(key: string) {
  cycleSheetVisible.value = false;
  form.value.cycle = key as 'MONTHLY' | 'YEARLY';
}

function onDatePick(v: string | null) {
  dateCalVisible.value = false;
  if (v) form.value.nextDate = v;
}

function submit() {
  if (!kitchenId.value || savingForm.value) return;
  if (!form.value.category) {
    uni.showToast({ title: '请选择分类', icon: 'none' });
    return;
  }
  const amountFen = yuanToFen(form.value.amount.trim()) ?? 0;
  if (amountFen <= 0) {
    uni.showToast({ title: '请输入正确的金额', icon: 'none' });
    return;
  }
  savingForm.value = true;
  ledgerApi
    .addRecurringBill(kitchenId.value, {
      type: form.value.type,
      category: form.value.category,
      amountFen,
      accountId: form.value.accountId ?? undefined,
      cycle: form.value.cycle,
      nextDate: form.value.nextDate,
      remark: form.value.remark.trim() || undefined,
    })
    .then(() => {
      sheetVisible.value = false;
      uni.showToast({ title: form.value.nextDate <= todayStr() ? '已创建并补记到期账' : '已创建', icon: 'none' });
      load();
    })
    .finally(() => (savingForm.value = false));
}
</script>

<template>
  <view class="page">
    <view class="card list">
      <view v-if="bills.length === 0" class="empty">
        <text class="empty-tip">还没有周期账单，添加房租/工资等固定收支自动入账</text>
      </view>
      <view v-for="b in bills" :key="b.id" class="bill-row">
        <view class="bill-icon" :class="b.type === 'INCOME' ? 'in' : 'out'">
          <text>{{ b.type === 'INCOME' ? '收' : '支' }}</text>
        </view>
        <view class="bill-info">
          <view class="bill-title-row">
            <text class="bill-cat">{{ b.category }}</text>
            <text v-if="accountName(b.accountId)" class="bill-tag">{{ accountName(b.accountId) }}</text>
            <text v-if="b.enabled !== 1" class="bill-tag off">已停用</text>
          </view>
          <text class="bill-sub">
            {{ CYCLE_LABEL[b.cycle] || b.cycle }} · 下次 {{ b.nextDate }}<text v-if="b.remark"> · {{ b.remark }}</text>
          </text>
        </view>
        <view class="bill-right">
          <text class="bill-amount" :class="b.type === 'INCOME' ? 'in' : 'out'">
            {{ b.type === 'INCOME' ? '+' : '−' }}¥{{ fen(b.amountFen) }}
          </text>
          <view class="bill-ops">
            <switch
              class="bill-switch"
              :checked="b.enabled === 1"
              color="#EFA63C"
              @change="toggle(b, $event)"
            />
            <text class="del" hover-class="press-dim" @tap="remove(b)">删除</text>
          </view>
        </view>
      </view>
    </view>

    <button class="btn-add" hover-class="press-sink" @tap="openCreate">＋ 新建周期账单</button>
    <text class="page-tip">首次日期不晚于今天时，保存后会立即补记到账。</text>

    <!-- 新建弹层 -->
    <view v-if="sheetVisible" class="mask" @tap="sheetVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">新建周期账单</text>
        <view class="f-row" hover-class="press-dim" @tap="typeSheetVisible = true">
          <text class="f-label">类型</text>
          <text class="f-value">{{ TYPE_LABEL[form.type] }}</text>
          <text class="chev">›</text>
        </view>
        <view class="f-row" hover-class="press-dim" @tap="catSheetVisible = true">
          <text class="f-label">分类</text>
          <text class="f-value">{{ form.category || '请选择' }}</text>
          <text class="chev">›</text>
        </view>
        <view class="f-row">
          <text class="f-label">金额</text>
          <input v-model="form.amount" class="f-input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="f-unit">元</text>
        </view>
        <view class="f-row" hover-class="press-dim" @tap="acctSheetVisible = true">
          <text class="f-label">账户</text>
          <text class="f-value">{{ form.accountId != null ? accountName(form.accountId) || '已选账户' : '未指定' }}</text>
          <text class="chev">›</text>
        </view>
        <view class="f-row" hover-class="press-dim" @tap="cycleSheetVisible = true">
          <text class="f-label">周期</text>
          <text class="f-value">{{ CYCLE_LABEL[form.cycle] }}</text>
          <text class="chev">›</text>
        </view>
        <view class="f-row" hover-class="press-dim" @tap="dateCalVisible = true">
          <text class="f-label">首次日期</text>
          <text class="f-value">{{ form.nextDate }} ›</text>
        </view>
        <view class="f-row">
          <text class="f-label">备注</text>
          <input v-model="form.remark" class="f-input" maxlength="30" placeholder="选填，如：房租" placeholder-class="ph" />
        </view>
        <text class="f-tip">首次日期 ≤ 今天时，保存后立即按周期补记到今天</text>
        <view class="sheet-btns">
          <text class="s-btn ok" :class="{ disabled: savingForm }" hover-class="press-dim" @tap="submit">保存</text>
        </view>
      </view>
    </view>

    <ActionSheet
      :visible="typeSheetVisible"
      :items="[
        { key: 'EXPENSE', title: '支出' },
        { key: 'INCOME', title: '收入' },
      ]"
      @select="onTypePick"
      @close="typeSheetVisible = false"
    />
    <ActionSheet
      :visible="catSheetVisible"
      :items="catItems()"
      @select="onCatPick"
      @close="catSheetVisible = false"
    />
    <ActionSheet
      :visible="acctSheetVisible"
      :items="acctItems()"
      @select="onAcctPick"
      @close="acctSheetVisible = false"
    />
    <ActionSheet
      :visible="cycleSheetVisible"
      :items="[
        { key: 'MONTHLY', title: '每月' },
        { key: 'YEARLY', title: '每年' },
      ]"
      @select="onCyclePick"
      @close="cycleSheetVisible = false"
    />
    <CalendarPicker
      :visible="dateCalVisible"
      :selected="form.nextDate"
      @select="onDatePick"
      @close="dateCalVisible = false"
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
.list { padding: 8rpx 32rpx 12rpx; }
.empty { padding: 60rpx 0; text-align: center; }
.empty-tip { font-size: 24rpx; color: v-bind('theme.sub'); }

.bill-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 26rpx 0;
  border-top: 2rpx solid v-bind('theme.divider');
}
.bill-icon {
  width: 64rpx; height: 64rpx; border-radius: 18rpx; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 26rpx; font-weight: 700;
}
.bill-icon.in { background: v-bind('theme.primaryLight'); color: v-bind('theme.income'); }
.bill-icon.out { background: v-bind('theme.successLight'); color: v-bind('theme.expense'); }
.bill-info { flex: 1; min-width: 0; }
.bill-title-row { display: flex; align-items: center; gap: 10rpx; }
.bill-cat {
  font-size: 28rpx; font-weight: 500; color: v-bind('theme.title');
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.bill-tag {
  font-size: 20rpx; color: v-bind('theme.sub');
  background: v-bind('theme.chipBg'); border-radius: 8rpx;
  padding: 2rpx 10rpx; flex-shrink: 0;
}
.bill-tag.off { color: v-bind('theme.warning'); background: v-bind('theme.warningLight'); }
.bill-sub {
  display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 6rpx;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.bill-right { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; flex-shrink: 0; }
.bill-amount { font-size: 30rpx; font-weight: 700; }
.bill-amount.in { color: v-bind('theme.income'); }
.bill-amount.out { color: v-bind('theme.expense'); }
.bill-ops { display: flex; align-items: center; gap: 16rpx; }
.bill-switch { transform: scale(0.7); }
.del { font-size: 26rpx; color: v-bind('theme.danger'); }

.btn-add {
  margin-top: 24rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-add::after { border: none; }
.page-tip {
  display: block; text-align: center; margin-top: 24rpx;
  font-size: 24rpx; color: v-bind('theme.sub');
}

/* 底部弹层表单 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(20, 16, 8, 0.55);
  z-index: 999;
  display: flex; flex-direction: column; justify-content: flex-end;
}
.sheet {
  background: v-bind('theme.card');
  border-radius: 32rpx 32rpx 0 0;
  padding: 36rpx 40rpx calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  display: block; text-align: center;
  font-size: 32rpx; font-weight: 600; color: v-bind('theme.title');
}
.f-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.f-label { width: 160rpx; font-size: 28rpx; color: v-bind('theme.title'); flex-shrink: 0; }
.f-input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.f-value { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.f-unit { font-size: 24rpx; color: v-bind('theme.sub'); }
.chev { color: v-bind('theme.sub'); }
.ph { color: v-bind('theme.sub'); }
.f-tip { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 16rpx; }
.sheet-btns { display: flex; align-items: center; margin-top: 24rpx; }
.s-btn {
  flex: 1; text-align: center; padding: 22rpx 0;
  border-radius: 16rpx; font-size: 30rpx;
}
.s-btn.ok { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
.s-btn.ok.disabled { opacity: 0.6; }
</style>
