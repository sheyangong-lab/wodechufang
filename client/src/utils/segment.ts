/**
 * 端侧主体识别抠图 —— 拍照/选图后在手机本地去除主体以外的背景。
 *
 * 实现：onnxruntime-web (WASM, 单线程 SIMD) + u2netp 显著性分割模型（4.4MB，
 * 随 APK 打包在 /static/models/，推理全程本地，无任何云端调用）。
 * 流程：原图 → 320x320 归一化 → u2netp 推理出显著图 → 阈值+羽化成蒙版 →
 *       原图贴透明蒙版 → 裁剪到主体外接框 → 返回 PNG Blob。
 * 失败（超时/不支持/无模型）由调用方回退为原图。
 */
import * as ort from 'onnxruntime-web';

const ORT_WASM_PATH = '/static/ort/';
const MODEL_URL = '/static/models/u2netp.onnx';
const INPUT_SIZE = 320;
const INFER_TIMEOUT_MS = 20000;

// u2net 训练时的归一化参数（ImageNet mean/std）
const MEAN = [0.485, 0.456, 0.406];
const STD = [0.229, 0.224, 0.225];

let sessionPromise: Promise<ort.InferenceSession> | null = null;

function ensureEnv() {
  ort.env.wasm.wasmPaths = ORT_WASM_PATH;
  ort.env.wasm.numThreads = 1; // WebView 无跨域隔离，禁多线程
  ort.env.logLevel = 'error';
}

function getSession(): Promise<ort.InferenceSession> {
  if (!sessionPromise) {
    ensureEnv();
    sessionPromise = (async () => {
      const buf = await fetch(MODEL_URL).then((r) => {
        if (!r.ok) throw new Error(`模型加载失败 ${r.status}`);
        return r.arrayBuffer();
      });
      return ort.InferenceSession.create(buf, { executionProviders: ['wasm'] });
    })();
    sessionPromise.catch(() => {
      sessionPromise = null; // 失败后允许重试
    });
  }
  return sessionPromise;
}

function loadImage(src: string, timeoutMs = 8000): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    const timer = setTimeout(() => reject(new Error('图片加载超时')), timeoutMs);
    img.onload = () => {
      clearTimeout(timer);
      resolve(img);
    };
    img.onerror = () => {
      clearTimeout(timer);
      reject(new Error('图片解码失败'));
    };
    img.src = src;
  });
}

/** 长边压到 maxSide，减小后续计算量 */
async function downscaleToCanvas(src: string, maxSide: number): Promise<HTMLCanvasElement> {
  const img = await loadImage(src);
  const scale = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
  const w = Math.max(1, Math.round(img.naturalWidth * scale));
  const h = Math.max(1, Math.round(img.naturalHeight * scale));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  canvas.getContext('2d')!.drawImage(img, 0, 0, w, h);
  return canvas;
}

/** 跑 u2netp，返回与输入同尺寸的显著性蒙版画布（灰度=主体概率） */
async function inferMask(source: HTMLCanvasElement): Promise<HTMLCanvasElement> {
  const session = await Promise.race([
    getSession(),
    new Promise<never>((_, rej) =>
      setTimeout(() => rej(new Error('模型初始化超时')), INFER_TIMEOUT_MS)
    ),
  ]);

  // 1. 缩到 320x320 并按 ImageNet 均值方差归一化
  const input = document.createElement('canvas');
  input.width = INPUT_SIZE;
  input.height = INPUT_SIZE;
  const ictx = input.getContext('2d')!;
  ictx.drawImage(source, 0, 0, INPUT_SIZE, INPUT_SIZE);
  const px = ictx.getImageData(0, 0, INPUT_SIZE, INPUT_SIZE).data;
  const tensorData = new Float32Array(3 * INPUT_SIZE * INPUT_SIZE);
  const plane = INPUT_SIZE * INPUT_SIZE;
  for (let i = 0; i < plane; i++) {
    tensorData[i] = (px[i * 4] / 255 - MEAN[0]) / STD[0];
    tensorData[plane + i] = (px[i * 4 + 1] / 255 - MEAN[1]) / STD[1];
    tensorData[plane * 2 + i] = (px[i * 4 + 2] / 255 - MEAN[2]) / STD[2];
  }
  const tensor = new ort.Tensor('float32', tensorData, [1, 3, INPUT_SIZE, INPUT_SIZE]);

  // 2. 推理（输出第一个即为 d1 显著图）
  const feeds: Record<string, ort.Tensor> = {};
  feeds[session.inputNames[0]] = tensor;
  const results = await Promise.race([
    session.run(feeds),
    new Promise<never>((_, rej) =>
      setTimeout(() => rej(new Error('推理超时')), INFER_TIMEOUT_MS)
    ),
  ]);
  const outName = session.outputNames[0];
  const map = results[outName].data as Float32Array;

  // 3. 概率 → 320 灰度画布（对比度拉伸让主体更实、背景更透）
  let max = 0;
  for (let i = 0; i < map.length; i++) if (map[i] > max) max = map[i];
  const norm = max > 0 ? 1 / max : 1;
  const maskCanvas = document.createElement('canvas');
  maskCanvas.width = INPUT_SIZE;
  maskCanvas.height = INPUT_SIZE;
  const mctx = maskCanvas.getContext('2d')!;
  const out = mctx.createImageData(INPUT_SIZE, INPUT_SIZE);
  for (let i = 0; i < map.length; i++) {
    const v = Math.min(255, Math.round(Math.min(1, map[i] * norm * 1.35) * 255));
    out.data[i * 4] = v;
    out.data[i * 4 + 1] = v;
    out.data[i * 4 + 2] = v;
    out.data[i * 4 + 3] = 255;
  }
  mctx.putImageData(out, 0, 0);
  return maskCanvas;
}

export interface SegmentResult {
  blob: Blob;
  width: number;
  height: number;
}

/**
 * 去除背景，返回主体 PNG。
 * @param src 图片 URL（blob:/data:/http: 均可）
 */
export async function removeBackground(src: string): Promise<SegmentResult> {
  const source = await downscaleToCanvas(src, 1280);

  // 蒙版放大到原图尺寸时做轻微羽化，边缘更自然
  const mask = await inferMask(source);
  const scaled = document.createElement('canvas');
  scaled.width = source.width;
  scaled.height = source.height;
  const sctx = scaled.getContext('2d')!;
  sctx.filter = 'blur(2px)';
  sctx.drawImage(mask, 0, 0, scaled.width, scaled.height);
  sctx.filter = 'none';

  // 用蒙版灰度作为透明度，把原图合成为透明背景
  const maskData = sctx.getImageData(0, 0, scaled.width, scaled.height).data;
  const outCanvas = document.createElement('canvas');
  outCanvas.width = scaled.width;
  outCanvas.height = scaled.height;
  const octx = outCanvas.getContext('2d')!;
  octx.drawImage(source, 0, 0);
  const outImg = octx.getImageData(0, 0, scaled.width, scaled.height);
  // 阈值坡道：0.25 以下全透明，0.6 以上不透明，中间线性过渡（羽化）
  for (let i = 0; i < outImg.data.length; i += 4) {
    const v = maskData[i] / 255;
    let a = (v - 0.25) / 0.35;
    a = a < 0 ? 0 : a > 1 ? 1 : a;
    outImg.data[i + 3] = Math.round(a * 255);
  }
  octx.putImageData(outImg, 0, 0);

  // 裁剪到主体外接框（留 4% 边距，且限制非空）
  const sx = scaled.width;
  const sy = scaled.height;
  let minX = sx, minY = sy, maxX = -1, maxY = -1;
  for (let y = 0; y < sy; y++) {
    for (let x = 0; x < sx; x++) {
      if (maskData[(y * sx + x) * 4] > 80) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  let crop = outCanvas;
  if (maxX > minX && maxY > minY) {
    const padX = Math.round((maxX - minX) * 0.04);
    const padY = Math.round((maxY - minY) * 0.04);
    const cx = Math.max(0, minX - padX);
    const cy = Math.max(0, minY - padY);
    const cw = Math.min(sx, maxX + padX + 1) - cx;
    const ch = Math.min(sy, maxY + padY + 1) - cy;
    crop = document.createElement('canvas');
    crop.width = cw;
    crop.height = ch;
    crop.getContext('2d')!.drawImage(outCanvas, cx, cy, cw, ch, 0, 0, cw, ch);
  }

  const blob = await new Promise<Blob>((resolve, reject) => {
    crop.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/png');
  });
  return { blob, width: crop.width, height: crop.height };
}
