<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi, UNIT_LABELS } from '@/api/fridge';
import type { FridgeCategoryView } from '@/api/fridge';
import { getCurrentKitchenId } from '@/api/kitchen';
import { ref } from 'vue';

interface Draft {
  name: string;
  categoryId: number | null;
  producedDate: string;
  shelfLifeValue: string;
  shelfLifeUnit: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
  quantity: string;
  remark: string;
}

const kitchenId = ref<number | null>(getCurrentKitchenId());
const categories = ref<FridgeCategoryView[]>([]);
const drafts = ref<Draft[]>([newDraft()]);
const submitting = ref(false);

function newDraft(): Draft {
  return {
    name: '',
    categoryId: null,
    producedDate: '',
    shelfLifeValue: '7',
    shelfLifeUnit: 'DAY',
    quantity: '',
    remark: '',
  };
}

function loadCategories() {
  if (!kitchenId.value) return;
  fridgeApi.categories(kitchenId.value).then((cs) => (categories.value = cs)).catch(() => {});
}
loadCategories();

function addItem() {
  if (drafts.value.length >= 10) {
    uni.showToast({ title: '一次最多 10 种食材', icon: 'none' });
    return;
  }
  drafts.value.push(newDraft());
}

function removeItem(i: number) {
  drafts.value.splice(i, 1);
}

function pickCategory(i: number) {
  const names = categories.value.map((c) => c.name);
  if (names.length === 0) {
    uni.showToast({ title: '还没有类别，去类别管理添加', icon: 'none' });
    return;
  }
  uni.showActionSheet({
    itemList: names,
    success: ({ tapIndex }) => (drafts.value[i].categoryId = categories.value[tapIndex].id),
  });
}

function pickProduced(i: number, e: { detail: { value: string } }) {
  drafts.value[i].producedDate = e.detail.value;
}

const units: ('DAY' | 'WEEK' | 'MONTH' | 'YEAR')[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

function submit() {
  if (submitting.value || !kitchenId.value) return;
  const valid = drafts.value.filter((d) => d.name.trim());
  if (valid.length === 0) {
    uni.showToast({ title: '请至少填写一种食材', icon: 'none' });
    return;
  }
  submitting.value = true;
  fridgeApi
    .createItems(
      kitchenId.value,
      valid.map((d) => ({
        name: d.name.trim(),
        categoryId: d.categoryId,
        producedDate: d.producedDate || null,
        shelfLifeValue: Number(d.shelfLifeValue) || 1,
        shelfLifeUnit: d.shelfLifeUnit,
        quantity: d.quantity || undefined,
        remark: d.remark || undefined,
      }))
    )
    .then(() => {
      uni.showToast({ title: `已放入 ${valid.length} 种食材`, icon: 'none' });
      setTimeout(() => uni.navigateBack(), 700);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view v-for="(d, i) in drafts" :key="i" class="card item-card">
      <view class="card-head">
        <text class="card-no">食材 {{ i + 1 }}</text>
        <text v-if="drafts.length > 1" class="card-del" hover-class="press-dim" @tap="removeItem(i)">✕</text>
      </view>

      <view class="row">
        <text class="label req">食材名称</text>
        <input v-model="d.name" class="input" maxlength="20" placeholder="食材名称" placeholder-class="ph" />
      </view>
      <view class="row" hover-class="press-dim" @tap="pickCategory(i)">
        <text class="label req">食材类别</text>
        <text class="value">{{ categories.find((c) => c.id === d.categoryId)?.name || '请选择类别' }}</text>
        <text class="chev">›</text>
      </view>
      <view class="row">
        <text class="label">生产日期</text>
        <picker mode="date" :value="d.producedDate" @change="pickProduced(i, $event)">
          <text class="value picker">{{ d.producedDate || '请选择生产日期' }} ›</text>
        </picker>
      </view>
      <view class="row">
        <text class="label req">保质期</text>
        <input v-model="d.shelfLifeValue" class="input life" type="number" maxlength="4" />
        <view class="units">
          <text
            v-for="unit in units"
            :key="unit"
            class="unit"
            :class="{ on: d.shelfLifeUnit === unit }"
            hover-class="press-dim"
            @tap="d.shelfLifeUnit = unit"
          >{{ UNIT_LABELS[unit] }}</text>
        </view>
      </view>
      <view class="row">
        <text class="label">数量</text>
        <input v-model="d.quantity" class="input" placeholder="数量+单位，如：100g" placeholder-class="ph" />
      </view>
      <view class="row col">
        <text class="label">备注</text>
        <input v-model="d.remark" class="input" maxlength="50" placeholder="备注（选填）" placeholder-class="ph" />
      </view>
    </view>

    <text class="count">共{{ drafts.length }}个食材</text>
    <button class="btn-add" hover-class="press-dim" @tap="addItem">＋ 添加食材</button>
    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">提交</button>
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
  margin-bottom: 20rpx;
}
.item-card { padding: 24rpx 32rpx; }
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8rpx;
}
.card-no { font-size: 26rpx; font-weight: 600; color: v-bind('theme.primaryBtn'); }
.card-del { font-size: 28rpx; color: v-bind('theme.danger'); padding: 4rpx 8rpx; }

.row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 24rpx 0;
}
.row.col { flex-direction: column; align-items: stretch; border-bottom: none; }
.label { font-size: 28rpx; color: v-bind('theme.title'); width: 160rpx; }
.label.req::before { content: '* '; color: v-bind('theme.danger'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.input.life { width: 100rpx; }
.value { font-size: 26rpx; color: v-bind('theme.sub'); }
.value.picker { color: v-bind('theme.title'); }
.chev { color: v-bind('theme.sub'); margin-left: 8rpx; }
.ph { color: v-bind('theme.sub'); }
.units { display: flex; gap: 10rpx; }
.unit {
  font-size: 24rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 10rpx;
  padding: 6rpx 18rpx;
}
.unit.on {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); font-weight: 600;
}

.count { display: block; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); margin: 8rpx 0 20rpx; }
.btn-add {
  background: #fff; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn');
  border-radius: 16rpx; font-size: 30rpx; line-height: 84rpx;
  margin-bottom: 20rpx;
}
.btn-add::after { border: none; }
.btn-submit {
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
