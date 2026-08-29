import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Capacitor 唯一配置源（本文件优先级高于 capacitor.config.json，两处并存时
 * .json 会被静默忽略——曾因此导致 CapacitorHttp/androidScheme 失效、APK 全部
 * 请求被 mixed content 拦截，故删除 .json 只保留此文件）。
 *
 * - androidScheme: 'http' 使页面 origin 为 http://localhost，与 http 后端同源，
 *   避免 WebView mixed content 拦截（https 页面禁发 http 请求）。
 * - CapacitorHttp 把 XHR/fetch 转发到原生层执行，绕过 WebView 网络限制。
 */
const config: CapacitorConfig = {
  appId: 'com.syg.sharedkitchen',
  appName: '共享厨房',
  webDir: 'dist/build/h5',
  server: {
    androidScheme: 'http',
  },
  plugins: {
    CapacitorHttp: {
      enabled: true,
    },
  },
};

export default config;
