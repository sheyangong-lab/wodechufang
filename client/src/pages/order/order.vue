<script setup lang="ts">
import { theme } from '@/styles/theme';
import { orderApi, STATUS_LABELS, STATUS_COLORS } from '@/api/order';
import type { OrderStatus, OrderView } from '@/api/order';
import { fenToYuan } from '@/api/dish';
import { getCurrentKitchenId, loadKitchenCache } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import CalendarPicker from '@/components/calendar-picker.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const canManage = ref(false);
const tab = ref<'received' | 'mine'>('received');
const orders = ref<OrderView[]>([]);
const loading = ref(true);

// 筛选状态
const statusSheetVisible = ref(false);
const statusFilter = ref<'ALL' | OrderStatus>('ALL');
const calendarVisible = ref(false);
const dateFilter = ref<string>('');

const statusItems = (Object.keys(STATUS_LABELS) as OrderStatus[]).map((k) => ({
  key: k,
  title: STATUS_LABELS[k],
}));

const statusText = computed(() =>
  statusFilter.value === 'ALL' ? '全部状态' : STATUS_LABELS[statusFilter.value]
);
const dateText = computed(() => (dateFilter.value ? dateFilter.value : '不限日期'));
const announcement = computed(() => loadKitchenCache()?.announcement || '');

onShow(() => {
  kitchenId.value = getCurrentKitchenId();
  const cache = loadKitchenCache();
  // 主账号和成员都有订单处理权
  canManage.value = ['OWNER', 'MEMBER'].includes(cache?.myRole || '');
  load();
});

async function load() {
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    orders.value = await orderApi.list(kitchenId.value, {
      role: tab.value,
      date: dateFilter.value || undefined,
      status: statusFilter.value,
    });
  } catch {
    orders.value = [];
  } finally {
    loading.value = false;
  }
}

function setTab(t: 'received' | 'mine') {
  tab.value = t;
  load();
}

function pickStatus(key: string) {
  statusSheetVisible.value = false;
  statusFilter.value = key as 'ALL' | OrderStatus;
  load();
}

function pickDate(date: string | null) {
  calendarVisible.value = false;
  dateFilter.value = date || '';
  load();
}

function summary(o: OrderView) {
  const first = o.items[0];
  const more = o.items.length - 1;
  let text = first ? `${first.dishName}${first.specName ? `(${first.specName})` : ''}×${first.quantity}` : '';
  if (more > 0) text += ` 等${o.items.length}道`;
  return text;
}

function fmtTime(iso: string) {
  // 订单列表只展示时:分，转本地时区
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ----- 状态操作 -----

function act(o: OrderView, action: 'complete' | 'refund-request' | 'refund-approve' | 'refund-reject') {
  const tips: Record<string, string> = {
    complete: '确认这道菜已完成？',
    'refund-request': '确定申请退单？',
    'refund-approve': '确定同意退单？金额将退回。',
    'refund-reject': '确定拒绝该退单申请？',
  };
  uni.showModal({
    title: '提示',
    content: tips[action],
    success: (res) => {
      if (!res.confirm) return;
      const calls: Record<string, () => Promise<OrderView>> = {
        complete: () => orderApi.complete(o.id),
        'refund-request': () => orderApi.requestRefund(o.id),
        'refund-approve': () => orderApi.approveRefund(o.id),
        'refund-reject': () => orderApi.rejectRefund(o.id),
      };
      calls[action]().then(() => {
        uni.showToast({ title: '已操作', icon: 'none' });
        load();
      });
    },
  });
}
</script>

<template>
  <view class="page">
    <!-- 筛选条（对照蓝本截图7） -->
    <view class="filters">
      <text class="filter-btn" hover-class="press-dim" @tap="calendarVisible = true">
        {{ dateText }} ⌄
      </text>
      <text class="filter-btn" hover-class="press-dim" @tap="statusSheetVisible = true">
        {{ statusText }} ⌄
      </text>
    </view>

    <!-- 二级 Tab -->
    <view class="sub-tabs">
      <text class="tab" :class="{ active: tab === 'received' }" hover-class="press-dim" @tap="setTab('received')">厨房订单</text>
      <text class="tab" :class="{ active: tab === 'mine' }" hover-class="press-dim" @tap="setTab('mine')">我下过的单</text>
    </view>

    <!-- 公告栏 -->
    <view class="notice">
      <text class="notice-text">📢 {{ announcement || '暂无公告' }}</text>
    </view>

    <!-- 空状态 -->
    <view v-if="!loading && orders.length === 0" class="card empty">
      <image class="empty-img" src="/static/icons/empty-order.png" mode="aspectFit" />
      <text class="empty-tip">还没有订单哦 ~</text>
    </view>

    <!-- 订单列表 -->
    <view v-for="o in orders" :key="o.id" class="card order-card">
      <view class="head">
        <text class="buyer">{{ tab === 'received' ? o.buyerNickname + ' 的订单' : '订单 #' + o.id }}</text>
        <text
          class="status"
          :style="{ background: STATUS_COLORS[o.status].bg, color: STATUS_COLORS[o.status].text }"
        >{{ STATUS_LABELS[o.status] }}</text>
      </view>
      <text class="summary">{{ summary(o) }}</text>
      <text v-if="o.remark" class="remark">备注：{{ o.remark }}</text>
      <view class="foot">
        <text class="meta">{{ o.dineDate }} {{ fmtTime(o.createdAt) }}</text>
        <text class="total">¥{{ fenToYuan(o.totalFen) }}</text>
      </view>
      <view v-if="o.items.length > 1" class="items-detail">
        <text v-for="(i, idx) in o.items" :key="idx" class="item-line">
          {{ i.dishName }}{{ i.specName ? `(${i.specName})` : '' }} ×{{ i.quantity }} = ¥{{ fenToYuan(i.priceFen * i.quantity) }}
        </text>
      </view>

      <!-- 操作区 -->
      <view class="ops" v-if="tab === 'received' && canManage && (o.status === 'PENDING' || o.status === 'REFUND_REQUESTED')">
        <text
          v-if="o.status === 'REFUND_REQUESTED'"
          class="op"
          hover-class="press-dim"
          @tap="act(o, 'refund-reject')"
        >拒绝退单</text>
        <text
          v-if="o.status === 'REFUND_REQUESTED'"
          class="op danger"
          hover-class="press-dim"
          @tap="act(o, 'refund-approve')"
        >同意退单</text>
        <text v-if="o.status === 'PENDING'" class="op primary" hover-class="press-sink" @tap="act(o, 'complete')">完成</text>
        <text v-if="o.status === 'PENDING'" class="op danger" hover-class="press-dim" @tap="act(o, 'refund-approve')">退单</text>
      </view>
      <view class="ops" v-if="tab === 'mine' && o.status === 'PENDING'">
        <text class="op danger" hover-class="press-dim" @tap="act(o, 'refund-request')">申请退单</text>
      </view>
      <view class="ops" v-if="tab === 'mine' && o.status === 'REFUND_REQUESTED'">
        <text class="waiting">退单审核中…</text>
      </view>
    </view>

    <!-- 状态筛选弹层 -->
    <ActionSheet
      :visible="statusSheetVisible"
      :items="statusItems"
      @select="pickStatus"
      @close="statusSheetVisible = false"
    />
    <!-- 日期选择日历 -->
    <CalendarPicker
      :visible="calendarVisible"
      :selected="dateFilter"
      @select="pickDate"
      @close="calendarVisible = false"
    />
  
    <CustomTabbar current="orders" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 60rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.filters { display: flex; gap: 20rpx; margin-bottom: 20rpx; }
.filter-btn {
  background: v-bind('theme.card'); border-radius: 14rpx;
  padding: 14rpx 24rpx; font-size: 26rpx; color: v-bind('theme.title');
  box-shadow: 0 2rpx 6rpx rgba(200, 160, 80, 0.08);
}
.sub-tabs { display: flex; justify-content: center; gap: 96rpx; margin-bottom: 16rpx; }
.tab { font-size: 30rpx; color: v-bind('theme.sub'); padding: 8rpx 12rpx 12rpx; }
.tab.active {
  color: v-bind('theme.title'); font-weight: 600;
  border-bottom: 6rpx solid v-bind('theme.primary');
}
.notice {
  background: v-bind('theme.primaryLight');
  border-radius: 14rpx; padding: 14rpx 24rpx; margin-bottom: 20rpx;
}
.notice-text { font-size: 24rpx; color: v-bind('theme.income'); }
.empty { padding: 100rpx 0; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.empty-img { width: 170rpx; height: 170rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }

.order-card { padding: 26rpx 30rpx; margin-bottom: 18rpx; }
.head { display: flex; align-items: center; justify-content: space-between; }
.buyer { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.status { font-size: 22rpx; border-radius: 8rpx; padding: 4rpx 14rpx; }
.summary { display: block; font-size: 26rpx; color: v-bind('theme.title'); margin-top: 14rpx; }
.remark { display: block; font-size: 24rpx; color: v-bind('theme.sub'); margin-top: 10rpx; }
.foot {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 16rpx;
}
.meta { font-size: 24rpx; color: v-bind('theme.sub'); }
.total { font-size: 30rpx; font-weight: 700; color: v-bind('theme.income'); }
.items-detail {
  margin-top: 14rpx; padding-top: 14rpx;
  border-top: 2rpx solid v-bind('theme.divider');
}
.item-line { display: block; font-size: 24rpx; color: v-bind('theme.sub'); line-height: 40rpx; }

.ops { display: flex; justify-content: flex-end; gap: 20rpx; margin-top: 20rpx; }
.op {
  font-size: 26rpx; color: v-bind('theme.title');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 30rpx;
  padding: 10rpx 30rpx;
}
.op.primary {
  color: #fff; background: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  font-weight: 600;
}
.op.danger { color: v-bind('theme.danger'); border-color: v-bind('theme.danger'); }
.waiting { font-size: 24rpx; color: v-bind('theme.sub'); margin-left: auto; }
</style>
