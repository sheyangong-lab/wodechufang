<script setup lang="ts">
import { theme } from '@/styles/theme';
import { kitchenApi, setCurrentKitchen, clearCurrentKitchen, ROLE_LABELS } from '@/api/kitchen';
import type { KitchenDetail } from '@/api/kitchen';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';

const detail = ref<KitchenDetail | null>(null);
const isOwner = computed(() => detail.value?.kitchen.myRole === 'OWNER');
const isVip = computed(() => !!detail.value?.kitchen.vipExpireAt);

// 额度进度（菜品/分类在 M2 后随真实数据变化）
const dishUsage = computed(() => ({ used: 0, total: detail.value?.kitchen.dishQuota ?? 50 }));
const catUsage = computed(() => ({ used: 0, total: detail.value?.kitchen.categoryQuota ?? 5 }));

onShow(refresh);

async function refresh() {
  const id = uni.getStorageSync('kitchenId');
  if (!id) {
    uni.showToast({ title: '请先创建或加入厨房', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 800);
    return;
  }
  try {
    detail.value = await kitchenApi.detail(Number(id));
  } catch {
    setTimeout(() => uni.navigateBack(), 800);
  }
}

function copyCode() {
  if (!detail.value) return;
  uni.setClipboardData({
    data: detail.value.kitchen.code,
    success: () => uni.showToast({ title: '厨房码已复制', icon: 'none' }),
  });
}

function switchKitchen() {
  kitchenApi.mine().then((list) => {
    if (list.length <= 1) {
      uni.showToast({ title: '你只有一个厨房，无需切换', icon: 'none' });
      return;
    }
    uni.showActionSheet({
      itemList: list.map((k) => `${k.name}（${ROLE_LABELS[k.myRole] || k.myRole}）`),
      success: ({ tapIndex }) => {
        setCurrentKitchen(list[tapIndex]);
        uni.showToast({ title: `已切换到「${list[tapIndex].name}」`, icon: 'none' });
        refresh();
      },
    });
  });
}

function editKitchen() {
  if (!detail.value) return;
  uni.showModal({
    title: '修改厨房名称',
    editable: true,
    placeholderText: detail.value.kitchen.name,
    success: (res) => {
      if (!res.confirm || !res.content || !res.content.trim()) return;
      kitchenApi.update(detail.value!.kitchen.id, { name: res.content!.trim() })
        .then((k) => {
          setCurrentKitchen(k);
          uni.showToast({ title: '已保存', icon: 'none' });
          refresh();
        });
    },
  });
}

function editAnnouncement() {
  if (!detail.value) return;
  uni.showModal({
    title: '编辑公告',
    editable: true,
    placeholderText: detail.value.kitchen.announcement || '写点公告给成员看',
    success: (res) => {
      if (!res.confirm || res.content == null) return;
      kitchenApi.update(detail.value!.kitchen.id, { announcement: res.content })
        .then((k) => {
          setCurrentKitchen(k);
          uni.showToast({ title: '公告已更新', icon: 'none' });
          refresh();
        });
    },
  });
}

function goMembers() {
  if (detail.value) {
    uni.navigateTo({ url: `/pages/kitchen/members?id=${detail.value.kitchen.id}` });
  }
}

function goBind() {
  uni.navigateTo({ url: '/pages/kitchen/bind' });
}

function upgrade() {
  uni.showToast({ title: '会员功能，M6 上线', icon: 'none' });
}

function placeholder(name: string, milestone = '后续版本') {
  uni.showToast({ title: `${name}：${mileageHint(milestone)}`, icon: 'none' });
}

function mileageHint(m: string) {
  return `${m} 开发`;
}

function dissolve() {
  if (!detail.value) return;
  uni.showModal({
    title: '解散厨房',
    content: `确定解散「${detail.value.kitchen.name}」？成员将全部移出，菜单清空，不可恢复。`,
    confirmColor: '#E05B4E',
    success: (res) => {
      if (!res.confirm) return;
      kitchenApi.dissolve(detail.value!.kitchen.id).then(() => {
        clearCurrentKitchen();
        uni.showToast({ title: '厨房已解散', icon: 'none' });
        setTimeout(() => uni.switchTab({ url: '/pages/kitchen/kitchen' }), 700);
      });
    },
  });
}

const gridA = ['厨房成员', '任务卡', '成员寄存'];
const gridB = ['修改厨房', '创建厨房', '克隆菜谱', '厨房主题'];
const gridC = ['分享广场', '经营分析', '备份导出', '回收站'];
const gridVip = ['高级设置', '下单表单', '厨房桌码', '收款码', '小票机', '厨房黑名单'];

function onGrid(item: string) {
  switch (item) {
    case '厨房成员': return goMembers();
    case '修改厨房': return isOwner.value ? editKitchen() : placeholder('修改厨房', '仅店长可操作');
    case '创建厨房': return goBind();
    default: return placeholder(item, ['克隆菜谱'].includes(item) ? 'M2' : '后续版本');
  }
}
</script>

<template>
  <view class="page">
    <template v-if="detail">
      <!-- 厨房信息卡（对照蓝本截图11） -->
      <view class="card head">
        <view class="head-row">
          <view class="avatar">
            <image class="avatar-img" src="/static/icons/chefhat.png" mode="aspectFit" />
          </view>
          <view class="head-info">
            <view class="name-row">
              <text class="lv">Lv.{{ detail.kitchen.level }}</text>
              <text class="name">{{ detail.kitchen.name }}</text>
            </view>
            <view class="code-row" hover-class="press-dim" @tap="copyCode">
              <text class="code">厨房码：{{ detail.kitchen.code.slice(0, 22) }}…</text>
              <text class="copy">复制</text>
            </view>
          </view>
          <text class="switch-btn" hover-class="press-bg" @tap="switchKitchen">切换厨房</text>
        </view>

        <!-- 额度进度条 -->
        <view class="quota">
          <text class="quota-label">菜品额度：</text>
          <view class="bar">
            <view class="bar-inner" :style="{ width: (dishUsage.used / dishUsage.total * 100) + '%' }" />
          </view>
          <text class="quota-num">{{ dishUsage.used }}/{{ dishUsage.total }}</text>
        </view>
        <view class="quota">
          <text class="quota-label">分类额度：</text>
          <view class="bar">
            <view class="bar-inner" :style="{ width: (catUsage.used / catUsage.total * 100) + '%' }" />
          </view>
          <text class="quota-num">{{ catUsage.used }}/{{ catUsage.total }}</text>
        </view>

        <text class="meta-line">创始人：{{ detail.kitchen.ownerNickname }}<text v-if="isOwner" class="meta-link" hover-class="press-dim" @tap="editKitchen"> 变更</text></text>
        <text class="meta-line">厨房会员：{{ isVip ? '生效中' : '未开通' }}<text class="meta-link" hover-class="press-dim" @tap="upgrade"> 去开通</text></text>

        <button class="btn-upgrade" hover-class="press-sink" @tap="upgrade">升级厨房</button>
        <text v-if="isOwner" class="dissolve" hover-class="press-dim" @tap="dissolve">解散厨房</text>
      </view>

      <!-- 功能宫格 -->
      <view class="card grid-card">
        <view class="grid-row">
          <text v-for="g in gridA" :key="g" class="grid-item" hover-class="press-dim" @tap="onGrid(g)">{{ g }}</text>
        </view>
      </view>
      <view class="card grid-card">
        <view class="grid-row wrap">
          <text v-for="g in gridB" :key="g" class="grid-item" hover-class="press-dim" @tap="onGrid(g)">{{ g }}</text>
        </view>
      </view>
      <view class="card grid-card">
        <view class="grid-row wrap">
          <text v-for="g in gridC" :key="g" class="grid-item" hover-class="press-dim" @tap="onGrid(g)">{{ g }}</text>
        </view>
      </view>
      <view class="card grid-card">
        <view class="grid-row wrap">
          <text v-for="g in gridVip" :key="g" class="grid-item vip" hover-class="press-dim" @tap="upgrade">{{ g }}<text class="vip-tag">会员</text></text>
        </view>
      </view>
    </template>
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
.head { padding: 28rpx 32rpx; }
.head-row { display: flex; align-items: center; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 24rpx;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
  margin-right: 20rpx;
}
.avatar-img { width: 60rpx; height: 60rpx; }
.head-info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 12rpx; }
.lv {
  background: v-bind('theme.primary'); color: #fff;
  font-size: 22rpx; border-radius: 8rpx; padding: 2rpx 10rpx;
}
.name { font-size: 34rpx; font-weight: 600; color: v-bind('theme.title'); }
.code-row { display: flex; align-items: center; gap: 12rpx; margin-top: 8rpx; }
.code { font-size: 22rpx; color: v-bind('theme.sub'); }
.copy { font-size: 22rpx; color: v-bind('theme.primaryBtn'); }
.switch-btn {
  font-size: 26rpx; color: #fff;
  background: v-bind('theme.primaryBtn'); border-radius: 12rpx;
  padding: 12rpx 20rpx;
}

.quota { display: flex; align-items: center; margin-top: 24rpx; }
.quota-label { font-size: 26rpx; color: v-bind('theme.title'); width: 170rpx; }
.bar {
  flex: 1; height: 16rpx; border-radius: 8rpx;
  background: #f2f0ea; overflow: hidden;
}
.bar-inner { height: 100%; background: v-bind('theme.primaryBtn'); border-radius: 8rpx; }
.quota-num { font-size: 24rpx; color: v-bind('theme.sub'); margin-left: 16rpx; width: 80rpx; text-align: right; }

.meta-line { display: block; font-size: 26rpx; color: v-bind('theme.title'); margin-top: 24rpx; }
.meta-link { color: v-bind('theme.primaryBtn'); }

.btn-upgrade {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 16rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-upgrade::after { border: none; }
.dissolve {
  display: block; text-align: center; margin-top: 24rpx;
  font-size: 28rpx; color: v-bind('theme.danger');
  text-decoration: underline;
}

.grid-card { padding: 28rpx 16rpx; }
.grid-row { display: flex; justify-content: space-around; }
.grid-row.wrap { flex-wrap: wrap; row-gap: 36rpx; }
.grid-item {
  font-size: 26rpx; color: v-bind('theme.title');
  width: 33%; text-align: center;
  padding: 8rpx 0;
}
.grid-row.wrap .grid-item { width: 25%; }
.grid-item.vip { color: v-bind('theme.sub'); }
.vip-tag {
  display: inline-block; margin-left: 6rpx;
  font-size: 18rpx; color: #8a6a1f;
  background: #f3e3b3; border-radius: 6rpx; padding: 2rpx 8rpx;
}
</style>
