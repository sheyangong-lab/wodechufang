<script setup lang="ts">
import { theme } from '@/styles/theme';
import { kitchenApi, ROLE_LABELS } from '@/api/kitchen';
import type { KitchenDetail } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';

const detail = ref<KitchenDetail | null>(null);
const kitchenId = ref<number | null>(null);

onLoad((query) => {
  kitchenId.value = query && query.id ? Number(query.id) : null;
  refresh();
});

async function refresh() {
  if (!kitchenId.value) return;
  try {
    detail.value = await kitchenApi.detail(kitchenId.value);
  } catch {
    // 错误 toast 已统一弹出
  }
}

function fmtTime(iso: string) {
  return iso.length >= 16 ? iso.slice(0, 16).replace('T', ' ') : iso;
}
</script>

<template>
  <view class="page">
    <view v-if="detail" class="list card">
      <view v-for="m in detail.members" :key="m.userId" class="member-row">
        <view class="avatar">
          <image class="avatar-img" src="/static/icons/person.png" mode="aspectFit" />
        </view>
        <view class="info">
          <view class="name-row">
            <text class="name">{{ m.nickname }}</text>
            <text class="role" :class="{ owner: m.role === 'OWNER' }">{{ ROLE_LABELS[m.role] || m.role }}</text>
          </view>
          <text class="time">{{ fmtTime(m.joinedAt) }} 加入厨房</text>
        </view>
      </view>
      <view class="count">
        <text class="count-text">共{{ detail.members.length }}个成员</text>
      </view>
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
}
.list { padding: 8rpx 32rpx; }
.member-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.avatar-img { width: 48rpx; height: 48rpx; }
.info { flex: 1; }
.name-row { display: flex; align-items: center; gap: 12rpx; }
.name { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.role {
  font-size: 20rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 8rpx;
  padding: 2rpx 10rpx;
}
.role.owner {
  color: #fff; background: v-bind('theme.primaryBtn');
  border-color: v-bind('theme.primaryBtn');
}
.time { font-size: 24rpx; color: v-bind('theme.sub'); }
.count { padding: 24rpx 0; text-align: center; }
.count-text { font-size: 24rpx; color: v-bind('theme.sub'); }
</style>
