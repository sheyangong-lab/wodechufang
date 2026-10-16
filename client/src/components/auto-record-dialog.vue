<script setup lang="ts">
/**
 * 半自动记账核对弹窗（全局，挂 App.vue）：
 * 原生悬浮球截屏 → 服务端 GLM 识别 → 本弹窗核对（名目/金额可改、可删行）→ 确认入账。
 * 流程由 App.vue 的 AutoRecordManager 驱动：show(items, screenshotB64)。
 */
import { theme } from '@/styles/theme';
import { computed, ref } from 'vue';

interface Row {
  name: string;
  amount: string;
}

const props = defineProps<{ visible: boolean; recognizing: boolean }>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'confirm', rows: { name: string; amountFen: number }[]): void;
}>();

const rows = ref<Row[]>([]);
const shotUrl = ref('');

function open(items: { name: string; amountFen: number }[], screenshotB64?: string) {
  rows.value = items.length
    ? items.map((i) => ({ name: i.name, amount: (i.amountFen / 100).toFixed(2) }))
    : [{ name: '', amount: '' }];
  shotUrl.value = screenshotB64 ? `data:image/jpeg;base64,${screenshotB64}` : '';
}

defineExpose({ open });

const totalFen = computed(() =>
  rows.value.reduce((sum, r) => {
    const fen = Math.round(parseFloat(r.amount || '0') * 100);
    return Number.isFinite(fen) && fen > 0 ? sum + fen : sum;
  }, 0)
);

function addRow() {
  rows.value.push({ name: '', amount: '' });
}

function removeRow(i: number) {
  rows.value.splice(i, 1);
}

function confirm() {
  const out: { name: string; amountFen: number }[] = [];
  for (const r of rows.value) {
    const fen = Math.round(parseFloat(r.amount || '0') * 100);
    if (!r.name.trim()) continue;
    if (!Number.isFinite(fen) || fen <= 0) continue;
    out.push({ name: r.name.trim(), amountFen: fen });
  }
  if (out.length === 0) {
    uni.showToast({ title: '至少保留一项有效明细', icon: 'none' });
    return;
  }
  emit('confirm', out);
}
</script>

<template>
  <view v-if="visible" class="mask" @tap="emit('close')">
    <view class="panel" @tap.stop>
      <view class="head">
        <text class="title">AI 识别结果 · 请核对</text>
        <text class="close" hover-class="press-dim" @tap="emit('close')">✕</text>
      </view>

      <image v-if="shotUrl" :src="shotUrl" class="shot" mode="widthFix" />

      <view v-if="recognizing" class="loading"><text>识别中…（GLM 视觉模型）</text></view>
      <template v-else>
        <view v-for="(r, i) in rows" :key="i" class="row">
          <input v-model="r.name" class="name" placeholder="名目" placeholder-class="ph" maxlength="20" />
          <input v-model="r.amount" class="amount" type="digit" placeholder="金额" placeholder-class="ph" />
          <text class="del" hover-class="press-dim" @tap="removeRow(i)">✕</text>
        </view>
        <view class="row add-row" hover-class="press-dim" @tap="addRow">
          <text class="add-text">＋ 补一项</text>
        </view>
        <view class="total"><text>合计 ¥{{ (totalFen / 100).toFixed(2) }}</text></view>
        <view class="btns">
          <text class="btn ghost" hover-class="press-dim" @tap="emit('close')">取消</text>
          <text class="btn primary" hover-class="press-dim" @tap="confirm">确认记入账本</text>
        </view>
      </template>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(40, 32, 16, 0.55); z-index: 1500;
  display: flex; flex-direction: column; justify-content: center;
  padding: 32rpx;
}
.panel {
  background: v-bind('theme.card'); border-radius: 24rpx; padding: 28rpx;
  max-height: 76vh; display: flex; flex-direction: column;
}
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.title { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.close { color: v-bind('theme.sub'); font-size: 30rpx; padding: 4rpx 8rpx; }
.shot { width: 100%; border-radius: 14rpx; margin-bottom: 16rpx; max-height: 32vh; }
.loading { padding: 60rpx 0; text-align: center; color: v-bind('theme.sub'); font-size: 26rpx; }
.row { display: flex; align-items: center; gap: 12rpx; margin-bottom: 14rpx; }
.name { flex: 1; background: v-bind('theme.primaryLight'); border-radius: 12rpx; padding: 14rpx 18rpx; font-size: 26rpx; }
.amount { width: 180rpx; background: v-bind('theme.primaryLight'); border-radius: 12rpx; padding: 14rpx 18rpx; font-size: 26rpx; text-align: right; }
.del { color: v-bind('theme.danger'); padding: 8rpx; font-size: 26rpx; }
.add-row { justify-content: center; }
.add-text { color: v-bind('theme.primaryBtn'); font-size: 26rpx; }
.total {
  text-align: right; font-size: 28rpx; font-weight: 700;
  color: v-bind('theme.title'); padding: 12rpx 4rpx;
}
.btns { display: flex; gap: 18rpx; margin-top: 6rpx; }
.btn { flex: 1; text-align: center; border-radius: 16rpx; padding: 20rpx 0; font-size: 28rpx; }
.btn.ghost { color: v-bind('theme.sub'); border: 2rpx solid v-bind('theme.divider'); }
.btn.primary { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
.ph { color: v-bind('theme.sub'); }
</style>
