# 打包机必读（Windows / Mac）

压缩包解压后就是一个完整的 `client` uni-app 项目。按顺序做：

## 1. 重装依赖（必须）

> 包里**没有** `node_modules`（跨平台二进制不兼容，带了也会炸）。

```bash
cd client
npm install
```

要求电脑已装 Node.js（LTS 版，nodejs.org 下载）。

## 2. HBuilderX 打开与出包

1. HBuilderX（最新正式版，登录 DCloud 账号）→ 文件 → 打开目录 → 选 `client`；
2. Android APK：**发行 → 原生 App-云打包** → Android、公共测试证书、包名自拟（如 `com.syg.sharedkitchen`）；
3. 鸿蒙 HAP：**发行 → App-HarmonyOS-云打包**（需华为开发者账号 + AGC 证书），或 `npm run build:mp-harmony` 后用 DevEco Studio 本地打包。

## 3. 真机连后端（调试必看）

后端跑在开发机 `192.168.1.187:8080`（写死在 `src/api/config.ts`，IP 变了就改这一处）。

- 真机调试：手机与开发机连**同一 Wi-Fi**；
- 浏览器调试：`npm run dev:h5`，地址用 `localhost:8080` 也能通（后端已开 CORS）；
- 开发环境验证码是万能码 **1234**，任何 11 位手机号都能注册/登录。

完整说明见仓库 `docs/HBuilderX打包指南.md`。
