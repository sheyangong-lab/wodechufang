# 共享厨房 · 情侣/家庭点菜系统

安卓 + 鸿蒙双端 App（uni-app）+ Spring Boot 后端 + Web 管理后台。
**当前状态：M1–M7 全功能完成并经真机/双端/后台三通道验证；M8 出包待 Windows HBuilderX（见 docs/HBuilderX打包指南.md）。**

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

- 后端地址唯一入口：`src/api/config.ts` 的 `API_BASE`
- Android APK 直出（Linux 可用）：`npx cap sync android && cd android && ./gradlew assembleDebug`

### 回归测试

```bash
./scripts/smoke-test.sh http://localhost:8080   # 28 用例全链路，SMOKE PASS 即交付态
```

## 关键约定

- 金额一律「分」整数存储；UI 色值只用 `client/src/styles/theme.ts`；弹层/输入只用 `components/` 两个自研组件（**禁 emoji 图标、禁原生 ActionSheet/showModal editable**）
- 每个后端改动后必跑 smoke-test；客户端改动后 `npm run build:h5` 必须过
- 测试账号：店长 `13800002222` / 家人 `13900003333`，验证码万能码 `1234`
