import { request } from '@/utils/request';

export interface UserView {
  id: number;
  nickname: string;
  avatar: string | null;
  openId: string;
  points: number;
  phoneMasked: string;
  createdAt: string;
}

export interface LoginResult {
  token: string;
  user: UserView;
}

/** 登录态读写：token + 用户信息都落本地存储 */
export function saveLogin(result: LoginResult) {
  uni.setStorageSync('token', result.token);
  uni.setStorageSync('user', JSON.stringify(result.user));
}

export function loadUser(): UserView | null {
  const raw = uni.getStorageSync('user');
  if (!raw) return null;
  try {
    return JSON.parse(raw as string) as UserView;
  } catch {
    return null;
  }
}

export function logout() {
  uni.removeStorageSync('token');
  uni.removeStorageSync('user');
}

export const authApi = {
  sendSmsCode: (phone: string) =>
    request<void>({ url: '/api/auth/sms-code', method: 'POST', data: { phone } }),
  register: (phone: string, smsCode: string) =>
    request<LoginResult>({ url: '/api/auth/register', method: 'POST', data: { phone, smsCode } }),
  login: (phone: string, smsCode: string) =>
    request<LoginResult>({ url: '/api/auth/login', method: 'POST', data: { phone, smsCode } }),
  me: () => request<UserView>({ url: '/api/me' }),
  updateNickname: (nickname: string) =>
    request<UserView>({ url: '/api/me', method: 'PATCH', data: { nickname } }),
};
