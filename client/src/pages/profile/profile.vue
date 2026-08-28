<script setup lang="ts">
import { theme } from '@/styles/theme';

const grid = [
  ['厨房管理', '任务大厅', '厨房菜篮', '饮食计划', '我的积分'],
  ['数据统计', '新手教程', '提点意见', '平台客服', '更多功能'],
];
const notices = ['绑定消息通知', '系统通知', '订单通知', '收到的评论'];
</script>

<template>
  <view class="page">
    <!-- 头部（对照蓝本截图13，暖黄渐变） -->
    <view class="header">
      <view class="user">
        <view class="avatar">🐻</view>
        <view>
          <text class="name">用户3242</text>
          <text class="hint">点击查看个人信息 ›</text>
        </view>
      </view>
      <text class="points">66.00 积分</text>
    </view>

    <!-- 会员横幅 -->
    <view class="vip">
      <text class="vip-text">会员尊享7项特权</text>
      <text class="vip-btn">去兑换</text>
    </view>

    <!-- 功能宫格 -->
    <view class="card grid-wrap">
      <view v-for="(row, i) in grid" :key="i" class="grid-row">
        <text v-for="item in row" :key="item" class="grid-item">{{ item }}</text>
      </view>
    </view>

    <!-- 通知列表 -->
    <view class="card notice-wrap">
      <view v-for="n in notices" :key="n" class="notice-row">
        <text class="notice">{{ n }}</text>
        <text class="chev">›</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: v-bind('theme.headerGradient'); padding: 24rpx; box-sizing: border-box; }
.header { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 8rpx; }
.user { display: flex; align-items: center; gap: 20rpx; }
.avatar {
  width: 110rpx; height: 110rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center; font-size: 56rpx;
}
.name { display: block; font-size: 36rpx; font-weight: 700; color: v-bind('theme.title'); }
.hint { font-size: 24rpx; color: v-bind('theme.sub'); }
.points { font-size: 26rpx; color: v-bind('theme.title'); }
.vip {
  display: flex; align-items: center; justify-content: space-between;
  background: v-bind('theme.vipGradient'); border-radius: 24rpx; padding: 24rpx 32rpx;
  margin: 24rpx 0;
}
.vip-text { color: #5c4a1e; font-size: 30rpx; font-weight: 700; }
.vip-btn {
  background: #3d3325; color: #e8c87e; font-size: 24rpx;
  border-radius: 32rpx; padding: 8rpx 24rpx;
}
.card {
  background: v-bind('theme.card'); border-radius: 24rpx; margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.grid-wrap { padding: 24rpx 0; }
.grid-row { display: flex; justify-content: space-around; margin-bottom: 32rpx; }
.grid-row:last-child { margin-bottom: 0; }
.grid-item { font-size: 26rpx; color: v-bind('theme.title'); }
.notice-wrap { padding: 8rpx 32rpx; }
.notice-row { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 0; }
.notice { font-size: 28rpx; color: v-bind('theme.title'); }
.chev { color: v-bind('theme.sub'); font-size: 32rpx; }
</style>
