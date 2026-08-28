<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ref } from 'vue';

const tabs = ['厨房订单', '我下过的单'] as const;
const active = ref(0);
</script>

<template>
  <view class="page">
    <view class="sub-tabs">
      <text
        v-for="(t, i) in tabs"
        :key="t"
        class="tab"
        :class="{ active: active === i }"
        @tap="active = i"
      >{{ t }}</text>
    </view>
    <view class="card empty">
      <text class="face">👨‍🍳</text>
      <text class="tip">还没有订单哦 ~</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background-color: v-bind('theme.bg'); padding: 24rpx; box-sizing: border-box; }
.sub-tabs { display: flex; justify-content: center; gap: 96rpx; margin-bottom: 24rpx; }
.tab { font-size: 30rpx; color: v-bind('theme.sub'); padding-bottom: 12rpx; }
.tab.active {
  color: v-bind('theme.title'); font-weight: 600;
  border-bottom: 6rpx solid v-bind('theme.primary');
}
.card {
  background: v-bind('theme.card'); border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.empty { padding: 120rpx 0; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.face { font-size: 96rpx; }
.tip { font-size: 26rpx; color: v-bind('theme.sub'); }
</style>
