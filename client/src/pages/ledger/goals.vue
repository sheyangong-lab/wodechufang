<script setup lang="ts">
import { theme } from '@/styles/theme';
import { ledgerApi, fen } from '@/api/ledger';
import type { SavingGoalView } from '@/api/ledger';
import { yuanToFen } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const goals = ref<SavingGoalView[]>([]);
const savingForm = ref(false);

onShow(async () => {
  kitchenId.value = await ensureKitchenId();
  load();
});

async function load() {
  if (!kitchenId.value) return;
  try {
    goals.value = await ledgerApi.savingGoals(kitchenId.value);
  } catch {
    // toast 已统一弹出
  }
}

function remove(g: SavingGoalView) {
  uni.showModal({
    title: '删除存款目标',
    content: `删除「${g.name}」？只删目标，存款账户余额不受影响。`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      ledgerApi.removeSavingGoal(kitchenId.value!, g.id).then(() => {
        uni.showToast({ title: '已删除', icon: 'none' });
        load();
      });
    },
  });
}

// ===== 新建弹层：名称 / 目标额 / 截止日（选填） =====
const sheetVisible = ref(false);
const dateCalVisible = ref(false);
const form = ref({ name: '', target: '', deadline: '' });

function openCreate() {
  form.value = { name: '', target: '', deadline: '' };
  sheetVisible.value = true;
}

function onDatePick(v: string | null) {
  dateCalVisible.value = false;
  if (v) form.value.deadline = v;
}

function clearDeadline() {
  form.value.deadline = '';
}

function submit() {
  if (!kitchenId.value || savingForm.value) return;
  const name = form.value.name.trim();
  if (!name) {
    uni.showToast({ title: '请填写目标名称', icon: 'none' });
    return;
  }
  const targetFen = yuanToFen(form.value.target.trim()) ?? 0;
  if (targetFen <= 0) {
    uni.showToast({ title: '请输入正确的目标金额', icon: 'none' });
    return;
  }
  savingForm.value = true;
  ledgerApi
    .addSavingGoal(kitchenId.value, {
      name,
      targetFen,
      deadline: form.value.deadline || undefined,
    })
    .then(() => {
      sheetVisible.value = false;
      uni.showToast({ title: '已创建', icon: 'none' });
      load();
    })
    .finally(() => (savingForm.value = false));
}
</script>

<template>
  <view class="page">
    <view v-if="goals.length === 0" class="card empty">
      <text class="empty-tip">还没有存款目标，定一个一起攒钱吧</text>
    </view>

    <view v-for="g in goals" :key="g.id" class="card goal">
      <view class="g-head">
        <text class="g-name">{{ g.name }}</text>
        <text class="g-del" hover-class="press-dim" @tap="remove(g)">删除</text>
      </view>
      <view class="g-pct-row">
        <text class="g-pct" :class="{ done: g.pct >= 100 }">{{ Math.min(999, g.pct) }}%</text>
        <text v-if="g.deadline" class="g-deadline">截止 {{ g.deadline }}</text>
      </view>
      <view class="g-bar">
        <view class="g-fill" :style="{ width: Math.min(100, g.pct) + '%' }" />
      </view>
      <view class="g-nums">
        <text class="g-progress">已存 ¥{{ fen(g.progressFen) }}</text>
        <text class="g-target">目标 ¥{{ fen(g.targetFen) }}</text>
      </view>
    </view>

    <button class="btn-add" hover-class="press-sink" @tap="openCreate">＋ 新建存款目标</button>
    <text class="page-tip">进度 = 全部存款账户余额合计，存钱后自动增长。</text>

    <!-- 新建弹层 -->
    <view v-if="sheetVisible" class="mask" @tap="sheetVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">新建存款目标</text>
        <view class="f-row">
          <text class="f-label">名称</text>
          <input v-model="form.name" class="f-input" maxlength="15" placeholder="如：旅行基金" placeholder-class="ph" />
        </view>
        <view class="f-row">
          <text class="f-label">目标金额</text>
          <input v-model="form.target" class="f-input" type="digit" placeholder="0.00" placeholder-class="ph" />
          <text class="f-unit">元</text>
        </view>
        <view class="f-row" hover-class="press-dim" @tap="dateCalVisible = true">
          <text class="f-label">截止日期</text>
          <text class="f-value">{{ form.deadline || '选填' }}</text>
          <text v-if="form.deadline" class="f-clear" hover-class="press-dim" @tap.stop="clearDeadline">清除</text>
          <text class="chev">›</text>
        </view>
        <view class="sheet-btns">
          <text class="s-btn ok" :class="{ disabled: savingForm }" hover-class="press-dim" @tap="submit">保存</text>
        </view>
      </view>
    </view>

    <CalendarPicker
      :visible="dateCalVisible"
      :selected="form.deadline || undefined"
      @select="onDatePick"
      @close="dateCalVisible = false"
    />
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
}
.empty { padding: 90rpx 32rpx; text-align: center; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }

.goal { padding: 28rpx 32rpx; margin-bottom: 24rpx; }
.g-head { display: flex; align-items: center; justify-content: space-between; }
.g-name { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.g-del { font-size: 26rpx; color: v-bind('theme.danger'); }
.g-pct-row {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-top: 18rpx;
}
.g-pct { font-size: 48rpx; font-weight: 700; color: v-bind('theme.primaryBtn'); }
.g-pct.done { color: v-bind('theme.expense'); }
.g-deadline { font-size: 22rpx; color: v-bind('theme.sub'); }
.g-bar {
  height: 16rpx; border-radius: 10rpx;
  background: v-bind('theme.chipBg'); overflow: hidden;
  margin-top: 14rpx;
}
.g-fill {
  height: 100%; border-radius: 10rpx;
  background: v-bind('theme.primaryBtn');
  transition: width 0.3s;
}
.g-nums {
  display: flex; justify-content: space-between;
  margin-top: 14rpx;
}
.g-progress { font-size: 24rpx; color: v-bind('theme.title'); }
.g-target { font-size: 24rpx; color: v-bind('theme.sub'); }

.btn-add {
  margin-top: 8rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-add::after { border: none; }
.page-tip {
  display: block; text-align: center; margin-top: 24rpx;
  font-size: 24rpx; color: v-bind('theme.sub');
}

/* 底部弹层表单 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(20, 16, 8, 0.55);
  z-index: 999;
  display: flex; flex-direction: column; justify-content: flex-end;
}
.sheet {
  background: v-bind('theme.card');
  border-radius: 32rpx 32rpx 0 0;
  padding: 36rpx 40rpx calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title {
  display: block; text-align: center;
  font-size: 32rpx; font-weight: 600; color: v-bind('theme.title');
}
.f-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.f-label { width: 180rpx; font-size: 28rpx; color: v-bind('theme.title'); flex-shrink: 0; }
.f-input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.f-value { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); text-align: right; }
.f-unit { font-size: 24rpx; color: v-bind('theme.sub'); }
.f-clear { font-size: 24rpx; color: v-bind('theme.sub'); }
.chev { color: v-bind('theme.sub'); }
.ph { color: v-bind('theme.sub'); }
.sheet-btns { display: flex; align-items: center; margin-top: 24rpx; }
.s-btn {
  flex: 1; text-align: center; padding: 22rpx 0;
  border-radius: 16rpx; font-size: 30rpx;
}
.s-btn.ok { background: v-bind('theme.primaryBtn'); color: #fff; font-weight: 600; }
.s-btn.ok.disabled { opacity: 0.6; }
</style>
