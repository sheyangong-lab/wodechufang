<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi } from '@/api/fridge';
import type { FridgeCategoryView } from '@/api/fridge';
import { getCurrentKitchenId, ensureKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const categories = ref<FridgeCategoryView[]>([]);
const addDialogVisible = ref(false);

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  try {
    categories.value = await fridgeApi.categories(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

/** 上移/下移类别，乐观更新 + 服务端持久化 */
async function move(index: number, dir: -1 | 1) {
  const target = index + dir;
  if (target < 0 || target >= categories.value.length) return;
  const arr = categories.value.slice();
  const tmp = arr[index];
  arr[index] = arr[target];
  arr[target] = tmp;
  categories.value = arr;
  try {
    await fridgeApi.reorderCategories(kitchenId.value!, arr.map((c) => c.id));
  } catch {
    load();
  }
}

function onAdd(name: string) {
  addDialogVisible.value = false;
  fridgeApi.createCategory(kitchenId.value!, name).then(load);
}

function remove(c: FridgeCategoryView) {
  uni.showModal({
    title: '删除类别',
    content: `确定删除「${c.name}」？`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      fridgeApi.deleteCategory(kitchenId.value!, c.id).then(load);
    },
  });
}
</script>

<template>
  <view class="page">
    <view class="card list">
      <view v-if="categories.length === 0" class="empty">
        <text class="empty-tip">还没有类别，添加一个开始整理冰箱吧</text>
      </view>
      <view v-for="(c, index) in categories" :key="c.id" class="cat-row">
        <text class="name">{{ c.name }}</text>
        <view class="order-btns">
          <text class="order-btn" :class="{ dim: index === 0 }" hover-class="press-dim" @tap="move(index, -1)">↑</text>
          <text class="order-btn" :class="{ dim: index === categories.length - 1 }" hover-class="press-dim" @tap="move(index, 1)">↓</text>
        </view>
        <text class="del" hover-class="press-dim" @tap="remove(c)">删除</text>
      </view>
    </view>
    <button class="btn-add" hover-class="press-sink" @tap="addDialogVisible = true">＋ 添加类别</button>

    <InputDialog
      :visible="addDialogVisible"
      title="添加类别"
      placeholder="如：蔬菜 / 肉类 / 调料"
      :maxlength="10"
      @confirm="onAdd"
      @close="addDialogVisible = false"
    />
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
.order-btns { display: flex; gap: 10rpx; }
.order-btn {
  width: 56rpx; height: 56rpx; border-radius: 12rpx;
  background: v-bind('theme.chipBg'); color: v-bind('theme.title');
  font-size: 28rpx; text-align: center; line-height: 56rpx;
}
.order-btn.dim { opacity: 0.3; }
.del { font-size: 26rpx; color: v-bind('theme.danger'); margin-left: 8rpx; }

.btn-add {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-add::after { border: none; }
</style>
