<script setup lang="ts">
import { theme } from '@/styles/theme';
import { fridgeApi, expiryText, UNIT_LABELS } from '@/api/fridge';
import type { FridgeItemView, FridgeCategoryView, FridgeSummary } from '@/api/fridge';
import { getCurrentKitchenId, loadKitchenCache } from '@/api/kitchen';
import { fullUrl } from '@/api/dish';
import type { FridgeItemView as FItem } from '@/api/fridge';
import { pushUnreadNotices } from '@/utils/notify';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';

const kitchenId = ref<number | null>(getCurrentKitchenId());
const canManage = computed(() =>
  ['OWNER', 'MEMBER'].includes(loadKitchenCache()?.myRole || '')
);

const summary = ref<FridgeSummary>({ fresh: 0, expiring: 0, expired: 0 });
const stateFilter = ref<'all' | 'fresh' | 'expiring' | 'expired'>('all');
const categories = ref<FridgeCategoryView[]>([]);
const activeCatId = ref<number | null>(null);
const keyword = ref('');
const searchVisible = ref(false);
const items = ref<FridgeItemView[]>([]);
const loading = ref(true);
const unread = ref(0);
const catDialogVisible = ref(false);

const stateDefs = computed(() => [
  { key: 'fresh' as const, label: `新鲜(${summary.value.fresh})`, color: theme.success },
  { key: 'expiring' as const, label: `快过期(${summary.value.expiring})`, color: theme.warning },
  { key: 'expired' as const, label: `已过期(${summary.value.expired})`, color: theme.danger },
]);

onShow(() => {
  kitchenId.value = getCurrentKitchenId();
  if (!kitchenId.value) return;
  load();
});

async function load() {
  if (!kitchenId.value) return;
  loading.value = true;
  try {
    const [sum, cats, list, unreadCount] = await Promise.all([
      fridgeApi.summary(kitchenId.value),
      fridgeApi.categories(kitchenId.value),
      fridgeApi.list(kitchenId.value, {
        state: stateFilter.value,
        categoryId: activeCatId.value,
        keyword: keyword.value || undefined,
      }),
      fridgeApi.unreadCount(kitchenId.value).catch(() => 0),
    ]);
    summary.value = sum;
    categories.value = cats;
    items.value = list;
    unread.value = unreadCount;
    // 有未读临期提醒时，同步弹到系统通知栏（APK 端生效，幂等去重）
    if (unreadCount > 0) {
      const notices = await fridgeApi.notifications(kitchenId.value!).catch(() => []);
      pushUnreadNotices(notices);
    }
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

function setState(s: 'all' | 'fresh' | 'expiring' | 'expired') {
  stateFilter.value = s;
  load();
}

function pickCat(id: number | null) {
  activeCatId.value = id;
  load();
}

function toggleSearch() {
  searchVisible.value = !searchVisible.value;
  if (!searchVisible.value && keyword.value) {
    keyword.value = '';
    load();
  }
}

function onSearch(e: any) {
  keyword.value = e.detail.value;
  load();
}

function goAdd() {
  uni.navigateTo({ url: '/pages/fridge/add' });
}

function goCategories() {
  uni.navigateTo({ url: '/pages/fridge/categories' });
}

function goNotice() {
  uni.navigateTo({ url: '/pages/fridge/notice' });
}

function goMatch(item: FridgeItemView) {
  uni.navigateTo({ url: `/pages/fridge/match?name=${encodeURIComponent(item.name)}` });
}

function checkNow() {
  if (!kitchenId.value) return;
  fridgeApi.checkExpiry(kitchenId.value).then((n) => {
    uni.showToast({
      title: n > 0 ? `发现 ${n} 种食材需要处理，已提醒` : '没有需要处理的食材',
      icon: 'none',
    });
    if (n > 0) {
      fridgeApi.notifications(kitchenId.value!).then((list) => pushUnreadNotices(list));
    }
    load();
  });
}

function clearAll() {
  if (!kitchenId.value) return;
  uni.showModal({
    title: '清仓',
    content: stateFilter.value === 'expired'
      ? '清空所有已过期食材？'
      : '确定清空当前列表的全部食材？',
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      fridgeApi
        .clear(kitchenId.value!, items.value.map((i) => i.id))
        .then((n) => {
          uni.showToast({ title: `已清仓 ${n} 种食材`, icon: 'none' });
          load();
        });
    },
  });
}

function onAddCategory(name: string) {
  catDialogVisible.value = false;
  fridgeApi.createCategory(kitchenId.value!, name).then(() => load());
}

/** 点缩略图看大图 */
function previewPhoto(url: string) {
  uni.previewImage({ urls: [fullUrl(url)] });
}

function tipMatch() {
  uni.showToast({ title: '点食材卡片上的「匹配菜谱」即可', icon: 'none' });
}

// ----- 左滑操作（像聊天列表左滑）：显示「改数量 / 删除」 -----
const BTN_W = uni.upx2px(240);
const openedId = ref<number | null>(null);
const dragId = ref<number | null>(null);
const dragDx = ref(0);
let touchStartX = 0;
let touchStartY = 0;
let touchBaseX = 0;
let dragging = false;
let decided = false;

function contentStyle(id: number) {
  if (dragId.value === id) {
    return { transform: `translateX(${dragDx.value}px)`, transition: 'none' };
  }
  if (openedId.value === id) {
    return { transform: `translateX(${-BTN_W}px)` };
  }
  return { transform: 'translateX(0px)' };
}

function onTouchStart(e: TouchEvent, id: number) {
  const t = e.touches[0];
  touchStartX = t.clientX;
  touchStartY = t.clientY;
  touchBaseX = openedId.value === id ? -BTN_W : 0;
  dragging = false;
  decided = false;
  dragId.value = null;
}

function onTouchMove(e: TouchEvent, id: number) {
  const t = e.touches[0];
  const dx = t.clientX - touchStartX;
  const dy = t.clientY - touchStartY;
  if (!decided) {
    if (Math.abs(dx) < 8) return;
    // 横向滑动才接管，纵向交给页面滚动
    if (Math.abs(dx) > Math.abs(dy)) {
      decided = true;
      dragging = true;
      dragId.value = id;
    } else {
      decided = true;
    }
  }
  if (dragging) {
    dragDx.value = Math.max(-BTN_W, Math.min(0, touchBaseX + dx));
  }
}

function onTouchEnd(id: number) {
  if (dragging && dragId.value === id) {
    openedId.value = dragDx.value < -BTN_W / 2 ? id : null;
  }
  dragging = false;
  dragId.value = null;
}

function onItemTap() {
  if (openedId.value !== null) openedId.value = null;
}

function openQty(i: FItem) {
  qtyTarget.value = i;
  qtyDialogVisible.value = true;
}

const qtyDialogVisible = ref(false);
const qtyTarget = ref<FItem | null>(null);

function onQtySave(v: string) {
  qtyDialogVisible.value = false;
  if (!qtyTarget.value || !kitchenId.value) return;
  fridgeApi.updateItem(kitchenId.value, qtyTarget.value.id, { quantity: v.trim() }).then(() => {
    uni.showToast({ title: '数量已更新', icon: 'none' });
    load();
  });
}

function removeOne(i: FItem) {
  fridgeApi.removeItem(kitchenId.value!, i.id).then(() => {
    uni.showToast({ title: `已删除「${i.name}」`, icon: 'none' });
    load();
  });
}
</script>

<template>
  <view class="page">
    <template v-if="canManage">
      <!-- 状态筛选（对照蓝本截图15） -->
      <view class="toolbar">
        <view class="chips">
          <text
            v-for="s in stateDefs"
            :key="s.key"
            class="chip"
            :class="{ on: stateFilter === s.key }"
            hover-class="press-dim"
            @tap="setState(s.key)"
          >
            <text class="dot" :style="{ background: s.color }" />{{ s.label }}
          </text>
        </view>
        <view class="head-btns">
          <view class="notice-btn" hover-class="press-dim" @tap="goNotice">
            <image class="icon-sm" src="/static/icons/receipt.png" mode="aspectFit" />
            <text v-if="unread > 0" class="badge">{{ unread }}</text>
          </view>
          <text class="put-btn" hover-class="press-bg" @tap="goAdd">放入食材</text>
        </view>
      </view>

      <!-- 搜索 -->
      <view class="search">
        <image class="icon-sm" src="/static/icons/search.png" mode="aspectFit" />
        <input
          v-if="searchVisible"
          class="search-input"
          :value="keyword"
          placeholder="输入食材名称"
          placeholder-class="ph"
          @input="onSearch"
        />
        <text v-else class="placeholder" hover-class="press-dim" @tap="searchVisible = true">输入食材名称进行搜索</text>
        <text v-if="searchVisible" class="cancel" hover-class="press-dim" @tap="toggleSearch">取消</text>
      </view>

      <view class="body">
        <!-- 类别侧栏 -->
        <view class="side">
          <text
            class="cat"
            :class="{ active: activeCatId === null }"
            hover-class="press-dim"
            @tap="pickCat(null)"
          >全部</text>
          <text
            v-for="c in categories"
            :key="c.id"
            class="cat"
            :class="{ active: activeCatId === c.id }"
            hover-class="press-dim"
            @tap="pickCat(c.id)"
          >{{ c.name }}</text>
          <text class="cat-manage" hover-class="press-dim" @tap="goCategories">类别管理</text>
        </view>

        <!-- 食材列表 -->
        <view class="list">
          <view v-if="loading" class="list-tip"><text>加载中…</text></view>
          <view v-else-if="items.length === 0" class="card empty">
            <image class="empty-img" src="/static/icons/basket.png" mode="aspectFit" />
            <text class="empty-tip">冰箱里空空的，放点食材吧</text>
          </view>
          <view v-for="i in items" :key="i.id" class="swipe-cell">
            <view class="swipe-actions">
              <text class="swipe-btn qty" hover-class="press-dim" @tap.stop="openQty(i)">改数量</text>
              <text class="swipe-btn del" hover-class="press-dim" @tap.stop="removeOne(i)">删除</text>
            </view>
            <view
              class="card item swipe-content"
              :class="{ warning: i.state === 'expiring', danger: i.state === 'expired' }"
              :style="contentStyle(i.id)"
              @touchstart="onTouchStart($event, i.id)"
              @touchmove="onTouchMove($event, i.id)"
              @touchend="onTouchEnd(i.id)"
              @touchcancel="onTouchEnd(i.id)"
              @tap="onItemTap"
            >
            <view class="item-main">
              <view class="item-head">
                <text class="name">{{ i.name }}</text>
                <text class="expire" :class="i.state">{{ expiryText(i) }}</text>
              </view>
              <text class="line">剩余数量：{{ i.quantity || '未填' }}</text>
              <text v-if="i.remark" class="line">注：{{ i.remark }}</text>
              <view class="item-foot">
                <text class="meta">产自：{{ i.producedDate || '—' }}　保质期：{{ i.shelfLifeValue }}{{ UNIT_LABELS[i.shelfLifeUnit] }}</text>
                <text class="match" hover-class="press-dim" @tap="goMatch(i)">匹配菜谱</text>
              </view>
            </view>
            <image v-if="i.imageUrl" class="item-photo" :src="fullUrl(i.imageUrl)" mode="aspectFit" @tap.stop="previewPhoto(i.imageUrl)" />
            </view>
          </view>
        </view>
      </view>

      <!-- 底部操作栏（所有修改实时提交后端，无需手动同步） -->
      <view class="bottom-ops">
        <text class="op" hover-class="press-dim" @tap="checkNow">临期通知</text>
        <text class="op danger" hover-class="press-dim" @tap="clearAll">清仓</text>
        <text class="op" hover-class="press-dim" @tap="catDialogVisible = true">修改类别</text>
        <text class="op" hover-class="press-dim" @tap="tipMatch">匹配菜谱</text>
      </view>
    </template>

    <!-- 顾客视角 -->
    <view v-else class="card empty big">
      <image class="empty-img" src="/static/icons/basket.png" mode="aspectFit" />
      <text class="empty-tip">食材冰箱仅主账号和成员可见</text>
    </view>

    <InputDialog
      :visible="qtyDialogVisible"
      title="修改数量"
      :default-value="qtyTarget?.quantity"
      placeholder="数量+单位，如：100g"
      :maxlength="20"
      @confirm="onQtySave"
      @close="qtyDialogVisible = false"
    />
    <InputDialog
      :visible="catDialogVisible"
      title="快速添加类别"
      placeholder="类别名称"
      :maxlength="10"
      @confirm="onAddCategory"
      @close="catDialogVisible = false"
    />
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 160rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.chips { display: flex; gap: 18rpx; }
.chip { font-size: 24rpx; color: v-bind('theme.title'); padding: 6rpx 4rpx; }
.chip.on { font-weight: 700; }
.dot { display: inline-block; width: 14rpx; height: 14rpx; border-radius: 50%; margin-right: 6rpx; }
.head-btns { display: flex; align-items: center; gap: 16rpx; }
.notice-btn { position: relative; display: flex; }
.icon-sm { width: 36rpx; height: 36rpx; }
.badge {
  position: absolute; top: -10rpx; right: -14rpx;
  min-width: 28rpx; height: 28rpx; border-radius: 14rpx;
  background: v-bind('theme.danger'); color: #fff;
  font-size: 18rpx; text-align: center; line-height: 28rpx;
}
.put-btn {
  border: 2rpx solid v-bind('theme.title'); border-radius: 12rpx;
  padding: 8rpx 20rpx; font-size: 26rpx; color: v-bind('theme.title');
}

.search {
  display: flex; align-items: center; gap: 12rpx;
  background: v-bind('theme.card');
  border: 2rpx solid v-bind('theme.divider');
  border-radius: 16rpx; padding: 14rpx 24rpx; margin-bottom: 20rpx;
}
.search-input { flex: 1; font-size: 26rpx; color: v-bind('theme.title'); }
.placeholder { flex: 1; font-size: 26rpx; color: v-bind('theme.sub'); }
.cancel { font-size: 26rpx; color: v-bind('theme.sub'); }
.ph { color: v-bind('theme.sub'); }

.body { display: flex; gap: 20rpx; align-items: flex-start; }
.side { width: 140rpx; flex-shrink: 0; display: flex; flex-direction: column; gap: 14rpx; }
.cat {
  font-size: 26rpx; color: v-bind('theme.title');
  background: v-bind('theme.card'); border-radius: 12rpx; padding: 14rpx 0;
  text-align: center;
  box-shadow: 0 2rpx 6rpx rgba(200, 160, 80, 0.08);
}
.cat.active { background: v-bind('theme.primaryLight'); color: v-bind('theme.primaryBtn'); font-weight: 600; }
.cat-manage { font-size: 24rpx; color: v-bind('theme.primaryBtn'); text-align: center; padding: 6rpx 0; }

.list { flex: 1; min-width: 0; }
.list-tip { padding: 40rpx 0; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.empty { padding: 70rpx 0; display: flex; flex-direction: column; align-items: center; gap: 14rpx; }
.empty.big { margin-top: 20vh; padding: 70rpx 0; }
.empty-img { width: 150rpx; height: 150rpx; }
.empty-tip { font-size: 26rpx; color: v-bind('theme.sub'); }

.swipe-cell { position: relative; margin-bottom: 16rpx; border-radius: 24rpx; overflow: hidden; }
.swipe-actions {
  position: absolute; top: 0; right: 0; bottom: 0;
  display: flex; align-items: stretch; z-index: 1;
}
.swipe-btn {
  width: 120rpx; display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 26rpx; font-weight: 600;
}
.swipe-btn.qty { background: v-bind('theme.primaryBtn'); }
.swipe-btn.del { background: v-bind('theme.danger'); }
.swipe-content { will-change: transform; }
.item { padding: 22rpx 26rpx; border: 2rpx solid v-bind('theme.divider'); display: flex; gap: 20rpx; align-items: flex-start; }
.item-main { flex: 1; min-width: 0; }
.item-photo { width: 120rpx; height: 120rpx; border-radius: 14rpx; flex-shrink: 0; background: v-bind('theme.primaryLight'); }
.item.warning { border-color: v-bind('theme.warning'); background: v-bind('theme.warningLight'); }
.item.danger { border-color: v-bind('theme.danger'); background: v-bind('theme.dangerLight'); }
.item-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.name { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.expire { font-size: 24rpx; }
.expire.fresh { color: v-bind('theme.success'); }
.expire.expiring { color: v-bind('theme.warning'); }
.expire.expired { color: v-bind('theme.danger'); }
.line { display: block; font-size: 24rpx; color: v-bind('theme.sub'); line-height: 38rpx; }
.item-foot {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 12rpx;
}
.meta { font-size: 22rpx; color: v-bind('theme.sub'); }
.match { font-size: 24rpx; color: v-bind('theme.primaryBtn'); font-weight: 600; }

.bottom-ops {
  position: fixed; left: 0; right: 0;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  display: flex;
  background: v-bind('theme.card');
  padding: 18rpx 12rpx;
  box-shadow: 0 -2rpx 12rpx rgba(200, 160, 80, 0.12);
}
.op {
  flex: 1; text-align: center; font-size: 26rpx; color: v-bind('theme.title');
  padding: 8rpx 0;
}
.op.danger { color: v-bind('theme.danger'); }
</style>
