<script setup lang="ts">
import { theme } from '@/styles/theme';
import { kitchenApi, setCurrentKitchen } from '@/api/kitchen';
import type { KitchenView } from '@/api/kitchen';
import { ref } from 'vue';

const mode = ref<'create' | 'join'>('create');
const name = ref('');
const code = ref('');
const submitting = ref(false);

function pick(m: 'create' | 'join') {
  mode.value = m;
}

function done(k: KitchenView) {
  setCurrentKitchen(k);
  uni.showToast({ title: `已进入「${k.name}」`, icon: 'none' });
  setTimeout(() => uni.navigateBack(), 700);
}

async function submit() {
  if (submitting.value) return;
  if (mode.value === 'create') {
    if (!name.value.trim()) {
      uni.showToast({ title: '请输入厨房名称', icon: 'none' });
      return;
    }
    submitting.value = true;
    try {
      done(await kitchenApi.create(name.value.trim()));
    } finally {
      submitting.value = false;
    }
  } else {
    if (!code.value.trim()) {
      uni.showToast({ title: '请输入厨房码', icon: 'none' });
      return;
    }
    submitting.value = true;
    try {
      done(await kitchenApi.join(code.value.trim()));
    } finally {
      submitting.value = false;
    }
  }
}

function pasteCode() {
  uni.getClipboardData({
    success: (res) => {
      if (res.data) code.value = res.data.trim();
    },
  });
}
</script>

<template>
  <view class="page">
    <view class="mode-cards">
      <view class="mode-card" :class="{ active: mode === 'create' }" hover-class="press-dim" @tap="pick('create')">
        <image class="mode-icon" src="/static/icons/pot.png" mode="aspectFit" />
        <text class="mode-title">创建厨房</text>
        <text class="mode-desc">当店长，开菜单拉人下单</text>
      </view>
      <view class="mode-card" :class="{ active: mode === 'join' }" hover-class="press-dim" @tap="pick('join')">
        <image class="mode-icon" src="/static/icons/basket.png" mode="aspectFit" />
        <text class="mode-title">加入厨房</text>
        <text class="mode-desc">有厨房码，进去点菜</text>
      </view>
    </view>

    <view class="card form">
      <block v-if="mode === 'create'">
        <text class="label">厨房名称</text>
        <input v-model="name" class="input" maxlength="20" placeholder="如：601宿舍小厨房" placeholder-class="ph" />
        <text class="tip">创建后你将成为店长，可以添加菜谱、管理成员</text>
      </block>
      <block v-else>
        <text class="label">厨房码</text>
        <view class="code-row">
          <input v-model="code" class="input" placeholder="粘贴或输入店主分享的厨房码" placeholder-class="ph" />
          <text class="paste" hover-class="press-dim" @tap="pasteCode">粘贴</text>
        </view>
        <text class="tip">厨房码在店长的「厨房页」右上角可查看复制</text>
      </block>

      <button class="btn-main" :disabled="submitting" hover-class="press-sink" @tap="submit">
        {{ mode === 'create' ? '创建并进入' : '加入厨房' }}
      </button>
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
.mode-cards { display: flex; gap: 20rpx; margin-bottom: 24rpx; }
.mode-card {
  flex: 1;
  background: v-bind('theme.card');
  border: 3rpx solid transparent;
  border-radius: 24rpx;
  padding: 32rpx 24rpx;
  display: flex; flex-direction: column; align-items: center; gap: 12rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.mode-card.active { border-color: v-bind('theme.primaryBtn'); background: v-bind('theme.primaryLight'); }
.mode-icon { width: 64rpx; height: 64rpx; }
.mode-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.mode-desc { font-size: 22rpx; color: v-bind('theme.sub'); text-align: center; }

.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.form { padding: 32rpx 40rpx 48rpx; }
.label { display: block; font-size: 28rpx; color: v-bind('theme.title'); margin-bottom: 16rpx; }
.input {
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 16rpx 0; font-size: 30rpx; color: v-bind('theme.title');
}
.ph { color: v-bind('theme.sub'); }
.code-row { display: flex; align-items: center; gap: 16rpx; }
.code-row .input { flex: 1; }
.paste {
  font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 24rpx;
  padding: 6rpx 20rpx;
}
.tip { display: block; margin-top: 20rpx; font-size: 24rpx; color: v-bind('theme.sub'); }

.btn-main {
  margin-top: 40rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-main[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-main::after { border: none; }
</style>
