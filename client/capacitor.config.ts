import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Capacitor 唯一配置源（本文件优先级高于 capacitor.config.json，两处并存时
 * .json 会被静默忽略——曾因此导致 CapacitorHttp/androidScheme 失效、APK 全部
 * 请求被 mixed content 拦截，故删除 .json 只保留此文件）。
 *
 * - androidScheme: 'https'（2026-09 起）：后端公网部署走 Caddy 自动 HTTPS，
 *   明文 http 时代结束。https origin 下 WebView 允许发 https 请求，与后端
 *   https://域名 同为 https，无 mixed content 问题。注意 https origin 下
 *   localStorage 的 key 不变（scheme 不影响 origin 里的 localhost 部分）。
 * - CapacitorHttp 把 XHR/fetch 转发到原生层执行（文件上传仍走页面内 fetch）。
 */
const config: CapacitorConfig = {
  appId: 'com.syg.sharedkitchen',
  appName: '共享厨房',
  webDir: 'dist/build/h5',
  server: {
    androidScheme: 'https',
  },
  plugins: {
    CapacitorHttp: {
      enabled: true,
    },
  },
};

export default config;
