import { request } from '@/utils/request';

export interface FoodbookItemView {
  id: number;
  imageUrl: string;
  x: number;
  y: number;
  width: number;
  zIndex: number;
}

export const foodbookApi = {
  page: (kitchenId: number, date: string) =>
    request<FoodbookItemView[]>({
      url: `/api/kitchens/${kitchenId}/foodbook?date=${date}`,
    }),
  addAll: (kitchenId: number, date: string, items: {
    imageUrl: string; x: number; y: number; width: number; zIndex: number;
  }[]) =>
    request<FoodbookItemView[]>({
      url: `/api/kitchens/${kitchenId}/foodbook/items?date=${date}`, method: 'POST', data: { items },
    }),
  updatePosition: (kitchenId: number, itemId: number, x: number, y: number) =>
    request<void>({
      url: `/api/kitchens/${kitchenId}/foodbook/items/${itemId}/position`,
      method: 'PUT', data: { x, y },
    }),
  remove: (kitchenId: number, itemId: number) =>
    request<void>({
      url: `/api/kitchens/${kitchenId}/foodbook/items/${itemId}`, method: 'DELETE',
    }),
};
