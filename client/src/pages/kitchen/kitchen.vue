<script setup lang="ts">
import { theme } from '@/styles/theme';
import { authApi, loadUser } from '@/api/auth';
import { kitchenApi, getCurrentKitchenId, loadKitchenCache, setCurrentKitchen } from '@/api/kitchen';
import type { KitchenDetail } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';

const loggedIn = ref(!!loadUser());
const hasKitchen = ref(false);
const detail = ref<KitchenDetail | null>(null);
const loading = ref(true);

const guide = [
  '1、点击右侧"分类管理"添加菜谱分类',
  '2、点击"添加菜谱"添加菜谱',
  '3、成员在点单页选菜下单',
];

onShow(async () => {
  loggedIn.value = !!loadUser();
  if (!loggedIn.value) {
    hasKitchen.value = false;
    loading.value = false;
    return;
  }
  const id = getCurrentKitchenId();
  if (!id) {
    // 本地没有记录时，尝试从服务端恢复（换设备场景）
    try {
      const mine = await kitchenApi.mine();
      if (mine.length > 0) {
        setCurrentKitchen(mine[mine.length - 1]);
        await loadDetail(mine[mine.length - 1].id);
        return;
      }
    } catch {
      // 未登录 token 失效等场景走 silent 处理
    }
    hasKitchen.value = false;
    loading.value = false;
    return;
  }
  detail.value = loadKitchenCache();
  hasKitchen.value = true;
  await loadDetail(id);
});

async function loadDetail(id: number) {
  try {
    detail.value = await kitchenApi.detail(id);
    hasKitchen.value = true;
    loading.value = false;
  } catch {
    // 403/404：厨房没了或被移出，清掉本地记录
    uni.removeStorageSync('kitchenId');
    uni.removeStorageSync('kitchenCache');
    detail.value = null;
    hasKitchen.value = false;
    loading.value = false;
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' });
}

function goBind() {
  uni.navigateTo({ url: '/pages/kitchen/bind' });
}

function goMembers() {
  if (detail.value) {
    uni.navigateTo({ url: `/pages/kitchen/members?id=${detail.value.kitchen.id}` });
  }
}

function copyCode() {
  if (!detail.value) return;
  uni.setClipboardData({
    data: detail.value.kitchen.code,
    success: () => uni.showToast({ title: '厨房码已复制，发给朋友即可加入', icon: 'none' }),
  });
}
</script>

<template>
  <view class="page">
    <!-- 未登录 -->
    <view v-if="!loggedIn" class="card state-card">
      <image class="state-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="state-title">登录后开始使用</text>
      <text class="state-desc">登录后可创建自己的厨房，或凭厨房码加入朋友的厨房</text>
      <button class="btn-main" hover-class="press-sink" @tap="goLogin">立即登录</button>
    </view>

    <!-- 已登录但无厨房 -->
    <view v-else-if="!hasKitchen" class="card state-card">
      <image class="state-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="state-title">还没有厨房</text>
      <text class="state-desc">创建一个厨房当店长，或输入朋友的厨房码加入</text>
      <view class="state-btns">
        <button class="btn-main half" hover-class="press-sink" @tap="goBind">创建 / 加入厨房</button>
      </view>
      <view v-if="loading" class="loading-tip"><text>加载中…</text></view>
    </view>

    <!-- 有厨房 -->
    <block v-else-if="detail">
      <!-- 厨房信息卡 -->
      <view class="kitchen-card card">
        <view class="kitchen-head">
          <view class="avatar">
            <image class="avatar-img" src="/static/icons/chefhat.png" mode="aspectFit" />
          </view>
          <view class="info" hover-class="press-dim" @tap="goMembers">
            <view class="name-row">
              <text class="lv">Lv.{{ detail.kitchen.level }}</text>
              <text class="name">{{ detail.kitchen.name }}</text>
            </view>
            <text class="meta">共{{ detail.kitchen.memberCount }}人 ›</text>
          </view>
          <image class="qr" src="/static/icons/qr.png" mode="aspectFit" hover-class="press-dim" @tap="copyCode" />
        </view>
        <text class="announce">公告：{{ detail.kitchen.announcement || '暂无' }}</text>
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

      <!-- 菜单空状态（M2 接入菜谱后替换为菜单列表） -->
      <view class="empty card">
        <text class="empty-title">菜单还是空的</text>
        <text v-for="line in guide" :key="line" class="empty-line">{{ line }}</text>
        <text class="warm-tip">邀请成员：点右上角二维码复制厨房码发给他</text>
      </view>

      <!-- 底部下单栏（贴近 tabBar） -->
      <view class="bottom-bar">
        <view class="cart-wrap" hover-class="press-dim">
          <image class="icon-lg" src="/static/icons/cart-active.png" mode="aspectFit" />
        </view>
        <text class="random" hover-class="press-dim">随机选菜</text>
        <text class="invite" hover-class="press-bg">邀请下单</text>
        <text class="submit disabled" hover-class="press-dim">下单</text>
      </view>
    </block>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 300rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.state-card {
  margin-top: 10vh;
  padding: 64rpx 48rpx;
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
}
.state-img { width: 180rpx; height: 180rpx; }
.state-title { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.state-desc { font-size: 26rpx; color: v-bind('theme.sub'); text-align: center; line-height: 40rpx; }
.state-btns { display: flex; width: 100%; margin-top: 24rpx; }
.btn-main {
  width: 100%;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 84rpx;
  margin-top: 24rpx;
}
.btn-main.half { margin-top: 0; }
.btn-main::after { border: none; }
.loading-tip { margin-top: 16rpx; font-size: 24rpx; color: v-bind('theme.sub'); }

.kitchen-card { padding: 24rpx; }
.kitchen-head { display: flex; align-items: center; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 24rpx;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
  margin-right: 20rpx;
}
.avatar-img { width: 60rpx; height: 60rpx; }
.info { flex: 1; }
.name-row { display: flex; align-items: center; gap: 12rpx; }
.lv {
  background: v-bind('theme.primary'); color: #fff;
  font-size: 22rpx; border-radius: 8rpx; padding: 2rpx 10rpx;
}
.name { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.meta { font-size: 24rpx; color: v-bind('theme.sub'); }
.qr { width: 44rpx; height: 44rpx; }
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

.empty { padding: 40rpx 32rpx; display: flex; flex-direction: column; }
.empty-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 16rpx; }
.empty-line { font-size: 26rpx; color: v-bind('theme.sub'); line-height: 48rpx; }
.warm-tip { margin-top: 24rpx; font-size: 26rpx; color: v-bind('theme.primaryBtn'); }

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx;
  /* 几乎贴着 tabBar，仅留防误触的丝缝 */
  bottom: calc(110rpx + env(safe-area-inset-bottom));
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
