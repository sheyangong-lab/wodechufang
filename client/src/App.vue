<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
import { initTheme } from "@/styles/theme";
import { App as CapApp } from "@capacitor/app";
import { flushQueue, queueSize } from "@/utils/offline";

onLaunch(() => {
  initTheme();
  setupAndroidBackButton();
});

onShow(() => {
  // 联网后自动回放离线期间的待同步操作（有队列才触发）
  if (queueSize() > 0) {
    setTimeout(() => {
      flushQueue().then(({ done }) => {
        if (done > 0) {
          uni.showToast({ title: `已同步离线操作 ${done} 条`, icon: "none" });
        }
      });
    }, 2500);
  }
});
onHide(() => {
  console.log("App Hide");
});

/**
 * 安卓硬件返回键接管：页面栈>1 时返回上一页，
 * 只剩首页才退出 App（修复「一按返回直接回桌面」）。
 */
function setupAndroidBackButton() {
  // #ifdef H5
  const cap = (globalThis as Record<string, any>).Capacitor;
  if (!cap?.isNativePlatform?.()) return;
  // 注意：这里必须静态 import —— uni-app h5 构建会把动态 import 生成
  // /assets/../node_modules-xxx 路径，运行时 404 导致返回键接管静默失效
  CapApp.addListener("backButton", () => {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      uni.navigateBack({});
    } else {
      CapApp.exitApp();
    }
  });
  // #endif
}
</script>
<style>
/* ===== 全局按压反馈（hover-class 用），替代 emoji 时代的"点了没反应" ===== */
/* 变暗：用于文字类可点元素 */
.press-dim {
  opacity: 0.55;
}
/* 主题浅黄底：用于按钮/卡片行，跟随深浅色 */
.press-bg {
  background-color: var(--sk-press-bg, #fdf3dc) !important;
}
/* 内陷：用于大按钮 */
.press-sink {
  transform: scale(0.97);
  opacity: 0.85;
}

/* ===== UI 边界感：全局卡片描边 + 阴影兜底（页面 scoped 样式可覆盖） ===== */
.card {
  border: 2rpx solid var(--sk-card-border, rgba(0, 0, 0, 0));
}

/* ===== 深浅色跟随：导航栏 / 页面底色用全局变量兜底 ===== */
/* #ifdef H5 */
body {
  background: var(--sk-bg, #fff8f2);
}
.uni-page-head {
  background-color: var(--sk-nav-bg, #fffbf2) !important;
}
.uni-page-head .uni-page-head__title {
  color: var(--sk-nav-title, #3d3325) !important;
}
.uni-page-head .uni-page-head-hd,
.uni-page-head .uni-page-head-ft {
  color: var(--sk-nav-title, #3d3325) !important;
}
.uni-tabbar {
  background-color: var(--sk-tab-bg, #ffffff) !important;
}
/* 底栏由自绘 CustomTabbar 承担：原生条与其占位符整体隐藏
   （uni.hideTabBar 在真机 H5 端会残留 50px 白色占位） */
.uni-tabbar,
.uni-placeholder {
  display: none !important;
}
/* #endif */
</style>
