<script setup lang="ts">
/**
 * 图片选区编辑器（账本 AI 识别用）：拍照/选图后先框选或涂抹要识别的区域。
 * - 裁剪：拖动/缩放选框，确认后只裁出选区；
 * - 涂抹：手指涂选，确认后涂过区域保留原图，其余盖黑（抠图）。
 * 仅 H5（APK 为 H5+Capacitor WebView，同样生效）。
 */
import { theme } from '@/styles/theme';
import { ref, watch } from 'vue';

const props = defineProps<{ visible: boolean; src: string }>();
const emit = defineEmits<{
  (e: 'confirm', blob: Blob): void;
  (e: 'cancel'): void;
  (e: 'skip'): void;
}>();

type Mode = 'crop' | 'paint';
const mode = ref<Mode>('crop');
const busy = ref(false);
const WRAP_ID = 'ai-edit-wrap';
const CVS_ID = 'ai-edit-canvas';

let img: HTMLImageElement | null = null;
let mask: HTMLCanvasElement | null = null; // natural2 尺寸的涂抹蒙版（白色=选中）
let cssW = 0; // 显示画布 css 宽
let cssH = 0;
let s1 = 0; // css → 原图 缩放（naturalW = cssW / s1）
const DPR = Math.min(2, window.devicePixelRatio || 1);

const crop = ref({ x: 0, y: 0, w: 0, h: 0 }); // css 坐标
let touchKind: '' | 'new' | 'move' | 'nw' | 'ne' | 'sw' | 'se' = '';
let startX = 0;
let startY = 0;
let cropStart = { x: 0, y: 0, w: 0, h: 0 };

watch(
  () => props.visible,
  (v) => {
    if (v) {
      mode.value = 'crop';
      busy.value = false;
      init();
    }
  }
);

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const im = new Image();
    im.onload = () => resolve(im);
    im.onerror = () => reject(new Error('图片加载失败'));
    im.src = src;
  });
}

function nextFrame(): Promise<void> {
  return new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => r())));
}

async function init() {
  // 等待遮罩层完成布局（canvas 由 v-if 挂载）
  for (let i = 0; i < 10; i++) {
    await nextFrame();
    if (document.getElementById(CVS_ID)?.querySelector('canvas')) break;
  }
  const cvs = document.getElementById(CVS_ID)?.querySelector('canvas');
  if (!cvs) return;
  try {
    img = await loadImage(props.src);
  } catch {
    uni.showToast({ title: '图片加载失败', icon: 'none' });
    emit('cancel');
    return;
  }
  // 可用宽高直接由视口推导，不依赖局部元素布局（修复画布只有一点画面的问题）
  const maxW = Math.max(200, Math.min(window.innerWidth - 28, 620));
  const maxH = window.innerHeight * 0.58;
  s1 = Math.min(maxW / img.naturalWidth, maxH / img.naturalHeight, 1);
  cssW = Math.round(img.naturalWidth * s1);
  cssH = Math.round(img.naturalHeight * s1);
  cvs.width = Math.round(cssW * DPR);
  cvs.height = Math.round(cssH * DPR);
  cvs.style.width = cssW + 'px';
  cvs.style.height = cssH + 'px';
  // 蒙版：导出分辨率（长边≤1600，识别足够且省流量）
  const s2 = Math.min(1600 / img.naturalWidth, 1600 / img.naturalHeight, 1);
  mask = document.createElement('canvas');
  mask.width = Math.max(1, Math.round(img.naturalWidth * s2));
  mask.height = Math.max(1, Math.round(img.naturalHeight * s2));
  const mctx = mask.getContext('2d')!;
  mctx.fillStyle = '#000';
  mctx.fillRect(0, 0, mask.width, mask.height); // 黑底=未选中
  // 默认给一个居中的 84% 选框，用户可直接拖角调整
  crop.value = { x: cssW * 0.08, y: cssH * 0.08, w: cssW * 0.84, h: cssH * 0.84 };
  redraw();
}

function ctx2d(): CanvasRenderingContext2D | null {
  const cvs = document.getElementById(CVS_ID)?.querySelector('canvas');
  return cvs ? cvs.getContext('2d') : null;
}

function canvasPos(e: TouchEvent): { x: number; y: number } {
  const cvs = document.getElementById(CVS_ID)?.querySelector('canvas')!;
  const r = cvs.getBoundingClientRect();
  const t = e.touches[0];
  return { x: t.clientX - r.left, y: t.clientY - r.top };
}

const HANDLE = 22; // 角手柄命中半径 css px
const BRUSH = 14; // 笔刷半径 css px

function redraw() {
  const ctx = ctx2d();
  if (!ctx || !img) return;
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  ctx.clearRect(0, 0, cssW, cssH);
  ctx.drawImage(img, 0, 0, cssW, cssH);
  if (mode.value === 'crop') {
    const c = crop.value;
    ctx.fillStyle = 'rgba(0,0,0,0.45)';
    ctx.fillRect(0, 0, cssW, c.y);
    ctx.fillRect(0, c.y, c.x, c.h);
    ctx.fillRect(c.x + c.w, c.y, cssW - c.x - c.w, c.h);
    ctx.fillRect(0, c.y + c.h, cssW, cssH - c.y - c.h);
    ctx.strokeStyle = '#EFA63C';
    ctx.lineWidth = 2;
    ctx.strokeRect(c.x, c.y, c.w, c.h);
    ctx.fillStyle = '#EFA63C';
    [
      [c.x, c.y],
      [c.x + c.w, c.y],
      [c.x, c.y + c.h],
      [c.x + c.w, c.y + c.h],
    ].forEach(([hx, hy]) => ctx.fillRect(hx - 7, hy - 7, 14, 14));
  } else if (mask) {
    // 涂抹预览：把蒙版按显示比例叠加为红色半透明
    const cvs = document.getElementById(CVS_ID)!.querySelector('canvas')!;
    ctx.save();
    ctx.globalAlpha = 0.45;
    ctx.drawImage(mask, 0, 0, cssW, cssH);
    ctx.restore();
  }
}

type touchKindAlias = '' | 'new' | 'move' | 'nw' | 'ne' | 'sw' | 'se';

function hitHandle(x: number, y: number): touchKindAlias {
  const c = crop.value;
  const corners: [number, number, touchKindAlias][] = [
    [c.x, c.y, 'nw'],
    [c.x + c.w, c.y, 'ne'],
    [c.x, c.y + c.h, 'sw'],
    [c.x + c.w, c.y + c.h, 'se'],
  ];
  for (const [hx, hy, k] of corners) {
    if (Math.abs(x - hx) <= HANDLE && Math.abs(y - hy) <= HANDLE) return k;
  }
  if (x >= c.x && x <= c.x + c.w && y >= c.y && y <= c.y + c.h) return 'move';
  return 'new';
}

function onTouchStart(e: TouchEvent) {
  if (!cssW) return;
  const p = canvasPos(e);
  startX = p.x;
  startY = p.y;
  if (mode.value === 'crop') {
    touchKind = hitHandle(p.x, p.y);
    cropStart = { ...crop.value };
    if (touchKind === 'new') {
      crop.value = { x: p.x, y: p.y, w: 0, h: 0 };
      cropStart = { ...crop.value };
    }
  } else {
    touchKind = 'new';
    paintAt(p.x, p.y);
  }
  redraw();
}

function onTouchMove(e: TouchEvent) {
  if (!touchKind || !cssW) return;
  const p = canvasPos(e);
  if (mode.value === 'paint') {
    paintAt(p.x, p.y);
    redraw();
    return;
  }
  const dx = p.x - startX;
  const dy = p.y - startY;
  const s = cropStart;
  let { x, y, w, h } = s;
  if (touchKind === 'move') {
    x = Math.max(0, Math.min(cssW - w, s.x + dx));
    y = Math.max(0, Math.min(cssH - h, s.y + dy));
  } else if (touchKind === 'new') {
    // 从空白处按下拖出新框：起点与当前点为对角
    x = Math.max(0, Math.min(startX, p.x));
    y = Math.max(0, Math.min(startY, p.y));
    w = Math.min(Math.abs(p.x - startX), cssW - x);
    h = Math.min(Math.abs(p.y - startY), cssH - y);
  } else {
    if (touchKind.includes('w')) {
      x = Math.max(0, Math.min(s.x + s.w - 40, s.x + dx));
      w = s.w + (s.x - x);
    }
    if (touchKind.includes('n')) {
      y = Math.max(0, Math.min(s.y + s.h - 40, s.y + dy));
      h = s.h + (s.y - y);
    }
    if (touchKind.includes('e')) w = Math.max(40, Math.min(cssW - s.x, s.w + dx));
    if (touchKind.includes('s')) h = Math.max(40, Math.min(cssH - s.y, s.h + dy));
  }
  crop.value = { x, y, w, h };
  redraw();
}

function onTouchEnd() {
  touchKind = '';
}

/** 蒙版坐标：css → 蒙版像素 */
function paintAt(cx: number, cy: number) {
  if (!mask) return;
  const k = mask.width / cssW;
  const mctx = mask.getContext('2d')!;
  mctx.fillStyle = '#fff';
  mctx.beginPath();
  mctx.arc(cx * k, cy * k, BRUSH * k, 0, Math.PI * 2);
  mctx.fill();
}

function setMode(m: Mode) {
  mode.value = m;
  if (m === 'paint' && cssW) {
    // 从裁剪切到涂抹：初始化默认全框选，避免空白结果
    paintAll();
  }
  redraw();
}

function paintAll() {
  if (!mask) return;
  const mctx = mask.getContext('2d')!;
  mctx.fillStyle = '#000';
  mctx.fillRect(0, 0, mask.width, mask.height);
}

function reset() {
  if (mode.value === 'crop') {
    crop.value = { x: cssW * 0.08, y: cssH * 0.08, w: cssW * 0.84, h: cssH * 0.84 };
  } else {
    paintAll();
  }
  redraw();
}

/** css 选区 → 蒙版（导出）坐标系 */
function exportScale(): number {
  return mask ? mask.width / cssW : 1;
}

async function exportBlob(): Promise<Blob> {
  if (!img || !mask) throw new Error('未初始化');
  const k = exportScale();
  const out = document.createElement('canvas');
  const ctx = out.getContext('2d')!;
  if (mode.value === 'crop') {
    const c = crop.value;
    out.width = Math.max(1, Math.round(c.w * k));
    out.height = Math.max(1, Math.round(c.h * k));
    const sx = c.x * k;
    const sy = c.y * k;
    const sw = c.w * k;
    const sh = c.h * k;
    const sN = img.naturalWidth / mask.width;
    ctx.drawImage(img, sx * sN, sy * sN, sw * sN, sh * sN, 0, 0, out.width, out.height);
  } else {
    out.width = mask.width;
    out.height = mask.height;
    ctx.drawImage(img, 0, 0, out.width, out.height);
    const mdata = mask.getContext('2d')!.getImageData(0, 0, mask.width, mask.height).data;
    const oimg = ctx.getImageData(0, 0, out.width, out.height);
    // 未涂抹处盖黑（抠图：只保留涂选区域可读）
    for (let i = 0; i < oimg.data.length; i += 4) {
      if (mdata[i] < 128) {
        oimg.data[i] = 18;
        oimg.data[i + 1] = 16;
        oimg.data[i + 2] = 14;
      }
    }
    ctx.putImageData(oimg, 0, 0);
  }
  return new Promise<Blob>((resolve, reject) =>
    out.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/jpeg', 0.85)
  );
}

async function confirm() {
  if (busy.value || !cssW) return;
  busy.value = true;
  try {
    if (mode.value === 'crop' && (crop.value.w < 30 || crop.value.h < 30)) {
      uni.showToast({ title: '先拖动角点框选区域', icon: 'none' });
      busy.value = false;
      return;
    }
    const blob = await exportBlob();
    busy.value = false;
    emit('confirm', blob);
  } catch {
    busy.value = false;
    uni.showToast({ title: '处理失败，请重试', icon: 'none' });
  }
}
</script>

<template>
  <view v-if="visible" class="editor-mask">
    <view class="editor-body">
      <view class="editor-title">
        <text class="t-main">选择要识别的区域</text>
        <text class="t-skip" hover-class="press-dim" @tap="emit('skip')">跳过，整图识别</text>
      </view>
      <view class="mode-row">
        <text class="mode-btn" :class="{ on: mode === 'crop' }" hover-class="press-dim" @tap="setMode('crop')">▣ 裁剪选区</text>
        <text class="mode-btn" :class="{ on: mode === 'paint' }" hover-class="press-dim" @tap="setMode('paint')">✎ 涂抹选中</text>
      </view>
      <view :id="WRAP_ID" class="canvas-wrap">
        <view :id="CVS_ID" class="cvs-host">
          <canvas
            class="edit-canvas"
            disable-scroll
            @touchstart="onTouchStart"
            @touchmove.stop.prevent="onTouchMove"
            @touchend="onTouchEnd"
            @touchcancel="onTouchEnd"
          />
        </view>
      </view>
      <view class="hint-row">
        <text v-if="mode === 'crop'" class="hint">拖动角点调整选区，只识别框内内容</text>
        <text v-else class="hint">手指涂抹要识别的位置，其余区域会被遮住</text>
      </view>
      <view class="btn-row">
        <text class="btn ghost" hover-class="press-dim" @tap="emit('cancel')">取消</text>
        <text class="btn ghost" hover-class="press-dim" @tap="reset">重置</text>
        <text class="btn primary" hover-class="press-dim" @tap="confirm">
          {{ busy ? '处理中…' : '确认识别' }}
        </text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.editor-mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  z-index: 1200;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 24rpx;
}
.editor-body {
  width: 100%;
  display: flex; flex-direction: column; align-items: center; gap: 20rpx;
}
.editor-title { display: flex; align-items: baseline; width: 100%; padding: 0 8rpx; }
.t-main { flex: 1; font-size: 30rpx; font-weight: 600; color: #fff; }
.t-skip { font-size: 24rpx; color: v-bind('theme.primaryBtn'); }
.mode-row { display: flex; gap: 16rpx; }
.mode-btn {
  font-size: 26rpx; color: #ddd;
  border: 2rpx solid #666; border-radius: 12rpx;
  padding: 10rpx 28rpx;
}
.mode-btn.on {
  color: #fff; border-color: v-bind('theme.primaryBtn');
  background: rgba(239, 166, 60, 0.18); font-weight: 600;
}
.canvas-wrap {
  width: 100%;
  display: flex; justify-content: center;
  border-radius: 16rpx; overflow: hidden;
}
.cvs-host { line-height: 0; }
.edit-canvas { touch-action: none; }
.hint-row { width: 100%; padding: 0 8rpx; }
.hint { font-size: 22rpx; color: #999; }
.btn-row { display: flex; gap: 20rpx; width: 100%; }
.btn {
  flex: 1; text-align: center;
  font-size: 28rpx; border-radius: 16rpx;
  padding: 18rpx 0;
}
.btn.ghost { color: #ddd; border: 2rpx solid #666; }
.btn.primary { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
</style>
