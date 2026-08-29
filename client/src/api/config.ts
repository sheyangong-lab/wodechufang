/**
 * 服务器地址配置（全项目唯一入口）。
 * 支持运行时修改：App 内「我 → 服务器设置」或直接改 DEFAULT_API_BASE 后重新构建。
 * 优先级：用户设置（本地存储）> DEFAULT_API_BASE。
 */
export const DEFAULT_API_BASE = 'http://192.168.1.187:8080';

const STORAGE_KEY = 'apiBase';

/** 获取当前生效的服务器地址（用户设置优先） */
export function getApiBase(): string {
  try {
    const saved = uni.getStorageSync(STORAGE_KEY);
    if (saved && typeof saved === 'string' && saved.startsWith('http')) {
      return saved.replace(/\/+$/, '');
    }
  } catch { /* 读不到就用默认 */ }
  return DEFAULT_API_BASE;
}

/** 保存用户自定义服务器地址（空值恢复默认） */
export function setApiBase(url: string | null) {
  if (!url || !url.trim()) {
    uni.removeStorageSync(STORAGE_KEY);
    return;
  }
  uni.setStorageSync(STORAGE_KEY, url.trim().replace(/\/+$/, ''));
}
