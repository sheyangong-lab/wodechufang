<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ref } from 'vue';

const props = defineProps<{
  visible: boolean;
  title: string;
  placeholder?: string;
  defaultValue?: string;
  type?: 'text' | 'textarea';
  maxlength?: number;
  /** 显示「测试连通」按钮，点击后 emit test 事件 */
  showTest?: boolean;
  /** 测试结果文本（空则不显示） */
  testResult?: string;
  /** 测试中状态 */
  testing?: boolean;
}>();

const emit = defineEmits<{
  (e: 'confirm', value: string): void;
  (e: 'close'): void;
  (e: 'test', value: string): void;
}>();

const value = ref('');

watch(
  () => props.visible,
  (v) => {
    if (v) value.value = props.defaultValue || '';
  }
);

function confirm() {
  const trimmed = value.value.trim();
  if (!trimmed) return;
  emit('confirm', trimmed);
}

function onTest() {
  emit('test', value.value.trim());
}

import { watch } from 'vue';
</script>

<template>
  <view v-if="visible" class="mask" @tap="emit('close')">
    <view class="dialog" @tap.stop>
      <text class="title">{{ title }}</text>
      <textarea
        v-if="type === 'textarea'"
        v-model="value"
        class="area"
        :placeholder="placeholder"
        placeholder-class="ph"
        :maxlength="maxlength || 100"
        :auto-height="true"
      />
      <input
        v-else
        v-model="value"
        class="input"
        :placeholder="placeholder"
        placeholder-class="ph"
        :maxlength="maxlength || 50"
      />
      <view v-if="showTest" class="test-row">
        <text class="test-btn" hover-class="press-dim" @tap="onTest">
          ⚡ 测试连通
        </text>
        <text v-if="testResult" class="test-result">{{ testResult }}</text>
      </view>
      <view class="btns">
        <text class="btn cancel" hover-class="press-dim" @tap="emit('close')">取消</text>
        <view class="btn-divider" />
        <text class="btn ok" :class="{ disabled: !value.trim() }" hover-class="press-dim" @tap="confirm">确定</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(40, 32, 16, 0.5);
  z-index: 999;
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.2s ease both;
}
.dialog {
  width: 600rpx;
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx 0;
  animation: pop 0.2s ease both;
}
.title {
  display: block; text-align: center;
  font-size: 32rpx; font-weight: 600; color: v-bind('theme.title');
}
.input {
  margin-top: 32rpx;
  background: v-bind('theme.primaryLight');
  border: 2rpx solid v-bind('theme.primary');
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx; color: v-bind('theme.title');
}
.area {
  width: 100%; box-sizing: border-box;
  margin-top: 32rpx; min-height: 160rpx;
  background: v-bind('theme.primaryLight');
  border: 2rpx solid v-bind('theme.primary');
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  font-size: 28rpx; color: v-bind('theme.title'); line-height: 44rpx;
}
.ph { color: v-bind('theme.sub'); }
.test-row {
  display: flex; align-items: center; gap: 16rpx;
  margin-top: 20rpx; min-height: 40rpx;
}
.test-btn {
  font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 10rpx;
  padding: 6rpx 20rpx;
}
.test-result {
  font-size: 24rpx; color: v-bind('theme.sub');
  flex: 1;
}
.btns {
  display: flex; align-items: center;
  margin-top: 36rpx;
  border-top: 2rpx solid v-bind('theme.divider');
}
.btn { flex: 1; text-align: center; padding: 26rpx 0; font-size: 30rpx; }
.cancel { color: v-bind('theme.sub'); }
.ok { color: v-bind('theme.primaryBtn'); font-weight: 600; }
.ok.disabled { color: v-bind('theme.sub'); }
.btn-divider { width: 2rpx; height: 40rpx; background: v-bind('theme.divider'); }
@keyframes pop {
  from { transform: scale(0.92); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
