<script setup lang="ts">
import { theme } from '@/styles/theme';
import { computed, ref, watch } from 'vue';

const props = defineProps<{
  visible: boolean;
  /** 已选日期 YYYY-MM-DD，空=不限 */
  selected?: string;
}>();

const emit = defineEmits<{
  (e: 'select', date: string | null): void;
  (e: 'close'): void;
}>();

const now = new Date();
const year = ref(now.getFullYear());
const month = ref(now.getMonth() + 1); // 1-12

watch(
  () => props.visible,
  (v) => {
    if (v && props.selected) {
      const [y, m] = props.selected.split('-').map(Number);
      if (y && m) {
        year.value = y;
        month.value = m;
      }
    }
  }
);

const weekHeads = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

interface DayCell {
  day: number;
  date: string;
  inMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
}

const cells = computed<DayCell[]>(() => {
  const first = new Date(year.value, month.value - 1, 1);
  const daysInMonth = new Date(year.value, month.value, 0).getDate();
  // 周一起始：getDay() 0=周日 → 偏移 6
  const startOffset = (first.getDay() + 6) % 7;
  const todayStr = fmt(new Date());
  const list: DayCell[] = [];
  for (let i = 0; i < startOffset; i++) {
    const d = new Date(year.value, month.value - 1, i - startOffset + 1);
    list.push(cell(d.getDate(), false, fmt(d), todayStr));
  }
  for (let d = 1; d <= daysInMonth; d++) {
    const date = fmt(new Date(year.value, month.value - 1, d));
    list.push(cell(d, true, date, todayStr));
  }
  return list;

  function cell(day: number, inMonth: boolean, date: string, todayStr: string): DayCell {
    return {
      day,
      date,
      inMonth,
      isToday: date === todayStr,
      isSelected: !!props.selected && props.selected === date,
    };
  }
});

function fmt(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function prevMonth() {
  if (month.value === 1) {
    year.value -= 1;
    month.value = 12;
  } else {
    month.value -= 1;
  }
}

function nextMonth() {
  if (month.value === 12) {
    year.value += 1;
    month.value = 1;
  } else {
    month.value += 1;
  }
}

function pick(cell: DayCell) {
  emit('select', cell.date);
}

function clear() {
  emit('select', null);
}
</script>

<template>
  <view v-if="visible" class="mask" @tap="emit('close')">
    <view class="panel" @tap.stop>
      <view class="head">
        <text class="title">选择日期</text>
        <text class="close" hover-class="press-dim" @tap="emit('close')">✕</text>
      </view>
      <view class="month-row">
        <text class="arrow" hover-class="press-dim" @tap="prevMonth">‹</text>
        <text class="month">{{ year }}年{{ month }}月</text>
        <text class="arrow" hover-class="press-dim" @tap="nextMonth">›</text>
      </view>
      <view class="week-row">
        <text v-for="w in weekHeads" :key="w" class="week">{{ w }}</text>
      </view>
      <view class="grid">
        <view
          v-for="c in cells"
          :key="c.date"
          class="day"
          :class="{ dim: !c.inMonth, today: c.isToday, selected: c.isSelected }"
          hover-class="press-dim"
          @tap="c.inMonth && pick(c)"
        >
          <text>{{ c.isToday && !c.isSelected ? '今天' : c.day }}</text>
        </view>
      </view>
      <view class="foot">
        <text class="clear" hover-class="press-dim" @tap="clear">不限日期</text>
      </view>
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
.panel {
  background: v-bind('theme.card');
  border-radius: 32rpx 32rpx 0 0;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.25s ease both;
}
.head { display: flex; align-items: center; justify-content: center; position: relative; }
.title { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.close {
  position: absolute; right: 0; top: 0;
  color: v-bind('theme.sub'); font-size: 30rpx; padding: 4rpx 8rpx;
}
.month-row {
  display: flex; align-items: center; justify-content: center; gap: 64rpx;
  padding: 28rpx 0 20rpx;
}
.month { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.arrow { font-size: 40rpx; color: v-bind('theme.title'); padding: 0 16rpx; line-height: 1; }
.week-row { display: flex; margin-bottom: 8rpx; }
.week { flex: 1; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.grid { display: flex; flex-wrap: wrap; }
.day {
  width: 14.28%; padding: 10rpx 0;
  display: flex; align-items: center; justify-content: center;
}
.day text {
  font-size: 28rpx; color: v-bind('theme.title');
  width: 68rpx; height: 68rpx; border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
}
.day.dim text { color: v-bind('theme.sub'); opacity: 0.55; }
.day.today text { text-decoration: underline; text-underline-offset: 6rpx; }
.day.selected text { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
.foot { text-align: center; padding-top: 16rpx; }
.clear { font-size: 26rpx; color: v-bind('theme.primaryBtn'); }
@keyframes slideUp {
  from { transform: translateY(40%); opacity: 0.5; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
