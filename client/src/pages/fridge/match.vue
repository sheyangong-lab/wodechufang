<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi } from '@/api/fridge';
import type { MatchedDish } from '@/api/fridge';
import { fenToYuan, fullUrl } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';

const ingredient = ref('');
const dishes = ref<MatchedDish[]>([]);
const loaded = ref(false);
const kitchenId = ref<number | null>(null);

onLoad(async (query) => {
  ingredient.value = (query && query.name) || '';
  kitchenId.value = await ensureKitchenId();
  search();
});

function search() {
  if (!kitchenId.value || !ingredient.value.trim()) {
    loaded.value = true;
    return;
  }
  fridgeApi
    .match(kitchenId.value, { ingredient: ingredient.value.trim() })
    .then((list) => {
      dishes.value = list;
      loaded.value = true;
    })
    .catch(() => (loaded.value = true));
}

function openDish(id: number) {
  uni.navigateTo({ url: `/pages/dish/detail?id=${id}` });
}
</script>

<template>
  <view class="page">
    <view class="search-bar">
      <input
        class="input"
        :value="ingredient"
        placeholder="输入食材名，用冰箱里的食材做菜"
        placeholder-class="ph"
        confirm-type="search"
        @input="(e: any) => (ingredient = e.detail.value)"
        @confirm="search"
      />
      <text class="go" hover-class="press-dim" @tap="search">搜索</text>
    </view>

    <view v-if="loaded && dishes.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="empty-tip">菜单里还没有用到「{{ ingredient }}」的菜</text>
    </view>

    <view v-for="d in dishes" :key="d.id" class="card dish-card" hover-class="press-dim" @tap="openDish(d.id)">
      <image v-if="d.imageUrl" class="thumb" :src="fullUrl(d.imageUrl)" mode="aspectFill" />
      <view v-else class="thumb holder">
        <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
      </view>
      <view class="info">
        <text class="name">{{ d.name }}</text>
        <text class="material">用料：{{ d.materialLine }}</text>
      </view>
      <text class="price">¥{{ fenToYuan(d.priceFen) }}</text>
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
.search-bar {
  display: flex; align-items: center; gap: 20rpx; margin-bottom: 20rpx;
}
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
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
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
.material { font-size: 24rpx; color: v-bind('theme.sub'); }
.price { font-size: 28rpx; font-weight: 700; color: v-bind('theme.income'); }
</style>
