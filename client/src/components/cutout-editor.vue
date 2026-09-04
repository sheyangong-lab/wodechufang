<script setup lang="ts">
/**
 * 手动抠图编辑器（食本贴纸用）：
 * - ▣ 框选抠图：拖选框圈住主体，确认后把选区交给自动主体识别（聚焦更快更准）；
 * - ✎ 涂抹抠图：手指涂抹主体，确认后涂过区域保留、其余透明，自动加白边贴纸（无需 AI）。
 */
import { theme } from '@/styles/theme';
import { computed, onUnmounted, ref, watch } from 'vue';
import { stickerFromMask } from '@/utils/sticker';

const props = defineProps<{ visible: boolean; src: string }>();
const emit = defineEmits<{
  (e: 'confirm', payload: { kind: 'auto' | 'box' | 'paint'; blob: Blob | null }): void;
  (e: 'cancel'): void;
}>();

type Mode = 'auto' | 'box' | 'paint';
const mode = ref<Mode>('box');
const busy = ref(false);
const WRAP_ID = 'cutout-wrap';
const CVS_ID = 'cutout-canvas';

let img: HTMLImageElement | null = null;
let mask: HTMLCanvasElement | null = null; // 显示坐标系蒙版（透明底、白笔=选中）
const strokes: { x: number; y: number }[] = []; // 涂抹点列表（预览+导出共用）
let cssW = 0;
let cssH = 0;
const DPR = Math.min(2, window.devicePixelRatio || 1);

const crop = ref({ x: 0, y: 0, w: 0, h: 0 });
let touchKind: '' | 'new' | 'move' | 'nw' | 'ne' | 'sw' | 'se' = '';
let startX = 0;
let startY = 0;
let cropStart = { x: 0, y: 0, w: 0, h: 0 };

const HANDLE = 22;
const BRUSH = 16;

watch(
  () => props.visible,
  (v) => {
    if (v) {
      mode.value = 'box';
      busy.value = false;
      init().catch(() => emit('cancel'));
    } else {
      destroyCanvas();
    }
  }
);
onUnmounted(destroyCanvas);

function nextFrame(): Promise<void> {
  return new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(() => r())));
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const im = new Image();
    im.onload = () => resolve(im);
    im.onerror = () => reject(new Error('图片加载失败'));
    im.src = src;
  });
}

/**
 * 用 JS 手挂的原生 <canvas>，不用 uni 的 canvas 组件：
 * uni h5 端会在自己的生命周期里把 backing 重置为 min(dpr,2)×CSS 尺寸，
 * 覆盖我们按 devicePixelRatio 设置的 backing——高 DPR 手机上可见内容
 * 只剩绘制区左上角（"上传图片只显示左上角一点点"的根因）。
 * 原生元素完全自控 backing，无人会重置。
 */
let boundCvs: HTMLCanvasElement | null = null;

function canvasEl(): HTMLCanvasElement | null {
  return boundCvs;
}

function mountCanvas(): HTMLCanvasElement {
  destroyCanvas();
  const cvs = document.createElement('canvas');
  cvs.style.touchAction = 'none';
  cvs.style.display = 'block';
  cvs.addEventListener('touchstart', onTouchStart as EventListener, { passive: true });
  cvs.addEventListener('touchmove', onTouchMove as EventListener, { passive: false });
  cvs.addEventListener('touchend', onTouchEnd as EventListener);
  cvs.addEventListener('touchcancel', onTouchEnd as EventListener);
  document.getElementById(CVS_ID)?.appendChild(cvs);
  boundCvs = cvs;
  return cvs;
}

function destroyCanvas() {
  if (!boundCvs) return;
  boundCvs.remove();
  boundCvs = null;
}

async function init() {
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
  const s = Math.min(maxW / img.naturalWidth, maxH / img.naturalHeight, 1);
  cssW = Math.round(img.naturalWidth * s);
  cssH = Math.round(img.naturalHeight * s);
  const cvs = mountCanvas();
  cvs.width = Math.round(cssW * DPR);
  cvs.height = Math.round(cssH * DPR);
  cvs.style.width = cssW + 'px';
  cvs.style.height = cssH + 'px';
  mask = document.createElement('canvas');
  mask.width = cssW;
  mask.height = cssH;
  strokes.length = 0;
  crop.value = { x: cssW * 0.08, y: cssH * 0.08, w: cssW * 0.84, h: cssH * 0.84 };
  redraw();
}

function ctx2d(): CanvasRenderingContext2D | null {
  const cvs = canvasEl();
  return cvs ? cvs.getContext('2d') : null;
}

function canvasPos(e: TouchEvent): { x: number; y: number } {
  const cvs = canvasEl()!;
  const r = cvs.getBoundingClientRect();
  const t = e.touches[0];
  return { x: t.clientX - r.left, y: t.clientY - r.top };
}

function redraw() {
  const ctx = ctx2d();
  if (!ctx || !img) return;
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  ctx.clearRect(0, 0, cssW, cssH);
  ctx.drawImage(img, 0, 0, cssW, cssH);
  if (mode.value === 'box') {
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
  } else if (mask && strokes.length) {
    // 涂抹预览：把笔迹染成半透明红色（在深浅图上都可见）
    const tmp = document.createElement('canvas');
    tmp.width = cssW;
    tmp.height = cssH;
    const tctx = tmp.getContext('2d')!;
    tctx.drawImage(mask, 0, 0);
    tctx.globalCompositeOperation = 'source-in';
    tctx.fillStyle = 'rgba(235, 80, 40, 0.55)';
    tctx.fillRect(0, 0, cssW, cssH);
    ctx.drawImage(tmp, 0, 0);
  }
}

function hitHandle(x: number, y: number): '' | 'new' | 'move' | 'nw' | 'ne' | 'sw' | 'se' {
  const c = crop.value;
  const corners: [number, number, 'nw' | 'ne' | 'sw' | 'se'][] = [
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
  if (mode.value === 'box') {
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

function paintAt(cx: number, cy: number) {
  if (!mask) return;
  strokes.push({ x: cx, y: cy });
  const mctx = mask.getContext('2d')!;
  mctx.fillStyle = '#fff';
  mctx.beginPath();
  mctx.arc(cx, cy, BRUSH, 0, Math.PI * 2);
  mctx.fill();
}

function setMode(m: Mode) {
  mode.value = m;
  if (m === 'box' && cssW) {
    crop.value = { x: cssW * 0.08, y: cssH * 0.08, w: cssW * 0.84, h: cssH * 0.84 };
  } else if (m === 'paint' && mask) {
    mask.getContext('2d')!.clearRect(0, 0, mask.width, mask.height);
    strokes.length = 0;
  }
  redraw();
}

function reset() {
  setMode(mode.value);
}

/** 框选导出：裁剪选区为 JPEG blob（交给自动主体识别聚焦处理） */
async function exportCrop(): Promise<Blob> {
  const c = crop.value;
  const sN = img!.naturalWidth / cssW;
  const out = document.createElement('canvas');
  out.width = Math.max(1, Math.round(c.w * sN));
  out.height = Math.max(1, Math.round(c.h * sN));
  out.getContext('2d')!.drawImage(
    img!,
    c.x * sN, c.y * sN, c.w * sN, c.h * sN,
    0, 0, out.width, out.height
  );
  return new Promise<Blob>((resolve, reject) =>
    out.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/jpeg', 0.9)
  );
}

/** 涂抹导出：涂选区域保留 + 白边贴纸 + 其余透明（PNG，无需 AI） */
async function exportSticker(): Promise<Blob> {
  const sN = img!.naturalWidth / cssW;
  const w = Math.round(cssW * sN);
  const h = Math.round(cssH * sN);
  // 原图与蒙版统一到导出分辨率（蒙版=透明底白笔，白笔 alpha 即选中度）
  const srcC = document.createElement('canvas');
  srcC.width = w;
  srcC.height = h;
  srcC.getContext('2d')!.drawImage(img!, 0, 0, w, h);
  const maskC = document.createElement('canvas');
  maskC.width = w;
  maskC.height = h;
  const mctx = maskC.getContext('2d')!;
  mctx.fillStyle = '#fff';
  mctx.strokeStyle = '#fff';
  mctx.lineWidth = BRUSH * 2 * sN;
  mctx.lineCap = 'round';
  mctx.lineJoin = 'round';
  // 笔迹按线段重画（连续点连线，圆头补点）
  for (let i = 0; i < strokes.length; i++) {
    const p = { x: strokes[i].x * sN, y: strokes[i].y * sN };
    if (i === 0 || (Math.abs(p.x - strokes[i - 1].x * sN) < 0.1 && Math.abs(p.y - strokes[i - 1].y * sN) < 0.1)) {
      mctx.beginPath();
      mctx.arc(p.x, p.y, BRUSH * sN, 0, Math.PI * 2);
      mctx.fill();
    } else {
      mctx.beginPath();
      mctx.moveTo(strokes[i - 1].x * sN, strokes[i - 1].y * sN);
      mctx.lineTo(p.x, p.y);
      mctx.stroke();
    }
  }
  const { blob } = await stickerFromMask(srcC, maskC);
  return blob;
}

async function confirm() {
  if (busy.value || !cssW) return;
  if (mode.value === 'auto') {
    busy.value = true;
    emit('confirm', { kind: 'auto', blob: null as unknown as Blob });
    busy.value = false;
    return;
  }
  busy.value = true;
  try {
    if (mode.value === 'box') {
      if (crop.value.w < 30 || crop.value.h < 30) {
        uni.showToast({ title: '先拖动角点框选主体', icon: 'none' });
        busy.value = false;
        return;
      }
      const blob = await exportCrop();
      busy.value = false;
      emit('confirm', { kind: 'box', blob });
    } else {
      const blob = await exportSticker();
      busy.value = false;
      emit('confirm', { kind: 'paint', blob });
    }
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
        <text class="t-main">手动抠图</text>
      </view>
      <view class="mode-row">
        <text class="mode-btn" :class="{ on: mode === 'auto' }" hover-class="press-dim" @tap="setMode('auto')">✦ 自动识别</text>
        <text class="mode-btn" :class="{ on: mode === 'box' }" hover-class="press-dim" @tap="setMode('box')">▣ 框选抠图</text>
        <text class="mode-btn" :class="{ on: mode === 'paint' }" hover-class="press-dim" @tap="setMode('paint')">✎ 涂抹抠图</text>
      </view>
      <view :id="WRAP_ID" class="canvas-wrap">
        <!-- canvas 由 JS 挂载原生元素（见 mountCanvas 注释），不用 uni canvas 组件 -->
        <view :id="CVS_ID" class="cvs-host" />
      </view>
      <view class="hint-row">
        <text v-if="mode === 'auto'" class="hint">自动识别主体并去除背景</text>
        <text v-else-if="mode === 'box'" class="hint">框住要抠的主体，只识别框内、更快更准</text>
        <text v-else class="hint">把主体涂白，涂到哪里就抠到哪里</text>
      </view>
      <view class="btn-row">
        <text class="btn ghost" hover-class="press-dim" @tap="emit('cancel')">取消</text>
        <text v-if="mode !== 'auto'" class="btn ghost" hover-class="press-dim" @tap="reset">重置</text>
        <text class="btn primary" hover-class="press-dim" @tap="confirm">
          {{ busy ? '处理中…' : mode === 'auto' ? '开始识别' : mode === 'box' ? '框选完成' : '生成贴纸' }}
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
.editor-title { width: 100%; padding: 0 8rpx; }
.t-main { font-size: 30rpx; font-weight: 600; color: #fff; }
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
