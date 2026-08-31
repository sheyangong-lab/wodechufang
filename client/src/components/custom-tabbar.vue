<script setup lang="ts">
/**
 * 自定义底部导航（6 个入口）。
 * uni-app 原生 tabBar 上限 5 个，要加「食本」只能自绘：App 启动时
 * uni.hideTabBar() 隐藏原生条，每个 tab 页底部放本组件。
 * 原 5 个 tab 页仍走 switchTab（原生隐藏不影响跳转），食本用 redirectTo。
 */
import { theme } from '@/styles/theme';

const props = defineProps<{ current: string }>();

interface TabDef {
  key: string;
  text: string;
  icon: string;
  iconActive: string;
  path: string;
  isTab: boolean;
}

const tabs: TabDef[] = [
  { key: 'kitchen', text: '厨房', icon: '/static/icons/pan.png', iconActive: '/static/icons/pan-active.png', path: '/pages/kitchen/kitchen', isTab: true },
  { key: 'orders', text: '订单', icon: '/static/icons/tab-order.png', iconActive: '/static/icons/tab-order-active.png', path: '/pages/order/order', isTab: true },
  { key: 'ledger', text: '账本', icon: '/static/icons/tab-ledger.png', iconActive: '/static/icons/tab-ledger-active.png', path: '/pages/ledger/ledger', isTab: true },
  { key: 'fridge', text: '冰箱', icon: '/static/icons/tab-fridge.png', iconActive: '/static/icons/tab-fridge-active.png', path: '/pages/fridge/fridge', isTab: true },
  { key: 'foodbook', text: '食本', icon: '/static/icons/tab-book.png', iconActive: '/static/icons/tab-book-active.png', path: '/pages/foodbook/foodbook', isTab: false },
  { key: 'me', text: '我', icon: '/static/icons/tab-me.png', iconActive: '/static/icons/tab-me-active.png', path: '/pages/profile/profile', isTab: true },
];

function go(t: TabDef) {
  if (t.key === props.current) return;
  if (t.isTab) {
    uni.switchTab({ url: t.path });
  } else {
    uni.redirectTo({ url: t.path });
  }
}
</script>

<template>
  <view class="ctb">
    <view
      v-for="t in tabs"
      :key="t.key"
      class="ctb-item"
      hover-class="press-dim"
      @tap="go(t)"
    >
      <image class="ctb-icon" :src="t.key === current ? t.iconActive : t.icon" mode="aspectFit" />
      <text class="ctb-text" :class="{ on: t.key === current }">{{ t.text }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.ctb {
  /* 文档流布局（页面根为 flex 列）：不依赖 fixed，规避 WebView containing-block 兼容问题 */
  position: relative;
  flex-shrink: 0;
  display: flex;
  background: v-bind('theme.tabBarBg');
  border-top: 2rpx solid v-bind('theme.divider');
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 500;
}
.ctb-item {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  padding: 12rpx 0 8rpx; gap: 2rpx;
}
.ctb-icon { width: 52rpx; height: 52rpx; }
.ctb-text { font-size: 20rpx; color: v-bind('theme.sub'); }
.ctb-text.on { color: v-bind('theme.primaryBtn'); font-weight: 600; }
</style>
