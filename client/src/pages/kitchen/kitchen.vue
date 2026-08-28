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
      <view class="kitchen-head" hover-class="press-dim">
        <view class="avatar">
          <image class="avatar-img" src="/static/icons/chefhat.png" mode="aspectFit" />
        </view>
        <view class="info">
          <view class="name-row">
            <text class="lv">Lv.0</text>
            <text class="name">我的厨房</text>
          </view>
          <text class="meta">共1人</text>
        </view>
        <image class="qr" src="/static/icons/qr.png" mode="aspectFit" />
      </view>
      <text class="announce">公告：暂无</text>
    </view>

    <!-- 点单 / 修改 Tab + 操作 -->
    <view class="toolbar">
      <view class="mode-tabs">
        <text class="mode active" hover-class="press-dim">点单</text>
        <text class="mode" hover-class="press-dim">修改</text>
      </view>
      <view class="actions">
        <text class="btn-outline" hover-class="press-bg">＋ 添加菜谱</text>
        <view class="btn-gray" hover-class="press-dim">
          <image class="icon-sm" src="/static/icons/search.png" mode="aspectFit" />
          <text>搜索</text>
        </view>
      </view>
    </view>

    <!-- 空状态引导（对照蓝本截图1） -->
    <view class="empty card">
      <image class="empty-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="empty-title">操作步骤</text>
      <text v-for="line in guide" :key="line" class="empty-line">{{ line }}</text>
      <text class="warm-tip">温馨提示：菜单消失的解决办法！</text>
      <text class="empty-line">1、检查是否已登录。</text>
      <text class="empty-line">2、已登录的点击"管理" > "切换厨房"</text>
    </view>

    <!-- 底部下单栏（与 tabBar 拉开距离） -->
    <view class="bottom-bar">
      <view class="cart-wrap" hover-class="press-dim">
        <image class="icon-lg" src="/static/icons/cart-active.png" mode="aspectFit" />
      </view>
      <text class="random" hover-class="press-dim">随机选菜</text>
      <text class="invite" hover-class="press-bg">邀请下单</text>
      <text class="submit disabled" hover-class="press-dim">下单</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 340rpx; /* 给悬浮下单栏留出空间，避免遮挡内容 */
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
  margin-right: 20rpx;
}
.avatar-img { width: 60rpx; height: 60rpx; }
.name-row { display: flex; align-items: center; gap: 12rpx; }
.lv {
  background: v-bind('theme.primary'); color: #fff;
  font-size: 22rpx; border-radius: 8rpx; padding: 2rpx 10rpx;
}
.name { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.meta { font-size: 24rpx; color: v-bind('theme.sub'); }
.qr { margin-left: auto; width: 44rpx; height: 44rpx; }
.announce { display: block; margin-top: 16rpx; font-size: 24rpx; color: v-bind('theme.sub'); }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 24rpx 0;
}
.mode-tabs { display: flex; gap: 32rpx; }
.mode { font-size: 30rpx; color: v-bind('theme.sub'); padding-bottom: 8rpx; }
.mode.active {
  color: v-bind('theme.title'); font-weight: 600;
  border-bottom: 6rpx solid v-bind('theme.primary');
}
.actions { display: flex; gap: 16rpx; }
.btn-outline {
  border: 2rpx solid v-bind('theme.primaryBtn'); color: v-bind('theme.primaryBtn');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}
.btn-gray {
  display: flex; align-items: center; gap: 8rpx;
  background: #f2f0ea; color: v-bind('theme.sub');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}
.icon-sm { width: 30rpx; height: 30rpx; }

.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.empty { padding: 40rpx 32rpx; display: flex; flex-direction: column; }
.empty-img { width: 160rpx; height: 160rpx; align-self: center; margin-bottom: 16rpx; }
.empty-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 16rpx; }
.empty-line { font-size: 26rpx; color: v-bind('theme.sub'); line-height: 48rpx; }
.warm-tip { margin-top: 24rpx; font-size: 26rpx; color: v-bind('theme.danger'); font-weight: 600; }

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx;
  /* 距 tabBar 约 60rpx 视觉间隙，不再贴着底部导航 */
  bottom: calc(170rpx + env(safe-area-inset-bottom));
  display: flex; align-items: center; gap: 24rpx;
  background: v-bind('theme.card'); border-radius: 48rpx; padding: 16rpx 32rpx;
  box-shadow: 0 6rpx 20rpx rgba(200, 160, 80, 0.28);
  border: 2rpx solid v-bind('theme.divider');
}
.cart-wrap { display: flex; }
.icon-lg { width: 44rpx; height: 44rpx; }
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
