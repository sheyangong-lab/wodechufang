<script setup lang="ts">
import { theme } from '@/styles/theme';
import { foodbookApi } from '@/api/foodbook';
import type { FoodbookItemView } from '@/api/foodbook';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { removeBackground, warmupSegmentation } from '@/utils/segment';
import { fullUrl } from '@/api/dish';
import { getApiBase } from '@/api/config';
import { onShow, onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import CustomTabbar from '@/components/custom-tabbar.vue';
import CalendarPicker from '@/components/calendar-picker.vue';
import ActionSheet from '@/components/action-sheet.vue';
import CutoutEditor from '@/components/cutout-editor.vue';
import { chooseOneImage, processCutout } from '@/utils/image-pick';

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
  warmupSegmentation(); // 空闲预热抠图模型，首次抠图不再等加载
});

// App 长期驻留后台跨天后，页面缓存的"今天"会过期：
// 若用户停在"创建那天的今天"，onShow 时静默追到新的今天并刷新；
// 用户手动选过的其他日期不动。
const bootedToday = todayStr();
onShow(() => {
  const t = todayStr();
  if (t !== bootedToday && date.value === bootedToday) {
    date.value = t;
    load();
  }
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
  { key: 'camera', title: '拍照 · 抠图', desc: '自动识别 / 框选 / 涂抹' },
  { key: 'album', title: '相册 · 抠图', desc: '自动识别 / 框选 / 涂抹' },
  { key: 'camera-raw', title: '拍照 · 原图', desc: '不抠图直接贴上' },
  { key: 'album-raw', title: '相册 · 原图', desc: '不抠图直接贴上' },
];

function openAdd() {
  addSheetVisible.value = true;
}

/** 单次选图（H5 返回 blob: URL 数组；用户取消返回空数组） */
function chooseOnce(sourceType: ('camera' | 'album')[], count: number): Promise<string[]> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType,
      success: (res) => resolve(res.tempFilePaths as string[]),
      fail: () => resolve([]),
    });
  });
}

/** 拍完一张后询问是否继续，实现连续拍照 */
function askKeepShooting(count: number): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '继续拍吗？',
      content: `已拍 ${count} 张，可继续拍或结束`,
      confirmText: '继续拍',
      cancelText: '就这些',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false),
    });
  });
}

async function pickTempPaths(sourceType: 'camera' | 'album', maxCount = 9): Promise<string[]> {
  if (sourceType === 'album') {
    return chooseOnce(['album'], maxCount);
  }
  // 系统相机一次只回传一张，这里循环调起并逐张询问，实现连续拍
  const paths: string[] = [];
  while (paths.length < maxCount) {
    const one = await chooseOnce(['camera'], 1);
    if (one.length === 0) break; // 用户取消
    paths.push(...one);
    if (paths.length >= maxCount) break;
    if (!(await askKeepShooting(paths.length))) break;
  }
  return paths;
}

async function onAddPick(key: string) {
  addSheetVisible.value = false;
  if (key.endsWith('raw')) {
    // 原图：不走编辑器
    const sourceType: 'camera' | 'album' = key.startsWith('camera') ? 'camera' : 'album';
    const tempPaths = await pickTempPaths(sourceType, 9);
    if (tempPaths.length === 0) return;
    processRawImages(tempPaths);
    return;
  }
  // 抠图（默认）：单张选图后进入编辑器（自动 / 框选 / 涂抹）
  const sourceType: 'camera' | 'album' = key.startsWith('camera') ? 'camera' : 'album';
  const tempPath = await chooseOneImage(sourceType);
  if (tempPath) {
    editorSrc.value = tempPath;
    editorVisible.value = true;
  }
}

/** 原图批量处理（不抠图直接贴上） */
async function processRawImages(tempPaths: string[]) {
  uni.showLoading({ title: `处理中 0/${tempPaths.length}`, mask: true });

  let slot = staged.value.length;
  for (let i = 0; i < tempPaths.length; i++) {
    uni.showLoading({ title: `处理中 ${i + 1}/${tempPaths.length}`, mask: true });
    try {
      const blob = await (await fetch(tempPaths[i])).blob();
      const url = await uploadBlob(blob);
      staged.value.push({
        key: `s-${Date.now()}-${i}`,
        imageUrl: url,
        left: 12 + ((slot * 17) % 44),
        top: 8 + ((slot * 13) % 46),
        widthPct: 62, // 原图宽
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

// ----- 手动抠图编辑器 -----

const editorVisible = ref(false);
const editorSrc = ref('');

function onCutoutCancel() {
  editorVisible.value = false;
}

async function onCutoutConfirm(payload: { kind: 'auto' | 'box' | 'paint'; blob: Blob | null }) {
  editorVisible.value = false;
  const titles = { auto: '识别主体中…', box: '识别框内主体…', paint: '生成贴纸…' };
  uni.showLoading({ title: titles[payload.kind], mask: true });
  try {
    const { url } = await processCutout(payload.kind, editorSrc.value, payload.blob, { keepFrame: payload.kind === 'box' });
    staged.value.push({
      key: `s-${Date.now()}-c`,
      imageUrl: url,
      left: 12 + ((staged.value.length * 17) % 44),
      top: 8 + ((staged.value.length * 13) % 46),
      widthPct: 42,
    });
    uni.hideLoading();
    uni.showToast({ title: '已贴上，拖动调整后点「完成」', icon: 'none' });
    measurePage();
  } catch {
    uni.hideLoading();
    uni.showToast({ title: '处理失败，请重试', icon: 'none' });
  }
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
// dragKey/dragDx/dragDy 必须是 ref：原来用普通变量，touchmove 不触发重渲染，
// 贴纸只在松手后跳位（表现为"卡顿"）；且拖动中用 translate3d 走合成层，不触发布局
const dragKey = ref('');
const dragDx = ref(0);
const dragDy = ref(0);
let dragStartX = 0;
let dragStartY = 0;
let dragOrigLeft = 0; // 拖动起点，px
let dragOrigTop = 0;

function stickerStyle(s: StagedSticker) {
  const style: Record<string, string | number> = {
    left: `${s.left}%`,
    top: `${s.top}%`,
    width: `${s.widthPct}%`,
    zIndex: 100,
  };
  if (dragKey.value === s.key) {
    style.transform = `translate3d(${dragDx.value}px, ${dragDy.value}px, 0)`;
  }
  return style;
}

function onTouchStart(e: TouchEvent, s: StagedSticker) {
  if (!pageW) measurePage();
  const t = e.touches[0];
  dragStartX = t.clientX;
  dragStartY = t.clientY;
  dragOrigLeft = (s.left / 100) * pageW;
  dragOrigTop = (s.top / 100) * pageH;
  dragKey.value = s.key;
  dragDx.value = 0;
  dragDy.value = 0;
}

function onTouchMove(e: TouchEvent, s: StagedSticker) {
  if (dragKey.value !== s.key) return;
  const t = e.touches[0];
  const wPx = (s.widthPct / 100) * pageW;
  const nx = Math.max(0, Math.min(pageW - wPx, dragOrigLeft + (t.clientX - dragStartX)));
  const ny = Math.max(0, Math.min(pageH - 40, dragOrigTop + (t.clientY - dragStartY)));
  dragDx.value = nx - dragOrigLeft;
  dragDy.value = ny - dragOrigTop;
}

function onTouchEnd(s: StagedSticker) {
  if (dragKey.value !== s.key) return;
  s.left = ((dragOrigLeft + dragDx.value) / (pageW || 1)) * 100;
  s.top = ((dragOrigTop + dragDy.value) / (pageH || 1)) * 100;
  dragKey.value = '';
  dragDx.value = 0;
  dragDy.value = 0;
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
        :style="stickerStyle(s)"
        @touchstart="onTouchStart($event, s)"
        @touchmove.stop.prevent="onTouchMove($event, s)"
        @touchend="onTouchEnd(s)"
        @touchcancel="onTouchEnd(s)"
      >
        <image class="sticker-img" :src="fullUrl(s.imageUrl)" mode="widthFix" />
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

    <!-- 手动抠图编辑器：框选 / 涂抹 -->
    <CutoutEditor
      :visible="editorVisible"
      :src="editorSrc"
      @confirm="onCutoutConfirm"
      @cancel="onCutoutCancel"
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
  /* 纸张色随深浅色主题切换（theme.paper），深色下不再刺眼 */
  background: v-bind('theme.paper');
  border: 2rpx solid v-bind('theme.divider');
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: inset 0 0 40rpx rgba(200, 160, 80, 0.12);
}
.page-title {
  position: absolute; top: 16rpx; left: 20rpx;
  font-size: 26rpx; font-weight: 700; color: v-bind('theme.paperTitle');
}
.page-empty {
  position: absolute; top: 50%; left: 50%;
  transform: translate(-50%, -50%);
  font-size: 26rpx; color: v-bind('theme.paperSub');
}
.sticker { position: absolute; }
.sticker.drag { opacity: 0.92; will-change: transform; }
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
