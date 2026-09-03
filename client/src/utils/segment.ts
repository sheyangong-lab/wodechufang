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
import { dilate } from './sticker';

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
      // 优先 WebGL（GPU 提速数倍），不支持时 ort 自动回退 wasm
      try {
        return await ort.InferenceSession.create(buf, { executionProviders: ['webgl'] });
      } catch {
        return ort.InferenceSession.create(buf, { executionProviders: ['wasm'] });
      }
    })();
    sessionPromise.catch(() => {
      sessionPromise = null; // 失败后允许重试
    });
  }
  return sessionPromise;
}

/** 页面空闲时预热（提前拉模型+建会话），首次抠图不再等待 */
export function warmupSegmentation() {
  getSession().catch(() => {});
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

  // 3. 概率 → 320 灰度画布：
  //    - 用 98 分位归一化（抗离群亮点），不做过度拉伸；
  //    - 低阈值起步（0.10~0.42 渐变），宁多留不误删；
  //    - 再做一次膨胀（max 滤波），把贴边主体完整保住。
  const sample: number[] = [];
  for (let i = 0; i < map.length; i += 7) sample.push(map[i]);
  sample.sort((a, b) => a - b);
  const p98 = sample[Math.floor(sample.length * 0.98)] || 1;
  const norm = p98 > 0.05 ? 1 / p98 : 1;
  const gray = new Uint8ClampedArray(map.length);
  for (let i = 0; i < map.length; i++) {
    const v = Math.min(1, map[i] * norm);
    // smoothstep(0.10, 0.42)：背景(远低于0.1)全透，主体(>0.4)全保留
    let a = (v - 0.1) / 0.32;
    a = a < 0 ? 0 : a > 1 ? 1 : a;
    a = a * a * (3 - 2 * a);
    gray[i] = Math.round(a * 255);
  }
  // 膨胀 r=2（可分离 max 滤波）：找回被阈值吃掉的边缘细节
  const dilated = dilate(gray, INPUT_SIZE, INPUT_SIZE, 2);
  const maskCanvas = document.createElement('canvas');
  maskCanvas.width = INPUT_SIZE;
  maskCanvas.height = INPUT_SIZE;
  const mctx = maskCanvas.getContext('2d')!;
  const out = mctx.createImageData(INPUT_SIZE, INPUT_SIZE);
  for (let i = 0; i < dilated.length; i++) {
    out.data[i * 4] = dilated[i];
    out.data[i * 4 + 1] = dilated[i];
    out.data[i * 4 + 2] = dilated[i];
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
 * @param opts.keepFrame 为 true 时不做"裁剪到主体外接框"，保留输入的完整画幅
 *   （框选模式用：用户框了什么就得到什么，识别不准也不会把结果裁得只剩一角）
 */
export async function removeBackground(
  src: string,
  opts?: { keepFrame?: boolean }
): Promise<SegmentResult> {
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

  // ---- 白色描边（贴纸风）----
  // 抠图边缘总有一圈半透明残边，与其留着杂色，不如整体盖成白描边：
  // 主体 alpha 膨胀出描边区域 → 白色硬边层垫底（1px 柔化锯齿）→ 主体盖上，
  // 半透明残边自然融进白色，观感等同「白色描边」。
  const w = scaled.width;
  const h = scaled.height;
  const alphaArr = new Uint8ClampedArray(w * h);
  for (let i = 0; i < w * h; i++) alphaArr[i] = outImg.data[i * 4 + 3];
  // 描边宽度随图幅自适应（短边的 1.4%，3~8px）
  const ringR = Math.max(3, Math.round(Math.min(w, h) * 0.014));
  const outlineAlpha = dilate(alphaArr, w, h, ringR);
  const outlineCanvas = document.createElement('canvas');
  outlineCanvas.width = w;
  outlineCanvas.height = h;
  const olctx = outlineCanvas.getContext('2d')!;
  const outlineImg = olctx.createImageData(w, h);
  for (let i = 0; i < w * h; i++) {
    outlineImg.data[i * 4] = 255;
    outlineImg.data[i * 4 + 1] = 255;
    outlineImg.data[i * 4 + 2] = 255;
    // 二值化出硬边贴纸感
    outlineImg.data[i * 4 + 3] = outlineAlpha[i] > 90 ? 255 : 0;
  }
  olctx.putImageData(outlineImg, 0, 0);

  const finalCanvas = document.createElement('canvas');
  finalCanvas.width = w;
  finalCanvas.height = h;
  const fctx = finalCanvas.getContext('2d')!;
  fctx.filter = 'blur(1px)';
  fctx.drawImage(outlineCanvas, 0, 0);
  fctx.filter = 'none';
  fctx.drawImage(outCanvas, 0, 0);

  // 框选模式：保留完整画幅（背景已透明），不再紧贴识别主体二次裁剪
  if (opts?.keepFrame) {
    const blob = await new Promise<Blob>((resolve, reject) => {
      finalCanvas.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/png');
    });
    return { blob, width: w, height: h };
  }

  // 裁剪到主体外接框（含描边，留 8% 边距，细长/贴边主体不顶格）
  const sx = w;
  const sy = h;
  let minX = sx, minY = sy, maxX = -1, maxY = -1;
  for (let y = 0; y < sy; y++) {
    for (let x = 0; x < sx; x++) {
      if (outlineAlpha[y * sx + x] > 10) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  let crop = finalCanvas;
  if (maxX > minX && maxY > minY) {
    const padX = Math.round((maxX - minX) * 0.08);
    const padY = Math.round((maxY - minY) * 0.08);
    const cx = Math.max(0, minX - padX);
    const cy = Math.max(0, minY - padY);
    const cw = Math.min(sx, maxX + padX + 1) - cx;
    const ch = Math.min(sy, maxY + padY + 1) - cy;
    crop = document.createElement('canvas');
    crop.width = cw;
    crop.height = ch;
    crop.getContext('2d')!.drawImage(finalCanvas, cx, cy, cw, ch, 0, 0, cw, ch);
  }

  const blob = await new Promise<Blob>((resolve, reject) => {
    crop.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/png');
  });
  return { blob, width: crop.width, height: crop.height };
}
