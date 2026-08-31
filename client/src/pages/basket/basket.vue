<script setup lang="ts">
import { theme } from '@/styles/theme';
import { basketApi } from '@/api/basket';
import type { BasketItemView } from '@/api/basket';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const items = ref<BasketItemView[]>([]);
const genDate = ref(todayStr());
const calVisible = ref(false);
const addDialogVisible = ref(false);
const loading = ref(true);

const unchecked = computed(() => items.value.filter((i) => i.checked === 0));
const checked = computed(() => items.value.filter((i) => i.checked === 1));

onLoad(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    items.value = await basketApi.list(kitchenId.value);
  } catch {
    // toast 已统一弹出
  } finally {
    loading.value = false;
  }
}

/** 从下单记录生成：提取当天下单菜品的用料，与冰箱比对，缺的加入 */
async function generate() {
  if (!kitchenId.value) return;
  uni.showLoading({ title: '比对中…', mask: true });
  try {
    const r = await basketApi.generate(kitchenId.value, genDate.value);
    await load();
    uni.hideLoading();
    uni.showToast({
      title: r.added > 0 ? `已加入 ${r.added} 项（冰箱已有 ${r.matched} 项）` : '下单用料冰箱里都有，无需采购',
      icon: 'none',
    });
  } catch (e) {
    uni.hideLoading();
    uni.showToast({ title: (e as Error).message?.slice(0, 30) || '生成失败', icon: 'none' });
  }
}

function onCalPick(date: string | null) {
  calVisible.value = false;
  if (date) {
    genDate.value = date;
    uni.showToast({ title: `将按 ${date} 的下单记录生成`, icon: 'none' });
  }
}

function onAdd(name: string) {
  addDialogVisible.value = false;
  basketApi.add(kitchenId.value!, name).then(load);
}

async function toggle(i: BasketItemView) {
  await basketApi.toggle(kitchenId.value!, i.id);
  i.checked = i.checked === 1 ? 0 : 1;
}

function remove(i: BasketItemView) {
  basketApi.remove(kitchenId.value!, i.id).then(() => load());
}

function clearChecked() {
  if (checked.value.length === 0) {
    uni.showToast({ title: '没有已买到的条目', icon: 'none' });
    return;
  }
  basketApi.clearChecked(kitchenId.value!).then((n) => {
    uni.showToast({ title: `已清掉 ${n} 项`, icon: 'none' });
    load();
  });
}
</script>

<template>
  <view class="page">
    <!-- 生成区 -->
    <view class="card sec">
      <view class="gen-row">
        <view class="gen-date" hover-class="press-dim" @tap="calVisible = true">
          <text class="gen-label">按下单日期生成</text>
          <text class="gen-value">{{ genDate }} ▾</text>
        </view>
        <text class="gen-btn" hover-class="press-sink" @tap="generate">生成菜篮</text>
      </view>
      <text class="gen-tip">提取当天下单菜品的用料，与冰箱比对后，缺的自动加入下方菜篮</text>
    </view>

    <!-- 待采购 -->
    <view class="card sec">
      <view class="sec-head">
        <text class="sec-title">待采购（{{ unchecked.length }}）</text>
        <text class="add-link" hover-class="press-dim" @tap="addDialogVisible = true">＋ 手动添加</text>
      </view>
      <view v-if="loading" class="empty-line"><text class="line dim">加载中…</text></view>
      <view v-else-if="unchecked.length === 0" class="empty-line">
        <text class="line dim">菜篮空空的，点上方「生成菜篮」或手动添加</text>
      </view>
      <view v-for="i in unchecked" :key="i.id" class="row" hover-class="press-dim" @tap="toggle(i)">
        <view class="checkbox" />
        <view class="row-main">
          <text class="row-name">{{ i.name }}</text>
          <text v-if="i.quantity" class="row-sub">{{ i.quantity }}</text>
        </view>
        <text v-if="i.source === 'AUTO'" class="src-tag">下单</text>
        <text class="row-del" hover-class="press-dim" @tap.stop="remove(i)">✕</text>
      </view>
    </view>

    <!-- 已买到 -->
    <view class="card sec">
      <view class="sec-head">
        <text class="sec-title">已买到（{{ checked.length }}）</text>
        <text class="clear-link" hover-class="press-dim" @tap="clearChecked">清空已买到</text>
      </view>
      <view v-if="checked.length === 0" class="empty-line"><text class="line dim">买齐的食材会出现在这里</text></view>
      <view v-for="i in checked" :key="i.id" class="row" hover-class="press-dim" @tap="toggle(i)">
        <view class="checkbox done">✓</view>
        <view class="row-main">
          <text class="row-name done-text">{{ i.name }}</text>
          <text v-if="i.quantity" class="row-sub">{{ i.quantity }}</text>
        </view>
        <text class="row-del" hover-class="press-dim" @tap.stop="remove(i)">✕</text>
      </view>
    </view>

    <CalendarPicker
      :visible="calVisible"
      :selected="genDate"
      @select="(d: string | null) => { calVisible = false; if (d) genDate = d; }"
      @close="calVisible = false"
    />
    <InputDialog
      :visible="addDialogVisible"
      title="手动添加到菜篮"
      placeholder="如：生抽 / 猪里脊"
      :maxlength="30"
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
  margin-bottom: 16rpx;
}
.sec { padding: 24rpx 32rpx; }
.sec-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8rpx;
}
.sec-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.add-link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }
.clear-link { font-size: 24rpx; color: v-bind('theme.sub'); }

.gen-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.gen-date { display: flex; flex-direction: column; gap: 4rpx; }
.gen-label { font-size: 24rpx; color: v-bind('theme.sub'); }
.gen-value { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.gen-btn {
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 28rpx; font-weight: 600;
  border-radius: 36rpx; padding: 16rpx 32rpx;
}
.gen-tip { display: block; margin-top: 14rpx; font-size: 22rpx; color: v-bind('theme.sub'); line-height: 34rpx; }

.empty-line { padding: 12rpx 0; }
.line { font-size: 26rpx; color: v-bind('theme.title'); }
.line.dim { color: v-bind('theme.sub'); font-size: 24rpx; }

.row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 22rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.checkbox {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  border: 3rpx solid v-bind('theme.divider');
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 24rpx; color: #fff;
}
.checkbox.done { background: v-bind('theme.success'); border-color: v-bind('theme.success'); }
.row-main { flex: 1; min-width: 0; }
.row-name { display: block; font-size: 28rpx; color: v-bind('theme.title'); font-weight: 500; }
.row-name.done-text { color: v-bind('theme.sub'); text-decoration: line-through; }
.row-sub { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 2rpx; }
.src-tag {
  font-size: 20rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 6rpx;
  padding: 0 8rpx; flex-shrink: 0;
}
.row-del { font-size: 28rpx; color: v-bind('theme.sub'); padding: 4rpx 8rpx; flex-shrink: 0; }
</style>
