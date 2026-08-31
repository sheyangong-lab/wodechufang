<script setup lang="ts">
import { theme } from '@/styles/theme';
import { onShow, onHide } from '@dcloudio/uni-app';
import { computed, onUnmounted, ref } from 'vue';
import {
  addOps, getPaired, getQueue, getSelf, isPaired, pairPeer, replaceQueue,
  unpairPeer, queueSize, flushQueue,
} from '@/utils/offline';
import { computeMerge, applyResolution, type Op, type MergeConflict } from '@/utils/sync-core';

const TCP_PORT = 51820;

// ---------- 原生桥（仅 App 内可用） ----------
// eslint-disable-next-line
const Bridge = (): any => (globalThis as Record<string, any>).Capacitor?.Plugins?.SyncBridge;
const isNative = computed(() => !!(globalThis as Record<string, any>).Capacitor?.isNativePlatform?.());

const self = getSelf();
const localIps = ref<string[]>([]);
const listening = ref(false);
const announcing = ref(false);
const pendingCount = ref(queueSize());
const paired = ref(getPaired());

interface LivePeer {
  deviceId: string;
  deviceName: string;
  kitchenId: string;
  ip: string;
  tcp: number;
  lastSeen: number;
}
const peers = ref<LivePeer[]>([]);
const pairingWith = ref<string>('');

// 同步状态机：idle / syncing / conflict / done
const phase = ref<'idle' | 'syncing' | 'conflict' | 'done'>('idle');
const syncLog = ref<string[]>([]);
const conflicts = ref<MergeConflict[]>([]);
const choices = ref<Record<string, 'local' | 'remote'>>({});
const lastResult = ref<{ auto: number; deduped: number; conflicts: number } | null>(null);

const currentPeer = ref<LivePeer | null>(null);
let handles: { remove: () => void }[] = [];
let msgSeq = Date.now();

function log(line: string) {
  syncLog.value.push(`${new Date().toLocaleTimeString()} ${line}`);
  if (syncLog.value.length > 30) syncLog.value.shift();
}

// ---------- 生命周期 ----------

onShow(async () => {
  pendingCount.value = queueSize();
  paired.value = getPaired();
  if (!isNative.value) return;
  const bridge = Bridge();
  if (!bridge) return;
  try {
    const info = await bridge.localIps();
    localIps.value = info.ips || [];
    if (!self.deviceName || self.deviceName === '我的设备') {
      // 默认设备名用机型
      if (info.model) {
        self.deviceName = info.model;
        const { setDeviceName } = await import('@/utils/offline');
        setDeviceName(info.model);
      }
    }
  } catch { /* 忽略 */ }

  const h1 = await bridge.addListener('peerFound', (e: LivePeer) => {
    const exist = peers.value.find((p) => p.deviceId === e.deviceId);
    if (exist) {
      exist.lastSeen = Date.now();
      exist.ip = e.ip;
    } else {
      peers.value.push({ ...e, lastSeen: Date.now() });
      log(`发现设备「${e.deviceName}」`);
      // 已配对设备出现 → 自动连接同步
      if (isPaired(e.deviceId) && phase.value === 'idle') {
        log(`「${e.deviceName}」是已配对设备，自动连接…`);
        setTimeout(() => startSync(e, true), 600);
      }
    }
  });
  const h2 = await bridge.addListener('request', (e: { requestId: string; ip: string; line: string }) => {
    handleRequest(e);
  });
  const h3 = await bridge.addListener('listenError', () => {
    log('本机同步端口启动失败（可能被占用）');
  });
  handles = [h1, h2, h3];

  await bridge.listen();
  listening.value = true;
  await bridge.startAnnounce({
    deviceId: self.deviceId,
    deviceName: self.deviceName,
    kitchenId: String(uni.getStorageSync('kitchenId') || ''),
  });
  announcing.value = true;

  // 过期设备清理
  setInterval(() => {
    const now = Date.now();
    peers.value = peers.value.filter((p) => now - p.lastSeen < 6000);
  }, 3000);
});

onHide(() => stopBridge());
onUnmounted(() => stopBridge());

function stopBridge() {
  const bridge = Bridge();
  if (!bridge) return;
  handles.forEach((h) => h.remove());
  handles = [];
  bridge.stopAnnounce();
  bridge.stopListen();
  announcing.value = false;
  listening.value = false;
}

// ---------- 请求分发（TCP 服务端收到的一行） ----------

async function handleRequest(e: { requestId: string; ip: string; line: string }) {
  let msg: Record<string, any>;
  try {
    msg = JSON.parse(e.line);
  } catch {
    respond(e.requestId, { type: 'BAD_JSON' });
    return;
  }
  const myKitchen = String(uni.getStorageSync('kitchenId') || '');

  if (msg.type === 'PAIR') {
    // 对端发起配对：弹窗确认
    const peer = { deviceId: msg.deviceId, deviceName: msg.deviceName, kitchenId: msg.kitchenId, ip: e.ip, tcp: TCP_PORT };
    const accept = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: '配对请求',
        content: `「${msg.deviceName}」请求与本机配对同步，是否同意？`,
        confirmText: '同意',
        success: (r) => resolve(!!r.confirm),
      });
    });
    if (accept && msg.kitchenId === myKitchen) {
      pairPeer({ deviceId: peer.deviceId, deviceName: peer.deviceName, kitchenId: peer.kitchenId });
      paired.value = getPaired();
    }
    respond(e.requestId, { type: 'PAIR_ACK', accepted: accept && msg.kitchenId === myKitchen, deviceName: self.deviceName });
    return;
  }

  if (msg.type === 'HELLO') {
    // 对端作为发起方：只回自己的队列，合并与冲突核对在发起方做
    respond(e.requestId, {
      type: 'HELLO_ACK',
      deviceId: self.deviceId,
      deviceName: self.deviceName,
      kitchenId: myKitchen,
      ops: getQueue(),
    });
    return;
  }

  if (msg.type === 'APPLY') {
    // 发起方裁决后的最终队列（remoteOps 是对端=本机应采用的队列）
    replaceQueue((msg.yourQueue || []) as Op[]);
    pendingCount.value = queueSize();
    if (msg.pairedIds && msg.pairedIds.length) {
      msg.pairedIds.forEach((id: string) => {
        /* 配对信息已在 PAIR/HELLO 阶段写入 */
      });
    }
    respond(e.requestId, { type: 'APPLY_OK' });
    // 收到新队列后若在线顺手回放服务器
    flushQueue();
    return;
  }

  respond(e.requestId, { type: 'UNKNOWN' });
}

function respond(requestId: string, obj: Record<string, unknown>) {
  Bridge()?.respond({ requestId, line: JSON.stringify(obj) });
}

// ---------- 发起同步 ----------

async function pairTap(peer: LivePeer) {
  if (phase.value === 'syncing') return;
  pairingWith.value = peer.deviceId;
  try {
    const reply = await Bridge().request({
      ip: peer.ip,
      port: peer.tcp,
      line: JSON.stringify({
        type: 'PAIR',
        deviceId: self.deviceId,
        deviceName: self.deviceName,
        kitchenId: String(uni.getStorageSync('kitchenId') || ''),
      }),
      timeoutMs: 20000,
    });
    const ack = JSON.parse(reply.line);
    if (ack.accepted) {
      pairPeer({ deviceId: peer.deviceId, deviceName: peer.deviceName, kitchenId: peer.kitchenId });
      paired.value = getPaired();
      uni.showToast({ title: '已配对', icon: 'none' });
      log(`与「${peer.deviceName}」配对成功`);
      startSync(peer, false);
    } else {
      uni.showToast({ title: '对方拒绝了配对', icon: 'none' });
    }
  } catch (err) {
    uni.showToast({ title: '连接失败：' + (err as Error).message?.slice(0, 30), icon: 'none' });
  } finally {
    pairingWith.value = '';
  }
}

async function startSync(peer: LivePeer, auto: boolean) {
  if (phase.value === 'syncing') return;
  phase.value = 'syncing';
  currentPeer.value = peer;
  conflicts.value = [];
  choices.value = {};
  lastResult.value = null;
  syncLog.value = [];
  log(auto ? `自动连接「${peer.deviceName}」…` : `正在与「${peer.deviceName}」同步…`);
  try {
    const reply = await Bridge().request({
      ip: peer.ip,
      port: peer.tcp,
      line: JSON.stringify({
        type: 'HELLO',
        seq: msgSeq++,
        deviceId: self.deviceId,
        deviceName: self.deviceName,
        kitchenId: String(uni.getStorageSync('kitchenId') || ''),
        ops: getQueue(),
      }),
      timeoutMs: 15000,
    });
    const ack = JSON.parse(reply.line) as {
      type: string; deviceId: string; deviceName: string; kitchenId: string; ops: Op[];
    };
    if (ack.type !== 'HELLO_ACK') throw new Error('协议错误');
    if (ack.kitchenId !== String(uni.getStorageSync('kitchenId') || '')) {
      throw new Error('对方属于不同厨房');
    }
    // 确保双方都记为已配对（自动连接场景下对端可能未入库）
    if (!isPaired(ack.deviceId)) {
      pairPeer({ deviceId: ack.deviceId, deviceName: ack.deviceName, kitchenId: ack.kitchenId });
      paired.value = getPaired();
    }

    const merge = computeMerge(getQueue(), ack.ops || []);
    if (merge.conflicts.length === 0) {
      // 无冲突：直接应用，并把最终队列发给对端
      const localFinal = merge.localOps;
      const remoteFinal = merge.remoteOps;
      applyBoth(peer, localFinal, remoteFinal);
      lastResult.value = { auto: remoteFinal.length, deduped: merge.deduped, conflicts: 0 };
      phase.value = 'done';
      log(`同步完成：合并对端 ${remoteFinal.length} 条操作`);
      finishToast(`已同步（合并 ${remoteFinal.length} 条）`);
    } else {
      // 冲突：逐条核对
      conflicts.value = merge.conflicts;
      merge.conflicts.forEach((c) => (choices.value[c.key] = 'local'));
      phase.value = 'conflict';
      log(`发现 ${merge.conflicts.length} 处双方都改过的内容，请核对`);
    }
  } catch (err) {
    log('同步失败：' + (err as Error).message);
    phase.value = 'idle';
  }
}

async function applyBoth(peer: LivePeer, localFinal: Op[], remoteFinal: Op[]) {
  replaceQueue(localFinal);
  pendingCount.value = queueSize();
  try {
    const reply = await Bridge().request({
      ip: peer.ip,
      port: peer.tcp,
      line: JSON.stringify({ type: 'APPLY', yourQueue: remoteFinal }),
      timeoutMs: 10000,
    });
    JSON.parse(reply.line);
  } catch {
    log('对端应用结果失败（下次同步会重做）');
  }
  flushQueue();
}

async function confirmConflicts() {
  if (!currentPeer.value) return;
  const resolved = applyResolution(
    { localOps: getQueue(), remoteOps: [], conflicts: conflicts.value },
    getQueue(),
    choices.value
  );
  const remoteFinal = resolved.remoteOps;
  const localFinal = resolved.localOps;
  applyBoth(currentPeer.value, localFinal, remoteFinal);
  lastResult.value = { auto: 0, deduped: 0, conflicts: conflicts.value.length };
  conflicts.value = [];
  phase.value = 'done';
  log('冲突核对完成，双方已按选择合并');
  finishToast('冲突已合并');
}

function finishToast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' });
}

// ---------- 冲突展示辅助 ----------

function describeConflict(c: MergeConflict) {
  const seg = c.key.split('/').filter(Boolean);
  const what = seg[2] === 'kitchens' ? seg[4] : seg[2] || '数据';
  return what;
}

function summaryOf(o: Op) {
  const b = (o.body || {}) as Record<string, unknown>;
  const bits: string[] = [];
  Object.keys(b).forEach((k) => {
    if (b[k] !== null && b[k] !== undefined && typeof b[k] !== 'object') bits.push(`${k}:${b[k]}`);
  });
  return `${o.method === 'DELETE' ? '删除' : ''}${bits.join('  ') || '（修改）'}`;
}

// ---------- 配对管理 ----------

function unpair(deviceId: string) {
  unpairPeer(deviceId);
  paired.value = getPaired();
}

const nearbyUnpaired = computed(() => peers.value.filter((p) => !isPaired(p.deviceId)));
const pairedLive = computed(() =>
  peers.value.filter((p) => isPaired(p.deviceId)).map((p) => ({
    ...p,
    name: getPaired().find((x) => x.deviceId === p.deviceId)?.deviceName || p.deviceName,
  }))
);
</script>

<template>
  <view class="page">
    <!-- 环境说明 -->
    <view v-if="!isNative" class="card tip-card">
      <text class="tip-text">设备直连需在手机 App 内使用（浏览器无直连能力）</text>
    </view>

    <!-- 本机 -->
    <view class="card sec">
      <view class="sec-head">
        <text class="sec-title">本机</text>
        <text class="badge" :class="{ ok: pendingCount === 0 }">
          待同步 {{ pendingCount }} 条
        </text>
      </view>
      <text class="line">设备名：{{ self.deviceName }}</text>
      <text class="line">地址：{{ localIps.join(' / ') || '获取中…' }}（端口 {{ 51820 }}）</text>
      <text class="line dim">
        {{ announcing && listening ? '正在广播并监听附近设备…' : isNative ? '启动中…' : '未启动' }}
      </text>
    </view>

    <!-- 已配对（自动连接） -->
    <view class="card sec">
      <view class="sec-head">
        <text class="sec-title">已配对设备（自动连接）</text>
      </view>
      <view v-if="paired.length === 0" class="empty-line"><text class="line dim">还没有配对设备，在下方选择附近设备配对</text></view>
      <view v-for="p in paired" :key="p.deviceId" class="peer-row">
        <view class="peer-info">
          <text class="peer-name">{{ p.deviceName }}</text>
          <text class="peer-sub">上次同步 {{ new Date(p.lastSyncAt).toLocaleString() }}</text>
        </view>
        <text
          v-if="peers.find((x) => x.deviceId === p.deviceId)"
          class="online-dot"
        />在线
        <text class="peer-act" hover-class="press-dim" @tap="unpair(p.deviceId)">移除</text>
      </view>
      <view v-for="p in pairedLive" :key="'live-' + p.deviceId" class="peer-row" hover-class="press-dim" @tap="startSync(p, false)">
        <view class="peer-info">
          <text class="peer-name">{{ p.name }}</text>
          <text class="peer-sub">在线 · 点这里立即同步</text>
        </view>
      </view>
    </view>

    <!-- 附近设备 -->
    <view class="card sec">
      <view class="sec-head">
        <text class="sec-title">附近设备</text>
        <text class="line dim">同一 WiFi / 热点下自动发现</text>
      </view>
      <view v-if="nearbyUnpaired.length === 0" class="empty-line">
        <text class="line dim">正在搜索… 请让对方也打开本页面</text>
      </view>
      <view
        v-for="p in nearbyUnpaired"
        :key="p.deviceId"
        class="peer-row"
        hover-class="press-dim"
        @tap="pairTap(p)"
      >
        <view class="peer-info">
          <text class="peer-name">{{ p.deviceName }}</text>
          <text class="peer-sub">{{ p.ip }}</text>
        </view>
        <text class="peer-act">{{ pairingWith === p.deviceId ? '请求中…' : '配对' }}</text>
      </view>
    </view>

    <!-- 同步进度 / 冲突核对 -->
    <view v-if="phase === 'syncing'" class="card sec">
      <text class="sec-title">同步中…</text>
      <view class="log-box">
        <text v-for="(l, i) in syncLog" :key="i" class="log-line">{{ l }}</text>
      </view>
    </view>

    <view v-if="phase === 'conflict'" class="card sec">
      <view class="sec-head">
        <text class="sec-title">双方都改过这些内容，请核对</text>
      </view>
      <view v-for="c in conflicts" :key="c.key" class="conflict-box">
        <text class="conflict-name">{{ describeConflict(c) }}</text>
        <view
          class="ver"
          :class="{ on: choices[c.key] === 'local' }"
          hover-class="press-dim"
          @tap="choices[c.key] = 'local'"
        >
          <text class="ver-tag">本机</text>
          <text class="ver-body">{{ summaryOf(c.local) }}</text>
        </view>
        <view
          class="ver"
          :class="{ on: choices[c.key] === 'remote' }"
          hover-class="press-dim"
          @tap="choices[c.key] = 'remote'"
        >
          <text class="ver-tag remote">对方</text>
          <text class="ver-body">{{ summaryOf(c.remote) }}</text>
        </view>
      </view>
      <button class="btn-main" hover-class="press-sink" @tap="confirmConflicts">
        应用选择（{{ conflicts.length }} 项）
      </button>
    </view>

    <view v-if="phase === 'done' && lastResult" class="card sec">
      <text class="sec-title">同步完成</text>
      <text class="line">自动合并 {{ lastResult.auto }} 条 · 重复跳过 {{ lastResult.deduped }} 条 · 核对 {{ lastResult.conflicts }} 条</text>
      <text class="line dim">联网后将自动写回服务器</text>
    </view>

    <!-- 日志 -->
    <view v-if="syncLog.length && phase !== 'syncing'" class="card sec">
      <view class="log-box">
        <text v-for="(l, i) in syncLog" :key="i" class="log-line">{{ l }}</text>
      </view>
    </view>

    <view class="card sec">
      <text class="line dim">说明：两台设备连同一个 WiFi（或一方开热点）。离线时的操作会先记录在本机，设备直连后互相合并，联网再统一写回服务器；同一内容两边都改过时会像上面那样逐条核对。</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  margin-bottom: 16rpx;
}
.tip-card { padding: 24rpx 32rpx; }
.tip-text { font-size: 26rpx; color: v-bind('theme.warning'); }
.sec { padding: 24rpx 32rpx; }
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.sec-title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.badge {
  font-size: 22rpx; color: #fff; background: v-bind('theme.primaryBtn');
  border-radius: 20rpx; padding: 4rpx 18rpx;
}
.badge.ok { background: v-bind('theme.success'); }
.line { display: block; font-size: 26rpx; color: v-bind('theme.title'); line-height: 42rpx; }
.line.dim { color: v-bind('theme.sub'); font-size: 24rpx; }
.empty-line { padding: 8rpx 0; }
.peer-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 22rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.peer-info { flex: 1; min-width: 0; }
.peer-name { display: block; font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.peer-sub { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.online-dot {
  width: 14rpx; height: 14rpx; border-radius: 50%;
  background: v-bind('theme.success');
}
.peer-act { font-size: 26rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }

.log-box {
  background: v-bind('theme.chipBg'); border-radius: 14rpx;
  padding: 16rpx 20rpx; margin-top: 12rpx;
  max-height: 300rpx; overflow: hidden;
}
.log-line { display: block; font-size: 22rpx; color: v-bind('theme.sub'); line-height: 36rpx; }

.conflict-box {
  border: 2rpx solid v-bind('theme.divider'); border-radius: 16rpx;
  padding: 20rpx; margin-top: 16rpx;
}
.conflict-name { display: block; font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 12rpx; }
.ver {
  display: flex; align-items: center; gap: 16rpx;
  border: 2rpx solid v-bind('theme.divider'); border-radius: 12rpx;
  padding: 16rpx 20rpx; margin-top: 12rpx;
}
.ver.on { border-color: v-bind('theme.primaryBtn'); background: v-bind('theme.primaryLight'); }
.ver-tag {
  font-size: 22rpx; color: #fff; background: v-bind('theme.primaryBtn');
  border-radius: 8rpx; padding: 2rpx 12rpx; flex-shrink: 0;
}
.ver-tag.remote { background: v-bind('theme.rose'); }
.ver-body { flex: 1; font-size: 24rpx; color: v-bind('theme.title'); word-break: break-all; }
.btn-main {
  margin-top: 24rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 84rpx;
}
.btn-main::after { border: none; }
</style>
