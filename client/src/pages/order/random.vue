<script setup lang="ts">
import { theme } from '@/styles/theme';
import { orderApi } from '@/api/order';
import { dishApi, fenToYuan, fullUrl } from '@/api/dish';
import type { DishView, CategoryView } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { useCartStore } from '@/stores/cart';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';

const cart = useCartStore();
const kitchenId = ref<number | null>(getCurrentKitchenId());
const categories = ref<CategoryView[]>([]);
const activeCatId = ref<number | null>(null);
const count = ref('1');
const results = ref<DishView[]>([]);
const rolling = ref(false);

onShow(() => {
  kitchenId.value = getCurrentKitchenId();
  if (kitchenId.value) {
    dishApi.categories(kitchenId.value).then((cs) => (categories.value = cs)).catch(() => {});
  }
});

const countNum = computed(() => {
  const n = Number(count.value);
  return Number.isNaN(n) || n < 1 ? 1 : Math.min(n, 10);
});

function pickCat(id: number | null) {
  activeCatId.value = id;
}

function roll() {
  if (!kitchenId.value || rolling.value) return;
  rolling.value = true;
  orderApi
    .randomDishes(kitchenId.value, activeCatId.value, countNum.value)
    .then((list) => {
      results.value = list;
      if (list.length === 0) uni.showToast({ title: '这个分类下还没有菜', icon: 'none' });
    })
    .finally(() => (rolling.value = false));
}

function addAll() {
  results.value.forEach((d) => cart.add(d));
  uni.showToast({ title: `已加入购物车（${results.value.length}道）`, icon: 'none' });
}

function addOne(d: DishView) {
  cart.add(d);
  uni.showToast({ title: '已加入购物车', icon: 'none' });
}

function goConfirm() {
  uni.navigateTo({ url: '/pages/order/confirm' });
}
</script>

<template>
  <view class="page">
    <view class="card section">
      <text class="sec-title">分类</text>
      <view class="chips">
        <text
          class="chip"
          :class="{ on: activeCatId === null }"
          hover-class="press-dim"
          @tap="pickCat(null)"
        >全部</text>
        <text
          v-for="c in categories"
          :key="c.id"
          class="chip"
          :class="{ on: activeCatId === c.id }"
          hover-class="press-dim"
          @tap="pickCat(c.id)"
        >{{ c.name }}</text>
      </view>

      <view class="count-row">
        <text class="label">随机道数</text>
        <input v-model="count" class="count-input" type="number" maxlength="2" />
      </view>

      <button class="btn-roll" :disabled="rolling" hover-class="press-sink" @tap="roll">
        ⇄ 随机点菜
      </button>
      <text class="slogan">选择困难症？让我来帮你决定！随机挑选美味佳肴，满足你的味蕾。</text>
    </view>

    <!-- 抽取结果 -->
    <view v-for="d in results" :key="d.id" class="card dish-card">
      <image v-if="d.imageUrl" class="thumb" :src="fullUrl(d.imageUrl)" mode="aspectFill" />
      <view v-else class="thumb holder">
        <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
      </view>
      <view class="info">
        <text class="name">{{ d.name }}</text>
        <text class="price">¥{{ fenToYuan(d.priceFen) }}</text>
      </view>
      <text class="add-btn" hover-class="press-bg" @tap="addOne(d)">加入购物车</text>
    </view>

    <view v-if="results.length > 1" class="add-all-wrap">
      <button class="btn-addall" hover-class="press-sink" @tap="addAll">全部加入购物车</button>
    </view>

    <!-- 广告占位（对照蓝本截图17） -->
    <view class="card ad">
      <view class="ad-box">
        <image class="ad-icon" src="/static/icons/receipt.png" mode="aspectFit" />
        <text class="ad-text">广告位招租</text>
      </view>
      <text class="ad-sorry">加个广告，感谢理解哦~</text>
    </view>

    <!-- 底部购物车栏 -->
    <view v-if="cart.count > 0" class="bottom-bar">
      <view class="cart-left" hover-class="press-dim" @tap="goConfirm">
        <image class="icon-lg" src="/static/icons/cart-active.png" mode="aspectFit" />
        <text class="cart-count">{{ cart.count }}</text>
        <text class="cart-total">¥{{ fenToYuan(cart.totalFen) }}</text>
      </view>
      <text class="go" hover-class="press-bg" @tap="goConfirm">去下单</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 220rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.section { padding: 28rpx 32rpx; margin-bottom: 20rpx; }
.sec-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.chips { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; }
.chip {
  font-size: 26rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 28rpx;
  padding: 8rpx 28rpx;
}
.chip.on {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); font-weight: 600;
}
.count-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 28rpx;
}
.label { font-size: 28rpx; color: v-bind('theme.title'); }
.count-input {
  width: 140rpx; text-align: center;
  border: 2rpx solid v-bind('theme.primary'); border-radius: 14rpx;
  padding: 12rpx 0; font-size: 30rpx; font-weight: 600; color: v-bind('theme.title');
}
.btn-roll {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-roll[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-roll::after { border: none; }
.slogan { display: block; text-align: center; margin-top: 24rpx; font-size: 24rpx; color: v-bind('theme.sub'); line-height: 40rpx; }

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
.price { font-size: 26rpx; color: v-bind('theme.income'); font-weight: 600; }
.add-btn {
  font-size: 24rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 28rpx;
  padding: 8rpx 20rpx;
}
.add-all-wrap { margin: 8rpx 0 20rpx; }
.btn-addall {
  background: #fff; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn');
  border-radius: 24rpx; font-size: 30rpx; line-height: 84rpx;
}
.btn-addall::after { border: none; }

.ad { padding: 24rpx; }
.ad-box {
  display: flex; flex-direction: column; align-items: center; gap: 12rpx;
  border: 2rpx dashed v-bind('theme.divider'); border-radius: 16rpx;
  padding: 36rpx 0;
}
.ad-icon { width: 64rpx; height: 64rpx; opacity: 0.5; }
.ad-text { font-size: 26rpx; color: v-bind('theme.sub'); }
.ad-sorry { display: block; text-align: right; margin-top: 12rpx; font-size: 22rpx; color: v-bind('theme.sub'); }

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx;
  bottom: calc(30rpx + env(safe-area-inset-bottom));
  display: flex; align-items: center; justify-content: space-between;
  background: v-bind('theme.title'); border-radius: 48rpx; padding: 14rpx 16rpx 14rpx 32rpx;
  box-shadow: 0 6rpx 20rpx rgba(61, 51, 37, 0.4);
  z-index: 10;
}
.cart-left { display: flex; align-items: center; gap: 14rpx; }
.icon-lg { width: 40rpx; height: 40rpx; }
.cart-count { font-size: 28rpx; color: #fff; font-weight: 600; }
.cart-total { font-size: 28rpx; color: v-bind('theme.primary'); font-weight: 700; }
.go {
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 28rpx; font-weight: 600; border-radius: 40rpx; padding: 14rpx 40rpx;
}
</style>
