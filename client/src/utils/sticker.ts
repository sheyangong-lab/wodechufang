/**
 * 涂抹抠图的白边贴纸合成：alpha 蒙版 → 主体保留 + 白描边 + 其余透明 → PNG。
 * 独立纯函数（无 onnxruntime 依赖），编辑器与测试可直接复用。
 */

/** 半径 r 的方形结构元膨胀（先横后纵两趟 max 滤波） */
export function dilate(src: Uint8ClampedArray, w: number, h: number, r: number): Uint8ClampedArray {
  const tmp = new Uint8ClampedArray(src.length);
  for (let y = 0; y < h; y++) {
    const row = y * w;
    for (let x = 0; x < w; x++) {
      let m = 0;
      const x0 = Math.max(0, x - r), x1 = Math.min(w - 1, x + r);
      for (let k = x0; k <= x1; k++) if (src[row + k] > m) m = src[row + k];
      tmp[row + x] = m;
    }
  }
  const out = new Uint8ClampedArray(src.length);
  for (let y = 0; y < h; y++) {
    const y0 = Math.max(0, y - r), y1 = Math.min(h - 1, y + r);
    for (let x = 0; x < w; x++) {
      let m = 0;
      for (let k = y0; k <= y1; k++) if (tmp[k * w + x] > m) m = tmp[k * w + x];
      out[y * w + x] = m;
    }
  }
  return out;
}

export interface StickerResult {
  blob: Blob;
  width: number;
  height: number;
}

/**
 * 依据 alpha 蒙版（透明底、白色笔迹=选中区，与 source 同尺寸）合成白边贴纸：
 * 1. 前景 = 原图 × 蒙版（destination-in，蒙版羽化让边缘柔和）；
 * 2. 白边 = 蒙版 alpha 膨胀出的一圈白色；
 * 3. 白边在下、主体在上，其余全透明，裁剪到主体外接框（留 8% 边距）。
 */
export async function stickerFromMask(
  source: HTMLCanvasElement | HTMLImageElement,
  alphaMask: HTMLCanvasElement
): Promise<StickerResult> {
  const nw = source instanceof HTMLImageElement ? source.naturalWidth : source.width;
  const nh = source instanceof HTMLImageElement ? source.naturalHeight : source.height;
  const mw = alphaMask.width;
  const mh = alphaMask.height;

  // 1. 蒙版轻羽化（透明底白笔，直接可用作 destination-in 的 alpha 蒙版）
  const feather = document.createElement('canvas');
  feather.width = mw;
  feather.height = mh;
  const fctx = feather.getContext('2d')!;
  fctx.filter = 'blur(1.5px)';
  fctx.drawImage(alphaMask, 0, 0);
  fctx.filter = 'none';

  // 2. 读取蒙版 alpha（自绘 canvas，无污染），阈值成主体 alpha
  const mdata = fctx.getImageData(0, 0, mw, mh).data;
  const alpha = new Uint8ClampedArray(mw * mh);
  for (let i = 0; i < mw * mh; i++) alpha[i] = mdata[i * 4 + 3];

  // 3. 前景：原图 × 蒙版
  const fg = document.createElement('canvas');
  fg.width = mw;
  fg.height = mh;
  const gctx = fg.getContext('2d')!;
  gctx.drawImage(source, 0, 0, mw, mh);
  gctx.globalCompositeOperation = 'destination-in';
  gctx.drawImage(feather, 0, 0);
  gctx.globalCompositeOperation = 'source-over';

  // 4. 白边：alpha 膨胀出的白色描边层
  const ringR = Math.max(3, Math.round(Math.min(mw, mh) * 0.014));
  const outline = dilate(alpha, mw, mh, ringR);
  const edge = document.createElement('canvas');
  edge.width = mw;
  edge.height = mh;
  const eimg = edge.getContext('2d')!.createImageData(mw, mh);
  for (let i = 0; i < mw * mh; i++) {
    if (alpha[i] > 0) continue; // 主体区域不需要白边
    const o = outline[i];
    if (o > 110) {
      eimg.data[i * 4] = 255;
      eimg.data[i * 4 + 1] = 255;
      eimg.data[i * 4 + 2] = 255;
      eimg.data[i * 4 + 3] = o > 160 ? 255 : o;
    }
  }
  edge.getContext('2d')!.putImageData(eimg, 0, 0);

  // 5. 合成：白边垫底 + 主体盖上
  const out = document.createElement('canvas');
  out.width = mw;
  out.height = mh;
  const octx = out.getContext('2d')!;
  octx.drawImage(edge, 0, 0);
  octx.drawImage(fg, 0, 0);

  // 6. 裁剪到主体外接框（含白边留 8% 边距）
  const odata = octx.getImageData(0, 0, mw, mh).data;
  let minX = mw, minY = mh, maxX = -1, maxY = -1;
  for (let y = 0; y < mh; y++) {
    for (let x = 0; x < mw; x++) {
      if (odata[(y * mw + x) * 4 + 3] > 10) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
      }
    }
  }
  let crop = out;
  if (maxX > minX && maxY > minY) {
    const padX = Math.round((maxX - minX) * 0.08);
    const padY = Math.round((maxY - minY) * 0.08);
    const cx = Math.max(0, minX - padX);
    const cy = Math.max(0, minY - padY);
    const cw = Math.min(mw, maxX + padX + 1) - cx;
    const ch = Math.min(mh, maxY + padY + 1) - cy;
    crop = document.createElement('canvas');
    crop.width = cw;
    crop.height = ch;
    crop.getContext('2d')!.drawImage(out, cx, cy, cw, ch, 0, 0, cw, ch);
  }
  const blob = await new Promise<Blob>((resolve, reject) =>
    crop.toBlob((b) => (b ? resolve(b) : reject(new Error('导出失败'))), 'image/png')
  );
  return { blob, width: crop.width, height: crop.height };
}
