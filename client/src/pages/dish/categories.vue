<script setup lang="ts">
import { theme } from '@/styles/theme';
import { dishApi } from '@/api/dish';
import type { CategoryView } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const categories = ref<CategoryView[]>([]);

onShow(load);

async function load() {
  if (!kitchenId.value) return;
  try {
    categories.value = await dishApi.categories(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

function add() {
  uni.showModal({
    title: '添加分类',
    editable: true,
    placeholderText: '如：荤菜 / 素菜 / 汤羹',
    success: (res) => {
      if (!res.confirm || !res.content || !res.content.trim()) return;
      dishApi.createCategory(kitchenId.value!, res.content.trim()).then(load);
    },
  });
}

function remove(c: CategoryView) {
  uni.showModal({
    title: '删除分类',
    content: `确定删除「${c.name}」？`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      dishApi.deleteCategory(kitchenId.value!, c.id).then(load);
    },
  });
}
</script>

<template>
  <view class="page">
    <view class="card list">
      <view v-if="categories.length === 0" class="empty">
        <text class="empty-tip">还没有分类，添加一个开始建菜单吧</text>
      </view>
      <view v-for="c in categories" :key="c.id" class="cat-row">
        <text class="name">{{ c.name }}</text>
        <text class="count">{{ c.dishCount }}道菜</text>
        <text class="del" hover-class="press-dim" @tap="remove(c)">删除</text>
      </view>
    </view>
    <button class="btn-add" hover-class="press-sink" @tap="add">＋ 添加分类</button>
    <text class="hint">免费版最多 5 个分类，升级厨房扩容到 50 个</text>
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
.empty { padding: 48rpx 0; text-align: center; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }
.cat-row {
  display: flex; align-items: center;
  padding: 28rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.name { flex: 1; font-size: 30rpx; color: v-bind('theme.title'); font-weight: 500; }
.count { font-size: 24rpx; color: v-bind('theme.sub'); margin-right: 24rpx; }
.del { font-size: 26rpx; color: v-bind('theme.danger'); }

.btn-add {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-add::after { border: none; }
.hint { display: block; text-align: center; margin-top: 24rpx; font-size: 22rpx; color: v-bind('theme.sub'); }
</style>
