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
}

export interface MemberView {
  userId: number;
  nickname: string;
  role: string;
  joinedAt: string;
  /** 自定义名字（空=用昵称） */
  alias: string;
  /** 自定义职称（空=按角色显示） */
  title: string;
  /** 全权限：1=可代主账号管理厨房 */
  fullAccess: number;
}

export interface KitchenDetail {
  kitchen: KitchenView;
  members: MemberView[];
}

export const ROLE_LABELS: Record<string, string> = {
  OWNER: '主账号',
  MEMBER: '成员',
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

/**
 * 子页面冷启动兜底：本地无 kitchenId 时从服务端恢复（换设备/清存储场景）。
 * 所有厨房子页面 onLoad 时都应 await 此函数，而不是直接读 storage。
 */
export async function ensureKitchenId(): Promise<number | null> {
  const local = getCurrentKitchenId();
  if (local) return local;
  try {
    const mine = await kitchenApi.mine();
    if (mine.length > 0) {
      setCurrentKitchen(mine[mine.length - 1]);
      return mine[mine.length - 1].id;
    }
  } catch {
    // 未登录/token 失效：由调用方处理
  }
  return null;
}

export const kitchenApi = {
  create: (name: string) =>
    request<KitchenView>({ url: '/api/kitchens', method: 'POST', data: { name } }),
  join: (code: string) =>
    request<KitchenView>({ url: '/api/kitchens/join', method: 'POST', data: { code } }),
  mine: () => request<KitchenView[]>({ url: '/api/kitchens/mine' }),
  detail: (id: number, silent = false) =>
    request<KitchenDetail>({ url: `/api/kitchens/${id}`, silent }),
  update: (id: number, data: { name?: string; announcement?: string }) =>
    request<KitchenView>({ url: `/api/kitchens/${id}`, method: 'PUT', data }),
  dissolve: (id: number) =>
    request<void>({ url: `/api/kitchens/${id}`, method: 'DELETE' }),
  /** 编辑成员：主账号可改任何成员（含全权限）；成员只能改自己的名字/职称 */
  updateMember: (kitchenId: number, userId: number, data: { alias?: string; title?: string; fullAccess?: number }) =>
    request<MemberView>({
      url: `/api/kitchens/${kitchenId}/members/${userId}`, method: 'PUT', data,
    }),
};
