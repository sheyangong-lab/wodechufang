<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, groupCategories } from '@/api/ledger';
import type { LedgerCategoryView } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { getApiBase } from '@/api/config';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { monthStr, todayStr } from '@/utils/fmt';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import InputDialog from '@/components/input-dialog.vue';
import CalendarPicker from '@/components/calendar-picker.vue';
import ImageEditOverlay from '@/components/image-edit-overlay.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const type = ref<'EXPENSE' | 'INCOME'>('EXPENSE');
const category = ref('');
const date = ref(todayStr());
const remark = ref('');
/** 分费用明细：总费用由这些分项自动求和 */
const subRows = ref<{ name: string; amount: string }[]>([{ name: '', amount: '' }]);
const groups = ref<{ expense: LedgerCategoryView[]; income: LedgerCategoryView[] }>({
  expense: [],
  income: [],
});
const submitting = ref(false);

onLoad(async (query) => {
  kitchenId.value = await ensureKitchenId();
  if (query && query.month) {
    // 从指定月进入：当月则默认今天；历史月默认该月1号（此前用"该月+今天日号"
    // 会拼出 2026-06-28 这类"看起来像今天其实不是"的日期，还可能拼出 02-31 非法日期）
    date.value = query.month === monthStr() ? todayStr() : `${query.month}-01`;
  }
  await loadCategories();
  if (!category.value) category.value = defaultCategory();
});

async function loadCategories() {
  if (!kitchenId.value) return;
  try {
    groups.value = groupCategories(await ledgerApi.categories(kitchenId.value));
  } catch {
    // toast 已统一弹出
  }
}

const currentCategories = computed(() =>
  type.value === 'EXPENSE' ? groups.value.expense : groups.value.income
);

function defaultCategory(): string {
  const list = currentCategories.value;
  if (list.length === 0) return '';
  const preferred = list.find((c) => (type.value === 'EXPENSE' ? c.name === '食材采购' : c.name === '菜品销售'));
  return (preferred || list[0]).name;
}

function setType(t: 'EXPENSE' | 'INCOME') {
  type.value = t;
  if (!currentCategories.value.some((c) => c.name === category.value)) {
    category.value = defaultCategory();
  }
}

// 分类选择：自研底部弹层（替代原生 ActionSheet，UI 统一）
const catSheetVisible = ref(false);
const catItems = computed(() =>
  currentCategories.value.map((c) => ({
    key: String(c.id),
    title: c.name,
    desc: category.value === c.name ? '当前分类' : '',
  }))
);

function pickCategory() {
  if (currentCategories.value.length === 0) {
    uni.showToast({ title: '还没有分类，先添加一个', icon: 'none' });
    catDialogVisible.value = true;
    return;
  }
  catSheetVisible.value = true;
}

function onCatPick(key: string) {
  catSheetVisible.value = false;
  const c = currentCategories.value.find((x) => String(x.id) === key);
  if (c) category.value = c.name;
}

const catDialogVisible = ref(false);

function onCatCreate(name: string) {
  catDialogVisible.value = false;
  ledgerApi.createCategory(kitchenId.value!, type.value, name).then((c) => {
    loadCategories();
    category.value = c.name;
  });
}

function goManage() {
  uni.navigateTo({ url: '/pages/ledger/categories' });
}

const dateCalVisible = ref(false);

function onDatePick(v: string | null) {
  dateCalVisible.value = false;
  if (v) date.value = v;
}

// ----- 分费用：总费用 = 各分项之和（自动计算，只读） -----

function addRow() {
  subRows.value.push({ name: '', amount: '' });
}

function removeRow(idx: number) {
  subRows.value.splice(idx, 1);
}

// ----- AI 识别：拍照/选图 → 裁剪或涂抹选区 → 服务端 GLM-4.6V 识别 → 回填待确认 -----

const aiRecognizing = ref(false);
const editorVisible = ref(false);
const editorSrc = ref('');

function aiRecognize() {
  if (aiRecognizing.value) return;
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const path = res.tempFilePaths[0];
      if (path) {
        editorSrc.value = path;
        editorVisible.value = true;
      }
    },
  });
}

function onEditorCancel() {
  editorVisible.value = false;
}

function onEditorSkip() {
  editorVisible.value = false;
  recognizeImage(editorSrc.value);
}

function onEditorConfirm(blob: Blob) {
  editorVisible.value = false;
  recognizeBlob(blob, 'jpg');
}

/** 上传前压缩：长边≤1280、JPEG 0.8，显著加快上传与模型处理 */
async function compressBlob(raw: Blob): Promise<Blob> {
  // #ifdef H5
  try {
    const url = URL.createObjectURL(raw);
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const im = new Image();
        im.onload = () => resolve(im);
        im.onerror = () => reject(new Error('decode'));
        im.src = url;
      });
      const scale = Math.min(1, 1280 / Math.max(img.naturalWidth, img.naturalHeight));
      if (scale >= 1 && raw.size < 600 * 1024) return raw; // 小图不重编
      const c = document.createElement('canvas');
      c.width = Math.round(img.naturalWidth * scale);
      c.height = Math.round(img.naturalHeight * scale);
      c.getContext('2d')!.drawImage(img, 0, 0, c.width, c.height);
      const out = await new Promise<Blob>((resolve, reject) =>
        c.toBlob((b) => (b ? resolve(b!) : reject(new Error('encode'))), 'image/jpeg', 0.8)
      );
      return out.size < raw.size ? out : raw;
    } finally {
      URL.revokeObjectURL(url);
    }
  } catch {
    return raw;
  }
  // #endif
  return raw;
}

async function recognizeImage(tempPath: string) {
  try {
    const raw = await (await fetch(tempPath)).blob();
    await recognizeBlob(raw, raw.type.includes('png') ? 'png' : 'jpg');
  } catch (e: any) {
    uni.showToast({ title: (e?.message || '图片读取失败').slice(0, 30), icon: 'none' });
  }
}

async function recognizeBlob(raw: Blob, ext: string) {
  if (!kitchenId.value) return;
  aiRecognizing.value = true;
  uni.showLoading({ title: 'AI 识别中…', mask: true });
  const t0 = Date.now();
  try {
    const blob = await compressBlob(raw);
    const fd = new FormData();
    fd.append('file', blob, `receipt.${ext}`);
    const token = uni.getStorageSync('token');
    const resp = await fetch(`${getApiBase()}/api/kitchens/${kitchenId.value}/ledger/recognize`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: fd,
    });
    const body = await resp.json();
    if (body.code !== 0) throw new Error(body.message || '识别失败');
    const items: { name: string; amountFen: number }[] = body.data || [];
    if (items.length === 0) {
      uni.showToast({ title: '没识别出消费项目，试试更清晰的照片', icon: 'none' });
      return;
    }
    // 已有内容时追加识别结果；只有空行时直接替换
    const kept = subRows.value.filter((r) => r.name.trim() || r.amount.trim());
    const filled = items.slice(0, 20 - kept.length).map((it) => ({
      name: it.name,
      amount: (it.amountFen / 100).toFixed(2),
    }));
    subRows.value = [...kept, ...filled];
    uni.showToast({ title: `${filled.length} 项·${((Date.now() - t0) / 1000).toFixed(1)}秒，核对后保存`, icon: 'none' });
  } catch (e: any) {
    uni.showToast({ title: (e?.message || '识别失败，请重试').slice(0, 30), icon: 'none' });
  } finally {
    uni.hideLoading();
    aiRecognizing.value = false;
  }
}

const totalFen = computed(() => {
  let sum = 0;
  subRows.value.forEach((r) => {
    const fen = yuanToFen(r.amount);
    if (fen != null && fen > 0) sum += fen;
  });
  return sum;
});

const totalYuan = computed(() => (totalFen.value / 100).toFixed(2));

function submit() {
  if (submitting.value || !kitchenId.value) return;
  const subItems: { name: string; amountFen: number }[] = [];
  for (const r of subRows.value) {
    const name = r.name.trim();
    if (!name) {
      uni.showToast({ title: '请填写分费用名目', icon: 'none' });
      return;
    }
    const fen = yuanToFen(r.amount);
    if (fen == null || fen <= 0) {
      uni.showToast({ title: `「${name}」请输入正确的金额`, icon: 'none' });
      return;
    }
    subItems.push({ name, amountFen: fen });
  }
  if (subItems.length === 0) {
    uni.showToast({ title: '请至少添加一项分费用', icon: 'none' });
    return;
  }
  const fen = totalFen.value;
  if (fen <= 0) {
    uni.showToast({ title: '请输入正确的金额', icon: 'none' });
    return;
  }
  submitting.value = true;
  ledgerApi
    .add(kitchenId.value, {
      type: type.value,
      category: category.value,
      amountFen: fen,
      date: date.value,
      remark: remark.value || undefined,
      subItems,
    })
    .then(() => {
      uni.showToast({ title: '已记一笔', icon: 'none' });
      setTimeout(() => uni.navigateBack(), 700);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view class="card section">
      <view class="type-row">
        <text class="type-btn" :class="{ on: type === 'EXPENSE', out: type === 'EXPENSE' }" hover-class="press-dim" @tap="setType('EXPENSE')">支出</text>
        <text class="type-btn" :class="{ on: type === 'INCOME', inc: type === 'INCOME' }" hover-class="press-dim" @tap="setType('INCOME')">收入</text>
      </view>

      <view class="row" hover-class="press-dim" @tap="pickCategory">
        <text class="label req">分类</text>
        <text class="value">{{ category || '请选择分类' }}</text>
        <text class="chev">›</text>
      </view>
      <view class="row">
        <text class="label">没有合适的？</text>
        <text class="link" hover-class="press-dim" @tap="catDialogVisible = true">＋ 添加分类</text>
        <text class="link manage" hover-class="press-dim" @tap="goManage">管理分类</text>
      </view>

      <!-- 分费用明细：总费用自动求和 -->
      <view class="sub-head">
        <text class="label req">分费用</text>
        <text class="sub-hint">各项之和即为总费用</text>
      </view>
      <view v-for="(r, idx) in subRows" :key="idx" class="sub-row">
        <input
          v-model="r.name"
          class="input sub-name"
          maxlength="20"
          placeholder="名目，如：蔬菜"
          placeholder-class="ph"
        />
        <input
          v-model="r.amount"
          class="input sub-amount"
          type="digit"
          placeholder="0.00"
          placeholder-class="ph"
        />
        <text v-if="subRows.length > 1" class="sub-del" hover-class="press-dim" @tap="removeRow(idx)">✕</text>
      </view>
      <view class="sub-btns">
        <text class="sub-add" hover-class="press-dim" @tap="addRow">
          <text>＋ 加一项</text>
        </text>
        <text class="sub-ai" hover-class="press-dim" @tap="aiRecognize">
          <text>✨ 拍照识别</text>
        </text>
      </view>
      <view class="row total-row">
        <text class="label req">总费用（元）</text>
        <text class="total-value">¥{{ totalYuan }}</text>
      </view>
      <view class="row" hover-class="press-dim" @tap="dateCalVisible = true">
        <text class="label">日期</text>
        <text class="value picker">{{ date }} ›</text>
      </view>
      <view class="row">
        <text class="label">备注</text>
        <input v-model="remark" class="input" maxlength="50" placeholder="选填，50字内" placeholder-class="ph" />
      </view>
    </view>

    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">保存</button>

    <ActionSheet
      :visible="catSheetVisible"
      :items="catItems"
      @select="onCatPick"
      @close="catSheetVisible = false"
    />
    <CalendarPicker
      :visible="dateCalVisible"
      :selected="date"
      @select="onDatePick"
      @close="dateCalVisible = false"
    />
    <InputDialog
      :visible="catDialogVisible"
      :title="type === 'EXPENSE' ? '添加支出分类' : '添加收入分类'"
      :placeholder="type === 'EXPENSE' ? '如：交通 /日用 /宠物' : '如：工资 /副业'"
      :maxlength="10"
      @confirm="onCatCreate"
      @close="catDialogVisible = false"
    />

    <!-- AI 识别选区编辑器：裁剪 / 涂抹 -->
    <ImageEditOverlay
      :visible="editorVisible"
      :src="editorSrc"
      @confirm="onEditorConfirm"
      @cancel="onEditorCancel"
      @skip="onEditorSkip"
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
.section { padding: 12rpx 32rpx 24rpx; }
.type-row { display: flex; gap: 20rpx; padding: 24rpx 0; }
.type-btn {
  flex: 1; text-align: center;
  font-size: 30rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 16rpx;
  padding: 18rpx 0;
}
.type-btn.on { font-weight: 700; }
.type-btn.out.on { color: v-bind('theme.expense'); border-color: v-bind('theme.expense'); background: v-bind('theme.successLight'); }
.type-btn.inc.on { color: v-bind('theme.income'); border-color: v-bind('theme.income'); background: v-bind('theme.primaryLight'); }

.row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 26rpx 0;
}
.label { font-size: 28rpx; color: v-bind('theme.title'); width: 180rpx; }
.label.req::before { content: '* '; color: v-bind('theme.danger'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.value { font-size: 26rpx; color: v-bind('theme.title'); }
.value.picker { color: v-bind('theme.title'); }
.chev { color: v-bind('theme.sub'); margin-left: 8rpx; }
.link { font-size: 26rpx; color: v-bind('theme.primaryBtn'); flex: 1; text-align: right; }
.link.manage { flex: 0.6; }
.ph { color: v-bind('theme.sub'); }

/* 分费用明细 */
.sub-head { display: flex; align-items: baseline; padding: 26rpx 0 6rpx; }
.sub-hint { flex: 1; text-align: right; font-size: 22rpx; color: v-bind('theme.sub'); }
.sub-row { display: flex; align-items: center; padding: 14rpx 0; }
.sub-name { flex: 1.2; text-align: left; font-size: 28rpx; color: v-bind('theme.title');
  background: v-bind('theme.chipBg'); border-radius: 12rpx; padding: 12rpx 20rpx; }
.sub-amount { flex: 1; text-align: right; font-size: 30rpx; font-weight: 600; color: v-bind('theme.title');
  margin-left: 16rpx; }
.sub-del { width: 56rpx; text-align: center; color: v-bind('theme.sub'); font-size: 28rpx; }
.sub-add, .sub-ai {
  display: inline-flex; align-items: center;
  font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx dashed v-bind('theme.primaryBtn'); border-radius: 12rpx;
  padding: 8rpx 24rpx; margin: 6rpx 0 10rpx;
}
.sub-btns { display: flex; gap: 16rpx; }
.sub-ai { color: v-bind('theme.primaryBtn'); border-style: solid; }
.total-row { border-top: 2rpx solid v-bind('theme.divider'); }
.total-row .label { width: auto; flex-shrink: 0; margin-right: 16rpx; white-space: nowrap; }
.total-value { flex: 1; text-align: right; font-size: 36rpx; font-weight: 700; color: v-bind('theme.title'); }

.btn-submit {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 32rpx; line-height: 92rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
</style>
