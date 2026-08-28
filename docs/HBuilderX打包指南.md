# HBuilderX 打包指南（Android APK + 鸿蒙 HAP）

> 适用：已在一台 **Windows / Mac** 电脑上装好 HBuilderX（装最新正式版即可，需注册并登录 DCloud 账号）。
> 日常开发在 Linux 上进行（H5 验证），本指南只管「出安装包」这一步。

## 0. 拿代码

把整个 `共享厨房` 目录拷到那台电脑（U盘/网盘/git 都行），只需要 `client/` 子目录。

## 1. 打开项目

文件 → **打开目录** → 选中 `client`（以 `package.json` 所在的那层为准）。HBuilderX 会识别为 uni-app Vue3 项目。

## 2. 重装依赖（必做，别跳过）

菜单「工具 → 命令行」或项目右键打开终端：

```bash
npm install
```

> ⚠️ 不要把 Linux 上已装好的 `node_modules` 拷过去——esbuild 等原生二进制跨平台不兼容，必须重新 install。`sass` 已写入 devDependencies，会一起装上。

## 3. 真机调试 Android（先跑起来再打包）

1. 安卓手机：设置 → 开发者选项 → 打开 **USB 调试**，数据线连电脑；
2. HBuilderX：**运行 → 运行到手机或模拟器 → 运行到 Android App 基座**；
3. 首次会往手机装「HBuilder 标准基座」，装完自动启动 App，改代码保存即热刷新。

## 4. 云打包出 APK（不需要自己搞证书）

**发行 → 原生 App-云打包**，按下面选：

| 项 | 选择 |
|----|------|
| 平台 | Android |
| 包名 | 自拟且全局唯一，如 `com.syg.sharedkitchen`（上架后不可改） |
| 证书 | 测试期选 **使用公共测试证书**（正式上架再换成自己的证书） |
| 打包类型 | 测试用「打正式包」也勾上（区别只在是否混淆，均可装） |
| 渠道/广告 SDK | 全不勾 |

点「打包」，云端排队几分钟后，控制台给出 APK 下载地址，传到手机安装即可。
首次打包会提示 **生成 DCloud appid**，一路确认（manifest.json 里 `appid` 会自动填上）。

## 5. 鸿蒙 HAP（HarmonyOS NEXT）

需要 **华为开发者账号（实名）**。两条路线：

**路线 A：HBuilderX 云打包（推荐，不用装 DevEco）**

1. 在 [AppGallery Connect](https://developer.huawei.com/consumer/cn/service/josp/agc/index.html)（AGC）创建「项目」和「应用」，包名与 Android 一致或自拟；
2. AGC 申请 **调试证书/发布证书**（.p12/.cer/.p7b 三个文件）；
3. HBuilderX：**发行 → App-HarmonyOS-云打包**，选包名、上传证书 → 打包 → 下载 HAP；
4. 真机：设置里进开发者模式，`hdc` 安装或 DevEco 拖装。

**路线 B：本地 DevEco（鸿蒙深度调试用）**

```bash
# 在 client 目录执行，产物是可被 DevEco 打开的 HarmonyOS 工程
npm run build:mp-harmony
```

用 **DevEco Studio** 打开产物目录 → 配置签名（File → Project Structure → Signing Configs，登录华为账号可自动生成调试签名）→ 运行到鸿蒙真机/模拟器。

## 6. 真机连后端的坑（必看）

App 里 `localhost` 指手机自己，**连不到你电脑上的后端**。真机调试时：

1. 手机和电脑连同一个 Wi-Fi；
2. 查电脑局域网 IP（Linux：`ip addr | grep "inet 192"`）；
3. 客户端的 API 地址统一改到一处（M1-T5 会加 `client/src/api/config.ts`，届时把地址改成 `http://<电脑IP>:8080` 即可）；
4. 后端放行：当前后端无防火墙限制则直接通；不通时 Linux 上 `sudo ufw allow 8080`（需要密码）。

## 7. 常见坑速查

| 症状 | 原因/解法 |
|------|-----------|
| 编译报编译器版本不匹配 | HBuilderX 版本太旧 → 升级到最新正式版 |
| 打开项目无 uni-app 菜单 | 打开的目录层级不对，必须是含 `package.json` 的 `client` 层 |
| 打包排队很久 | 云端高峰正常，10-30 分钟 |
| 公共证书能上架吗 | 不能，上架前在 HBuilderX 里生成/导入自己的正式证书 |
| 鸿蒙真机装不上 | 未进开发者模式 / 证书不是该设备的调试证书 |
| App 白屏打不开接口 | 见第 6 节，API 地址没改局域网 IP |

## 8. 分工约定

- Linux（我这边）：写代码、跑后端、H5 回归；
- 打包机（你那边）：每次要新安装包时，`git pull`（或拷贝最新 `client/`）→ 重跑第 4/5 步，其余不用动。
