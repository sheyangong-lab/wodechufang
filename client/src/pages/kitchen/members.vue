<script setup lang="ts">
import { theme } from '@/styles/theme';
import { kitchenApi, ROLE_LABELS } from '@/api/kitchen';
import type { KitchenDetail, MemberView } from '@/api/kitchen';
import { loadUser } from '@/api/auth';
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import InputDialog from '@/components/input-dialog.vue';

const detail = ref<KitchenDetail | null>(null);
const kitchenId = ref<number | null>(null);
const myUserId = ref<number | null>((loadUser() as { id?: number } | null)?.id ?? null);

const isOwner = computed(() => detail.value?.kitchen.myRole === 'OWNER');

onLoad((query) => {
  kitchenId.value = query && query.id ? Number(query.id) : null;
  refresh();
});

async function refresh() {
  if (!kitchenId.value) return;
  try {
    detail.value = await kitchenApi.detail(kitchenId.value);
  } catch {
    // 错误 toast 已统一弹出
  }
}

/** 展示名：自定义名字优先，缺省账号昵称 */
function displayName(m: MemberView) {
  return m.alias || m.nickname;
}

/** 展示职称：自定义职称优先，缺省按角色 */
function displayTitle(m: MemberView) {
  return m.title || ROLE_LABELS[m.role] || m.role;
}

function fmtTime(iso: string) {
  return iso.length >= 16 ? iso.slice(0, 16).replace('T', ' ') : iso;
}

// ----- 编辑（名字/职称；主账号可设全权限）-----
const editVisible = ref(false);
const editTarget = ref<MemberView | null>(null);
const editAlias = ref('');
const editTitle = ref('');
const editFull = ref(false);
/** 主账号编辑别人 → 出全权限开关；编辑自己（是主账号）→ 不出（本身即全权限） */
const showFullSwitch = computed(() => {
  if (!editTarget.value || !isOwner.value) return false;
  return editTarget.value.role !== 'OWNER';
});

function openEdit(m: MemberView) {
  editTarget.value = m;
  editAlias.value = m.alias;
  editTitle.value = m.title;
  editFull.value = m.fullAccess === 1;
  editVisible.value = true;
}

function saveEdit() {
  if (!editTarget.value || !kitchenId.value) return;
  const payload: { alias?: string; title?: string; fullAccess?: number } = {
    alias: editAlias.value.trim(),
    title: editTitle.value.trim(),
  };
  if (showFullSwitch.value) payload.fullAccess = editFull.value ? 1 : 0;
  kitchenApi
    .updateMember(kitchenId.value, editTarget.value.userId, payload)
    .then(() => {
      editVisible.value = false;
      uni.showToast({ title: '已保存', icon: 'none' });
      refresh();
    })
    .catch(() => {});
}
</script>

<template>
  <view class="page">
    <view v-if="detail" class="list card">
      <view
        v-for="m in detail.members"
        :key="m.userId"
        class="member-row"
        hover-class="press-dim"
        @tap="openEdit(m)"
      >
        <view class="avatar">
          <image class="avatar-img" src="/static/icons/person.png" mode="aspectFit" />
        </view>
        <view class="info">
          <view class="name-row">
            <text class="name">{{ displayName(m) }}</text>
            <text class="role" :class="{ owner: m.role === 'OWNER', full: m.fullAccess === 1 }">
              {{ displayTitle(m) }}
            </text>
            <text v-if="m.fullAccess === 1 && m.role !== 'OWNER'" class="full-tag">全权限</text>
            <text v-if="m.userId === myUserId" class="me-tag">我</text>
          </view>
          <text class="time">{{ fmtTime(m.joinedAt) }} 加入厨房</text>
        </view>
        <text class="edit-chev">›</text>
      </view>
      <view class="count">
        <text class="count-text">共{{ detail.members.length }}个成员</text>
      </view>
    </view>

    <view v-if="detail" class="perm-legend card">
      <text class="legend-title">角色说明</text>
      <text class="legend-line">主账号：拥有全部权限，可授予成员全权限、解散厨房</text>
      <text class="legend-line">成员账号：点单、加菜谱、处理订单、记账</text>
      <text class="legend-line">全权限成员：额外可修改厨房信息（名称/公告）</text>
      <text class="legend-line">点成员卡片可自定义名字与职称</text>
    </view>

    <!-- 编辑弹层 -->
    <view v-if="editVisible" class="mask" @tap="editVisible = false">
      <view class="sheet" @tap.stop>
        <text class="sheet-title">编辑成员</text>
        <text class="sheet-sub">{{ editTarget?.nickname }}</text>

        <view class="edit-row">
          <text class="edit-label">自定义名字</text>
          <input v-model="editAlias" class="edit-input" maxlength="20" placeholder="留空显示昵称" placeholder-class="ph" />
        </view>
        <view class="edit-row">
          <text class="edit-label">职称</text>
          <input v-model="editTitle" class="edit-input" maxlength="10" placeholder="如：主厨 / 采购员" placeholder-class="ph" />
        </view>
        <view v-if="showFullSwitch" class="edit-row">
          <view class="edit-label-col">
            <text class="edit-label">全权限</text>
            <text class="edit-hint">可修改厨房信息等管理操作</text>
          </view>
          <switch :checked="editFull" color="#EFA63C" @change="(e: any) => (editFull = e.detail.value)" />
        </view>

        <button class="sheet-btn" hover-class="press-sink" @tap="saveEdit">保存</button>
        <text class="sheet-cancel" hover-class="press-dim" @tap="editVisible = false">取消</text>
      </view>
    </view>
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
.list { padding: 8rpx 32rpx; }
.member-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid v-bind('theme.divider');
}
.avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%;
  background: v-bind('theme.primaryLight');
  display: flex; align-items: center; justify-content: center;
}
.avatar-img { width: 48rpx; height: 48rpx; }
.info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
.name { font-size: 30rpx; font-weight: 600; color: v-bind('theme.title'); }
.role {
  font-size: 20rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider'); border-radius: 8rpx;
  padding: 2rpx 10rpx;
}
.role.owner {
  color: #fff; background: v-bind('theme.primaryBtn');
  border-color: v-bind('theme.primaryBtn');
}
.role.full {
  color: v-bind('theme.primaryBtn'); border-color: v-bind('theme.primaryBtn');
}
.full-tag {
  font-size: 18rpx; color: v-bind('theme.primaryBtn');
  background: v-bind('theme.primaryLight');
  border-radius: 8rpx; padding: 2rpx 8rpx;
}
.me-tag {
  font-size: 18rpx; color: v-bind('theme.sub');
  border: 2rpx solid v-bind('theme.divider');
  border-radius: 8rpx; padding: 2rpx 8rpx;
}
.time { font-size: 24rpx; color: v-bind('theme.sub'); }
.edit-chev { color: v-bind('theme.sub'); font-size: 34rpx; }
.count { padding: 24rpx 0; text-align: center; }
.count-text { font-size: 24rpx; color: v-bind('theme.sub'); }

.perm-legend { padding: 24rpx 32rpx; margin-top: 20rpx; }
.legend-title { display: block; font-size: 26rpx; font-weight: 600; color: v-bind('theme.title'); margin-bottom: 12rpx; }
.legend-line { display: block; font-size: 24rpx; color: v-bind('theme.sub'); line-height: 42rpx; }

.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(15, 12, 6, 0.6);
  z-index: 999;
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.2s ease both;
}
.sheet {
  width: 620rpx;
  background: v-bind('theme.card');
  border-radius: 28rpx;
  padding: 40rpx 36rpx 28rpx;
  animation: pop 0.2s ease both;
}
.sheet-title { display: block; text-align: center; font-size: 32rpx; font-weight: 600; color: v-bind('theme.title'); }
.sheet-sub { display: block; text-align: center; font-size: 22rpx; color: v-bind('theme.sub'); margin-top: 6rpx; }
.edit-row {
  display: flex; align-items: center; justify-content: space-between; gap: 20rpx;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 26rpx 0;
}
.edit-label-col { display: flex; flex-direction: column; gap: 4rpx; }
.edit-label { font-size: 28rpx; color: v-bind('theme.title'); }
.edit-hint { font-size: 22rpx; color: v-bind('theme.sub'); }
.edit-input { flex: 1; text-align: right; font-size: 28rpx; color: v-bind('theme.title'); }
.ph { color: v-bind('theme.sub'); }
.sheet-btn {
  margin-top: 32rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 84rpx;
}
.sheet-btn::after { border: none; }
.sheet-cancel { display: block; text-align: center; padding: 22rpx 0 6rpx; font-size: 28rpx; color: v-bind('theme.sub'); }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes pop { from { transform: scale(0.94); opacity: 0.6; } to { transform: scale(1); opacity: 1; } }
</style>
