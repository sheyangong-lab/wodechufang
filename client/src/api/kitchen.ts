import { request } from '@/utils/request';

export interface KitchenView {
  id: number;
  name: string;
  code: string;
  level: number;
  announcement: string;
  memberCount: number;
  myRole: 'OWNER' | 'MEMBER' | 'CUSTOMER';
  ownerNickname: string;
  dishQuota: number;
  categoryQuota: number;
  vipExpireAt: string | null;
}

export interface MemberView {
  userId: number;
  nickname: string;
  role: string;
  joinedAt: string;
}

export interface KitchenDetail {
  kitchen: KitchenView;
  members: MemberView[];
}

export const ROLE_LABELS: Record<string, string> = {
  OWNER: '店长',
  MEMBER: '成员',
  CUSTOMER: '顾客',
};

/** 当前厨房 id 的本地持久化（切换厨房 = 改这里） */
export function getCurrentKitchenId(): number | null {
  const v = uni.getStorageSync('kitchenId');
  return v ? Number(v) : null;
}

export function setCurrentKitchen(k: KitchenView) {
  uni.setStorageSync('kitchenId', k.id);
  uni.setStorageSync('kitchenCache', JSON.stringify(k));
}

export function clearCurrentKitchen() {
  uni.removeStorageSync('kitchenId');
  uni.removeStorageSync('kitchenCache');
}

export function loadKitchenCache(): KitchenView | null {
  const raw = uni.getStorageSync('kitchenCache');
  if (!raw) return null;
  try {
    return JSON.parse(raw as string) as KitchenView;
  } catch {
    return null;
  }
}

export const kitchenApi = {
  create: (name: string) =>
    request<KitchenView>({ url: '/api/kitchens', method: 'POST', data: { name } }),
  join: (code: string) =>
    request<KitchenView>({ url: '/api/kitchens/join', method: 'POST', data: { code } }),
  mine: () => request<KitchenView[]>({ url: '/api/kitchens/mine' }),
  detail: (id: number, silent = false) =>
    request<KitchenDetail>({ url: `/api/kitchens/${id}`, silent }),
};
