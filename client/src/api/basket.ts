import { request } from '@/utils/request';

export interface BasketItemView {
  id: number;
  name: string;
  quantity: string;
  checked: number;
  source: 'AUTO' | 'MANUAL';
}

export interface GenerateResult {
  added: number;
  matched: number;
  addedItems: BasketItemView[];
}

export const basketApi = {
  list: (kitchenId: number) =>
    request<BasketItemView[]>({ url: `/api/kitchens/${kitchenId}/basket/items` }),
  add: (kitchenId: number, name: string, quantity?: string) =>
    request<BasketItemView>({
      url: `/api/kitchens/${kitchenId}/basket/items`, method: 'POST',
      data: { name, quantity: quantity || '' },
    }),
  toggle: (kitchenId: number, itemId: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/basket/items/${itemId}/toggle`, method: 'POST' }),
  remove: (kitchenId: number, itemId: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/basket/items/${itemId}`, method: 'DELETE' }),
  clearChecked: (kitchenId: number) =>
    request<number>({ url: `/api/kitchens/${kitchenId}/basket/clear-checked`, method: 'POST' }),
  /** 从下单记录生成菜篮（date 默认当天） */
  generate: (kitchenId: number, date?: string) =>
    request<GenerateResult>({
      url: `/api/kitchens/${kitchenId}/basket/generate${date ? '?date=' + date : ''}`,
      method: 'POST',
    }),
};
