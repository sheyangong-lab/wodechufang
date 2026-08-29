<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi, UNIT_LABELS } from '@/api/fridge';
import type { FridgeCategoryView } from '@/api/fridge';
import { uploadImage, fullUrl } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';

interface Draft {
  name: string;
  imageUrl: string;
  categoryId: number | null;
  producedDate: string;
  shelfLifeValue: string;
  shelfLifeUnit: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
  quantity: string;
  remark: string;
}

const kitchenId = ref<number | null>(getCurrentKitchenId());
const categories = ref<FridgeCategoryView[]>([]);
const drafts = ref<Draft[]>([newDraft()]);
const submitting = ref(false);

function newDraft(): Draft {
  return {
    name: '',
    imageUrl: '',
    categoryId: null,
    producedDate: '',
    shelfLifeValue: '7',
    shelfLifeUnit: 'DAY',
    quantity: '',
    remark: '',
  };
}

onLoad(async () => {
  kitchenId.value = await ensureKitchenId();
  loadCategories();
});

function loadCategories() {
  if (!kitchenId.value) return;
  fridgeApi.categories(kitchenId.value).then((cs) => (categories.value = cs)).catch(() => {});
}
loadCategories();

function addItem() {
  if (drafts.value.length >= 10) {
    uni.showToast({ title: '一次最多 10 种食材', icon: 'none' });
    return;
  }
  drafts.value.push(newDraft());
}

function removeItem(i: number) {
  drafts.value.splice(i, 1);
}

/** 拍照或从相册选图上传（uni.chooseImage 默认双来源），回填相对 URL */
function pickImage(i: number) {
  uploadImage()
    .then((url) => (drafts.value[i].imageUrl = url))
    .catch(() => {});
}

function removeImage(i: number) {
  drafts.value[i].imageUrl = '';
}

// 分类选择：自研底部弹层（UI 与菜谱编辑页统一）
const catSheetVisible = ref(false);
const catSheetIndex = ref(0);
const catItems = computed(() =>
  categories.value.map((c) => ({ key: String(c.id), title: c.name }))
);

function pickCategory(i: number) {
  if (categories.value.length === 0) {
    uni.showToast({ title: '还没有类别，去冰箱页「类别管理」添加', icon: 'none' });
    return;
  }
  catSheetIndex.value = i;
  catSheetVisible.value = true;
}

function onCatPick(key: string) {
  catSheetVisible.value = false;
  drafts.value[catSheetIndex.value].categoryId = Number(key);
}

function pickProduced(i: number, e: { detail: { value: string } }) {
  drafts.value[i].producedDate = e.detail.value;
}

const units: ('DAY' | 'WEEK' | 'MONTH' | 'YEAR')[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

function submit() {
  if (submitting.value || !kitchenId.value) return;
  const valid = drafts.value.filter((d) => d.name.trim());
  if (valid.length === 0) {
    uni.showToast({ title: '请至少填写一种食材', icon: 'none' });
    return;
  }
  submitting.value = true;
  fridgeApi
    .createItems(
      kitchenId.value,
      valid.map((d) => ({
        name: d.name.trim(),
        categoryId: d.categoryId,
        imageUrl: d.imageUrl || null,
        producedDate: d.producedDate || null,
        shelfLifeValue: Number(d.shelfLifeValue) || 1,
        shelfLifeUnit: d.shelfLifeUnit,
        quantity: d.quantity || undefined,
        remark: d.remark || undefined,
      }))
    )
    .then(() => {
      uni.showToast({ title: `已放入 ${valid.length} 种食材`, icon: 'none' });
      setTimeout(() => uni.navigateBack(), 700);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view v-for="(d, i) in drafts" :key="i" class="card item-card">
      <view class="card-head">
        <text class="card-no">食材 {{ i + 1 }}</text>
        <text v-if="drafts.length > 1" class="card-del" hover-class="press-dim" @tap="removeItem(i)">✕</text>
      </view>

      <!-- 照片：拍照/相册 -->
      <view class="photo-row">
        <view class="photo-box" hover-class="press-dim" @tap="pickImage(i)">
          <image v-if="d.imageUrl" class="photo" :src="fullUrl(d.imageUrl)" mode="aspectFill" />
          <view v-else class="photo-holder">
            <image class="photo-icon" src="/static/icons/pot.png" mode="aspectFit" />
            <text class="photo-tip">拍照 / 相册</text>
          </view>
          <text v-if="d.imageUrl" class="photo-del" @tap.stop="removeImage(i)">✕</text>
        </view>
        <view class="photo-side">
          <view class="row no-border">
            <text class="label req">食材名称</text>
            <input v-model="d.name" class="input" maxlength="20" placeholder="食材名称" placeholder-class="ph" />
          </view>
          <view class="row no-border" hover-class="press-dim" @tap="pickCategory(i)">
            <text class="label req">食材类别</text>
            <text class="value">{{ categories.find((c) => c.id === d.categoryId)?.name || '请选择类别' }}</text>
            <text class="chev">›</text>
          </view>
        </view>
      </view>

      <view class="row">
        <text class="label">生产日期</text>
        <picker mode="date" :value="d.producedDate" @change="pickProduced(i, $event)">
          <text class="value picker">{{ d.producedDate || '请选择生产日期' }} ›</text>
        </picker>
      </view>
      <view class="row">
        <text class="label req">保质期</text>
        <input v-model="d.shelfLifeValue" class="input life" type="number" maxlength="4" />
        <view class="units">
          <text
            v-for="unit in units"
            :key="unit"
            class="unit"
            :class="{ on: d.shelfLifeUnit === unit }"
            hover-class="press-dim"
            @tap="d.shelfLifeUnit = unit"
          >{{ UNIT_LABELS[unit] }}</text>
        </view>
      </view>
      <view class="row">
        <text class="label">数量</text>
        <input v-model="d.quantity" class="input" placeholder="数量+单位，如：100g" placeholder-class="ph" />
      </view>
      <view class="row col">
        <text class="label">备注</text>
        <input v-model="d.remark" class="input" maxlength="50" placeholder="备注（选填）" placeholder-class="ph" />
      </view>
    </view>

    <text class="count">共{{ drafts.length }}个食材</text>
    <button class="btn-add" hover-class="press-dim" @tap="addItem">＋ 添加食材</button>
    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">提交</button>

    <ActionSheet
      :visible="catSheetVisible"
      :items="catItems"
      @select="onCatPick"
      @close="catSheetVisible = false"
    />
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
  margin-bottom: 20rpx;
}
.item-card { padding: 24rpx 32rpx; }
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 8rpx;
}
.card-no { font-size: 26rpx; font-weight: 600; color: v-bind('theme.primaryBtn'); }
.card-del { font-size: 28rpx; color: v-bind('theme.danger'); padding: 4rpx 8rpx; }

.photo-row { display: flex; gap: 24rpx; padding-top: 16rpx; }
.photo-box {
  position: relative;
  width: 200rpx; height: 200rpx; flex-shrink: 0;
  border: 2rpx dashed v-bind('theme.divider'); border-radius: 16rpx;
  overflow: visible;
}
.photo { width: 100%; height: 100%; border-radius: 14rpx; }
.photo-holder {
  width: 100%; height: 100%;
  background: v-bind('theme.primaryLight');
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10rpx;
  border-radius: 14rpx; box-sizing: border-box;
}
.photo-icon { width: 56rpx; height: 56rpx; opacity: 0.5; }
.photo-tip { font-size: 22rpx; color: v-bind('theme.sub'); }
.photo-del {
  position: absolute; top: -14rpx; right: -14rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: v-bind('theme.danger'); color: #fff;
  font-size: 24rpx; text-align: center; line-height: 40rpx;
  z-index: 2;
}
.photo-side { flex: 1; min-width: 0; display: flex; flex-direction: column; }

.row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 24rpx 0;
}
.row.no-border { border-bottom: none; padding: 18rpx 0; }
.row.col { flex-direction: column; align-items: stretch; border-bottom: none; }
.label { font-size: 28rpx; color: v-bind('theme.title'); width: 160rpx; flex-shrink: 0; }
.label.req::before { content: '* '; color: v-bind('theme.danger'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; min-width: 0; }
.input.life { width: 100rpx; }
.value { font-size: 26rpx; color: v-bind('theme.sub'); }
.value.picker { color: v-bind('theme.title'); }
.chev { color: v-bind('theme.sub'); margin-left: 8rpx; }
.ph { color: v-bind('theme.sub'); }
.units { display: flex; gap: 10rpx; }
.unit {
  font-size: 24rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 10rpx;
  padding: 6rpx 18rpx;
}
.unit.on {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); font-weight: 600;
}

.count { display: block; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); margin: 8rpx 0 20rpx; }
.btn-add {
  background: v-bind('theme.card'); color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn');
  border-radius: 16rpx; font-size: 30rpx; line-height: 84rpx;
  margin-bottom: 20rpx;
}
.btn-add::after { border: none; }
.btn-submit {
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
