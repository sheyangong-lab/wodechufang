<script setup lang="ts">
import { theme } from '@/styles/theme';
import { planApi } from '@/api/plan';
import type { PlanDay, PlanItemView } from '@/api/plan';
import { dishApi, fullUrl, uploadImage } from '@/api/dish';
import type { DishView } from '@/api/dish';
import { ensureKitchenId, getCurrentKitchenId } from '@/api/kitchen';
import { todayStr } from '@/utils/fmt';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const date = ref(todayStr());
const calVisible = ref(false);
const slots = ref<string[]>([]);
const items = ref<PlanItemView[]>([]);
const dishes = ref<DishView[]>([]);
const loading = ref(true);
const planLoaded = ref(false);

// ---------- 加载 ----------

onLoad(async () => {
  kitchenId.value = await ensureKitchenId();
  await load();
});

async function load() {
  if (!kitchenId.value) return;
  loading.value = !planLoaded.value;
  try {
    const day: PlanDay = await planApi.day(kitchenId.value, date.value);
    slots.value = day.slots;
    items.value = day.items;
    planLoaded.value = true;
  } catch {
    // toast 已统一弹出
  } finally {
    loading.value = false;
  }
}

function shiftDate(days: number) {
  const [y, m, d] = date.value.split('-').map(Number);
  const next = new Date(y, m - 1, d + days);
  const pad = (n: number) => String(n).padStart(2, '0');
  date.value = `${next.getFullYear()}-${pad(next.getMonth() + 1)}-${pad(next.getDate())}`;
  load();
}

function onCalPick(d: string | null) {
  calVisible.value = false;
  if (d && d !== date.value) {
    date.value = d;
    load();
  }
}

const dateLabel = computed(() => {
  const today = todayStr();
  if (date.value === today) return '今天';
  const [y, m, d] = today.split('-').map(Number);
  const tomorrow = `${y}-${String(m).padStart(2, '0')}-${String(d + 1).padStart(2, '0')}`;
  if (date.value === tomorrow) return '明天';
  return date.value;
});

// ---------- 添加条目 ----------

const addSheetVisible = ref(false);
const addSlotIndex = ref(0);

function openAdd(slotIndex: number) {
  addSlotIndex.value = slotIndex;
  addSheetVisible.value = true;
}

const addItems = [
  { key: 'dish', title: '添加菜谱', desc: '从厨房菜单里选' },
  { key: 'menu', title: '添加菜单', desc: '上传图片 + 自定义名称和备注' },
];

function onAddTypePick(key: string) {
  addSheetVisible.value = false;
  if (key === 'dish') {
    openDishPicker();
  } else {
    openCustomForm();
  }
}

// 添加菜谱：选厨房菜单
const dishSheetVisible = ref(false);
const dishSheetItems = computed(() =>
  dishes.value.map((d) => ({
    key: String(d.id),
    title: d.name,
    desc: `¥${(d.priceFen / 100).toFixed(2)}${d.materials ? ' · 有用料' : ''}`,
  }))
);

async function openDishPicker() {
  if (dishes.value.length === 0) {
    try {
      dishes.value = await dishApi.list(kitchenId.value!, { mode: 'order' });
    } catch {
      return;
    }
  }
  if (dishes.value.length === 0) {
    uni.showToast({ title: '厨房还没有菜谱', icon: 'none' });
    return;
  }
  dishSheetVisible.value = true;
}

async function onDishPick(key: string) {
  dishSheetVisible.value = false;
  const d = dishes.value.find((x) => String(x.id) === key);
  if (!d) return;
  await planApi.addItem(kitchenId.value!, {
    date: date.value,
    slotIndex: addSlotIndex.value,
    itemType: 'DISH',
    dishId: d.id,
    name: d.name,
    imageUrl: d.imageUrl || '',
  });
  uni.showToast({ title: `「${d.name}」已加入${slots.value[addSlotIndex.value]}`, icon: 'none' });
  load();
}

// 添加菜单（自定义）：传图 + 名称 + 备注
const customFormVisible = ref(false);
const customName = ref('');
const customRemark = ref('');
const customImage = ref('');

function openCustomForm() {
  customName.value = '';
  customRemark.value = '';
  customImage.value = '';
  customFormVisible.value = true;
}

function pickCustomImage() {
  uploadImage()
    .then((url) => (customImage.value = url))
    .catch(() => {});
}

async function saveCustom() {
  if (!customName.value.trim()) {
    uni.showToast({ title: '请输入菜单名称', icon: 'none' });
    return;
  }
  await planApi.addItem(kitchenId.value!, {
    date: date.value,
    slotIndex: addSlotIndex.value,
    itemType: 'CUSTOM',
    name: customName.value.trim(),
    imageUrl: customImage.value,
    remark: customRemark.value.trim(),
  });
  customFormVisible.value = false;
  uni.showToast({ title: '已加入计划', icon: 'none' });
  load();
}

function removeItem(item: PlanItemView) {
  uni.showModal({
    title: '移除',
    content: `把「${item.name}」从这餐移除？`,
    confirmColor: '#E05B4E',
    success: (r) => {
      if (!r.confirm) return;
      planApi.removeItem(kitchenId.value!, item.id).then(load);
    },
  });
}

// ---------- 餐段配置（底部设置） ----------

const configVisible = ref(false);
const editSlots = ref<string[]>([]);

function openConfig() {
  editSlots.value = slots.value.slice();
  configVisible.value = true;
}

function addSlot() {
  if (editSlots.value.length >= 8) {
    uni.showToast({ title: '最多 8 餐', icon: 'none' });
    return;
  }
  editSlots.value.push('');
}

function removeSlot(i: number) {
  editSlots.value.splice(i, 1);
}

async function saveConfig() {
  const cleaned = editSlots.value.map((s) => s.trim()).filter(Boolean);
  if (cleaned.length === 0) {
    uni.showToast({ title: '至少保留一餐', icon: 'none' });
    return;
  }
  try {
    slots.value = await planApi.updateSlots(kitchenId.value!, cleaned);
    configVisible.value = false;
    uni.showToast({ title: '餐段已更新', icon: 'none' });
    load();
  } catch {
    // toast 已统一弹出
  }
}

// ---------- AI 自动规划（预留） ----------

function autoPlan() {
  uni.showToast({ title: 'AI 自动规划：敬请期待', icon: 'none' });
}

// ---------- 按餐段分组 ----------

const grouped = computed(() => {
  return slots.value.map((name, index) => ({
    index,
    name,
    items: items.value.filter((i) => i.slotIndex === index),
  }));
});
</script>

<template>
  <view class="page">
    <!-- 日期切换 -->
    <view class="date-row">
      <text class="arrow" hover-class="press-dim" @tap="shiftDate(-1)">‹</text>
      <view class="date-mid" hover-class="press-dim" @tap="calVisible = true">
        <text class="date-main">{{ dateLabel }}</text>
        <text class="date-sub">{{ date }} ▾</text>
      </view>
      <text class="arrow" hover-class="press-dim" @tap="shiftDate(1)">›</text>
    </view>

    <!-- 各餐段 -->
    <view v-if="loading" class="card tip-card"><text class="line dim">加载中…</text></view>
    <view v-for="g in grouped" :key="g.index" class="card sec">
      <view class="meal-head">
        <text class="meal-name">{{ g.name }}</text>
        <view class="meal-add" hover-class="press-dim" @tap="openAdd(g.index)">＋</view>
      </view>
      <view v-if="g.items.length === 0" class="empty-line">
        <text class="line dim">还没安排，点右上角 ＋ 添加</text>
      </view>
      <view v-for="item in g.items" :key="item.id" class="dish-card" hover-class="press-dim" @longpress="removeItem(item)">
        <image
          v-if="item.imageUrl || item.itemType === 'DISH'"
          class="dish-img"
          :src="fullUrl(item.imageUrl || '')"
          mode="aspectFit"
        />
        <view v-else class="dish-img holder">
          <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
        </view>
        <view class="dish-info">
          <text class="dish-name">{{ item.name }}</text>
          <text v-if="item.remark" class="dish-remark">{{ item.remark }}</text>
          <text v-if="item.itemType === 'CUSTOM'" class="dish-tag">自定义菜单</text>
        </view>
        <text class="dish-del" hover-class="press-dim" @tap.stop="removeItem(item)">✕</text>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <text class="bar-btn" hover-class="press-dim" @tap="openConfig">⚙ 设置</text>
      <text class="bar-btn ai" hover-class="press-dim" @tap="autoPlan">✦ AI 自动规划</text>
    </view>

    <CalendarPicker
      :visible="calVisible"
      :selected="date"
      @select="onCalPick"
      @close="calVisible = false"
    />
    <ActionSheet
      :visible="addSheetVisible"
      :items="addItems"
      @select="onAddTypePick"
      @close="addSheetVisible = false"
    />
    <ActionSheet
      :visible="dishSheetVisible"
      :items="dishSheetItems"
      @select="onDishPick"
      @close="dishSheetVisible = false"
    />

    <!-- 自定义菜单表单 -->
    <view v-if="customFormVisible" class="mask" @tap="customFormVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">添加菜单</text>
        <view class="img-box" hover-class="press-dim" @tap="pickCustomImage">
          <image v-if="customImage" class="img-preview" :src="fullUrl(customImage)" mode="aspectFit" />
          <text v-else class="img-tip">点击上传图片</text>
        </view>
        <view class="form-row">
          <text class="form-label">菜名</text>
          <input v-model="customName" class="form-input" maxlength="30" placeholder="如：周末火锅" placeholder-class="ph" />
        </view>
        <view class="form-row">
          <text class="form-label">备注</text>
          <input v-model="customRemark" class="form-input" maxlength="100" placeholder="选填，如：需要提前备菜" placeholder-class="ph" />
        </view>
        <button class="sheet-btn" hover-class="press-sink" @tap="saveCustom">保存</button>
        <text class="sheet-cancel" hover-class="press-dim" @tap="customFormVisible = false">取消</text>
      </view>
    </view>

    <!-- 餐段配置 -->
    <view v-if="configVisible" class="mask" @tap="configVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">餐段设置</text>
        <text class="sheet-sub">1~8 餐，每餐名称 1~6 个字</text>
        <view v-for="(s, i) in editSlots" :key="i" class="slot-row">
          <input v-model="editSlots[i]" class="slot-input" maxlength="6" placeholder-class="ph" />
          <text class="slot-del" hover-class="press-dim" @tap="removeSlot(i)">删除</text>
        </view>
        <text class="slot-add" hover-class="press-dim" @tap="addSlot">＋ 添加一餐</text>
        <button class="sheet-btn" hover-class="press-sink" @tap="saveConfig">保存</button>
        <text class="sheet-cancel" hover-class="press-dim" @tap="configVisible = false">取消</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 200rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
  margin-bottom: 16rpx;
}
.tip-card { padding: 24rpx 32rpx; }
.line { font-size: 26rpx; color: v-bind('theme.title'); }
.line.dim { color: v-bind('theme.sub'); font-size: 24rpx; }

.date-row {
  display: flex; align-items: center; justify-content: center; gap: 64rpx;
  padding: 8rpx 0 20rpx;
}
.arrow { font-size: 44rpx; color: v-bind('theme.title'); padding: 0 16rpx; line-height: 1; }
.date-mid { display: flex; flex-direction: column; align-items: center; }
.date-main { font-size: 34rpx; font-weight: 700; color: v-bind('theme.title'); }
.date-sub { font-size: 22rpx; color: v-bind('theme.sub'); }

.sec { padding: 24rpx 32rpx; }
.meal-head { display: flex; align-items: center; justify-content: space-between; }
.meal-name { font-size: 30rpx; font-weight: 700; color: v-bind('theme.title'); }
.meal-add {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight'); color: v-bind('theme.primaryBtn');
  font-size: 36rpx; text-align: center; line-height: 52rpx; font-weight: 700;
}
.empty-line { padding: 10rpx 0; }

.dish-card {
  display: flex; align-items: flex-start; gap: 20rpx;
  padding: 18rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.dish-img {
  width: 110rpx; height: 110rpx; border-radius: 14rpx; flex-shrink: 0;
  background: v-bind('theme.primaryLight');
}
.holder { display: flex; align-items: center; justify-content: center; }
.holder-icon { width: 52rpx; height: 52rpx; opacity: 0.5; }
.dish-info { flex: 1; min-width: 0; }
.dish-name { display: block; font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); }
.dish-remark { display: block; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 4rpx; }
.dish-tag {
  display: inline-block; margin-top: 6rpx;
  font-size: 18rpx; color: v-bind('theme.rose');
  border: 2rpx solid v-bind('theme.rose'); border-radius: 6rpx; padding: 0 8rpx;
}
.dish-del { font-size: 26rpx; color: v-bind('theme.sub'); padding: 4rpx 8rpx; }

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx;
  bottom: calc(120rpx + env(safe-area-inset-bottom));
  display: flex; gap: 20rpx;
  background: v-bind('theme.card'); border-radius: 48rpx; padding: 16rpx 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(200, 160, 80, 0.28);
  border: 2rpx solid v-bind('theme.divider');
  z-index: 10;
}
.bar-btn {
  flex: 1; text-align: center;
  font-size: 28rpx; color: v-bind('theme.title'); font-weight: 600;
  padding: 10rpx 0;
}
.bar-btn.ai { color: v-bind('theme.primaryBtn'); }

.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(15, 12, 6, 0.6);
  z-index: 999;
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.2s ease both;
}
.sheet {
  width: 640rpx; max-height: 82vh; overflow-y: auto;
  background: v-bind('theme.card');
  border-radius: 28rpx;
  padding: 40rpx 36rpx 28rpx;
  animation: pop 0.2s ease both;
}
.sheet-title { display: block; text-align: center; font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.sheet-sub { display: block; text-align: center; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 6rpx; margin-bottom: 12rpx; }
.img-box {
  width: 260rpx; height: 260rpx; margin: 20rpx auto 0;
  border: 2rpx dashed v-bind('theme.divider'); border-radius: 16rpx;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}
.img-preview { width: 100%; height: 100%; background: v-bind('theme.primaryLight'); }
.img-tip { font-size: 24rpx; color: v-bind('theme.sub'); }
.form-row {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 24rpx 0;
}
.form-label { font-size: 28rpx; color: v-bind('theme.title'); width: 120rpx; flex-shrink: 0; }
.form-input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); }
.ph { color: v-bind('theme.sub'); }
.sheet-btn {
  margin-top: 30rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 84rpx;
}
.sheet-btn::after { border: none; }
.sheet-cancel { display: block; text-align: center; padding: 20rpx 0 4rpx; font-size: 28rpx; color: v-bind('theme.sub'); }

.slot-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 18rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.slot-input {
  flex: 1; background: v-bind('theme.chipBg');
  border-radius: 12rpx; padding: 14rpx 20rpx;
  font-size: 28rpx; color: v-bind('theme.title');
}
.slot-del { font-size: 26rpx; color: v-bind('theme.danger'); }
.slot-add {
  display: block; text-align: center; padding: 24rpx 0 4rpx;
  font-size: 28rpx; color: v-bind('theme.primaryBtn'); font-weight: 600;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes pop { from { transform: scale(0.94); opacity: 0.6; } to { transform: scale(1); opacity: 1; } }
</style>
