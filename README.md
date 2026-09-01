# 共享厨房 · 情侣/家庭点菜系统

安卓 + 鸿蒙双端 App（uni-app）+ Spring Boot 后端 + Web 管理后台。
**当前状态：M1–M8 全功能完成；APK 由 Linux 侧 Capacitor 直出；全功能免费（VIP 已下线）；支持深/浅色/跟随系统主题。**

## 快速导航

| 需要什么 | 去哪里 |
|----------|--------|
| 出 APK/HAP（Windows） | [docs/HBuilderX打包指南.md](docs/HBuilderX打包指南.md) |
| 产品需求 / 页面蓝本 / 实施计划 | docs/ 下三份方案文档 |
| 全部踩坑与测试 SOP | [docs/M1进度与尖峰记录.md](docs/M1进度与尖峰记录.md) |
| UI 蓝本（原版截图逐页拆解） | [docs/页面功能分析.md](docs/页面功能分析.md) |

## 本地运行

### 后端（Java 21 + Maven，SQLite 开发库免安装）

```bash
cd server
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ~/.local/opt/apache-maven-3.9.11/bin/mvn -B -q -DskipTests package
setsid nohup java -jar target/server-0.1.0.jar --spring.profiles.active=dev > /tmp/sk-server.log 2>&1 &
curl http://localhost:8080/api/health   # {"code":0,...} 即成功
```

- 生产切 PostgreSQL：`--spring.profiles.active=prod` + 环境变量 `DB_URL/DB_USER/DB_PASSWORD`
- 后台控制台：`http://localhost:8080/admin/index.html`（admin / admin123）

### 客户端（uni-app，H5 预览）

```bash
cd client
npm install            # 报 peer 冲突加 --legacy-peer-deps
npm run dev:h5         # 浏览器打开提示的地址
```

- 后端地址唯一入口：`src/api/config.ts`（App 内也可在「我 → 服务器设置」运行时改）
- Android APK 直出（Linux 可用）：**必须用 JDK 17**（默认 Java 26 会让 Gradle 8.2.1 报 Unsupported class file major version 70）

```bash
npx cap sync android
cd android && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew assembleDebug
# 产物: android/app/build/outputs/apk/debug/app-debug.apk
```

### 回归测试

```bash
./scripts/smoke-test.sh http://localhost:8080   # 后端全链路，SMOKE PASS 即交付态
cd client && node e2e-selftest.mjs              # 浏览器自测(需先起后端+dev:h5)，14 断言
```

## 关键约定

- 金额一律「分」整数存储；UI 色值只用 `client/src/styles/theme.ts`（reactive，支持深/浅色/跟随系统，页面 `v-bind('theme.xxx')` 自动跟随）；弹层/输入只用 `components/` 自研组件（**禁 emoji 图标、禁原生 ActionSheet/showModal editable**）
- 表结构一律走 Flyway（`db/migration/{sqlite,postgresql}`），禁改历史迁移
- 安卓端行为：Capacitor 插件 `@capacitor/app`（返回键：栈内返回/末栈退出）、`@capacitor/local-notifications`（临期提醒进系统通知栏）、`@capacitor/status-bar`（状态栏跟随主题）——**插件大版本必须与 `@capacitor/core` 一致（当前 6.x）**
- 每个后端改动后必跑 smoke-test；客户端改动后 `npm run build:h5` + `npx vue-tsc --noEmit` 必须过
- 测试账号：店长 `13800002222` / 家人 `13900003333`，验证码万能码 `1234`

## 开源协议

[AGPL-3.0](./LICENSE)

- 你可以自由使用、修改、部署本项目（包括自建服务端），但**基于本项目修改后对外提供网络服务或分发衍生作品时，必须以 AGPL-3.0 开源你的修改**；
- 版权人（本项目作者）不受本协议约束，保留商业化（托管服务、广告、赞助等）的全部权利。

### 内置 AI 模型说明

App 的「拍照/选图自动去背景」功能内置 [U-2-Net](https://github.com/xuebinqin/U-2-Net) 轻量版模型（`u2netp.onnx`，4.4MB，Apache-2.0），经 onnxruntime-web 在设备本地推理，**不上传任何图片到网络**。第三方资产详见 [THIRD-PARTY-NOTICES.md](./THIRD-PARTY-NOTICES.md)。
