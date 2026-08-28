<script setup lang="ts">
import { theme } from '@/styles/theme';
import { computed, ref } from 'vue';

const monthLabel = ref('2026年08月');
// 演示数据：M5-T3 接真实流水
const summary = ref({ income: '0.00', expense: '0.00', balance: '0.00' });
const cards = computed(() => [
  { label: '本月收入', value: summary.value.income, color: theme.income },
  { label: '本月支出', value: summary.value.expense, color: theme.expense },
  { label: '本月结余', value: summary.value.balance, color: theme.title },
]);
</script>

<template>
  <view class="page">
    <view class="month-row">
      <text class="arrow">◀</text>
      <text class="month">{{ monthLabel }}</text>
      <text class="arrow">▶</text>
    </view>
    <view class="cards">
      <view v-for="c in cards" :key="c.label" class="card">
        <text class="label">{{ c.label }}</text>
        <text class="value" :style="{ color: c.color }">¥{{ c.value }}</text>
      </view>
    </view>
    <view class="card actions">
      <text class="add">+ 记一笔</text>
      <text class="export">导出 Excel</text>
    </view>
    <view class="card empty">
      <text class="face">📒</text>
      <text class="tip">本月还没有收支记录</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background-color: v-bind('theme.bg'); padding: 24rpx; box-sizing: border-box; }
.month-row { display: flex; align-items: center; justify-content: center; gap: 48rpx; padding: 16rpx 0 24rpx; }
.month { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.arrow { color: v-bind('theme.sub'); font-size: 26rpx; }
.cards { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.card {
  background: v-bind('theme.card'); border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.cards .card { flex: 1; padding: 24rpx 0; display: flex; flex-direction: column; align-items: center; }
.label { font-size: 22rpx; color: v-bind('theme.sub'); }
.value { font-size: 30rpx; font-weight: 600; margin-top: 8rpx; }
.actions { display: flex; padding: 20rpx 32rpx; gap: 48rpx; margin-bottom: 16rpx; }
.add { color: v-bind('theme.primaryBtn'); font-size: 28rpx; font-weight: 600; }
.export { margin-left: auto; color: v-bind('theme.title'); font-size: 28rpx; }
.empty { padding: 96rpx 0; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.face { font-size: 96rpx; }
.tip { font-size: 26rpx; color: v-bind('theme.sub'); }
</style>
