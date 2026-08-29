/**
 * 系统通知栏推送 —— 仅 APK/Capacitor 环境生效，浏览器里静默跳过。
 * 场景：食材临期提醒从「站内通知」同步弹到系统消息栏。
 * 以站内通知 id 做幂等去重，避免同一提醒反复弹。
 */
const SHOWN_KEY = 'sysNotifiedIds';

export interface NoticeLike {
  id: number;
  title: string;
  content: string;
  isRead: number;
}

export function inCapacitor(): boolean {
  // #ifdef H5
  const cap = (globalThis as Record<string, any>).Capacitor;
  return !!cap?.isNativePlatform?.();
  // #endif
  // #ifndef H5
  return false;
  // #endif
}

function loadShownIds(): number[] {
  try {
    return JSON.parse((uni.getStorageSync(SHOWN_KEY) as string) || '[]') as number[];
  } catch {
    return [];
  }
}

function saveShownIds(ids: number[]) {
  // 只保留最近 200 条，防止无限膨胀
  uni.setStorageSync(SHOWN_KEY, JSON.stringify(ids.slice(-200)));
}

/** 直接弹一条系统通知 */
export async function pushSystemNotice(title: string, body: string, id: number): Promise<void> {
  if (!inCapacitor()) return;
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');
    let perm = await LocalNotifications.checkPermissions();
    if (perm.display !== 'granted') {
      perm = await LocalNotifications.requestPermissions();
      if (perm.display !== 'granted') return;
    }
    await LocalNotifications.schedule({
      notifications: [
        {
          id: Math.abs(id) % 2147483647,
          title,
          body,
          schedule: { at: new Date(Date.now() + 300) },
        },
      ],
    });
  } catch {
    // 通知失败不影响主流程
  }
}

/** 批量把未读站内通知推到系统栏（已弹过的自动跳过） */
export async function pushUnreadNotices(notices: NoticeLike[]): Promise<number> {
  if (!inCapacitor()) return 0;
  const shown = loadShownIds();
  const pending = notices.filter((n) => !n.isRead && !shown.includes(n.id));
  for (const n of pending) {
    await pushSystemNotice(n.title, n.content, n.id);
    shown.push(n.id);
  }
  if (pending.length > 0) saveShownIds(shown);
  return pending.length;
}
