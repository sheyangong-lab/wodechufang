<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, groupCategories } from '@/api/ledger';
import type { LedgerCategoryView } from '@/api/ledger';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const groups = ref<{ expense: LedgerCategoryView[]; income: LedgerCategoryView[] }>({
  expense: [],
  income: [],
});
const addType = ref<'INCOME' | 'EXPENSE'>('EXPENSE');
const addDialogVisible = ref(false);

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  try {
    groups.value = groupCategories(await ledgerApi.categories(kitchenId.value));
  } catch {
    // toast 已统一弹出
  }
}

function openAdd(type: 'INCOME' | 'EXPENSE') {
  addType.value = type;
  addDialogVisible.value = true;
}

function onAdd(name: string) {
  addDialogVisible.value = false;
  ledgerApi.createCategory(kitchenId.value!, addType.value, name).then(load);
}

function remove(c: LedgerCategoryView) {
  uni.showModal({
    title: '删除分类',
    content: `确定删除「${c.name}」？已有流水按名称保留，不受影响。`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      ledgerApi.deleteCategory(kitchenId.value!, c.id).then(load);
    },
  });
}
</script>

<template>
  <view class="page">
    <view class="card list">
      <view class="group-head">
        <text class="group-title">支出分类</text>
        <text class="add-link" hover-class="press-dim" @tap="openAdd('EXPENSE')">＋ 添加</text>
      </view>
      <view v-if="groups.expense.length === 0" class="empty"><text class="empty-tip">暂无支出分类</text></view>
      <view v-for="c in groups.expense" :key="c.id" class="cat-row">
        <text class="name">{{ c.name }}</text>
        <text class="del" hover-class="press-dim" @tap="remove(c)">删除</text>
      </view>

      <view class="group-head second">
        <text class="group-title">收入分类</text>
        <text class="add-link" hover-class="press-dim" @tap="openAdd('INCOME')">＋ 添加</text>
      </view>
      <view v-if="groups.income.length === 0" class="empty"><text class="empty-tip">暂无收入分类</text></view>
      <view v-for="c in groups.income" :key="c.id" class="cat-row">
        <text class="name">{{ c.name }}</text>
        <text class="del" hover-class="press-dim" @tap="remove(c)">删除</text>
      </view>
    </view>
    <text class="tip">账本是全功能账本：分类随便改，历史流水按名称保留。</text>

    <InputDialog
      :visible="addDialogVisible"
      :title="addType === 'EXPENSE' ? '添加支出分类' : '添加收入分类'"
      :placeholder="addType === 'EXPENSE' ? '如：交通 /日用 /宠物' : '如：工资 /副业'"
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
.list { padding: 8rpx 32rpx 24rpx; }
.group-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 0 8rpx;
}
.group-head.second { margin-top: 24rpx; border-top: 2rpx solid v-bind('theme.divider'); }
.group-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.add-link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }
.empty { padding: 20rpx 0; text-align: center; }
.empty-tip { font-size: 24rpx; color: v-bind('theme.sub'); }
.cat-row {
  display: flex; align-items: center;
  padding: 26rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.cat-row:last-child { border-bottom: none; }
.name { flex: 1; font-size: 30rpx; color: v-bind('theme.title'); font-weight: 500; }
.del { font-size: 26rpx; color: v-bind('theme.danger'); }
.tip {
  display: block; text-align: center; margin-top: 24rpx;
  font-size: 24rpx; color: v-bind('theme.sub');
}
</style>
