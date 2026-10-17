<script setup lang="ts">
/**
 * 自定义底部导航（5 个入口：中枢/账本/冰箱/菜谱/我）。
 * uni-app 原生 tabBar 仅作兜底注册（pages.json 与此处保持一致），
 * App 启动时 uni.hideTabBar() 隐藏原生条，每个 tab 页底部放本组件，
 * 跳转统一走 switchTab。订单/食本不再是 tab，分别从中枢/菜谱页与「我」页进入。
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
  { key: 'hub', text: '中枢', icon: '/static/icons/tab-hub.png', iconActive: '/static/icons/tab-hub-active.png', path: '/pages/hub/hub', isTab: true },
  { key: 'ledger', text: '账本', icon: '/static/icons/tab-ledger.png', iconActive: '/static/icons/tab-ledger-active.png', path: '/pages/ledger/ledger', isTab: true },
  { key: 'fridge', text: '冰箱', icon: '/static/icons/tab-fridge.png', iconActive: '/static/icons/tab-fridge-active.png', path: '/pages/fridge/fridge', isTab: true },
  { key: 'kitchen', text: '菜谱', icon: '/static/icons/pan.png', iconActive: '/static/icons/pan-active.png', path: '/pages/kitchen/kitchen', isTab: true },
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
  position: fixed; left: 0; right: 0; bottom: 0;
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
