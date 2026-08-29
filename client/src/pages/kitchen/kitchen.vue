<script setup lang="ts">
import { theme } from '@/styles/theme';
import { authApi, loadUser } from '@/api/auth';
import { kitchenApi, getCurrentKitchenId, loadKitchenCache, setCurrentKitchen } from '@/api/kitchen';
import type { KitchenDetail } from '@/api/kitchen';
import { dishApi, fenToYuan, fullUrl, parseSpecs } from '@/api/dish';
import type { CategoryView, DishView } from '@/api/dish';
import { useCartStore } from '@/stores/cart';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import ActionSheet from '@/components/action-sheet.vue';
import type { SheetItem } from '@/components/action-sheet.vue';

const cart = useCartStore();

const loggedIn = ref(!!loadUser());
const hasKitchen = ref(false);
const loading = ref(true);
const detail = ref<KitchenDetail | null>(null);

// 菜单状态
const mode = ref<'order' | 'manage'>('order');
const categories = ref<CategoryView[]>([]);
const dishes = ref<DishView[]>([]);
const activeCatId = ref<number | null>(null); // null=全部
const keyword = ref('');
const searchVisible = ref(false);
const menuLoading = ref(false);

const guide = [
  '1、点击右侧"分类管理"添加菜谱分类',
  '2、点击"添加菜谱"添加菜谱',
  '3、成员在点单页选菜下单',
];

onShow(async () => {
  loggedIn.value = !!loadUser();
  if (!loggedIn.value) {
    hasKitchen.value = false;
    loading.value = false;
    return;
  }
  const id = getCurrentKitchenId();
  if (!id) {
    try {
      const mine = await kitchenApi.mine();
      if (mine.length > 0) {
        setCurrentKitchen(mine[mine.length - 1]);
        await loadKitchen(mine[mine.length - 1].id);
        return;
      }
    } catch {
      // token 失效等
    }
    hasKitchen.value = false;
    loading.value = false;
    return;
  }
  const cached = loadKitchenCache();
  if (cached) {
    // 缓存存的是 KitchenView，补一层 KitchenDetail 形状保证模板可渲染
    detail.value = { kitchen: cached, members: [] };
  }
  hasKitchen.value = true;
  await loadKitchen(id);
});

async function loadKitchen(id: number) {
  try {
    detail.value = await kitchenApi.detail(id);
    hasKitchen.value = true;
    loading.value = false;
    cart.ensureKitchen(id);
    await Promise.all([loadCategories(), loadDishes()]);
  } catch {
    uni.removeStorageSync('kitchenId');
    uni.removeStorageSync('kitchenCache');
    detail.value = null;
    hasKitchen.value = false;
    loading.value = false;
  }
}

async function loadCategories() {
  if (!hasKitchen.value) return;
  try {
    categories.value = await dishApi.categories(getCurrentKitchenId()!);
  } catch {
    categories.value = [];
  }
}

async function loadDishes() {
  if (!hasKitchen.value) return;
  menuLoading.value = true;
  try {
    dishes.value = await dishApi.list(getCurrentKitchenId()!, {
      mode: mode.value,
      categoryId: activeCatId.value,
      keyword: keyword.value || undefined,
    });
  } catch {
    dishes.value = [];
  } finally {
    menuLoading.value = false;
  }
}

function setMode(m: 'order' | 'manage') {
  mode.value = m;
  loadDishes();
}

function pickCat(id: number | null) {
  activeCatId.value = id;
  if (mode.value === 'order') loadDishes();
}

function toggleSearch() {
  searchVisible.value = !searchVisible.value;
  if (!searchVisible.value && keyword.value) {
    keyword.value = '';
    loadDishes();
  }
}

function onSearchInput(e: any) {
  keyword.value = e.detail.value;
  loadDishes();
}

function goCategories() {
  uni.navigateTo({ url: '/pages/dish/categories' });
}

function goRecycle() {
  uni.navigateTo({ url: '/pages/dish/recycle' });
}

function openDish(d: DishView) {
  uni.navigateTo({
    url: `/pages/dish/detail?id=${d.id}${mode.value === 'manage' ? '&from=manage' : ''}`,
  });
}

/** 点单模式快速加购：多规格菜跳详情选规格，简单菜直接进购物车 */
function quickAdd(d: DishView) {
  if (parseSpecs(d.specsJson).length > 0) {
    uni.navigateTo({ url: `/pages/dish/detail?id=${d.id}` });
    return;
  }
  cart.add(d);
  uni.showToast({ title: `「${d.name}」已加入购物车`, icon: 'none' });
}

function addDish() {
  addSheetVisible.value = true;
}

const addSheetVisible = ref(false);
const addItems: SheetItem[] = [
  { key: 'manual', title: '手动添加', desc: '手动记录拿手菜做法与技巧' },
  { key: 'clone', title: '克隆菜谱', desc: '复制厨房码，快速复刻同款菜单' },
  { key: 'import', title: '快捷导入', desc: '复制链接，快速导入菜谱' },
  { key: 'square', title: '广场偷菜', desc: '广场上百万菜谱供你选择', badge: '推荐' },
  { key: 'batch', title: '批量添加', desc: '快速进行批量手动添加' },
];

function onAddSelect(key: string) {
  addSheetVisible.value = false;
  if (key === 'manual') {
    uni.navigateTo({ url: '/pages/dish/edit' });
  } else if (key === 'square') {
    uni.navigateTo({ url: '/pages/square/square' });
  } else if (key === 'clone') {
    uni.navigateTo({ url: '/pages/square/square' });
  } else if (key === 'import') {
    uni.showToast({ title: '快捷导入：外部链接抓取不可控，暂不开发', icon: 'none' });
  } else {
    uni.showToast({ title: '批量添加：后续版本开发', icon: 'none' });
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/login' });
}

function goBind() {
  uni.navigateTo({ url: '/pages/kitchen/bind' });
}

function goMembers() {
  if (detail.value) {
    uni.navigateTo({ url: `/pages/kitchen/members?id=${detail.value.kitchen.id}` });
  }
}

function copyCode() {
  if (!detail.value) return;
  uni.setClipboardData({
    data: detail.value.kitchen.code,
    success: () => uni.showToast({ title: '厨房码已复制，发给朋友即可加入', icon: 'none' }),
  });
}

// ----- 下单栏 -----

function goRandom() {
  uni.navigateTo({ url: '/pages/order/random' });
}

function goConfirm() {
  if (cart.count === 0) {
    uni.showToast({ title: '先从菜单里加点菜吧', icon: 'none' });
    return;
  }
  uni.navigateTo({ url: '/pages/order/confirm' });
}

function invite() {
  copyCode();
}

const emptyDishes = computed(() => !menuLoading.value && dishes.value.length === 0);
</script>

<template>
  <view class="page">
    <!-- 未登录 -->
    <view v-if="!loggedIn" class="card state-card">
      <image class="state-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="state-title">登录后开始使用</text>
      <text class="state-desc">登录后可创建自己的厨房，或凭厨房码加入朋友的厨房</text>
      <button class="btn-main" hover-class="press-sink" @tap="goLogin">立即登录</button>
    </view>

    <!-- 已登录但无厨房 -->
    <view v-else-if="!hasKitchen" class="card state-card">
      <image class="state-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
      <text class="state-title">还没有厨房</text>
      <text class="state-desc">创建一个厨房当主账号，或输入朋友的厨房码加入</text>
      <view class="state-btns">
        <button class="btn-main half" hover-class="press-sink" @tap="goBind">创建 / 加入厨房</button>
      </view>
    </view>

    <!-- 有厨房 -->
    <block v-else-if="detail">
      <!-- 厨房信息卡 -->
      <view class="kitchen-card card">
        <view class="kitchen-head">
          <view class="avatar">
            <image class="avatar-img" src="/static/icons/pan.png" mode="aspectFit" />
          </view>
          <view class="info" hover-class="press-dim" @tap="goMembers">
            <view class="name-row">
              <text class="lv">Lv.{{ detail.kitchen.level }}</text>
              <text class="name">{{ detail.kitchen.name }}</text>
              <image class="love" src="/static/icons/heart.png" mode="aspectFit" />
            </view>
            <text class="meta">共{{ detail.kitchen.memberCount }}人 ›</text>
          </view>
          <image class="qr" src="/static/icons/qr.png" mode="aspectFit" hover-class="press-dim" @tap="copyCode" />
        </view>
        <text class="announce">公告：{{ detail.kitchen.announcement || '暂无' }}</text>
      </view>

      <!-- 点单 / 修改 Tab + 操作 -->
      <view class="toolbar">
        <view class="mode-tabs">
          <text class="mode" :class="{ active: mode === 'order' }" hover-class="press-dim" @tap="setMode('order')">点单</text>
          <text class="mode" :class="{ active: mode === 'manage' }" hover-class="press-dim" @tap="setMode('manage')">修改</text>
        </view>
        <view class="actions">
          <text class="btn-outline" hover-class="press-bg" @tap="addDish">＋ 添加菜谱</text>
          <view class="btn-gray" hover-class="press-dim" @tap="toggleSearch">
            <image class="icon-sm" src="/static/icons/search.png" mode="aspectFit" />
            <text>搜索</text>
          </view>
        </view>
      </view>

      <!-- 搜索条 -->
      <view v-if="searchVisible" class="search-bar">
        <input
          class="search-input"
          :value="keyword"
          placeholder="搜索菜谱名称"
          placeholder-class="ph"
          confirm-type="search"
          @input="onSearchInput"
        />
        <text class="search-cancel" hover-class="press-dim" @tap="toggleSearch">取消</text>
      </view>

      <view class="body">
        <!-- 分类侧栏 -->
        <view class="side">
          <text class="cat" :class="{ active: mode === 'order' && activeCatId === null }" hover-class="press-dim" @tap="pickCat(null)">全部</text>
          <text
            v-for="c in categories"
            :key="c.id"
            class="cat"
            :class="{ active: mode === 'order' && activeCatId === c.id }"
            hover-class="press-dim"
            @tap="pickCat(c.id)"
          >{{ c.name }}</text>
          <text class="cat-manage" hover-class="press-dim" @tap="goCategories">⚙ 分类管理</text>
          <text v-if="mode === 'manage'" class="cat-manage" hover-class="press-dim" @tap="goRecycle">回收站</text>
        </view>

        <!-- 菜单列表 -->
        <view class="menu">
          <view v-if="menuLoading" class="menu-tip"><text>加载中…</text></view>
          <view v-else-if="emptyDishes" class="card empty">
            <image class="empty-img" src="/static/icons/empty-kitchen.png" mode="aspectFit" />
            <text class="empty-title">{{ mode === 'order' ? '菜单还是空的' : '还没有菜品' }}</text>
            <text v-for="line in guide" :key="line" class="empty-line">{{ line }}</text>
            <text class="warm-tip">点右上角二维码复制厨房码，发给成员下单</text>
          </view>
          <view v-for="d in dishes" :key="d.id" class="card dish-card" hover-class="press-dim" @tap="openDish(d)">
            <image v-if="d.imageUrl" class="dish-img" :src="fullUrl(d.imageUrl)" mode="aspectFit" />
            <view v-else class="dish-img holder">
              <image class="holder-icon" src="/static/icons/pot.png" mode="aspectFit" />
            </view>
            <view class="dish-info">
              <view class="dish-name-row">
                <text class="dish-name">{{ d.name }}</text>
                <text v-if="d.status === 0" class="off-tag">已下架</text>
              </view>
              <view v-if="d.recommendStars > 0" class="dish-stars">
                <text v-for="i in d.recommendStars" :key="i" class="star on">★</text>
              </view>
              <text v-if="d.categoryName" class="dish-cat">{{ d.categoryName }}</text>
              <view class="dish-bottom">
                <text class="dish-price">¥{{ fenToYuan(d.priceFen) }}</text>
                <text
                  v-if="mode === 'order'"
                  class="quick-add"
                  hover-class="press-sink"
                  @tap.stop="quickAdd(d)"
                >＋</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部下单栏（贴近 tabBar） -->
      <view class="bottom-bar">
        <view class="cart-wrap" hover-class="press-dim" @tap="goConfirm">
          <image class="icon-lg" src="/static/icons/cart-active.png" mode="aspectFit" />
          <text v-if="cart.count > 0" class="cart-badge">{{ cart.count }}</text>
        </view>
        <text v-if="cart.count > 0" class="cart-total">¥{{ fenToYuan(cart.totalFen) }}</text>
        <text class="random" hover-class="press-dim" @tap="goRandom">随机选菜</text>
        <text class="invite" hover-class="press-bg" @tap="invite">邀请下单</text>
        <text class="submit" :class="{ disabled: cart.count === 0 }" hover-class="press-sink" @tap="goConfirm">下单</text>
      </view>

      <!-- 添加菜谱弹层（对照蓝本截图3） -->
      <ActionSheet
        :visible="addSheetVisible"
        :items="addItems"
        @select="onAddSelect"
        @close="addSheetVisible = false"
      />
    </block>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: v-bind('theme.bg');
  padding: 24rpx;
  padding-bottom: 300rpx;
  box-sizing: border-box;
}
.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.state-card {
  margin-top: 10vh;
  padding: 64rpx 48rpx;
  display: flex; flex-direction: column; align-items: center; gap: 16rpx;
}
.state-img { width: 180rpx; height: 180rpx; }
.state-title { font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.state-desc { font-size: 26rpx; color: v-bind('theme.sub'); text-align: center; line-height: 40rpx; }
.state-btns { display: flex; width: 100%; margin-top: 24rpx; }
.btn-main {
  width: 100%;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 84rpx;
  margin-top: 24rpx;
}
.btn-main.half { margin-top: 0; }
.btn-main::after { border: none; }

.kitchen-card { padding: 24rpx; }
.kitchen-head { display: flex; align-items: center; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 24rpx;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
  margin-right: 20rpx;
}
.avatar-img { width: 60rpx; height: 60rpx; }
.info { flex: 1; }
.name-row { display: flex; align-items: center; gap: 12rpx; }
.lv {
  background: v-bind('theme.primary'); color: #fff;
  font-size: 22rpx; border-radius: 8rpx; padding: 2rpx 10rpx;
}
.name { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.meta { font-size: 24rpx; color: v-bind('theme.sub'); }
.qr { width: 44rpx; height: 44rpx; }
.love { width: 32rpx; height: 32rpx; }
.announce { display: block; margin-top: 16rpx; font-size: 24rpx; color: v-bind('theme.sub'); }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  margin: 24rpx 0;
}
.mode-tabs { display: flex; gap: 32rpx; }
.mode { font-size: 30rpx; color: v-bind('theme.sub'); padding-bottom: 8rpx; }
.mode.active {
  color: v-bind('theme.title'); font-weight: 600;
  border-bottom: 6rpx solid v-bind('theme.primary');
}
.actions { display: flex; gap: 16rpx; }
.btn-outline {
  border: 2rpx solid v-bind('theme.primaryBtn'); color: v-bind('theme.primaryBtn');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}
.btn-gray {
  display: flex; align-items: center; gap: 8rpx;
  background: v-bind('theme.chipBg'); color: v-bind('theme.sub');
  border-radius: 32rpx; padding: 10rpx 24rpx; font-size: 26rpx;
}
.icon-sm { width: 30rpx; height: 30rpx; }

.search-bar {
  display: flex; align-items: center; gap: 20rpx;
  margin-bottom: 20rpx;
}
.search-input {
  flex: 1;
  background: v-bind('theme.card');
  border: 2rpx solid v-bind('theme.primary');
  border-radius: 16rpx;
  padding: 14rpx 24rpx; font-size: 26rpx; color: v-bind('theme.title');
}
.ph { color: v-bind('theme.sub'); }
.search-cancel { font-size: 26rpx; color: v-bind('theme.sub'); }

.body { display: flex; gap: 20rpx; align-items: flex-start; }
.side {
  width: 150rpx; flex-shrink: 0;
  display: flex; flex-direction: column; gap: 16rpx;
}
.cat {
  font-size: 26rpx; color: v-bind('theme.title');
  background: v-bind('theme.card'); border-radius: 12rpx; padding: 14rpx 0;
  text-align: center;
  box-shadow: 0 2rpx 6rpx rgba(200, 160, 80, 0.08);
}
.cat.active { background: v-bind('theme.primaryLight'); color: v-bind('theme.primaryBtn'); font-weight: 600; }
.cat-manage { font-size: 24rpx; color: v-bind('theme.primaryBtn'); text-align: center; padding: 8rpx 0; }

.menu { flex: 1; min-width: 0; }
.menu-tip { padding: 60rpx 0; text-align: center; font-size: 24rpx; color: v-bind('theme.sub'); }
.empty { padding: 40rpx 28rpx; display: flex; flex-direction: column; }
.empty-img { width: 140rpx; height: 140rpx; align-self: center; margin-bottom: 12rpx; }
.empty-title { font-size: 28rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 12rpx; text-align: center; }
.empty-line { font-size: 24rpx; color: v-bind('theme.sub'); line-height: 44rpx; text-align: center; }
.warm-tip { margin-top: 16rpx; font-size: 24rpx; color: v-bind('theme.primaryBtn'); text-align: center; }

.dish-card {
  display: flex; gap: 20rpx;
  padding: 20rpx; margin-bottom: 16rpx;
}
.dish-img { width: 140rpx; height: 140rpx; border-radius: 16rpx; flex-shrink: 0; background: v-bind('theme.primaryLight'); }
.dish-img.holder {
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.holder-icon { width: 64rpx; height: 64rpx; opacity: 0.5; }
.dish-info { flex: 1; min-width: 0; }
.dish-name-row { display: flex; align-items: center; gap: 12rpx; }
.dish-name {
  font-size: 30rpx; font-weight: 600; color: v-bind('theme.title');
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.off-tag {
  font-size: 20rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 8rpx; padding: 2rpx 8rpx;
  flex-shrink: 0;
}
.dish-stars { display: flex; margin-top: 6rpx; }
.star { color: v-bind('theme.primaryBtn'); font-size: 24rpx; margin-right: 2rpx; }
.dish-cat {
  display: inline-block; font-size: 20rpx; color: v-bind('theme.sub');
  background: v-bind('theme.chipBg'); border-radius: 8rpx; padding: 2rpx 12rpx; margin-top: 8rpx;
}
.dish-price { font-size: 30rpx; font-weight: 700; color: v-bind('theme.income'); }
.dish-bottom { display: flex; align-items: center; justify-content: space-between; margin-top: 10rpx; }
.quick-add {
  width: 52rpx; height: 52rpx; border-radius: 50%;
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 36rpx; font-weight: 700; text-align: center; line-height: 48rpx;
}

.bottom-bar {
  position: fixed; left: 24rpx; right: 24rpx;
  /* 几乎贴着 tabBar，仅留防误触的丝缝 */
  bottom: calc(110rpx + env(safe-area-inset-bottom));
  display: flex; align-items: center; gap: 24rpx;
  background: v-bind('theme.card'); border-radius: 48rpx; padding: 16rpx 32rpx;
  box-shadow: 0 6rpx 20rpx rgba(200, 160, 80, 0.28);
  border: 2rpx solid v-bind('theme.divider');
  z-index: 10;
}
.cart-wrap { display: flex; position: relative; }
.icon-lg { width: 44rpx; height: 44rpx; }
.cart-badge {
  position: absolute; top: -14rpx; right: -18rpx;
  min-width: 32rpx; height: 32rpx; border-radius: 16rpx;
  background: v-bind('theme.danger'); color: #fff;
  font-size: 20rpx; text-align: center; line-height: 32rpx;
  padding: 0 6rpx; box-sizing: border-box;
}
.cart-total { font-size: 28rpx; font-weight: 700; color: v-bind('theme.income'); }
.random { font-size: 26rpx; color: v-bind('theme.title'); text-decoration: underline; }
.invite {
  margin-left: auto; font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn'); border-radius: 32rpx; padding: 8rpx 24rpx;
}
.submit {
  background: v-bind('theme.primaryBtn'); color: #fff;
  font-size: 28rpx; border-radius: 32rpx; padding: 12rpx 40rpx;
}
.submit.disabled { background: v-bind('theme.divider'); }
</style>
