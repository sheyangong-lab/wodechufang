<script setup lang="ts">
import { theme } from '@/styles/theme';

export interface SheetItem {
  key: string;
  title: string;
  desc?: string;
  badge?: string;
  vip?: boolean;
}

defineProps<{
  visible: boolean;
  items: SheetItem[];
  cancelText?: string;
}>();

const emit = defineEmits<{
  (e: 'select', key: string): void;
  (e: 'close'): void;
}>();
</script>

<template>
  <view v-if="visible" class="mask" @tap="emit('close')">
    <view class="sheet" @tap.stop>
      <view
        v-for="it in items"
        :key="it.key"
        class="item"
        hover-class="press-dim"
        @tap="emit('select', it.key)"
      >
        <view class="title-row">
          <text class="item-title">{{ it.title }}</text>
          <text v-if="it.badge" class="badge" :class="{ 'badge-vip': it.vip }">{{ it.badge }}</text>
        </view>
        <text v-if="it.desc" class="item-desc">{{ it.desc }}</text>
      </view>
      <view class="gap" />
      <view class="cancel" hover-class="press-dim" @tap="emit('close')">{{ cancelText || '取消' }}</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(40, 32, 16, 0.5);
  z-index: 999;
  display: flex; flex-direction: column; justify-content: flex-end;
  animation: fadeIn 0.2s ease both;
}
.sheet {
  background: #fdfaf3;
  border-radius: 32rpx 32rpx 0 0;
  padding: 16rpx 0 calc(24rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.25s ease both;
}
.item { padding: 30rpx 40rpx; display: flex; flex-direction: column; align-items: center; }
.title-row { display: flex; align-items: center; gap: 12rpx; }
.item-title { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.item-desc { font-size: 24rpx; color: v-bind('theme.sub'); margin-top: 8rpx; }
.badge {
  font-size: 20rpx; color: #fff;
  background: v-bind('theme.primaryBtn');
  border-radius: 8rpx; padding: 2rpx 12rpx;
}
.badge-vip {
  color: #6b5312;
  background: #eccf8e;
}
.gap { height: 14rpx; background: #f0e9da; }
.cancel {
  padding: 30rpx 0; text-align: center;
  font-size: 32rpx; color: v-bind('theme.sub');
}
@keyframes slideUp {
  from { transform: translateY(40%); opacity: 0.5; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
