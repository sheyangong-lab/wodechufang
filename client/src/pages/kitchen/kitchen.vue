<script setup lang="ts">
import { theme } from '@/styles/theme';

const guide = [
  '1、点击"立即登录"完成登录',
  '2、点击右侧"分类管理"添加菜谱分类',
  '3、点击"添加菜谱"添加菜谱',
];
</script>

<template>
  <view class="page">
    <!-- 厨房信息卡 -->
    <view class="kitchen-card">
      <view class="kitchen-head">
        <view class="avatar">🍳</view>
        <view class="info">
          <view class="name-row">
            <text class="lv">Lv.0</text>
            <text class="name">我的厨房</text>
          </view>
          <text class="meta">共1人</text>
        </view>
        <view class="qr">▣</view>
      </view>
      <text class="announce">公告：暂无</text>
    </view>

    <!-- 点单 / 修改 Tab + 操作 -->
    <view class="toolbar">
      <view class="mode-tabs">
        <text class="mode active">点单</text>
        <text class="mode">修改</text>
      </view>
      <view class="actions">
        <text class="btn-outline">+ 添加菜谱</text>
        <text class="btn-gray">🔍 搜索</text>
      </view>
    </view>

    <!-- 空状态引导（对照蓝本截图1） -->
    <view class="empty card">
      <text class="empty-title">操作步骤</text>
      <text v-for="line in guide" :key="line" class="empty-line">{{ line }}</text>
      <text class="warm-tip">温馨提示：菜单消失的解决办法！</text>
      <text class="empty-line">1、检查是否已登录。</text>
      <text class="empty-line">2、已登录的点击"管理" > "切换厨房"</text>
    </view>

    <!-- 底部下单栏 -->
    <view class="bottom-bar">
      <text class="cart">🛒</text>
      <text class="random">随机选菜</text>
      <text class="invite">邀请下单</text>
      <text class="submit disabled">下单</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  box-sizing: border-box;
}
.kitchen-card {
  padding: 24rpx;
}
.kitchen-head { display: flex; align-items: center; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 24rpx;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
  font-size: 48rpx; margin-right: 20rpx;
}
.name-row { display: flex; align-items: center; gap: 12rpx; }
.lv {
  background: v-bind('theme.primary'); color: #fff;
  font-size: 22rpx; border-radius: 8rpx; padding: 2rpx 10rpx;
}
.name { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.meta { font-size: 24rpx; color: v-bind('theme.sub'); }
.qr { margin-left: auto; font-size: 40rpx; color: v-bind('theme.title'); }
.announce { display: block; margin-top: 16rpx; font-size: 24rpx; color: v-bind('theme.sub'); }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 24rpx 0;
}
.mode-tabs { display: flex; gap: 32rpx; }
.mode { font-size: 30rpx; color: v-bind('theme.sub'); }
.mode.active {
  color: v-bind('theme.title'); font-weight: 600;
  border-bottom: 6rpx solid v-bind('theme.primary'); padding-bottom: 8rpx;
}
.actions { display: flex; gap: 16rpx; }
.btn-outline {
  border: 2rpx solid v-bind('theme.primaryBtn'); color: v-bind('theme.primaryBtn');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}
.btn-gray {
  background: #f2f0ea; color: v-bind('theme.sub');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}

.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.empty { padding: 48rpx 32rpx; }
.empty-title { display: block; font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 16rpx; }
.empty-line { display: block; font-size: 26rpx; color: v-bind('theme.sub'); line-height: 48rpx; }
.warm-tip { display: block; margin-top: 24rpx; font-size: 26rpx; color: v-bind('theme.danger'); font-weight: 600; }

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx; bottom: calc(120rpx + env(safe-area-inset-bottom));
  display: flex; align-items: center; gap: 24rpx;
  background: v-bind('theme.card'); border-radius: 48rpx; padding: 16rpx 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(200, 160, 80, 0.18);
}
.cart { font-size: 36rpx; }
.random { font-size: 26rpx; color: v-bind('theme.title'); text-decoration: underline; }
.invite {
  margin-left: auto; font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 32rpx; padding: 8rpx 24rpx;
}
.submit {
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 28rpx; border-radius: 32rpx; padding: 12rpx 40rpx;
}
.submit.disabled { background: #ddd8cc; }
</style>
