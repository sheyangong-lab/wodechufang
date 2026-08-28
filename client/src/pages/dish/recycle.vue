<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi, fenToYuan, fullUrl } from '@/api/dish';
import type { DishView } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const dishes = ref<DishView[]>([]);

onShow(load);

async function load() {
  if (!kitchenId.value) return;
  try {
    dishes.value = await dishApi.recycleList(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

function restore(d: DishView) {
  dishApi.restore(d.id).then(() => {
    uni.showToast({ title: `「${d.name}」已恢复`, icon: 'none' });
    load();
  });
}
</script>

<template>
  <view class="page">
    <view v-if="dishes.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="empty-tip">回收站是空的</text>
    </view>
    <view v-for="d in dishes" :key="d.id" class="card row-card" hover-class="press-dim">
      <image v-if="d.imageUrl" class="thumb" :src="fullUrl(d.imageUrl)" mode="aspectFill" />
      <view v-else class="thumb holder">
        <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
      </view>
      <view class="info">
        <text class="name">{{ d.name }}</text>
        <text class="price">¥{{ fenToYuan(d.priceFen) }}</text>
      </view>
      <text class="restore" hover-class="press-dim" @tap.stop="restore(d)">恢复</text>
    </view>
    <text v-if="dishes.length > 0" class="hint">回收站的菜不占菜品额度，恢复时重新计算</text>
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
.empty { padding: 100rpx 0; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-img { width: 160rpx; height: 160rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
.row-card {
  display: flex; align-items: center; gap: 20rpx;
  padding: 20rpx 24rpx; margin-bottom: 16rpx;
}
.thumb { width: 96rpx; height: 96rpx; border-radius: 16rpx; }
.thumb.holder {
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.holder-icon { width: 56rpx; height: 56rpx; opacity: 0.5; }
.info { flex: 1; }
.name { display: block; font-size: 30rpx; font-weight: 500; color: v-bind('theme.title'); }
.price { font-size: 26rpx; color: v-bind('theme.income'); font-weight: 600; }
.restore {
  font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 28rpx;
  padding: 8rpx 24rpx;
}
.hint { display: block; text-align: center; margin-top: 24rpx; font-size: 22rpx; color: v-bind('theme.sub'); }
</style>
