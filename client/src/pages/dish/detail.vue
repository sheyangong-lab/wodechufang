<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi, fenToYuan, fullUrl, parseSpecs } from '@/api/dish';
import type { DishView, DishSpec } from '@/api/dish';
import { useCartStore } from '@/stores/cart';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';

const cart = useCartStore();
const dish = ref<DishView | null>(null);
const isOwnerView = ref(false);
const specs = ref<DishSpec[]>([]);
const selectedSpec = ref<DishSpec | null>(null);

const currentPriceFen = computed(() =>
  selectedSpec.value ? selectedSpec.value.priceFen : dish.value?.priceFen ?? 0
);

onLoad((query) => {
  const id = query && query.id ? Number(query.id) : null;
  if (!id) return;
  isOwnerView.value = query && query.from === 'manage';
  dishApi.detail(id).then((d) => {
    dish.value = d;
    specs.value = parseSpecs(d.specsJson);
    selectedSpec.value = specs.value[0] || null;
  });
});

function pickSpec(s: DishSpec) {
  selectedSpec.value = s;
}

function addToCart() {
  if (!dish.value) return;
  cart.add(dish.value, selectedSpec.value || undefined);
  uni.showToast({ title: '已加入购物车', icon: 'none' });
}

function edit() {
  if (dish.value) {
    uni.redirectTo({ url: `/pages/dish/edit?id=${dish.value.id}` });
  }
}

function toggleShelf() {
  if (!dish.value) return;
  const next = dish.value.status === 1 ? 0 : 1;
  dishApi.updateStatus(dish.value.id, next as 0 | 1).then((d) => {
    dish.value = d;
    uni.showToast({ title: next === 1 ? '已上架' : '已下架', icon: 'none' });
  });
}

function remove() {
  if (!dish.value) return;
  uni.showModal({
    title: '移入回收站',
    content: `「${dish.value.name}」将进入回收站，可在回收站恢复`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      dishApi.recycle(dish.value!.id).then(() => {
        uni.showToast({ title: '已移入回收站', icon: 'none' });
        setTimeout(() => uni.navigateBack(), 700);
      });
    },
  });
}

function fmtTime(iso: string) {
  return iso ? iso.slice(0, 10) : '';
}
</script>

<template>
  <view class="page">
    <template v-if="dish">
      <image v-if="dish.imageUrl" class="hero" :src="fullUrl(dish.imageUrl)" mode="aspectFill" />
      <view class="card head">
        <view class="title-row">
          <text class="name">{{ dish.name }}</text>
          <text v-if="dish.status === 0" class="off-tag">已下架</text>
        </view>
        <text class="price">¥{{ fenToYuan(currentPriceFen) }}</text>
        <view v-if="specs.length > 0" class="spec-chips">
          <text
            v-for="s in specs"
            :key="s.name"
            class="spec-chip"
            :class="{ on: selectedSpec?.name === s.name }"
            hover-class="press-dim"
            @tap="pickSpec(s)"
          >{{ s.name }}</text>
        </view>
        <view v-if="dish.recommendStars > 0" class="stars">
          <text class="star on" v-for="i in dish.recommendStars" :key="i">★</text>
        </view>
        <text v-if="dish.description" class="desc">{{ dish.description }}</text>
        <view class="meta">
          <text v-if="dish.categoryName" class="meta-item">{{ dish.categoryName }}</text>
          <text v-if="dish.servings" class="meta-item">{{ dish.servings }}</text>
          <text v-if="dish.cookMinutes" class="meta-item">{{ dish.cookMinutes }}分钟</text>
          <text v-if="dish.difficulty" class="meta-item">{{ dish.difficulty }}</text>
          <text v-if="dish.calories" class="meta-item">{{ dish.calories }}</text>
        </view>
        <text class="time">更新于 {{ fmtTime(dish.updatedAt) }}</text>
      </view>

      <view v-if="dish.materials" class="card section">
        <text class="sec-title">用料</text>
        <text v-for="(line, i) in dish.materials.split('\n').filter((l) => l.trim())" :key="i" class="material-line">{{ line }}</text>
      </view>

      <view v-if="dish.steps" class="card section">
        <text class="sec-title">制作过程</text>
        <!-- 富文本 HTML 直接渲染；旧纯文本按行编号 -->
        <rich-text v-if="dish.steps.includes('<')" class="rich" :nodes="dish.steps" />
        <block v-else>
          <view v-for="(line, i) in dish.steps.split('\n').filter((l) => l.trim())" :key="i" class="step-line">
            <text class="step-no">{{ i + 1 }}</text>
            <text class="step-text">{{ line }}</text>
          </view>
        </block>
      </view>

      <!-- 点单：加入购物车 -->
      <view class="cart-bar" v-if="dish.status === 1">
        <text class="cart-price">¥{{ fenToYuan(currentPriceFen) }}</text>
        <button class="cart-btn" hover-class="press-sink" @tap="addToCart">加入购物车</button>
      </view>

      <!-- 店长操作 -->
      <view v-if="isOwnerView" class="ops">
        <button class="op-btn primary" hover-class="press-sink" @tap="edit">编辑菜谱</button>
        <button class="op-btn" hover-class="press-dim" @tap="toggleShelf">
          {{ dish.status === 1 ? '下架' : '上架' }}
        </button>
        <button class="op-btn danger" hover-class="press-dim" @tap="remove">移入回收站</button>
      </view>
    </template>
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
.hero {
  width: 100%; height: 420rpx;
  border-radius: 24rpx;
  margin-bottom: 20rpx;
  background: v-bind('theme.primaryLight');
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  margin-bottom: 20rpx;
}
.head { padding: 28rpx 32rpx; }
.title-row { display: flex; align-items: center; gap: 16rpx; }
.name { font-size: 40rpx; font-weight: 700; color: v-bind('theme.title'); }
.off-tag {
  font-size: 20rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 8rpx; padding: 2rpx 10rpx;
}
.price { display: block; font-size: 36rpx; font-weight: 700; color: v-bind('theme.income'); margin-top: 12rpx; }
.spec-chips { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 16rpx; }
.spec-chip {
  font-size: 26rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 28rpx;
  padding: 8rpx 28rpx;
}
.spec-chip.on {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); font-weight: 600;
}

.cart-bar {
  display: flex; align-items: center; justify-content: space-between;
  background: v-bind('theme.card'); border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  padding: 20rpx 28rpx; margin-bottom: 20rpx;
}
.cart-price { font-size: 34rpx; font-weight: 700; color: v-bind('theme.income'); }
.cart-btn {
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 40rpx; font-size: 30rpx; line-height: 76rpx;
  padding: 0 56rpx; margin: 0;
}
.cart-btn::after { border: none; }
.stars { display: flex; margin-top: 8rpx; }
.star { color: #e5dfd2; font-size: 30rpx; margin-right: 4rpx; }
.star.on { color: v-bind('theme.primaryBtn'); }
.desc { display: block; font-size: 26rpx; color: v-bind('theme.sub'); margin-top: 16rpx; line-height: 42rpx; }
.meta { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 20rpx; }
.meta-item {
  font-size: 22rpx; color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); border-radius: 8rpx; padding: 4rpx 14rpx;
}
.time { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 20rpx; }

.section { padding: 28rpx 32rpx; }
.sec-title { display: block; font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 20rpx; }
.material-line {
  display: block; font-size: 28rpx; color: v-bind('theme.title');
  line-height: 52rpx; border-bottom: 2rpx solid v-bind('theme.divider');
}
.step-line { display: flex; gap: 16rpx; margin-bottom: 24rpx; }
.rich { font-size: 28rpx; color: v-bind('theme.title'); line-height: 48rpx; }
.rich img { max-width: 100%; border-radius: 12rpx; }
.step-no {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 24rpx; text-align: center; line-height: 40rpx;
  flex-shrink: 0;
}
.step-text { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); line-height: 44rpx; }

.ops { display: flex; gap: 20rpx; margin-top: 8rpx; }
.op-btn {
  flex: 1;
  background: #fff; color: v-bind('theme.title');
  border: 2rpx solid v-bind('theme.divider');
  border-radius: 16rpx; font-size: 28rpx; line-height: 84rpx;
  padding: 0;
}
.op-btn.primary { background: v-bind('theme.primaryBtn'); color: #fff; border-color: v-bind('theme.primaryBtn'); }
.op-btn.danger { color: v-bind('theme.danger'); }
.op-btn::after { border: none; }
</style>
