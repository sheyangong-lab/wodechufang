<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, groupCategories } from '@/api/ledger';
import type { LedgerCategoryView } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import InputDialog from '@/components/input-dialog.vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const type = ref<'EXPENSE' | 'INCOME'>('EXPENSE');
const category = ref('');
const amountYuan = ref('');
const date = ref(new Date().toISOString().slice(0, 10));
const remark = ref('');
const groups = ref<{ expense: LedgerCategoryView[]; income: LedgerCategoryView[] }>({
  expense: [],
  income: [],
});
const submitting = ref(false);

onLoad(async (query) => {
  kitchenId.value = await ensureKitchenId();
  if (query && query.month) {
    // 从指定月进入时，日期默认为该月今天（补记场景用日期选择器调整）
    date.value = `${query.month}-${String(new Date().getDate()).padStart(2, '0')}`;
  }
  await loadCategories();
  if (!category.value) category.value = defaultCategory();
});

async function loadCategories() {
  if (!kitchenId.value) return;
  try {
    groups.value = groupCategories(await ledgerApi.categories(kitchenId.value));
  } catch {
    // toast 已统一弹出
  }
}

const currentCategories = computed(() =>
  type.value === 'EXPENSE' ? groups.value.expense : groups.value.income
);

function defaultCategory(): string {
  const list = currentCategories.value;
  if (list.length === 0) return '';
  const preferred = list.find((c) => (type.value === 'EXPENSE' ? c.name === '食材采购' : c.name === '菜品销售'));
  return (preferred || list[0]).name;
}

function setType(t: 'EXPENSE' | 'INCOME') {
  type.value = t;
  if (!currentCategories.value.some((c) => c.name === category.value)) {
    category.value = defaultCategory();
  }
}

// 分类选择：自研底部弹层（替代原生 ActionSheet，UI 统一）
const catSheetVisible = ref(false);
const catItems = computed(() =>
  currentCategories.value.map((c) => ({
    key: String(c.id),
    title: c.name,
    desc: category.value === c.name ? '当前分类' : '',
  }))
);

function pickCategory() {
  if (currentCategories.value.length === 0) {
    uni.showToast({ title: '还没有分类，先添加一个', icon: 'none' });
    catDialogVisible.value = true;
    return;
  }
  catSheetVisible.value = true;
}

function onCatPick(key: string) {
  catSheetVisible.value = false;
  const c = currentCategories.value.find((x) => String(x.id) === key);
  if (c) category.value = c.name;
}

const catDialogVisible = ref(false);

function onCatCreate(name: string) {
  catDialogVisible.value = false;
  ledgerApi.createCategory(kitchenId.value!, type.value, name).then((c) => {
    loadCategories();
    category.value = c.name;
  });
}

function goManage() {
  uni.navigateTo({ url: '/pages/ledger/categories' });
}

const dateCalVisible = ref(false);

function onDatePick(v: string | null) {
  dateCalVisible.value = false;
  if (v) date.value = v;
}

function submit() {
  if (submitting.value || !kitchenId.value) return;
  const fen = yuanToFen(amountYuan.value);
  if (fen == null || fen <= 0) {
    uni.showToast({ title: '请输入正确的金额', icon: 'none' });
    return;
  }
  submitting.value = true;
  ledgerApi
    .add(kitchenId.value, {
      type: type.value,
      category: category.value,
      amountFen: fen,
      date: date.value,
      remark: remark.value || undefined,
    })
    .then(() => {
      uni.showToast({ title: '已记一笔', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 700);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view class="card section">
      <view class="type-row">
        <text class="type-btn" :class="{ on: type === 'EXPENSE', out: type === 'EXPENSE' }" hover-class="press-dim" @tap="setType('EXPENSE')">支出</text>
        <text class="type-btn" :class="{ on: type === 'INCOME', inc: type === 'INCOME' }" hover-class="press-dim" @tap="setType('INCOME')">收入</text>
      </view>

      <view class="row" hover-class="press-dim" @tap="pickCategory">
        <text class="label req">分类</text>
        <text class="value">{{ category || '请选择分类' }}</text>
        <text class="chev">›</text>
      </view>
      <view class="row">
        <text class="label">没有合适的？</text>
        <text class="link" hover-class="press-dim" @tap="catDialogVisible = true">＋ 添加分类</text>
        <text class="link manage" hover-class="press-dim" @tap="goManage">管理分类</text>
      </view>
      <view class="row">
        <text class="label req">金额（元）</text>
        <input v-model="amountYuan" class="input amount" type="digit" placeholder="0.00" placeholder-class="ph" />
      </view>
      <view class="row" hover-class="press-dim" @tap="dateCalVisible = true">
        <text class="label">日期</text>
        <text class="value picker">{{ date }} ›</text>
      </view>
      <view class="row">
        <text class="label">备注</text>
        <input v-model="remark" class="input" maxlength="50" placeholder="选填，50字内" placeholder-class="ph" />
      </view>
    </view>

    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">保存</button>

    <ActionSheet
      :visible="catSheetVisible"
      :items="catItems"
      @select="onCatPick"
      @close="catSheetVisible = false"
    />
    <CalendarPicker
      :visible="dateCalVisible"
      :selected="date"
      @select="onDatePick"
      @close="dateCalVisible = false"
    />
    <InputDialog
      :visible="catDialogVisible"
      :title="type === 'EXPENSE' ? '添加支出分类' : '添加收入分类'"
      :placeholder="type === 'EXPENSE' ? '如：交通 /日用 /宠物' : '如：工资 /副业'"
      :maxlength="10"
      @confirm="onCatCreate"
      @close="catDialogVisible = false"
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
.section { padding: 12rpx 32rpx 24rpx; }
.type-row { display: flex; gap: 20rpx; padding: 24rpx 0; }
.type-btn {
  flex: 1; text-align: center;
  font-size: 30rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 16rpx;
  padding: 18rpx 0;
}
.type-btn.on { font-weight: 700; }
.type-btn.out.on { color: v-bind('theme.expense'); border-color: v-bind('theme.expense'); background: v-bind('theme.successLight'); }
.type-btn.inc.on { color: v-bind('theme.income'); border-color: v-bind('theme.income'); background: v-bind('theme.primaryLight'); }

.row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 26rpx 0;
}
.label { font-size: 28rpx; color: v-bind('theme.title'); width: 180rpx; }
.label.req::before { content: '* '; color: v-bind('theme.danger'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.input.amount { font-size: 34rpx; font-weight: 700; }
.value { font-size: 26rpx; color: v-bind('theme.title'); }
.value.picker { color: v-bind('theme.title'); }
.chev { color: v-bind('theme.sub'); margin-left: 8rpx; }
.link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); flex: 1; text-align: right; }
.link.manage { flex: 0.6; }
.ph { color: v-bind('theme.sub'); }

.btn-submit {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
