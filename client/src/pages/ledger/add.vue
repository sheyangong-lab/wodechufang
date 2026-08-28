<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const type = ref<'EXPENSE' | 'INCOME'>('EXPENSE');
const category = ref('食材采购');
const amountYuan = ref('');
const date = ref(new Date().toISOString().slice(0, 10));
const remark = ref('');
const categories = ref<{ expense: string[]; income: string[] }>({ expense: [], income: [] });
const submitting = ref(false);

onLoad((query) => {
  if (query && query.month) {
    // 从指定月进入时，日期默认为该月今天（补记场景用日期选择器调整）
    date.value = `${query.month}-${String(new Date().getDate()).padStart(2, '0')}`;
  }
  if (kitchenId.value) {
    ledgerApi
      .categoriesOf(kitchenId.value)
      .then((c) => (categories.value = c))
      .catch(() => {});
  }
});

const currentCategories = () => (type.value === 'EXPENSE' ? categories.value.expense : categories.value.income);

function setType(t: 'EXPENSE' | 'INCOME') {
  type.value = t;
  const list = currentCategories();
  if (list.length > 0 && !list.includes(category.value)) {
    category.value = list[0];
  }
}

function pickCategory() {
  const list = currentCategories();
  if (list.length === 0) return;
  uni.showActionSheet({
    itemList: list,
    success: ({ tapIndex }) => (category.value = list[tapIndex]),
  });
}

function pickDate(e: { detail: { value: string } }) {
  date.value = e.detail.value;
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
        <text class="label req">金额（元）</text>
        <input v-model="amountYuan" class="input amount" type="digit" placeholder="0.00" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">日期</text>
        <picker mode="date" :value="date" @change="pickDate">
          <text class="value picker">{{ date }} ›</text>
        </picker>
      </view>
      <view class="row">
        <text class="label">备注</text>
        <input v-model="remark" class="input" maxlength="50" placeholder="选填，50字内" placeholder-class="ph" />
      </view>
    </view>

    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">保存</button>
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
.type-btn.out.on { color: v-bind('theme.expense'); border-color: v-bind('theme.expense'); background: #e9f3ec; }
.type-btn.inc.on { color: v-bind('theme.income'); border-color: v-bind('theme.income'); background: #fdefe2; }

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
.ph { color: v-bind('theme.sub'); }

.btn-submit {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
