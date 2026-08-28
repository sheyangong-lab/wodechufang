import { API_BASE } from '@/api/config';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

interface RequestOptions {
  url: string;
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: Record<string, unknown>;
  /** 静默模式：出错不弹 toast，由调用方处理 */
  silent?: boolean;
}

/**
 * 统一请求封装：自动带 token；code=0 才算成功；
 * 401 清登录态并跳登录页。
 */
export function request<T>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token');
    uni.request({
      url: API_BASE + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: token ? { Authorization: `Bearer ${token}` } : {},
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
          return resolve(body.data);
        }
        if (!options.silent) {
          uni.showToast({ title: body?.message || '请求失败', icon: 'none' });
        }
        reject(body);
      },
      fail: (err) => {
        if (!options.silent) {
          uni.showToast({ title: '网络异常，请检查 api/config.ts 的后端地址', icon: 'none' });
        }
        reject(err);
      },
    });
  });
}
