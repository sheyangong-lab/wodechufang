import { request } from '@/utils/request';

export interface HubOverview {
  month: { income: number; refund: number; expense: number; balance: number };
  fridge: { total: number; expiring: number; expiringNames: string[] };
  tonight: { name: string; imageUrl: string } | null;
  basket: { pendingCount: number; pendingNames: string[] };
}

export const hubApi = {
  overview: (kitchenId: number) =>
    request<HubOverview>({ url: `/api/kitchens/${kitchenId}/hub/overview` }),
};
