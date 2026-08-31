import { getApiBase } from '@/api/config';
import { cacheGet, cacheSet, enqueueOp, newOpId } from '@/utils/offline';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

interface RequestOptions {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  data?: object;
  /** 静默模式：出错不弹 toast，由调用方处理 */
  silent?: boolean;
  /** 离线队列回放时携带的操作幂等 ID */
  opId?: string;
}

/**
 * 统一请求封装：自动带 token；code=0 才算成功；
 * 401 清登录态并跳登录页。
 */
/** 离线队列展示用：从路径推断操作描述 */
function describeOp(url: string, method: string): string {
  const m: Record<string, string> = { POST: '新增', PUT: '修改', DELETE: '删除' };
  const seg = url.split('?')[0].split('/').filter(Boolean);
  const what = seg[2] === 'kitchens' ? seg[4] : seg[2];
  return `${m[method]}${what || ''}`;
}

export function request<T>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token');
    const method = (options.method || 'GET').toUpperCase();
    uni.request({
      url: getApiBase() + options.url,
      // uni.request 类型定义未收录 PATCH，但 H5/App 端运行时透传可用
      method: method as any,
      data: options.data,
      header: token
        ? { Authorization: `Bearer ${token}`, ...(options.opId ? { 'X-Op-Id': options.opId } : {}) }
        : options.opId ? { 'X-Op-Id': options.opId } : {},
      success: (res) => {
        if (res.statusCode === 401) {
          uni.removeStorageSync('token');
          uni.removeStorageSync('user');
          if (!options.silent) {
            uni.showToast({ title: '请先登录', icon: 'none' });
            setTimeout(() => uni.navigateTo({ url: '/pages/login/login' }), 500);
          }
          return reject(res.data);
        }
        const body = res.data as ApiResponse<T>;
        if (body && body.code === 0) {
          if (method === 'GET') cacheSet(options.url, body.data);
          return resolve(body.data);
        }
        if (!options.silent) {
          uni.showToast({ title: body?.message || '请求失败', icon: 'none' });
        }
        (body as { statusCode?: number }).statusCode = res.statusCode;
        reject(body);
      },
      fail: (err) => {
        // 网络失败：GET 回退缓存；写操作入离线队列（联网后自动回放）
        if (method === 'GET') {
          const cached = cacheGet(options.url);
          if (cached !== null) {
            return resolve(cached as T);
          }
        } else if (method === 'POST' || method === 'PUT' || method === 'DELETE') {
          enqueueOp({
            opId: options.opId || newOpId(),
            ts: Date.now(),
            method: method as 'POST' | 'PUT' | 'DELETE',
            url: options.url,
            body: options.data as Record<string, unknown> | undefined,
            desc: describeOp(options.url, method),
          });
          if (!options.silent) {
            uni.showToast({ title: '当前离线，操作已记录，联网后自动同步', icon: 'none' });
          }
          return resolve(null as T);
        }
        if (!options.silent) {
          uni.showToast({ title: '网络异常，请检查 api/config.ts 的后端地址', icon: 'none' });
        }
        reject(err);
      },
    });
  });
}
