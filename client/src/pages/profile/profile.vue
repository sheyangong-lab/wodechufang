<script setup lang="ts">
import { theme } from '@/styles/theme';
import { authApi, loadUser, logout } from '@/api/auth';
import type { UserView } from '@/api/auth';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';
import { getApiBase, setApiBase } from '@/api/config';

const user = ref<UserView | null>(loadUser());
const grid = [
  ['厨房管理', '任务大厅', '厨房菜篮', '饮食计划', '我的积分'],
  ['数据统计', '新手教程', '提点意见', '平台客服', '更多功能'],
];
const notices = ['绑定消息通知', '系统通知', '订单通知', '收到的评论'];

function onGrid(item: string) {
  if (item === '厨房管理') {
    uni.navigateTo({ url: '/pages/profile/manage' });
    return;
  }
  uni.showToast({ title: `${item}：后续版本开发`, icon: 'none' });
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' });
}

const nameDialogVisible = ref(false);
const serverDialogVisible = ref(false);

function openServerSetting() {
  serverDialogVisible.value = true;
}

function onSaveServer(url: string) {
  serverDialogVisible.value = false;
  setApiBase(url);
  uni.showToast({ title: '服务器地址已保存', icon: 'none' });
}

function editNickname() {
  if (!user.value) return;
  nameDialogVisible.value = true;
}

function onNickname(nickname: string) {
  nameDialogVisible.value = false;
  authApi.updateNickname(nickname).then((u) => {
    uni.setStorageSync('user', JSON.stringify(u));
    user.value = u;
    uni.showToast({ title: '昵称已更新', icon: 'none' });
  });
}

onShow(() => {
  user.value = loadUser();
});

function onLogout() {
  logout();
  user.value = null;
  uni.showToast({ title: '已退出登录', icon: 'none' });
}
</script>

<template>
  <view class="page">
    <!-- 头部（对照蓝本截图13，暖黄渐变） -->
    <view class="header" hover-class="press-dim" @tap="user ? editNickname() : goLogin()">
      <view class="user">
        <view class="avatar">
          <image class="avatar-img" src="/static/icons/person.png" mode="aspectFit" />
        </view>
        <view>
          <text class="name">{{ user ? user.nickname : '点击登录' }}</text>
          <text class="hint">{{ user ? user.phoneMasked + ' · 点此改昵称 ›' : '登录后开启共享厨房 ›' }}</text>
        </view>
      </view>
      <text class="points">{{ user ? user.points.toFixed(2) + ' 积分' : '' }}</text>
    </view>

    <!-- 会员横幅 -->
    <view class="vip" hover-class="press-sink">
      <text class="vip-text">会员尊享7项特权</text>
      <text class="vip-btn" hover-class="press-dim">去兑换</text>
    </view>

    <!-- 功能宫格 -->
    <view class="card grid-wrap">
      <view v-for="(row, i) in grid" :key="i" class="grid-row">
        <text v-for="item in row" :key="item" class="grid-item" hover-class="press-dim" @tap="onGrid(item)">{{ item }}</text>
      </view>
    </view>

    <!-- 通知列表 -->
    <view class="card notice-wrap">
      <view v-for="n in notices" :key="n" class="notice-row" hover-class="press-bg">
        <text class="notice">{{ n }}</text>
        <text class="chev">›</text>
      </view>
    </view>

    <view class="card notice-wrap">
      <view class="notice-row" hover-class="press-bg" @tap="serverDialogVisible = true">
        <text class="notice">服务器设置</text>
        <text class="chev">›</text>
      </view>
    </view>

    <view v-if="user" class="card notice-wrap">
      <view class="notice-row" hover-class="press-bg" @tap="onLogout">
        <text class="notice logout">退出登录</text>
      </view>
    </view>

    <InputDialog
      :visible="nameDialogVisible"
      title="修改昵称"
      :default-value="user?.nickname"
      :maxlength="20"
      @confirm="onNickname"
      @close="nameDialogVisible = false"
    />

    <InputDialog
      :visible="serverDialogVisible"
      title="服务器设置"
      :default-value="getApiBase()"
      placeholder="http://IP:8080"
      :maxlength="100"
      @confirm="onSaveServer"
      @close="serverDialogVisible = false"
    />
  </view>
</template>

<style lang="scss" scoped>
.page { min-height: 100vh; background: v-bind('theme.headerGradient'); padding: 24rpx; box-sizing: border-box; }
.header { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 8rpx; }
.user { display: flex; align-items: center; gap: 20rpx; }
.avatar {
  width: 110rpx; height: 110rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.avatar-img { width: 60rpx; height: 60rpx; }
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
.grid-item { font-size: 26rpx; color: v-bind('theme.title'); padding: 8rpx 12rpx; }
.notice-wrap { padding: 8rpx 32rpx; }
.notice-row { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 0; }
.notice { font-size: 28rpx; color: v-bind('theme.title'); }
.logout { color: v-bind('theme.danger'); text-align: center; width: 100%; }
.chev { color: v-bind('theme.sub'); font-size: 32rpx; }
</style>
