import { API_BASE } from './config';
import { request } from '@/utils/request';

export interface DishSpec {
  name: string;
  priceFen: number;
}

export interface DishView {
  id: number;
  kitchenId: number;
  categoryId: number | null;
  categoryName: string | null;
  name: string;
  description: string;
  imageUrl: string | null;
  priceFen: number;
  specsJson: string | null;
  recommendStars: number;
  materials: string;
  steps: string;
  servings: string;
  cookMinutes: number | null;
  difficulty: string;
  calories: string;
  shareSquare: number;
  status: number;
  deleted: number;
  updatedAt: string;
}

export interface CategoryView {
  id: number;
  name: string;
  dishCount: number;
}

export interface DishPayload {
  name: string;
  description?: string;
  imageUrl?: string | null;
  priceFen: number;
  specs?: DishSpec[] | null;
  categoryId?: number | null;
  recommendStars?: number;
  materials?: string;
  steps?: string;
  servings?: string;
  cookMinutes?: number | null;
  difficulty?: string;
  calories?: string;
  shareSquare?: boolean;
}

/** 分 → 元（展示用，两位小数字符串） */
export function fenToYuan(fen: number): string {
  return (fen / 100).toFixed(2);
}

/** 元字符串 → 分（整数，禁止浮点入库） */
export function yuanToFen(yuan: string): number | null {
  const n = Number(yuan);
  if (!yuan || Number.isNaN(n) || n < 0) return null;
  return Math.round(n * 100);
}

/** 相对地址 → 完整图片 URL */
export function fullUrl(url: string | null | undefined): string {
  if (!url) return '';
  return url.startsWith('http') ? url : API_BASE + url;
}

export function parseSpecs(json: string | null): DishSpec[] {
  if (!json) return [];
  try {
    return JSON.parse(json) as DishSpec[];
  } catch {
    return [];
  }
}

/** 选图并上传，返回服务端相对 URL */
export function uploadImage(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      success: (choose) => {
        const token = uni.getStorageSync('token');
        uni.showLoading({ title: '上传中…' });
        uni.uploadFile({
          url: API_BASE + '/api/uploads',
          filePath: choose.tempFilePaths[0],
          name: 'file',
          header: token ? { Authorization: `Bearer ${token}` } : {},
          success: (res) => {
            uni.hideLoading();
            try {
              const body = JSON.parse(res.data as string);
              if (body.code === 0 && body.data?.url) return resolve(body.data.url as string);
              uni.showToast({ title: body.message || '上传失败', icon: 'none' });
              reject(body);
            } catch {
              uni.showToast({ title: '上传失败', icon: 'none' });
              reject(res.data);
            }
          },
          fail: (err) => {
            uni.hideLoading();
            uni.showToast({ title: '上传失败，请检查网络', icon: 'none' });
            reject(err);
          },
        });
      },
      fail: () => reject(new Error('cancel')),
    });
  });
}

export const dishApi = {
  categories: (kitchenId: number) =>
    request<CategoryView[]>({ url: `/api/kitchens/${kitchenId}/categories` }),
  createCategory: (kitchenId: number, name: string) =>
    request<CategoryView>({ url: `/api/kitchens/${kitchenId}/categories`, method: 'POST', data: { name } }),
  deleteCategory: (kitchenId: number, categoryId: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/categories/${categoryId}`, method: 'DELETE' }),

  create: (kitchenId: number, data: DishPayload) =>
    request<DishView>({ url: `/api/kitchens/${kitchenId}/dishes`, method: 'POST', data }),
  list: (kitchenId: number, opts: { categoryId?: number | null; keyword?: string; mode?: 'order' | 'manage' } = {}) => {
    let url = `/api/kitchens/${kitchenId}/dishes?mode=${opts.mode || 'order'}`;
    if (opts.categoryId) url += `&categoryId=${opts.categoryId}`;
    if (opts.keyword) url += `&keyword=${encodeURIComponent(opts.keyword)}`;
    return request<DishView[]>({ url });
  },
  recycleList: (kitchenId: number) =>
    request<DishView[]>({ url: `/api/kitchens/${kitchenId}/recycle` }),
  detail: (id: number) => request<DishView>({ url: `/api/dishes/${id}` }),
  update: (id: number, data: DishPayload) =>
    request<DishView>({ url: `/api/dishes/${id}`, method: 'PUT', data }),
  updateStatus: (id: number, status: 0 | 1) =>
    request<DishView>({ url: `/api/dishes/${id}/status`, method: 'PATCH', data: { status } }),
  recycle: (id: number) => request<void>({ url: `/api/dishes/${id}`, method: 'DELETE' }),
  restore: (id: number) => request<DishView>({ url: `/api/dishes/${id}/restore`, method: 'POST' }),

  /** 广场：分享到广场的菜谱池 */
  squareList: (keyword?: string) =>
    request<DishView[]>({
      url: '/api/square/dishes' + (keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''),
    }),
  /** 克隆广场菜谱到自己厨房 */
  clone: (kitchenId: number, dishId: number) =>
    request<DishView>({
      url: `/api/kitchens/${kitchenId}/dishes/${dishId}/clone`, method: 'POST',
    }),
};
