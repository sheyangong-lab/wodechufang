<script setup lang="ts">
import { theme } from '@/styles/theme';
import { foodbookApi } from '@/api/foodbook';
import type { FoodbookItemView } from '@/api/foodbook';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { chooseSubjectImage } from '@/utils/image-pick';
import { removeBackground } from '@/utils/segment';
import { getApiBase } from '@/api/config';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import CustomTabbar from '@/components/custom-tabbar.vue';
import CalendarPicker from '@/components/calendar-picker.vue';
import ActionSheet from '@/components/action-sheet.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const date = ref(todayStr());
const saved = ref<FoodbookItemView[]>([]);
/** 编辑中的贴纸（未落库），可自由拖动 */
const staged = ref<StagedSticker[]>([]);
const calVisible = ref(false);
const addSheetVisible = ref(false);
const loading = ref(true);

interface StagedSticker {
  key: string;
  imageUrl: string;
  left: number; // 页面容器内百分比
  top: number;
  widthPct: number; // 宽度百分比
}

const PAGE_ID = 'foodbook-page';
let pageW = 0;
let pageH = 0;
let dragKey = '';
let dragStartX = 0;
let dragStartY = 0;
let dragOrigLeft = 0;
let dragOrigTop = 0;

const dateLabel = computed(() => {
  const today = todayStr();
  if (date.value === today) return '今天';
  const [y, m, d] = today.split('-').map(Number);
  const tomorrow = `${y}-${String(m).padStart(2, '0')}-${String(d + 1).padStart(2, '0')}`;
  if (date.value === tomorrow) return '明天';
  return '';
});

function shiftDate(days: number) {
  const [y, m, d] = date.value.split('-').map(Number);
  const next = new Date(y, m - 1, d + days);
  const pad = (n: number) => String(n).padStart(2, '0');
  date.value = `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())}`;
  staged.value = [];
  load();
}

function onCalPick(d: string | null) {
  calVisible.value = false;
  if (d && d !== date.value) {
    date.value = d;
    staged.value = [];
    load();
  }
}

onLoad(async () => {
  kitchenId.value = await ensureKitchenId();
  await load();
  measurePage();
});

async function load() {
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    saved.value = await foodbookApi.page(kitchenId.value, date.value);
  } catch {
    // toast 已统一弹出
  } finally {
    loading.value = false;
  }
  measurePage();
}

function measurePage() {
  setTimeout(() => {
    // #ifdef H5
    const el = document.getElementById(PAGE_ID);
    if (el) {
      pageW = el.clientWidth;
      pageH = el.clientHeight;
    }
    // #endif
  }, 100);
}

// ---------- 添加贴纸（拍照/上传，可多张，可选主体识别） ----------

const addItems = [
  { key: 'camera-seg', title: '拍照 · 主体识别', desc: '拍多张，自动抠图加白边' },
  { key: 'album-seg', title: '相册 · 主体识别', desc: '选多张，自动抠图加白边' },
  { key: 'camera', title: '拍照 · 原图', desc: '不抠图直接贴上' },
  { key: 'album', title: '相册 · 原图', desc: '不抠图直接贴上' },
];

function openAdd() {
  addSheetVisible.value = true;
}

async function onAddPick(key: string) {
  addSheetVisible.value = false;
  const seg = key.endsWith('seg');
  const sourceType: ('camera' | 'album')[] = [key.startsWith('camera') ? 'camera' : 'album'];

  // 多选临时图（H5 为 blob: URL）
  const tempPaths = await new Promise<string[]>((resolve, reject) => {
    uni.chooseImage({
      count: 9,
      sizeType: ['compressed'],
      sourceType,
      success: (res) => resolve(res.tempFilePaths as string[]),
      fail: () => reject(new Error('cancel')),
    });
  }).catch(() => []);

  if (tempPaths.length === 0) return;
  uni.showLoading({ title: `处理中 0/${tempPaths.length}`, mask: true });

  let slot = staged.value.length;
  for (let i = 0; i < tempPaths.length; i++) {
    uni.showLoading({ title: `处理中 ${i + 1}/${tempPaths.length}`, mask: true });
    try {
      let blob: Blob;
      if (seg) {
        blob = (await removeBackground(tempPaths[i])).blob;
      } else {
        blob = await (await fetch(tempPaths[i])).blob();
      }
      const url = await uploadBlob(blob);
      const wPct = seg ? 42 : 62; // 抠图窄、原图宽
      staged.value.push({
        key: `s-${Date.now()}-${i}`,
        imageUrl: url,
        left: 12 + ((slot * 17) % 44), // 初始错开摆放
        top: 8 + ((slot * 13) % 46),
        widthPct: wPct,
      });
      slot++;
    } catch {
      // 单张失败跳过
    }
  }
  uni.hideLoading();
  if (staged.value.length > 0) {
    uni.showToast({ title: `${staged.value.length} 张待摆放，拖动调整后点「完成」`, icon: 'none' });
  }
  measurePage();
}

function uploadBlob(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const fd = new FormData();
    fd.append('file', blob, 'sticker.png');
    const token = uni.getStorageSync('token');
    fetch(getApiBase() + '/api/uploads', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: fd,
    })
      .then((r) => r.json())
      .then((body) => (body.code === 0 ? resolve(body.data.url) : reject(new Error(body.message))))
      .catch(reject);
  });
}

// ---------- 拖动（staged 自由摆放） ----------

function pxLeft(s: StagedSticker) {
  return dragKey === s.key ? dragLeft : (s.left / 100) * pageW;
}
function pxTop(s: StagedSticker) {
  return dragKey === s.key ? dragTop : (s.top / 100) * pageH;
}
let dragLeft = 0;
let dragTop = 0;

function onTouchStart(e: TouchEvent, s: StagedSticker) {
  if (!pageW) measurePage();
  const t = e.touches[0];
  dragStartX = t.clientX;
  dragStartY = t.clientY;
  dragOrigLeft = pxLeft(s);
  dragOrigTop = pxTop(s);
  dragKey = s.key;
  dragLeft = dragOrigLeft;
  dragTop = dragOrigTop;
}

function onTouchMove(e: TouchEvent, s: StagedSticker) {
  if (dragKey !== s.key) return;
  const t = e.touches[0];
  const wPx = (s.widthPct / 100) * pageW;
  dragLeft = Math.max(0, Math.min(pageW - wPx, dragOrigLeft + (t.clientX - dragStartX)));
  dragTop = Math.max(0, Math.min(pageH - 40, dragOrigTop + (t.clientY - dragStartY)));
}

function onTouchEnd(s: StagedSticker) {
  if (dragKey !== s.key) return;
  s.left = (dragLeft / (pageW || 1)) * 100;
  s.top = (dragTop / (pageH || 1)) * 100;
  dragKey = '';
}

// ---------- 完成（落库） / 删除 / 导出 ----------

const saving = ref(false);

async function commitStaged() {
  if (staged.value.length === 0 || !kitchenId.value) return;
  if (saving.value) return;
  saving.value = true;
  try {
    const items = staged.value.map((s, i) => ({
      imageUrl: s.imageUrl,
      x: Number(((s.left / (pageW || 1)) * 100).toFixed(2)),
      y: Number(((s.top / (pageH || 1)) * 100).toFixed(2)),
      width: s.widthPct,
      zIndex: saved.value.length + i + 1,
    }));
    const savedItems = await foodbookApi.addAll(kitchenId.value, date.value, items);
    saved.value.push(...savedItems);
    staged.value = [];
    uni.showToast({ title: `已放入手账 ${savedItems.length} 张`, icon: 'none' });
  } catch {
    uni.showToast({ title: '保存失败，请重试', icon: 'none' });
  } finally {
    saving.value = false;
  }
}

function removeSticker(s: { key?: string; id?: number }) {
  if (s.key) {
    staged.value = staged.value.filter((x) => x.key !== s.key);
    return;
  }
  if (s.id && kitchenId.value) {
    foodbookApi.remove(kitchenId.value, s.id).then(load);
  }
}

/** 导出当天手账整页为图片，存入系统相册 */
async function exportPage() {
  // #ifdef H5
  const el = document.getElementById(PAGE_ID);
  if (!el) return;
  const dpr = window.devicePixelRatio || 1;
  const W = el.clientWidth;
  const H = el.clientHeight;
  const canvas = document.createElement('canvas');
  canvas.width = W * dpr;
  canvas.height = H * dpr;
  const ctx = canvas.getContext('2d')!;
  ctx.scale(dpr, dpr);
  // 纸张底色
  ctx.fillStyle = '#FFF6EA';
  ctx.fillRect(0, 0, W, H);
  // 标题
  ctx.fillStyle = '#3D3325';
  ctx.font = 'bold 16px system-ui, sans-serif';
  ctx.fillText(`${date.value} 的食本`, 12, 24);

  const entries: { url: string; left: number; top: number; widthPct: number }[] = [
    ...saved.value.map((s) => ({
      url: getApiBase() + s.imageUrl,
      left: s.x ?? 10, top: s.y ?? 10, widthPct: s.width ?? 40,
    })),
    ...staged.value.map((s) => ({
      url: s.imageUrl.startsWith('http') ? s.imageUrl : getApiBase() + s.imageUrl,
      left: s.left, top: s.top, widthPct: s.widthPct,
    })),
  ];
  const imgs = await Promise.all(entries.map((g) => new Promise<HTMLImageElement | null>((res) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => res(img);
    img.onerror = () => res(null);
    img.src = g.url;
  })));
  imgs.forEach((img, i) => {
    if (!img) return;
    const g = entries[i];
    const x = (g.left / 100) * W;
    const y = (g.top / 100) * H;
    const w = (g.widthPct / 100) * W;
    const ratio = img.naturalHeight / (img.naturalWidth || 1);
    ctx.drawImage(img, x, y, w, w * ratio);
  });

  const dataUrl = canvas.toDataURL('image/png');
  const bridge = (globalThis as Record<string, any>).Capacitor?.Plugins?.AppBridge;
  if (bridge && (globalThis as Record<string, any>).Capacitor?.isNativePlatform?.()) {
    try {
      await bridge.saveImage({ dataUrl, name: `foodbook-${date.value}` });
      uni.showToast({ title: '已保存到相册', icon: 'none' });
    } catch (e) {
      uni.showToast({ title: (e as Error).message?.slice(0, 30) || '保存失败', icon: 'none' });
    }
  } else {
    // 浏览器：打开预览长按保存
    uni.previewImage({ urls: [dataUrl] });
  }
  // #endif
}
</script>

<template>
  <view class="page">
    <view class="page-scroll">
    <!-- 日期切换 -->
    <view class="date-row">
      <text class="arrow" hover-class="press-dim" @tap="shiftDate(-1)">‹</text>
      <view class="date-mid" hover-class="press-dim" @tap="calVisible = true">
        <text class="date-main">{{ dateLabel || date }}</text>
        <text class="date-sub">{{ date }} ▾</text>
      </view>
      <text class="arrow" hover-class="press-dim" @tap="shiftDate(1)">›</text>
    </view>

    <!-- 手账页面 -->
    <view :id="PAGE_ID" class="page-area">
      <text class="page-title">{{ date }} 的食本</text>
      <!-- 已保存贴纸 -->
      <view
        v-for="s in saved"
        :key="'sv-' + s.id"
        class="sticker"
        :style="{ left: (s.x || 0) + '%', top: (s.y || 0) + '%', width: (s.width || 40) + '%', zIndex: s.zIndex || 1 }"
      >
        <image class="sticker-img" :src="getApiBase() + s.imageUrl" mode="widthFix" @longpress="removeSticker({ id: s.id })" />
      </view>
      <!-- 编辑中贴纸（可拖动） -->
      <view
        v-for="s in staged"
        :key="s.key"
        class="sticker drag"
        :style="{ left: pxLeft(s) + 'px', top: pxTop(s) + 'px', width: s.widthPct + '%' }"
        @touchstart="onTouchStart($event, s)"
        @touchmove.stop.prevent="onTouchMove($event, s)"
        @touchend="onTouchEnd(s)"
      >
        <image class="sticker-img" :src="s.imageUrl" mode="widthFix" />
        <text class="sticker-del" @tap.stop="removeSticker({ key: s.key })">✕</text>
      </view>
      <text v-if="saved.length === 0 && staged.length === 0" class="page-empty">
        点击下方「添加」记录今天吃到的美味吧
      </text>
    </view>

    <!-- 操作区 -->
    <view class="ops">
      <text class="op" hover-class="press-dim" @tap="openAdd">＋ 添加</text>
      <text class="op primary" hover-class="press-dim" @tap="commitStaged">
        完成{{ staged.length ? `（${staged.length}）` : '' }}
      </text>
      <text class="op" hover-class="press-dim" @tap="exportPage">导出手账</text>
    </view>

    <CalendarPicker
      :visible="calVisible"
      :selected="date"
      @select="onCalPick"
      @close="calVisible = false"
    />
    <ActionSheet
      :visible="addSheetVisible"
      :items="addItems"
      @select="onAddPick"
      @close="addSheetVisible = false"
    />

        </view>
    <CustomTabbar current="foodbook" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex; flex-direction: column;
  /* WebView 的 vh 计算不稳（部分机型 100vh > 可视区导致顶部裁切/底栏溢出），
     App.vue 启动时 JS 写入实际屏幕 px */
  height: calc(var(--sk-vh, 100vh) - var(--window-top, 0px)); /* 减去fixed导航栏高度，配合uni-page-body的padding-top让位 */
  overflow: hidden;
}
.page-scroll {
  flex: 1; overflow-y: auto;
  min-height: 0;
  padding: 24rpx;
  box-sizing: border-box;
}
.date-row {
  display: flex; align-items: center; justify-content: center; gap: 64rpx;
  padding: 8rpx 0 20rpx;
}
.arrow { font-size: 44rpx; color: v-bind('theme.title'); padding: 0 16rpx; line-height: 1; }
.date-mid { display: flex; flex-direction: column; align-items: center; }
.date-main { font-size: 34rpx; font-weight: 700; color: v-bind('theme.title'); }
.date-sub { font-size: 22rpx; color: v-bind('theme.sub'); }

.page-area {
  position: relative;
  height: 72vh;
  background: #FFF6EA;
  border: 2rpx solid v-bind('theme.divider');
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: inset 0 0 40rpx rgba(200, 160, 80, 0.12);
}
.page-title {
  position: absolute; top: 16rpx; left: 20rpx;
  font-size: 26rpx; font-weight: 700; color: #3D3325;
}
.page-empty {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  font-size: 26rpx; color: #B8AD9C;
}
.sticker { position: absolute; }
.sticker.drag { opacity: 0.92; }
.sticker-img { width: 100%; }
.sticker-del {
  position: absolute; top: -12rpx; right: -12rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: v-bind('theme.danger'); color: #fff;
  font-size: 24rpx; text-align: center; line-height: 40rpx;
}

.ops {
  display: flex; gap: 20rpx;
  margin-top: 24rpx;
  padding-bottom: 160rpx;
}
.op {
  flex: 1; text-align: center;
  border: 2rpx solid v-bind('theme.primaryBtn'); color: v-bind('theme.primaryBtn');
  border-radius: 44rpx; font-size: 28rpx; font-weight: 600;
  padding: 16rpx 0;
}
.op.primary {
  background: v-bind('theme.primaryBtn'); color: #fff;
}
</style>
