import { request } from '@/utils/request';

export type FridgeState = 'fresh' | 'expiring' | 'expired';

export interface FridgeItemView {
  id: number;
  kitchenId: number;
  categoryId: number | null;
  name: string;
  producedDate: string | null;
  shelfLifeValue: number;
  shelfLifeUnit: 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';
  quantity: string;
  remark: string;
  expireDate: string;
  daysLeft: number;
  state: FridgeState;
}

export interface FridgeCategoryView {
  id: number;
  name: string;
}

export interface FridgeSummary {
  fresh: number;
  expiring: number;
  expired: number;
}

export interface MatchedDish {
  id: number;
  name: string;
  imageUrl: string | null;
  priceFen: number;
  materialLine: string;
}

export interface NotificationView {
  id: number;
  type: string;
  title: string;
  content: string;
  isRead: number;
  createdAt: string;
}

export const UNIT_LABELS: Record<string, string> = {
  DAY: '天',
  WEEK: '周',
  MONTH: '月',
  YEAR: '年',
};

export function expiryText(item: FridgeItemView): string {
  if (item.daysLeft < 0) return `已过期${-item.daysLeft}天`;
  if (item.daysLeft === 0) return '今天过期';
  return `${item.daysLeft}天后过期`;
}

export const fridgeApi = {
  categories: (kitchenId: number) =>
    request<FridgeCategoryView[]>({ url: `/api/kitchens/${kitchenId}/fridge/categories` }),
  createCategory: (kitchenId: number, name: string) =>
    request<FridgeCategoryView>({
      url: `/api/kitchens/${kitchenId}/fridge/categories`, method: 'POST', data: { name },
    }),
  deleteCategory: (kitchenId: number, categoryId: number) =>
    request<void>({
      url: `/api/kitchens/${kitchenId}/fridge/categories/${categoryId}`, method: 'DELETE',
    }),

  createItems: (
    kitchenId: number,
    items: {
      name: string; categoryId?: number | null; producedDate?: string | null;
      shelfLifeValue: number; shelfLifeUnit: string; quantity?: string; remark?: string;
    }[]
  ) =>
    request<FridgeItemView[]>({
      url: `/api/kitchens/${kitchenId}/fridge/items`, method: 'POST', data: { items },
    }),
  list: (kitchenId: number, opts: { state?: string; categoryId?: number | null; keyword?: string } = {}) => {
    let url = `/api/kitchens/${kitchenId}/fridge/items?state=${opts.state || 'all'}`;
    if (opts.categoryId) url += `&categoryId=${opts.categoryId}`;
    if (opts.keyword) url += `&keyword=${encodeURIComponent(opts.keyword)}`;
    return request<FridgeItemView[]>({ url });
  },
  summary: (kitchenId: number) =>
    request<FridgeSummary>({ url: `/api/kitchens/${kitchenId}/fridge/summary` }),
  clear: (kitchenId: number, ids: number[]) =>
    request<number>({ url: `/api/kitchens/${kitchenId}/fridge/clear`, method: 'POST', data: { ids } }),
  match: (kitchenId: number, data: { itemId?: number; ingredient?: string }) =>
    request<MatchedDish[]>({ url: `/api/kitchens/${kitchenId}/fridge/match`, method: 'POST', data }),

  checkExpiry: (kitchenId: number) =>
    request<number>({ url: `/api/kitchens/${kitchenId}/fridge/check-expiry`, method: 'POST' }),
  notifications: (kitchenId: number) =>
    request<NotificationView[]>({ url: `/api/kitchens/${kitchenId}/fridge/notifications` }),
  unreadCount: (kitchenId: number) =>
    request<number>({ url: `/api/kitchens/${kitchenId}/fridge/notifications/unread-count` }),
  markRead: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/fridge/notifications/${id}/read`, method: 'POST' }),
};
