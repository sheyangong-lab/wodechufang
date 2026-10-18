<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, fen } from '@/api/ledger';
import type { AccountView, AccountType } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const accounts = ref<AccountView[]>([]);

const TYPE_LABEL: Record<AccountType, string> = { DISPOSABLE: '可支配', SAVING: '存款', CREDIT: '信用卡' };
const GROUPS: { type: AccountType; title: string; tip: string }[] = [
  { type: 'DISPOSABLE', title: '💰 可支配', tip: '日常收支钱包' },
  { type: 'SAVING', title: '🐖 存款', tip: '存起来不动的钱' },
  { type: 'CREDIT', title: '💳 信用卡', tip: '刷卡消费记欠款' },
];

function byType(t: AccountType): AccountView[] {
  return accounts.value.filter((a) => a.type === t);
}

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  try {
    accounts.value = await ledgerApi.accounts(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

/** 信用卡剩余额度 = 额度 + 余额（余额为负欠款） */
function creditRemain(a: AccountView): number {
  return (a.creditLimitFen || 0) + a.balanceFen;
}

/** 金额展示：信用卡欠款为负数显示 -¥ */
function balanceText(a: AccountView): string {
  return (a.type === 'CREDIT' && a.balanceFen < 0 ? '-¥' + fen(-a.balanceFen) : '¥' + fen(a.balanceFen));
}

// ===== 新建 / 编辑账户弹层 =====
const sheetVisible = ref(false);
const editing = ref<AccountView | null>(null);
const form = ref({ name: '', type: 'DISPOSABLE' as AccountType, initial: '', limit: '' });
const typeSheetVisible = ref(false);
const savingForm = ref(false);

function openCreate() {
  editing.value = null;
  form.value = { name: '', type: 'DISPOSABLE', initial: '', limit: '' };
  sheetVisible.value = true;
}

function openEdit(a: AccountView) {
  editing.value = a;
  form.value = {
    name: a.name,
    type: a.type,
    initial: (a.initialFen / 100).toFixed(2),
    limit: a.creditLimitFen != null ? (a.creditLimitFen / 100).toFixed(2) : '',
  };
  sheetVisible.value = true;
}

function onTypePick(key: string) {
  typeSheetVisible.value = false;
  form.value.type = key as AccountType;
}

/** 金额串→分；空串返回 undefined（编辑时不覆盖），非法返回 null */
function parseFen(text: string): number | null | undefined {
  const t = text.trim();
  if (t === '') return undefined;
  return yuanToFen(t);
}

function save() {
  if (!kitchenId.value || savingForm.value) return;
  const name = form.value.name.trim();
  if (!name) {
    uni.showToast({ title: '请填写账户名称', icon: 'none' });
    return;
  }
  const initialFen = parseFen(form.value.initial);
  if (initialFen === null || (initialFen != null && initialFen < 0)) {
    uni.showToast({ title: '期初余额请输入正确的金额', icon: 'none' });
    return;
  }
  const isCredit = form.value.type === 'CREDIT';
  const limitFen = isCredit ? parseFen(form.value.limit) : undefined;
  if (limitFen === null || (limitFen != null && limitFen < 0)) {
    uni.showToast({ title: '信用额度请输入正确的金额', icon: 'none' });
    return;
  }
  savingForm.value = true;
  const req = editing.value
    ? ledgerApi.updateAccount(kitchenId.value, editing.value.id, {
        name,
        initialFen: initialFen ?? undefined,
        creditLimitFen: editing.value.type === 'CREDIT' ? limitFen ?? undefined : undefined,
      })
    : ledgerApi.createAccount(kitchenId.value, {
        name,
        type: form.value.type,
        initialFen: initialFen ?? 0,
        creditLimitFen: isCredit ? limitFen ?? 0 : undefined,
      });
  req
    .then(() => {
      sheetVisible.value = false;
      uni.showToast({ title: editing.value ? '已保存' : '已新建', icon: 'none' });
      load();
    })
    .finally(() => (savingForm.value = false));
}

function removeAccount() {
  const a = editing.value;
  if (!a || !kitchenId.value) return;
  uni.showModal({
    title: '删除账户',
    content: `确定删除「${a.name}」？名下已有流水的账户无法删除。`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      sheetVisible.value = false;
      ledgerApi.deleteAccount(kitchenId.value!, a.id).then(() => {
        uni.showToast({ title: '已删除', icon: 'none' });
        load();
      });
    },
  });
}

// ===== 信用卡还款：选可支配账户 + 金额 → 转账 =====
const repaySheetVisible = ref(false);
const repayTarget = ref<AccountView | null>(null);
const repayItems = () =>
  byType('DISPOSABLE').map((a) => ({
    key: String(a.id),
    title: a.name,
    desc: `余额 ¥${fen(a.balanceFen)}`,
  }));

function repay(a: AccountView) {
  if (byType('DISPOSABLE').length === 0) {
    uni.showToast({ title: '请先建立可支配账户', icon: 'none' });
    return;
  }
  repayTarget.value = a;
  repaySheetVisible.value = true;
}

function onRepayFrom(key: string) {
  repaySheetVisible.value = false;
  const from = accounts.value.find((x) => String(x.id) === key);
  const to = repayTarget.value;
  if (!from || !to || !kitchenId.value) return;
  askAmount(`还款给「${to.name}」`, '还款金额(元)', (amountFen) => {
    ledgerApi
      .transfer(kitchenId.value!, {
        fromAccountId: from.id,
        toAccountId: to.id,
        amountFen,
        date: todayStr(),
        remark: '信用卡还款',
      })
      .then(() => {
        uni.showToast({ title: '已还款', icon: 'none' });
        load();
      });
  });
}

// ===== 存款取回：金额 → 转到首个可支配账户 =====
function withdraw(a: AccountView) {
  const to = byType('DISPOSABLE')[0];
  if (!to || !kitchenId.value) {
    uni.showToast({ title: '请先建立可支配账户', icon: 'none' });
    return;
  }
  askAmount(`从「${a.name}」取回`, '取回金额(元)', (amountFen) => {
    ledgerApi
      .transfer(kitchenId.value!, {
        fromAccountId: a.id,
        toAccountId: to.id,
        amountFen,
        date: todayStr(),
        remark: '取回到可支配',
      })
      .then(() => {
        uni.showToast({ title: '已取回', icon: 'none' });
        load();
      });
  });
}

/** 弹原生可编辑 modal 要金额（校验 >0 后回调分值） */
function askAmount(title: string, placeholder: string, ok: (amountFen: number) => void) {
  uni.showModal({
    title,
    editable: true,
    placeholderText: placeholder,
    success: (res) => {
      if (!res.confirm) return;
      const amountFen = yuanToFen((res.content || '').trim()) ?? 0;
      if (amountFen <= 0) {
        uni.showToast({ title: '请输入正确金额', icon: 'none' });
        return;
      }
      ok(amountFen);
    },
  });
}
</script>

<template>
  <view class="page">
    <view v-for="g in GROUPS" :key="g.type" class="card group">
      <view class="group-head">
        <text class="group-title">{{ g.title }}</text>
        <text class="group-tip">{{ g.tip }}</text>
      </view>
      <view v-if="byType(g.type).length === 0" class="empty">
        <text class="empty-tip">还没有{{ TYPE_LABEL[g.type] }}账户</text>
      </view>
      <view
        v-for="a in byType(g.type)"
        :key="a.id"
        class="acct-row"
        hover-class="press-dim"
        @tap="openEdit(a)"
      >
        <view class="acct-info">
          <text class="acct-name">{{ a.name }}</text>
          <text v-if="a.type === 'CREDIT'" class="acct-sub">
            额度 ¥{{ fen(a.creditLimitFen || 0) }} · 剩余 ¥{{ fen(creditRemain(a)) }}
          </text>
        </view>
        <view class="acct-right">
          <text class="acct-balance" :class="{ neg: a.type === 'CREDIT' && a.balanceFen < 0 }">
            {{ balanceText(a) }}
          </text>
          <view class="mini-btns">
            <text v-if="a.type === 'CREDIT'" class="mini-btn" @tap.stop="repay(a)">还款</text>
            <text v-if="a.type === 'SAVING'" class="mini-btn" @tap.stop="withdraw(a)">取回</text>
          </view>
        </view>
      </view>
    </view>

    <button class="btn-add" hover-class="press-sink" @tap="openCreate">＋ 新建账户</button>
    <text class="page-tip">点账户可改名称/期初/额度；还款与取回走账户间转账。</text>

    <!-- 新建/编辑弹层 -->
    <view v-if="sheetVisible" class="mask" @tap="sheetVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">{{ editing ? '编辑账户' : '新建账户' }}</text>
        <view class="f-row">
          <text class="f-label">名称</text>
          <input v-model="form.name" class="f-input" maxlength="10" placeholder="如：微信 / 招行卡" placeholder-class="ph" />
        </view>
        <view v-if="!editing" class="f-row" hover-class="press-dim" @tap="typeSheetVisible = true">
          <text class="f-label">类型</text>
          <text class="f-value">{{ TYPE_LABEL[form.type] }}</text>
          <text class="chev">›</text>
        </view>
        <view class="f-row">
          <text class="f-label">期初余额</text>
          <input v-model="form.initial" class="f-input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="f-unit">元</text>
        </view>
        <view v-if="form.type === 'CREDIT'" class="f-row">
          <text class="f-label">信用额度</text>
          <input v-model="form.limit" class="f-input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="f-unit">元</text>
        </view>
        <text class="f-tip">编辑期初/额度会立即影响余额计算</text>
        <view class="sheet-btns">
          <text v-if="editing" class="s-btn danger" hover-class="press-dim" @tap="removeAccount">删除</text>
          <text class="s-btn ok" :class="{ disabled: savingForm }" hover-class="press-dim" @tap="save">保存</text>
        </view>
      </view>
    </view>

    <ActionSheet
      :visible="typeSheetVisible"
      :items="[
        { key: 'DISPOSABLE', title: '可支配', desc: '日常收支钱包' },
        { key: 'SAVING', title: '存款', desc: '存钱目标进度按存款账户合计' },
        { key: 'CREDIT', title: '信用卡', desc: '支出记欠款，可还款' },
      ]"
      @select="onTypePick"
      @close="typeSheetVisible = false"
    />
    <ActionSheet
      :visible="repaySheetVisible"
      :items="repayItems()"
      @select="onRepayFrom"
      @close="repaySheetVisible = false"
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
.group { padding: 8rpx 32rpx 12rpx; margin-bottom: 24rpx; }
.group-head {
  display: flex; align-items: baseline; justify-content: space-between;
  padding: 26rpx 0 10rpx;
}
.group-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.group-tip { font-size: 22rpx; color: v-bind('theme.sub'); }
.empty { padding: 24rpx 0; text-align: center; }
.empty-tip { font-size: 24rpx; color: v-bind('theme.sub'); }

.acct-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 26rpx 0;
  border-top: 2rpx solid v-bind('theme.divider');
}
.acct-info { flex: 1; min-width: 0; }
.acct-name {
  font-size: 30rpx; font-weight: 500; color: v-bind('theme.title');
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.acct-sub { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 6rpx; }
.acct-right { display: flex; flex-direction: column; align-items: flex-end; gap: 10rpx; }
.acct-balance { font-size: 32rpx; font-weight: 700; color: v-bind('theme.title'); }
.acct-balance.neg { color: v-bind('theme.danger'); }
.mini-btns { display: flex; gap: 12rpx; }
.mini-btn {
  font-size: 22rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 999rpx;
  padding: 4rpx 20rpx;
}

.btn-add {
  margin-top: 8rpx;
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
.sheet-btns { display: flex; align-items: center; margin-top: 24rpx; gap: 24rpx; }
.s-btn {
  flex: 1; text-align: center; padding: 22rpx 0;
  border-radius: 16rpx; font-size: 30rpx;
}
.s-btn.ok { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
.s-btn.ok.disabled { opacity: 0.6; }
.s-btn.danger { color: v-bind('theme.danger'); border: 2rpx solid v-bind('theme.danger'); }
</style>
