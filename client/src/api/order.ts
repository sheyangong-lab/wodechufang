import { request } from '@/utils/request';

export type OrderStatus = 'PENDING' | 'COMPLETED' | 'REFUND_REQUESTED' | 'REFUNDED';

export interface OrderItemView {
  dishId: number | null;
  dishName: string;
  specName: string | null;
  priceFen: number;
  quantity: number;
}

export interface OrderView {
  id: number;
  kitchenId: number;
  buyerId: number;
  buyerNickname: string;
  status: OrderStatus;
  remark: string;
  totalFen: number;
  dineDate: string;
  createdAt: string;
  items: OrderItemView[];
}

export const STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: '未完成',
  COMPLETED: '已完成',
  REFUND_REQUESTED: '申请退单',
  REFUNDED: '已退单',
};

/** 状态徽标配色（暖色系） */
export const STATUS_COLORS: Record<OrderStatus, { bg: string; text: string }> = {
  PENDING: { bg: '#FDF3DC', text: '#B07B1A' },
  COMPLETED: { bg: '#E8F5E9', text: '#3E7C43' },
  REFUND_REQUESTED: { bg: '#FDECE7', text: '#C2542F' },
  REFUNDED: { bg: '#F0EEE9', text: '#8C7F6A' },
};

export const orderApi = {
  create: (
    kitchenId: number,
    data: {
      remark?: string;
      dineDate?: string;
      items: { dishId: number; specName?: string | null; quantity: number }[];
    }
  ) => request<OrderView>({ url: `/api/kitchens/${kitchenId}/orders`, method: 'POST', data }),
  list: (
    kitchenId: number,
    opts: { role?: 'received' | 'mine'; date?: string; status?: string } = {}
  ) => {
    let url = `/api/kitchens/${kitchenId}/orders?role=${opts.role || 'received'}`;
    if (opts.date) url += `&date=${opts.date}`;
    if (opts.status && opts.status !== 'ALL') url += `&status=${opts.status}`;
    return request<OrderView[]>({ url });
  },
  detail: (id: number) => request<OrderView>({ url: `/api/orders/${id}` }),
  complete: (id: number) => request<OrderView>({ url: `/api/orders/${id}/complete`, method: 'POST' }),
  requestRefund: (id: number) =>
    request<OrderView>({ url: `/api/orders/${id}/refund-request`, method: 'POST' }),
  approveRefund: (id: number) =>
    request<OrderView>({ url: `/api/orders/${id}/refund-approve`, method: 'POST' }),
  rejectRefund: (id: number) =>
    request<OrderView>({ url: `/api/orders/${id}/refund-reject`, method: 'POST' }),
  randomDishes: (kitchenId: number, categoryId?: number | null, count = 1) => {
    let url = `/api/kitchens/${kitchenId}/random-dishes?count=${count}`;
    if (categoryId) url += `&categoryId=${categoryId}`;
    return request<import('./dish').DishView[]>({ url });
  },
};
