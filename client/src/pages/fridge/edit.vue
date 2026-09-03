<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi, UNIT_LABELS } from '@/api/fridge';
import type { FridgeCategoryView, FridgeItemView } from '@/api/fridge';
import { fullUrl } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import CutoutEditor from '@/components/cutout-editor.vue';
import { chooseOneImage, processCutout, type CutoutKind } from '@/utils/image-pick';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const itemId = ref<number | null>(null);
const categories = ref<FridgeCategoryView[]>([]);
const submitting = ref(false);

const name = ref('');
const imageUrl = ref('');
const categoryId = ref<number | null>(null);
const producedDate = ref('');
const shelfLifeValue = ref('7');
const shelfLifeUnit = ref<'DAY' | 'WEEK' | 'MONTH' | 'YEAR'>('DAY');
const quantity = ref('');
const remark = ref('');

const units: ('DAY' | 'WEEK' | 'MONTH' | 'YEAR')[] = ['DAY', 'WEEK', 'MONTH', 'YEAR'];

onLoad(async (query) => {
  kitchenId.value = await ensureKitchenId();
  if (!kitchenId.value || !query || !query.itemId) {
    uni.showToast({ title: '参数缺失', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 600);
    return;
  }
  itemId.value = Number(query.itemId);
  fridgeApi.categories(kitchenId.value).then((cs) => (categories.value = cs)).catch(() => {});
  await loadItem();
});

async function loadItem() {
  if (!kitchenId.value || !itemId.value) return;
  try {
    const all: FridgeItemView[] = await fridgeApi.list(kitchenId.value, { state: 'all' });
    const item = all.find((i) => i.id === itemId.value);
    if (!item) {
      uni.showToast({ title: '食材不存在', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 600);
      return;
    }
    name.value = item.name;
    imageUrl.value = item.imageUrl || '';
    categoryId.value = item.categoryId;
    producedDate.value = item.producedDate || '';
    shelfLifeValue.value = String(item.shelfLifeValue);
    shelfLifeUnit.value = item.shelfLifeUnit;
    quantity.value = item.quantity;
    remark.value = item.remark;
  } catch {
    // toast 已统一弹出
  }
}

const catSheetVisible = ref(false);
const catItems = computed(() => categories.value.map((c) => ({ key: String(c.id), title: c.name })));

function pickCategory() {
  if (categories.value.length === 0) {
    uni.showToast({ title: '还没有类别，去冰箱页「类别管理」添加', icon: 'none' });
    return;
  }
  catSheetVisible.value = true;
}

function onCatPick(key: string) {
  catSheetVisible.value = false;
  categoryId.value = Number(key);
}

// ----- 图片：拍照/相册 → 抠图编辑器（自动/框选/涂抹）→ 上传，与其他图片入口一致 -----
const photoSourceVisible = ref(false);
const photoSourceItems = computed(() => {
  const items: { key: string; title: string; desc: string }[] = [];
  if (imageUrl.value) items.push({ key: 'preview', title: '查看大图', desc: '原图完整查看' });
  items.push({ key: 'camera', title: '拍照', desc: '拍完自动识别主体、去除背景' });
  items.push({ key: 'album', title: '从相册选择', desc: '选完自动识别主体、去除背景' });
  return items;
});

function pickImage() {
  photoSourceVisible.value = true;
}

async function onPhotoSourcePick(key: string) {
  photoSourceVisible.value = false;
  if (key === 'preview') {
    uni.previewImage({ urls: [fullUrl(imageUrl.value)] });
    return;
  }
  const source = key as 'camera' | 'album';
  const tempPath = await chooseOneImage(source);
  if (!tempPath) return;
  cutoutSrc.value = tempPath;
  cutoutVisible.value = true;
}

const cutoutVisible = ref(false);
const cutoutSrc = ref('');

async function onCutoutConfirm(payload: { kind: CutoutKind; blob: Blob | null }) {
  cutoutVisible.value = false;
  const titles = { auto: '识别主体中…', box: '识别框内主体…', paint: '生成贴纸…' };
  uni.showLoading({ title: titles[payload.kind], mask: true });
  try {
    const { url, segmented } = await processCutout(payload.kind, cutoutSrc.value, payload.blob, {
      keepFrame: payload.kind === 'box',
    });
    imageUrl.value = url;
    uni.hideLoading();
    uni.showToast({ title: segmented ? '已抠图去背景' : '已使用裁剪图', icon: 'none' });
  } catch {
    uni.hideLoading();
    uni.showToast({ title: '处理失败，请重试', icon: 'none' });
  }
}

function onCutoutCancel() {
  cutoutVisible.value = false;
}

function removeImage() {
  imageUrl.value = '';
}

function pickProduced(e: { detail: { value: string } }) {
  producedDate.value = e.detail.value;
}

function submit() {
  if (submitting.value || !kitchenId.value || !itemId.value) return;
  if (!name.value.trim()) {
    uni.showToast({ title: '请输入食材名称', icon: 'none' });
    return;
  }
  submitting.value = true;
  fridgeApi
    .updateItem(kitchenId.value, itemId.value, {
      name: name.value.trim(),
      categoryId: categoryId.value,
      producedDate: producedDate.value || null,
      shelfLifeValue: Number(shelfLifeValue.value) || 1,
      shelfLifeUnit: shelfLifeUnit.value,
      quantity: quantity.value.trim(),
      remark: remark.value.trim(),
    })
    .then(() => {
      uni.showToast({ title: '已保存', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 600);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view class="card item-card">
      <view class="photo-row">
        <view class="photo-box" hover-class="press-dim" @tap="pickImage">
          <image v-if="imageUrl" class="photo" :src="fullUrl(imageUrl)" mode="aspectFit" />
          <view v-else class="photo-holder">
            <image class="photo-icon" src="/static/icons/pot.png" mode="aspectFit" />
            <text class="photo-tip">拍照 / 相册</text>
          </view>
          <text v-if="imageUrl" class="photo-del" @tap.stop="removeImage">✕</text>
        </view>
        <view class="photo-side">
          <view class="row no-border">
            <text class="label req">食材名称</text>
            <input v-model="name" class="input" maxlength="30" placeholder="食材名称" placeholder-class="ph" />
          </view>
          <view class="row no-border" hover-class="press-dim" @tap="pickCategory">
            <text class="label">食材类别</text>
            <text class="value">{{ categories.find((c) => c.id === categoryId)?.name || '未设置' }}</text>
            <text class="chev">›</text>
          </view>
        </view>
      </view>

      <view class="row">
        <text class="label">生产日期</text>
        <picker mode="date" :value="producedDate" @change="pickProduced">
          <text class="value picker">{{ producedDate || '未设置' }} ›</text>
        </picker>
      </view>
      <view class="row">
        <text class="label req">保质期</text>
        <input v-model="shelfLifeValue" class="input life" type="number" maxlength="4" />
        <view class="units">
          <text
            v-for="unit in units"
            :key="unit"
            class="unit"
            :class="{ on: shelfLifeUnit === unit }"
            hover-class="press-dim"
            @tap="shelfLifeUnit = unit"
          >{{ UNIT_LABELS[unit] }}</text>
        </view>
      </view>
      <view class="row">
        <text class="label">数量</text>
        <input v-model="quantity" class="input" placeholder="数量+单位，如：100g" placeholder-class="ph" />
      </view>
      <view class="row">
        <text class="label">备注</text>
        <input v-model="remark" class="input" maxlength="50" placeholder="备注（选填）" placeholder-class="ph" />
      </view>
    </view>

    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">保存</button>

    <ActionSheet
      :visible="catSheetVisible"
      :items="catItems"
      @select="onCatPick"
      @close="catSheetVisible = false"
    />
    <ActionSheet
      :visible="photoSourceVisible"
      :items="photoSourceItems"
      @select="onPhotoSourcePick"
      @close="photoSourceVisible = false"
    />
    <CutoutEditor
      :visible="cutoutVisible"
      :src="cutoutSrc"
      @confirm="onCutoutConfirm"
      @cancel="onCutoutCancel"
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
.item-card { padding: 24rpx 32rpx; }
.photo-row { display: flex; gap: 24rpx; padding-top: 8rpx; }
.photo-box {
  position: relative;
  width: 200rpx; height: 200rpx; flex-shrink: 0;
  border: 2rpx dashed v-bind('theme.divider'); border-radius: 16rpx;
}
.photo { width: 100%; height: 100%; border-radius: 14rpx; background: v-bind('theme.primaryLight'); }
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
  font-size: 24rpx; text-align: center; line-height: 40rpx; z-index: 2;
}
.photo-side { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 24rpx 0;
}
.row.no-border { border-bottom: none; padding: 18rpx 0; }
.label { font-size: 28rpx; color: v-bind('theme.title'); width: 160rpx; flex-shrink: 0; }
.label.req::before { content: '* '; color: v-bind('theme.danger'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; min-width: 0; }
.input.life { width: 100rpx; }
.value { font-size: 26rpx; color: v-bind('theme.title'); }
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
.btn-submit {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
