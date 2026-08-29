/**
 * 选图（拍照/相册）→ 本地抠主体 → 上传，失败自动回退原图。
 * 拍照经 uni.chooseImage 的 capture 能力调起系统相机（无需 WebView 相机权限）。
 *
 * 上传统一走页面内 fetch + FormData（文件名自控为 .png）：
 * - uni.uploadFile 对 blob: URL 会截取 UUID 当文件名，被后端扩展名校验拒绝(400)；
 * - Capacitor 原生层也不支持 blob: 路径。
 */
import { getApiBase } from '@/api/config';
import { removeBackground } from './segment';

async function uploadBlob(blob: Blob, filename = 'photo.png'): Promise<string> {
  const fd = new FormData();
  fd.append('file', blob, filename);
  const token = uni.getStorageSync('token');
  const res = await fetch(getApiBase() + '/api/uploads', {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  });
  const body = await res.json();
  if (body.code === 0 && body.data?.url) return body.data.url as string;
  throw new Error(body.message || '上传失败');
}

/** uni.uploadFile 的 blob 路径不可靠，这里先把临时图取成 Blob 再统一上传 */
async function uploadTempPath(tempPath: string): Promise<string> {
  const res = await fetch(tempPath);
  const blob = await res.blob();
  return uploadBlob(blob, 'photo.jpg');
}

/** 选图：返回临时路径（H5 下为 blob: URL） */
function chooseTempImage(sourceType: ('album' | 'camera')[]): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType,
      success: (res) => resolve(res.tempFilePaths[0]),
      fail: () => reject(new Error('cancel')),
    });
  });
}

export interface SubjectImageResult {
  /** 服务端相对 URL */
  url: string;
  /** 是否成功抠图（false=识别失败回退原图） */
  segmented: boolean;
}

/**
 * 选图并尝试本地抠主体：
 * 1. 调相机/相册选图；2. 本地 u2netp 推理去背景（失败/超时回退原图）；
 * 3. 上传结果图，返回服务端 URL。
 */
export async function chooseSubjectImage(
  sourceType: ('album' | 'camera')[]
): Promise<SubjectImageResult> {
  const tempPath = await chooseTempImage(sourceType);
  uni.showLoading({ title: '识别主体中…', mask: true });
  try {
    const { blob } = await removeBackground(tempPath);
    const url = await uploadBlob(blob, 'subject.png');
    return { url, segmented: true };
  } catch {
    // 抠图失败（超时/不支持/模型缺失）→ 回退原图
    const url = await uploadTempPath(tempPath);
    return { url, segmented: false };
  }
}
