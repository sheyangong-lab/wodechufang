<script setup lang="ts">
import { theme } from '@/styles/theme';
import { computed, ref } from 'vue';

// 演示数据：M4-T2 接真实食材
const states = ref({ fresh: 0, expiring: 1, expired: 0 });
const chips = computed(() => [
  { label: `新鲜(${states.value.fresh})`, color: theme.success },
  { label: `快过期(${states.value.expiring})`, color: theme.warning },
  { label: `已过期(${states.value.expired})`, color: theme.danger },
]);
</script>

<template>
  <view class="page">
    <view class="toolbar">
      <view class="chips">
        <text v-for="c in chips" :key="c.label" class="chip">
          <text class="dot" :style="{ background: c.color }" />{{ c.label }}
        </text>
      </view>
      <text class="put-btn">放入食材</text>
    </view>
    <view class="search">
      <text class="placeholder">🔍 输入食材名称进行搜索</text>
      <text class="go">搜索</text>
    </view>
    <view class="body">
      <view class="side">
        <text class="cat active">全部</text>
        <text class="cat-manage">⚙️ 类别管理</text>
      </view>
      <view class="card item">
        <text class="icon">🧺</text>
        <view class="info">
          <view class="row">
            <text class="name">111</text>
            <text class="expiring">1天后过期</text>
          </view>
          <text class="line">剩余数量：27272</text>
          <text class="line">注：快过期了3</text>
          <text class="line">产自：2026-08-28　保质期：1天</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background-color: v-bind('theme.bg'); padding: 24rpx; box-sizing: border-box; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.chips { display: flex; gap: 20rpx; }
.chip { font-size: 24rpx; color: v-bind('theme.title'); }
.dot { display: inline-block; width: 16rpx; height: 16rpx; border-radius: 50%; margin-right: 6rpx; }
.put-btn {
  border: 2rpx solid v-bind('theme.title'); border-radius: 12rpx;
  padding: 8rpx 20rpx; font-size: 26rpx; color: v-bind('theme.title');
}
.search {
  display: flex; align-items: center; background: v-bind('theme.card');
  border-radius: 16rpx; padding: 16rpx 24rpx; margin-bottom: 20rpx;
}
.placeholder { flex: 1; font-size: 26rpx; color: v-bind('theme.sub'); }
.go { color: v-bind('theme.primaryBtn'); font-size: 28rpx; font-weight: 600; }
.body { display: flex; gap: 20rpx; }
.side { width: 160rpx; display: flex; flex-direction: column; gap: 24rpx; }
.cat {
  font-size: 28rpx; color: v-bind('theme.title');
  background: v-bind('theme.primaryLight'); border-radius: 12rpx; padding: 12rpx 0;
  text-align: center;
}
.cat-manage { font-size: 24rpx; color: v-bind('theme.primaryBtn'); }
.card {
  background: v-bind('theme.card'); border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.item {
  flex: 1; display: flex; gap: 20rpx; padding: 24rpx;
  border: 2rpx solid v-bind('theme.warning');
}
.icon { font-size: 56rpx; }
.info { flex: 1; }
.row { display: flex; justify-content: space-between; margin-bottom: 8rpx; }
.name { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.expiring { font-size: 24rpx; color: v-bind('theme.warning'); }
.line { display: block; font-size: 24rpx; color: v-bind('theme.sub'); line-height: 40rpx; }
</style>
