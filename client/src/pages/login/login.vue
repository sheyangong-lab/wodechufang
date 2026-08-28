<script setup lang="ts">
import { theme } from '@/styles/theme';
import { authApi, saveLogin } from '@/api/auth';
import { ref } from 'vue';

const phone = ref('');
const smsCode = ref('');
const sending = ref(false);
const submitting = ref(false);

const phoneOk = () => /^1\d{10}$/.test(phone.value.trim());

async function sendCode() {
  if (!phoneOk() || sending.value) return;
  sending.value = true;
  try {
    await authApi.sendSmsCode(phone.value.trim());
    uni.showToast({ title: '已发送（开发环境万能码 1234）', icon: 'none' });
  } finally {
    setTimeout(() => (sending.value = false), 3000);
  }
}

async function doLogin(mode: 'login' | 'register') {
  if (submitting.value) return;
  if (!phoneOk()) {
    uni.showToast({ title: '请输入正确的11位手机号', icon: 'none' });
    return;
  }
  if (!smsCode.value.trim()) {
    uni.showToast({ title: '请输入验证码', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const result =
      mode === 'login'
        ? await authApi.login(phone.value.trim(), smsCode.value.trim())
        : await authApi.register(phone.value.trim(), smsCode.value.trim());
    saveLogin(result);
    uni.showToast({ title: `欢迎，${result.user.nickname}`, icon: 'none' });
    setTimeout(() => uni.switchTab({ url: '/pages/kitchen/kitchen' }), 600);
  } catch (e) {
    // 错误 toast 已在 request 封装中统一弹出
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <view class="page">
    <view class="logo">
      <text class="logo-icon">🍳</text>
      <text class="title">共享厨房</text>
      <text class="sub">一起做饭，一起吃饭</text>
    </view>

    <view class="card form">
      <view class="field">
        <text class="label">手机号</text>
        <input
          v-model="phone"
          class="input"
          type="number"
          maxlength="11"
          placeholder="请输入11位手机号"
          placeholder-class="ph"
        />
      </view>
      <view class="field">
        <text class="label">验证码</text>
        <input
          v-model="smsCode"
          class="input"
          type="number"
          maxlength="6"
          placeholder="请输入验证码"
          placeholder-class="ph"
        />
        <text class="send-btn" :class="{ disabled: !phoneOk() || sending }" @tap="sendCode">
          {{ sending ? '已发送' : '获取验证码' }}
        </text>
      </view>
      <text class="tip">开发环境无需真实短信，验证码输 1234 即可</text>
      <button class="btn-main" :disabled="submitting" @tap="doLogin('login')">登录</button>
      <button class="btn-sub" :disabled="submitting" @tap="doLogin('register')">
        新手机号？注册并登录
      </button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: v-bind('theme.headerGradient');
  padding: 24rpx;
  box-sizing: border-box;
}
.logo {
  display: flex; flex-direction: column; align-items: center;
  padding: 96rpx 0 64rpx;
}
.logo-icon { font-size: 120rpx; }
.title { font-size: 44rpx; font-weight: 700; color: v-bind('theme.title'); margin-top: 16rpx; }
.sub { font-size: 26rpx; color: v-bind('theme.sub'); margin-top: 8rpx; }

.card {
  background: v-bind('theme.card');
  border-radius: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(200, 160, 80, 0.1);
}
.form { padding: 32rpx 40rpx 48rpx; }
.field {
  display: flex; align-items: center;
  border-bottom: 2rpx solid v-bind('theme.divider');
  padding: 28rpx 0;
}
.label { width: 140rpx; font-size: 28rpx; color: v-bind('theme.title'); }
.input { flex: 1; font-size: 28rpx; color: v-bind('theme.title'); }
.ph { color: v-bind('theme.sub'); }
.send-btn {
  font-size: 26rpx; color: v-bind('theme.primaryBtn');
  border: 2rpx solid v-bind('theme.primaryBtn');
  border-radius: 28rpx; padding: 8rpx 20rpx;
}
.send-btn.disabled { color: v-bind('theme.sub'); border-color: v-bind('theme.divider'); }
.tip { display: block; margin-top: 20rpx; font-size: 24rpx; color: v-bind('theme.warning'); }

.btn-main {
  margin-top: 40rpx;
  background: v-bind('theme.primaryBtn'); color: #fff;
  border-radius: 44rpx; font-size: 30rpx; line-height: 88rpx;
}
.btn-main[disabled] { background: v-bind('theme.primaryBtnPressed'); color: #fff; }
.btn-sub {
  margin-top: 20rpx;
  background: transparent; color: v-bind('theme.sub');
  border-radius: 44rpx; font-size: 28rpx; line-height: 80rpx;
  border: 2rpx solid v-bind('theme.divider');
}
.btn-sub::after, .btn-main::after { border: none; }
</style>
