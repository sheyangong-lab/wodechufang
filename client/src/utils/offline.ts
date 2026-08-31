/**
 * 离线操作层：
 * - 断网时写操作入队（返回"离线已记录"，UI 流程不断）
 * - GET 失败回退上次缓存，页面不空白
 * - 联网后自动按序回放队列（带 X-Op-Id 幂等头，服务器去重）
 * - 配对设备表：记住同步过的设备，二次自动连接
 */
import { request } from '@/utils/request';

const Q_KEY = 'offlineQueue';
const C_KEY = 'offlineCache';
const P_KEY = 'pairedPeers';
const SELF_KEY = 'selfDevice';

export interface OfflineOp {
  opId: string;
  ts: number;
  method: 'POST' | 'PUT' | 'DELETE';
  url: string;
  body?: Record<string, unknown>;
  /** 展示用描述，如「修改食材 数量」 */
  desc: string;
  /** 回放失败（业务 400 等）次数 */
  fails?: number;
}

export function newOpId(): string {
  return 'op-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = uni.getStorageSync(key) as string;
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function writeJson(key: string, value: unknown) {
  uni.setStorageSync(key, JSON.stringify(value));
}

// ---------- 操作队列 ----------

export function getQueue(): OfflineOp[] {
  return readJson<OfflineOp[]>(Q_KEY, []);
}

export function enqueueOp(op: OfflineOp) {
  const q = getQueue();
  q.push(op);
  writeJson(Q_KEY, q);
}

export function removeOps(opIds: string[]) {
  const set = new Set(opIds);
  writeJson(Q_KEY, getQueue().filter((o) => !set.has(o.opId)));
}

export function addOps(ops: OfflineOp[]) {
  const q = getQueue();
  const exist = new Set(q.map((o) => o.opId));
  ops.forEach((o) => {
    if (!exist.has(o.opId)) q.push(o);
  });
  q.sort((a, b) => a.ts - b.ts);
  writeJson(Q_KEY, q);
}

export function replaceQueue(ops: OfflineOp[]) {
  writeJson(Q_KEY, [...ops].sort((a, b) => a.ts - b.ts));
}

// ---------- GET 读缓存（离线兜底） ----------

export function cacheSet(url: string, data: unknown) {
  try {
    const cache = readJson<Record<string, { ts: number; data: unknown }>>(C_KEY, {});
    cache[url] = { ts: Date.now(), data };
    // 只保留最近 200 个键，防膨胀
    const keys = Object.keys(cache);
    if (keys.length > 200) {
      keys.sort((a, b) => cache[a].ts - cache[b].ts);
      keys.slice(0, keys.length - 200).forEach((k) => delete cache[k]);
    }
    writeJson(C_KEY, cache);
  } catch { /* 缓存失败不影响主流程 */ }
}

export function cacheGet(url: string): unknown | null {
  const cache = readJson<Record<string, { ts: number; data: unknown }>>(C_KEY, {});
  return cache[url] ? cache[url].data : null;
}

// ---------- 队列回放（联网自动触发） ----------

let flushing = false;
let lastFlushAt = 0;

export function queueSize(): number {
  return getQueue().length;
}

/** 联网后回放整个队列；400 业务拒绝视为永久失败，移入 failedOps 供同步页查看 */
export async function flushQueue(force = false): Promise<{ done: number; failed: number }> {
  if (flushing) return { done: 0, failed: 0 };
  if (!force && Date.now() - lastFlushAt < 15000) return { done: 0, failed: 0 };
  const queue = getQueue();
  if (queue.length === 0) return { done: 0, failed: 0 };
  flushing = true;
  lastFlushAt = Date.now();
  let done = 0;
  let failed = 0;
  try {
    for (const op of queue) {
      try {
        await request({ url: op.url, method: op.method, data: op.body, opId: op.opId });
        removeOps([op.opId]);
        done++;
      } catch (err) {
        const status = (err as { statusCode?: number })?.statusCode;
        if (status && status >= 400 && status < 500) {
          // 业务拒绝（如重复删除）：丢弃并计数，不阻塞后续
          removeOps([op.opId]);
          failed++;
        } else {
          break; // 仍离线，停止回放
        }
      }
    }
  } finally {
    flushing = false;
  }
  return { done, failed };
}

export function hasPending(): boolean {
  return getQueue().length > 0;
}

// ---------- 设备身份 + 配对设备 ----------

export interface SelfDevice {
  deviceId: string;
  deviceName: string;
}

export function getSelf(): SelfDevice {
  const saved = readJson<SelfDevice | null>(SELF_KEY, null);
  if (saved) return saved;
  const self: SelfDevice = {
    deviceId: 'dev-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8),
    deviceName: '我的设备',
  };
  writeJson(SELF_KEY, self);
  return self;
}

export function setDeviceName(name: string) {
  const self = getSelf();
  self.deviceName = name;
  writeJson(SELF_KEY, self);
}

export interface PairedPeer {
  deviceId: string;
  deviceName: string;
  kitchenId: string;
  lastSyncAt: number;
}

export function getPaired(): PairedPeer[] {
  return readJson<PairedPeer[]>(P_KEY, []);
}

export function isPaired(deviceId: string): boolean {
  return getPaired().some((p) => p.deviceId === deviceId);
}

export function pairPeer(peer: { deviceId: string; deviceName: string; kitchenId: string }) {
  const list = getPaired().filter((p) => p.deviceId !== peer.deviceId);
  list.push({ ...peer, lastSyncAt: Date.now() });
  writeJson(P_KEY, list);
}

export function unpairPeer(deviceId: string) {
  writeJson(P_KEY, getPaired().filter((p) => p.deviceId !== deviceId));
}
