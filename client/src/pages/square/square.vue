<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi, fenToYuan, fullUrl } from '@/api/dish';
import type { DishView } from '@/api/dish';
import { ensureKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';

const keyword = ref('');
const dishes = ref<DishView[]>([]);
const loaded = ref(false);
const cloningId = ref<number | null>(null);

// 广场是全局的，不依赖 kitchenId；克隆时才定位目标厨房
onLoad(() => search());

function search() {
  dishApi
    .squareList(keyword.value || undefined)
    .then((list) => {
      dishes.value = list;
      loaded.value = true;
    })
    .catch(() => (loaded.value = true));
}

function onInput(e: { detail: { value: string } }) {
  keyword.value = e.detail.value;
}

/** 一键克隆到自己厨房 */
function clone(d: DishView) {
  if (cloningId.value) return;
  cloningId.value = d.id;
  ensureKitchenId()
    .then((kitchenId) => {
      if (!kitchenId) {
        uni.showToast({ title: '请先创建或加入厨房', icon: 'none' });
        return;
      }
      return dishApi.clone(kitchenId, d.id).then(() => {
        uni.showToast({ title: `「${d.name}」已克隆到你的厨房`, icon: 'none' });
        search();
      });
    })
    .finally(() => (cloningId.value = null));
}
</script>

<template>
  <view class="page">
    <view class="search-bar">
      <input
        class="input"
        :value="keyword"
        placeholder="搜索广场菜谱"
        placeholder-class="ph"
        confirm-type="search"
        @input="onInput"
        @confirm="search"
      />
      <text class="go" hover-class="press-dim" @tap="search">搜索</text>
    </view>

    <view v-if="loaded && dishes.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="empty-tip">广场上还没有分享的菜谱，首家等你来</text>
    </view>

    <view v-for="d in dishes" :key="d.id" class="card dish-card" hover-class="press-dim">
      <image v-if="d.imageUrl" class="thumb" :src="fullUrl(d.imageUrl)" mode="aspectFill" />
      <view v-else class="thumb holder">
        <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
      </view>
      <view class="info">
        <text class="name">{{ d.name }}</text>
        <view v-if="d.recommendStars > 0" class="stars">
          <text v-for="i in d.recommendStars" :key="i" class="star">★</text>
        </view>
        <text class="price">¥{{ fenToYuan(d.priceFen) }}</text>
      </view>
      <text class="clone-btn" hover-class="press-bg" @tap="clone(d)">克隆</text>
    </view>

    <text class="foot-tip">克隆会把菜谱复制一份到自己厨房，可再编辑</text>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 60rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.search-bar { display: flex; align-items: center; gap: 20rpx; margin-bottom: 20rpx; }
.input {
  flex: 1;
  background: v-bind('theme.card');
  border: 2rpx solid v-bind('theme.primary');
  border-radius: 16rpx;
  padding: 16rpx 24rpx; font-size: 26rpx; color: v-bind('theme.title');
}
.ph { color: v-bind('theme.sub'); }
.go { font-size: 28rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }
.empty { padding: 90rpx 0; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.empty-img { width: 150rpx; height: 150rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); text-align: center; }
.dish-card {
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
.stars { display: flex; }
.star { color: v-bind('theme.primaryBtn'); font-size: 22rpx; margin-right: 2rpx; }
.price { font-size: 26rpx; font-weight: 700; color: v-bind('theme.income'); }
.clone-btn {
  font-size: 26rpx; color: #fff; background: v-bind('theme.primaryBtn');
  border-radius: 28rpx; padding: 10rpx 26rpx;
}
.foot-tip { display: block; text-align: center; margin-top: 20rpx; font-size: 22rpx; color: v-bind('theme.sub'); }
</style>
