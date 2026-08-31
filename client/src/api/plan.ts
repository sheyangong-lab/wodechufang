import { request } from '@/utils/request';

export interface PlanItemView {
  id: number;
  slotIndex: number;
  itemType: 'DISH' | 'CUSTOM';
  dishId: number | null;
  name: string;
  imageUrl: string;
  remark: string;
}

export interface PlanDay {
  date: string;
  slots: string[];
  items: PlanItemView[];
}

export const planApi = {
  day: (kitchenId: number, date?: string) =>
    request<PlanDay>({
      url: `/api/kitchens/${kitchenId}/plan${date ? '?date=' + date : ''}`,
    }),
  addItem: (
    kitchenId: number,
    data: {
      date: string; slotIndex: number; itemType: 'DISH' | 'CUSTOM';
      dishId?: number | null; name?: string; imageUrl?: string; remark?: string;
    }
  ) =>
    request<PlanItemView>({
      url: `/api/kitchens/${kitchenId}/plan/items`, method: 'POST', data,
    }),
  removeItem: (kitchenId: number, itemId: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/plan/items/${itemId}`, method: 'DELETE' }),
  slots: (kitchenId: number) =>
    request<string[]>({ url: `/api/kitchens/${kitchenId}/plan/slots` }),
  updateSlots: (kitchenId: number, slots: string[]) =>
    request<string[]>({
      url: `/api/kitchens/${kitchenId}/plan/slots`, method: 'PUT', data: { slots },
    }),
};
