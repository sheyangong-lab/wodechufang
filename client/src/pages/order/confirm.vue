<script setup lang="ts">
import { theme } from '@/styles/theme';
import { useCartStore } from '@/stores/cart';
import { orderApi } from '@/api/order';
import { fenToYuan, fullUrl } from '@/api/dish';
import { getCurrentKitchenId } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';

const cart = useCartStore();
const kitchenId = ref<number | null>(getCurrentKitchenId());
const remark = ref('');
const diningToday = ref(true);
const submitting = ref(false);

onShow(() => {
  kitchenId.value = getCurrentKitchenId();
  if (!kitchenId.value || cart.items.length === 0) {
    uni.showToast({ title: '购物车是空的', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 700);
  }
});

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const total = computed(() => fenToYuan(cart.totalFen));

function changeQty(item: { dishId: number; specName: string | null }, delta: number) {
  const found = cart.items.find((i) => i.dishId === item.dishId && i.specName === item.specName);
  if (!found) return;
  cart.setQuantity(item.dishId, item.specName, found.quantity + delta);
}

function submit() {
  if (submitting.value || !kitchenId.value) return;
  submitting.value = true;
  orderApi
    .create(kitchenId.value, {
      remark: remark.value.trim() || undefined,
      dineDate: diningToday.value ? todayStr() : undefined,
      items: cart.items.map((i) => ({
        dishId: i.dishId,
        specName: i.specName,
        quantity: i.quantity,
      })),
    })
    .then(() => {
      cart.clear();
      uni.showToast({ title: '下单成功，等主账号开灶！', icon: 'none' });
      setTimeout(() => uni.switchTab({ url: '/pages/order/order' }), 900);
    })
    .finally(() => (submitting.value = false));
}
</script>

<template>
  <view class="page">
    <view class="card list">
      <view v-for="i in cart.items" :key="i.dishId + (i.specName || '')" class="item">
        <image v-if="i.imageUrl" class="thumb" :src="fullUrl(i.imageUrl)" mode="aspectFit" />
        <view v-else class="thumb holder">
          <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
        </view>
        <view class="info">
          <text class="name">{{ i.name }}<text v-if="i.specName" class="spec">（{{ i.specName }}）</text></text>
          <text class="price">¥{{ fenToYuan(i.priceFen) }}</text>
        </view>
        <view class="qty">
          <text class="qty-btn" hover-class="press-dim" @tap="changeQty(i, -1)">−</text>
          <text class="qty-num">{{ i.quantity }}</text>
          <text class="qty-btn" hover-class="press-dim" @tap="changeQty(i, 1)">＋</text>
        </view>
      </view>
    </view>

    <view class="card section">
      <text class="label">整单备注</text>
      <textarea
        v-model="remark"
        class="remark"
        :maxlength="100"
        placeholder="口味偏好、忌口、几点吃…"
        placeholder-class="ph"
      />
    </view>

    <view class="card section row">
      <text class="label">用餐日期</text>
      <view class="date-pick">
        <text class="date-btn" :class="{ on: diningToday }" hover-class="press-dim" @tap="diningToday = true">今天</text>
        <text class="date-btn" :class="{ on: !diningToday }" hover-class="press-dim" @tap="diningToday = false">明天</text>
      </view>
    </view>

    <view class="card section total-row">
      <text class="label">合计</text>
      <text class="total">¥{{ total }}</text>
    </view>

    <button class="btn-submit" :disabled="submitting" hover-class="press-sink" @tap="submit">
      提交订单（{{ cart.count }} 件）
    </button>
    <text class="hint">提交后按当前菜价结算，改价以服务端为准</text>
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
  margin-bottom: 20rpx;
}
.list { padding: 8rpx 28rpx; }
.item {
  display: flex; align-items: center; gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.thumb { width: 88rpx; height: 88rpx; border-radius: 16rpx; }
.thumb.holder {
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.holder-icon { width: 52rpx; height: 52rpx; opacity: 0.5; }
.info { flex: 1; }
.name { display: block; font-size: 28rpx; color: v-bind('theme.title'); font-weight: 500; }
.spec { font-size: 24rpx; color: v-bind('theme.sub'); font-weight: 400; }
.price { font-size: 26rpx; color: v-bind('theme.income'); font-weight: 600; }
.qty { display: flex; align-items: center; gap: 20rpx; }
.qty-btn {
  width: 52rpx; height: 52rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight'); color: v-bind('theme.primaryBtn');
  font-size: 32rpx; text-align: center; line-height: 48rpx; font-weight: 700;
}
.qty-num { font-size: 28rpx; color: v-bind('theme.title'); min-width: 40rpx; text-align: center; }

.section { padding: 28rpx 32rpx; }
.label { font-size: 28rpx; color: v-bind('theme.title'); }
.remark {
  width: 100%; box-sizing: border-box; margin-top: 16rpx; min-height: 100rpx;
  font-size: 26rpx; color: v-bind('theme.title'); line-height: 42rpx;
}
.ph { color: v-bind('theme.sub'); }
.row { display: flex; align-items: center; justify-content: space-between; }
.date-pick { display: flex; gap: 16rpx; }
.date-btn {
  font-size: 26rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 12rpx;
  padding: 8rpx 28rpx;
}
.date-btn.on {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight'); font-weight: 600;
}
.total-row { display: flex; align-items: center; justify-content: space-between; }
.total { font-size: 36rpx; font-weight: 700; color: v-bind('theme.income'); }

.btn-submit {
  margin-top: 8rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 24rpx; font-size: 32rpx; line-height: 96rpx;
}
.btn-submit[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-submit::after { border: none; }
.hint { display: block; text-align: center; margin-top: 20rpx; font-size: 22rpx; color: v-bind('theme.sub'); }
</style>
