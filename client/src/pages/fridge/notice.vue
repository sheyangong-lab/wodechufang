<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi } from '@/api/fridge';
import type { NotificationView } from '@/api/fridge';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { fmtDateTime } from '@/utils/fmt';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const notices = ref<NotificationView[]>([]);

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  try {
    notices.value = await fridgeApi.notifications(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

function tap(n: NotificationView) {
  if (!n.isRead && kitchenId.value) {
    fridgeApi.markRead(kitchenId.value, n.id).then(() => (n.isRead = 1));
  }
}

function fmtTime(iso: string) {
  return fmtDateTime(iso);
}
</script>

<template>
  <view class="page">
    <view v-if="notices.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-order.png" mode="aspectFit" />
      <text class="empty-tip">暂无通知，食材临期时会提醒你</text>
    </view>
    <view
      v-for="n in notices"
      :key="n.id"
      class="card notice"
      :class="{ unread: !n.isRead }"
      hover-class="press-dim"
      @tap="tap(n)"
    >
      <view class="head">
        <text class="title">{{ n.title }}</text>
        <text v-if="!n.isRead" class="dot" />
      </view>
      <text class="content">{{ n.content }}</text>
      <text class="time">{{ fmtTime(n.createdAt) }}</text>
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
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  margin-bottom: 16rpx;
}
.empty { padding: 100rpx 0; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.empty-img { width: 150rpx; height: 150rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
.notice { padding: 26rpx 30rpx; }
.notice.unread { border-left: 6rpx solid v-bind('theme.primaryBtn'); }
.head { display: flex; align-items: center; gap: 12rpx; }
.title { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: v-bind('theme.danger'); }
.content { display: block; font-size: 26rpx; color: v-bind('theme.sub'); line-height: 42rpx; margin-top: 10rpx; }
.time { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 12rpx; }
</style>
