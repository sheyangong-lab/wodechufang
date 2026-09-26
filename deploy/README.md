# 公网部署指南（Docker + 自动 HTTPS）

## 快速开始（一键脚本，推荐）

```bash
git clone https://github.com/sheyangong-lab/wodechufang.git && cd wodechufang
chmod +x deploy.sh
./deploy.sh kitchen.example.com            # 首次部署（域名必填）
./deploy.sh kitchen.example.com --with-db  # 首次部署 + Postgres
./deploy.sh                                # 之后升级（git pull 后重跑）
```

脚本自动完成：生成 .env（含随机 JWT_SECRET）→ 经国内镜像拉取全部基础镜像 →
构建（Maven 走阿里云源）→ 启动 → 健康检查 → 输出访问地址。
以下手动步骤供理解原理或故障排查。

## 0. 前置条件

- 一台公网服务器（2C4G 起步即可），已装 Docker + Docker Compose
- 一个域名（如 `kitchen.example.com`），DNS A 记录指向服务器公网 IP
- 服务器安全组/防火墙放行 **80、443** 端口（80 用于证书签发挑战）

## 1. 传输项目到服务器

```bash
# 服务器上拉代码（推荐）或 rsync 本地代码（不含 node_modules/target）
git clone https://github.com/sheyangong-lab/wodechufang.git sharedkitchen && cd sharedkitchen
```

## 2. 配置环境变量

```bash
cp deploy/env.example .env
vi .env
# 必填三项：
#   DOMAIN=kitchen.example.com          ← 你的域名
#   JWT_SECRET=$(openssl rand -base64 48)
#   GLM_API_KEY=...                     ← 可选，票据识别用
```

## 3. 国内服务器：拉镜像加速（海外服务器跳过）

Docker Hub 直连常被墙，两种解法任选：

```bash
# 方案A：配置全局镜像加速（推荐，需 root）
sudo tee /etc/docker/daemon.json <<'EOF'
{ "registry-mirrors": ["https://docker.m.daocloud.io"] }
EOF
sudo systemctl restart docker

# 方案B：手动从镜像站拉取并重打 tag（无需 root）
docker pull docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21
docker tag  docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 maven:3.9-eclipse-temurin-21
docker pull docker.m.daocloud.io/library/eclipse-temurin:21-jre
docker tag  docker.m.daocloud.io/library/eclipse-temurin:21-jre eclipse-temurin:21-jre
docker pull docker.m.daocloud.io/library/caddy:2-alpine
docker tag  docker.m.daocloud.io/library/caddy:2-alpine caddy:2-alpine
# 用到 Postgres 时再拉 postgres:16-alpine，同理
```

## 4. 构建并启动

```bash
docker compose up -d --build
docker compose logs -f server    # 看启动日志，出现 Started ... 即成功
curl https://$DOMAIN/api/health  # {"code":0,...,"status":"UP"}
```

Caddy 会自动向 Let's Encrypt 申请证书并续期，无需手动管证书。

## 5. 数据库档位

- **SQLite（默认）**：数据存在 `server-data` 卷（`/app/data/sharedkitchen.db` + uploads 图片 + apk 更新包），情侣两人场景足够。
- **Postgres**：`.env` 里设 `SPRING_PROFILES_ACTIVE=prod`、`DB_URL=jdbc:postgresql://db:5432/sharedkitchen`、`DB_USER`、`DB_PASSWORD`，再 `docker compose up -d --build`。

## 6. 备份与升级

```bash
# 备份（SQLite 档）
docker run --rm -v shared-kitchen_server-data:/data -v $PWD:/backup alpine \
  tar czf /backup/server-data-$(date +%F).tgz -C /data .

# 升级版本
git pull && docker compose up -d --build   # 表结构迁移由 Flyway 自动执行

# 看实时日志 / 重启
docker compose logs -f server
docker compose restart server
```

## 7. 上传 App 更新包

构建好新 APK 后（本机出包流程见 README），上传到服务器并更新版本元数据：

```bash
scp client/android/app/build/outputs/apk/debug/app-debug.apk server:/tmp/
ssh server 'docker cp /tmp/app-debug.apk $(docker compose ps -q server):/app/data/apk/app-1.3.3.apk'
# 然后更新容器内 /app/data/apk/version.json（或直接进容器跑 scripts/publish-apk.sh 的逻辑）
```

App 内「我 → 检查更新」即可拉到新包（HTTPS 下载，防中间人替换 APK）。

## 7.4 Lucky 证书模式（自动从 Lucky 拉 ACME 证书）

如果你已经在 Lucky 上配好了 ACME 自动证书（它每天凌晨 5:06 自动检测续期），
可以让部署直接复用 Lucky 的证书，Caddy 不再自己申请：

**Lucky 侧**：SSL 证书模块 → 你的证书 → 底部启用**映射** → 目标目录选一个
部署机能访问的目录（如 `/mnt/lucky-certs`），Lucky 会把证书文件写成
`域名.pem`/`域名.key`（或 .crt/.key，以实际为准）。

**部署机**：

```bash
./deploy.sh kitchen.example.com --lucky /mnt/lucky-certs
```

脚本会校验证书/私钥文件存在（`域名.crt` 或 `域名.pem`）、拷入项目
`deploy/lucky-certs/`（gitignore）、注入 Caddy `tls` 手动证书段并挂载进容器。
Caddy 监听证书文件变化——**Lucky 每天续期后自动热加载，无需重启任何东西**。
文件名对不上时按报错提示核对映射目录里的实际文件名即可。

## 7.5 无域名部署（IP + Lucky 端口映射）

不绑域名时 Caddy 用**自签证书**（加密强度相同，只是证书签发者不是公共 CA）：

```bash
./deploy.sh 你的服务器公网IP        # 例如 ./deploy.sh 203.0.113.10
```

Lucky 配置：把外部端口（如 8443）的 TCP 转发到部署机 443，App 里填
`https://公网IP:外部端口`。

手机信任自签证书（可选但推荐，免掉"不安全"警告、App 静默可用）：

```bash
# 部署机导出自签根证书（脚本结束时也会打印这条命令）
docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt > sharedkitchen-root.crt
# 传到手机 → 设置 → 安全 → 更多安全设置 → 加密与凭据 → 安装证书 → CA 证书
```

注意：TEST-NET 段（203.0.113.x 等）仅文档示例；ACME 无法给纯 IP 签公共证书，
所以 IP 模式只能自签，这是 CA 行业规则不是工具限制。

## 8. 客户端

- App 版本 ≥ **1.3.2** 起默认走 `https://域名`（见 `client/src/api/config.ts` 的 DEFAULT_API_BASE，**部署前改成你的真实域名再出包**）
- 老版本 App（http 时代）：在「我 → 服务器设置」里手动填 `https://你的域名` 即可无缝迁移
- 局域网模式不受影响：Android 明文仅对 192.168/10./172.16 网段放行

## 安全说明（为什么这么部署）

- 全链路 HTTPS：客户端 token/验证码/图片、admin 账密、APK 更新下载全部加密传输；APK 下载链路此前可被同网段中间人替换任意安装，是本次改造的最高风险点
- 8080 不对公网暴露（仅 Caddy 容器内网可连），调试时在 compose 里临时放开 `127.0.0.1:8080`
- JWT_SECRET 必须 48+ 字节随机值且不进 git；泄露等同于任何人可伪造任意用户登录态
- P2P 直连同步（局域网 TCP 51820）仍是明文，属已知设计边界——局域网内可信设备间使用，公网不同步数据
