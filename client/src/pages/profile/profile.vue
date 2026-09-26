<script setup lang="ts">
import { theme, getThemeMode, setThemeMode, MODE_LABELS } from '@/styles/theme';
import type { ThemeMode } from '@/styles/theme';
import { authApi, loadUser, logout } from '@/api/auth';
import type { UserView } from '@/api/auth';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import CustomTabbar from '@/components/custom-tabbar.vue';
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
  { key: '厨房管理', icon: '/static/icons/pan.png', url: '/pages/profile/manage' },
  { key: '厨房菜篮', icon: '/static/icons/basket.png', url: '/pages/basket/basket' },
  { key: '饮食计划', icon: '/static/icons/grid-calendar.png', url: '/pages/plan/plan' },
  { key: '数据统计', icon: '/static/icons/grid-chart.png' },
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

// ---------- 检查更新 ----------
// eslint-disable-next-line
const AppBridge = (): any => (globalThis as Record<string, any>).Capacitor?.Plugins?.AppBridge;
const updating = ref(false);

async function checkUpdate() {
  if (updating.value) return;
  updating.value = true;
  try {
    const res = await fetch(getApiBase().replace(/\/+$/, '') + '/api/app/version');
    const body = await res.json();
    if (body.code !== 0) throw new Error(body.message || '检查失败');
    const meta = body.data as {
      hasUpdate: boolean; versionCode?: number; versionName?: string;
      notes?: string; url?: string;
    };
    const bridge = AppBridge();
    let localCode = 0;
    let localName = '未知';
    if (bridge && (globalThis as Record<string, any>).Capacitor?.isNativePlatform?.()) {
      const local = await bridge.checkLocal();
      localCode = Number(local.versionCode) || 0;
      localName = String(local.versionName);
    }
    if (!meta.hasUpdate) {
      uni.showToast({ title: '服务器暂无更新包', icon: 'none' });
      return;
    }
    if (localCode > 0 && meta.versionCode && meta.versionCode <= localCode) {
      uni.showToast({ title: `已是最新版本 ${localName}`, icon: 'none' });
      return;
    }
    uni.showModal({
      title: `发现新版本 ${meta.versionName || ''}`,
      content: (meta.notes || '功能与问题修复') + '\n是否下载安装？',
      confirmText: '立即更新',
      success: (r) => {
        if (!r.confirm || !bridge || !meta.url) return;
        uni.showLoading({ title: '下载中 0%', mask: true });
        bridge.addListener('progress', (p: { percent: number }) => {
          uni.showLoading({ title: `下载中 ${p.percent}%`, mask: true });
        });
        bridge
          .downloadApk({ url: getApiBase() + meta.url })
          .then((d: { path: string }) => bridge.installApk({ path: d.path }))
          .then(() => uni.hideLoading())
          .catch((e: Error) => {
            uni.hideLoading();
            uni.showToast({ title: e.message?.slice(0, 40) || '更新失败', icon: 'none' });
          });
      },
    });
  } catch (e) {
    uni.showToast({ title: (e as Error).message?.slice(0, 40) || '检查更新失败', icon: 'none' });
  } finally {
    updating.value = false;
  }
}

function goSync() {
  uni.navigateTo({ url: '/pages/sync/sync' });
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
    .then((res) => {
      if (!res.ok) {
        // 有 HTTP 响应但非 2xx：网络是通的，服务端/路径问题
        testResult.value = `✗ HTTP ${res.status}`;
        testing.value = false;
        return null;
      }
      return res.json();
    })
    .then((data: Record<string, unknown> | null) => {
      if (data === null) return;
      const ms = Date.now() - start;
      if (data.code === 0) {
        testResult.value = `✓ 连通 · ${ms}ms`;
      } else {
        testResult.value = `✗ 响应异常`;
      }
    })
    .catch((err: unknown) => {
      // 透出具体原因：证书校验失败 / 超时 / 其他，便于远程定位
      const msg = err instanceof Error ? err.message : String(err);
      const short = /certificate|SSL|TLS/i.test(msg)
        ? '✗ 证书校验失败'
        : /timeout/i.test(msg)
          ? '✗ 连接超时'
          : `✗ 无法连接 (${msg.slice(0, 40)})`;
      testResult.value = short;
    })
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
    <view class="page-scroll">
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
      <view class="notice-row" hover-class="press-bg" @tap="checkUpdate">
        <view class="row-with-icon">
          <image class="row-icon" src="/static/icons/grid-chart.png" mode="aspectFit" />
          <text class="notice">检查更新</text>
        </view>
        <text class="mode-value">{{ updating ? '检查中…' : '新版本自动提醒 ›' }}</text>
      </view>
      <view class="notice-row" hover-class="press-bg" @tap="modeSheetVisible = true">
        <view class="row-with-icon">
          <image class="row-icon" src="/static/icons/grid-moon.png" mode="aspectFit" />
          <text class="notice">外观模式</text>
        </view>
        <text class="mode-value">{{ MODE_LABELS[currentMode] }} ›</text>
      </view>
      <view class="notice-row" hover-class="press-bg" @tap="goSync">
        <view class="row-with-icon">
          <image class="row-icon" src="/static/icons/grid-dots.png" mode="aspectFit" />
          <text class="notice">设备直连同步</text>
        </view>
        <text class="mode-value">附近设备 · 免服务器 ›</text>
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
      placeholder="https://你的域名 或 http://192.168.x.x:8080"
      :maxlength="100"
      :show-test="true"
      :test-result="testResult"
      :testing="testing"
      @test="onTestServer"
      @confirm="onSaveServer"
      @close="serverDialogVisible = false"
    />
  
        </view>
    <CustomTabbar current="me" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex; flex-direction: column;
  /* WebView 的 vh 计算不稳（部分机型 100vh > 可视区导致顶部裁切/底栏溢出），
     App.vue 启动时 JS 写入实际屏幕 px */
  height: calc(var(--sk-vh, 100vh) - var(--window-top, 0px)); /* 减去fixed导航栏高度，配合uni-page-body的padding-top让位 */
  overflow: hidden;
}
.page-scroll {
  flex: 1; overflow-y: auto;
  min-height: 0;
  min-height: 100vh; background: v-bind('theme.headerGradient'); padding: 24rpx; box-sizing: border-box;
}
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
