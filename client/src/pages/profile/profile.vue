<script setup lang="ts">
import { theme, getThemeMode, setThemeMode, MODE_LABELS } from '@/styles/theme';
import type { ThemeMode } from '@/styles/theme';
import { authApi, loadUser, logout } from '@/api/auth';
import type { UserView } from '@/api/auth';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';
import ActionSheet from '@/components/action-sheet.vue';
import { getApiBase, setApiBase } from '@/api/config';

interface GridItem {
  key: string;
  icon: string;
  url?: string;
}

const user = ref<UserView | null>(loadUser());
const grid: GridItem[] = [
  { key: '厨房管理', icon: '/static/icons/chefhat.png', url: '/pages/profile/manage' },
  { key: '任务大厅', icon: '/static/icons/grid-mission.png' },
  { key: '厨房菜篮', icon: '/static/icons/basket.png' },
  { key: '饮食计划', icon: '/static/icons/grid-calendar.png' },
  { key: '我的积分', icon: '/static/icons/grid-coin.png' },
  { key: '数据统计', icon: '/static/icons/grid-chart.png' },
  { key: '新手教程', icon: '/static/icons/grid-book.png' },
  { key: '提点意见', icon: '/static/icons/grid-chat.png' },
  { key: '平台客服', icon: '/static/icons/grid-headset.png' },
  { key: '更多功能', icon: '/static/icons/grid-dots.png' },
];
const notices = [
  { key: 'bind', label: '绑定消息通知' },
  { key: 'system', label: '系统通知' },
  { key: 'order', label: '订单通知' },
  { key: 'comment', label: '收到的评论' },
];

const modeSheetVisible = ref(false);
const modeItems = (Object.keys(MODE_LABELS) as ThemeMode[]).map((m) => ({
  key: m,
  title: MODE_LABELS[m],
  desc: m === 'auto' ? '跟系统深浅色保持一致' : '',
}));
const currentMode = ref<ThemeMode>(getThemeMode());

function onGrid(item: GridItem) {
  if (item.url) {
    uni.navigateTo({ url: item.url });
    return;
  }
  uni.showToast({ title: `${item.key}：后续版本开发`, icon: 'none' });
}

function onPickMode(key: string) {
  modeSheetVisible.value = false;
  setThemeMode(key as ThemeMode);
  currentMode.value = key as ThemeMode;
  uni.showToast({ title: `外观：${MODE_LABELS[key as ThemeMode]}`, icon: 'none' });
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' });
}

const nameDialogVisible = ref(false);
const serverDialogVisible = ref(false);
const testResult = ref('');
const testing = ref(false);

function openServerSetting() {
  serverDialogVisible.value = true;
}

function onSaveServer(url: string) {
  serverDialogVisible.value = false;
  setApiBase(url);
  uni.showToast({ title: '服务器地址已保存', icon: 'none' });
}

function onTestServer(url: string) {
  if (!url || testing.value) return;
  testing.value = true;
  testResult.value = '测试中…';
  const start = Date.now();
  fetch(url.replace(/\/+$/, '') + '/api/health')
    .then((res) => res.json())
    .then((data: Record<string, unknown>) => {
      const ms = Date.now() - start;
      if (data.code === 0) {
        testResult.value = `✓ 连通 · ${ms}ms`;
      } else {
        testResult.value = `✗ 响应异常`;
      }
    })
    .catch(() => (testResult.value = '✗ 无法连接'))
    .finally(() => (testing.value = false));
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

    <!-- 功能宫格 -->
    <view class="card grid-wrap">
      <view class="grid">
        <view v-for="g in grid" :key="g.key" class="grid-item" hover-class="press-dim" @tap="onGrid(g)">
          <image class="grid-icon" :src="g.icon" mode="aspectFit" />
          <text class="grid-label">{{ g.key }}</text>
        </view>
      </view>
    </view>

    <!-- 通知列表 -->
    <view class="card notice-wrap">
      <view v-for="n in notices" :key="n.key" class="notice-row" hover-class="press-bg">
        <text class="notice">{{ n.label }}</text>
        <text class="chev">›</text>
      </view>
    </view>

    <!-- 设置 -->
    <view class="card notice-wrap">
      <view class="notice-row" hover-class="press-bg" @tap="modeSheetVisible = true">
        <view class="row-with-icon">
          <image class="row-icon" src="/static/icons/grid-moon.png" mode="aspectFit" />
          <text class="notice">外观模式</text>
        </view>
        <text class="mode-value">{{ MODE_LABELS[currentMode] }} ›</text>
      </view>
      <view class="notice-row" hover-class="press-bg" @tap="openServerSetting">
        <view class="row-with-icon">
          <image class="row-icon" src="/static/icons/grid-server.png" mode="aspectFit" />
          <text class="notice">服务器设置</text>
        </view>
        <text class="chev">›</text>
      </view>
    </view>

    <view v-if="user" class="card notice-wrap">
      <view class="notice-row" hover-class="press-bg" @tap="onLogout">
        <text class="notice logout">退出登录</text>
      </view>
    </view>

    <ActionSheet
      :visible="modeSheetVisible"
      :items="modeItems"
      @select="onPickMode"
      @close="modeSheetVisible = false"
    />

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
      :show-test="true"
      :test-result="testResult"
      :testing="testing"
      @test="onTestServer"
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

.card {
  background: v-bind('theme.card'); border-radius: 24rpx; margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.grid-wrap { padding: 28rpx 12rpx 12rpx; }
.grid { display: flex; flex-wrap: wrap; }
.grid-item {
  width: 20%; display: flex; flex-direction: column; align-items: center; gap: 12rpx;
  margin-bottom: 28rpx;
}
.grid-icon { width: 64rpx; height: 64rpx; }
.grid-label { font-size: 24rpx; color: v-bind('theme.title'); }

.notice-wrap { padding: 8rpx 32rpx; }
.notice-row { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 0; }
.row-with-icon { display: flex; align-items: center; gap: 16rpx; }
.row-icon { width: 40rpx; height: 40rpx; }
.notice { font-size: 28rpx; color: v-bind('theme.title'); }
.mode-value { font-size: 26rpx; color: v-bind('theme.sub'); }
.logout { color: v-bind('theme.danger'); text-align: center; width: 100%; }
.chev { color: v-bind('theme.sub'); font-size: 32rpx; }
</style>
