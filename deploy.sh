#!/usr/bin/env bash
# ============================================================================
# 共享厨房 公网部署脚本（全程走国内镜像源：Docker 镜像 + Maven 依赖 + 运行镜像）
#
# 用法（在服务器上、项目根目录执行）：
#   chmod +x deploy.sh
#   ./deploy.sh kitchen.example.com            # 首次部署（域名作唯一必填参数）
#   ./deploy.sh kitchen.example.com --with-db  # 首次部署并用 Postgres（默认 SQLite）
#   ./deploy.sh                                # 升级（git pull + 重建 + 平滑重启）
#
# 交互缺失时自动生成 JWT_SECRET；GLM_API_KEY 可后续加进 .env 再 ./deploy.sh 升级。
# ============================================================================
set -euo pipefail

# ---------------- 可按需修改的镜像源 ----------------
DOCKER_MIRROR="${DOCKER_MIRROR:-docker.m.daocloud.io}"   # Docker Hub 镜像（registry 命名空间方式）
MAVEN_MIRROR="${MAVEN_MIRROR:-https://maven.aliyun.com/repository/public}"

IMAGES=(
  "library/maven:3.9-eclipse-temurin-21|maven:3.9-eclipse-temurin-21"
  "library/eclipse-temurin:21-jre|eclipse-temurin:21-jre"
  "library/caddy:2-alpine|caddy:2-alpine"
  "library/postgres:16-alpine|postgres:16-alpine"
  "library/alpine:3.20|alpine:3.20"
)

DOMAIN=""
WITH_DB=false
UPGRADE=false

say()  { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m  ✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------- 参数解析 ----------------
for arg in "$@"; do
  case "$arg" in
    --with-db) WITH_DB=true ;;
    --help|-h) grep '^#   ' "$0" | sed 's/^#   //'; exit 0 ;;
    http*) DOMAIN="$arg" ;;
    "") ;;
    *) [ -z "$DOMAIN" ] && DOMAIN="$arg" || die "多余参数: $arg" ;;
  esac
done

command -v docker >/dev/null || die "未安装 docker（curl -fsSL https://get.docker.com | sh）"
docker compose version >/dev/null 2>&1 || die "缺少 docker compose 插件"

# ---------------- 升级模式 / 首次模式 ----------------
if [ -z "$DOMAIN" ] && [ -f .env ]; then
  UPGRADE=true
  DOMAIN=$(grep -E '^DOMAIN=' .env | cut -d= -f2-)
  say "升级模式：沿用 .env 中的 DOMAIN=$DOMAIN"
else
  [ -z "$DOMAIN" ] && die "用法: ./deploy.sh <域名> [--with-db]   （无参数且无 .env 时视为升级）"
fi

# ---------------- .env 生成 / 校验 ----------------
if [ ! -f .env ]; then
  say "生成 .env"
  JWT=$(openssl rand -base64 48 2>/dev/null | tr -d '\n' || head -c 64 /dev/urandom | base64 | tr -d '\n')
  cat > .env <<EOF
DOMAIN=$DOMAIN
JWT_SECRET=$JWT
GLM_API_KEY=
SPRING_PROFILES_ACTIVE=dev
DB_URL=
DB_USER=sharedkitchen
DB_PASSWORD=
EOF
  chmod 600 .env
  ok ".env 已生成（GLM_API_KEY 留空，需要票据识别时编辑 .env 后重新部署）"
else
  grep -qE '^DOMAIN=.' .env || die ".env 缺 DOMAIN"
  grep -qE '^JWT_SECRET=.{30,}' .env || die ".env 的 JWT_SECRET 为空或太短（openssl rand -base64 48）"
  ok ".env 校验通过"
fi

# ---------------- 镜像准备（全部走国内镜像） ----------------
say "拉取基础镜像（经 $DOCKER_MIRROR）"
for item in "${IMAGES[@]}"; do
  src="${item%%|*}"; dst="${item##*|}"
  if $WITH_DB || [ "$dst" != "postgres:16-alpine" ]; then
    if ! docker image inspect "$dst" >/dev/null 2>&1; then
      docker pull "$DOCKER_MIRROR/$src" >/dev/null 2>&1 \
        || docker pull "$src" >/dev/null 2>&1 \
        || die "拉取 $src 失败（检查网络或换 DOCKER_MIRROR）"
      docker tag "$DOCKER_MIRROR/$src" "$dst" 2>/dev/null || true
      ok "$dst"
    else
      ok "$dst（已存在）"
    fi
  fi
done

# ---------------- 运行镜像：直接改 compose 用镜像命名空间 ----------------
say "配置 compose 使用镜像源"
# 让 FROM maven:... / eclipse-temurin:... / caddy:2-alpine 命中本地 tag；
# Docker 拉取顺序=本地→registry，本地已有则不再访问外网。
docker image inspect maven:3.9-eclipse-temurin-21 >/dev/null || die "本地缺 maven 镜像"

# ---------------- 构建并启动 ----------------
say "构建服务端镜像（Maven 依赖走 $MAVEN_MIRROR，已写入 Dockerfile）"
export DOMAIN JWT_SECRET GLM_API_KEY SPRING_PROFILES_ACTIVE DB_URL DB_USER DB_PASSWORD
export $(grep -E '^(DOMAIN|JWT_SECRET|GLM_API_KEY|SPRING_PROFILES_ACTIVE|DB_URL|DB_USER|DB_PASSWORD)=' .env | xargs -d '\n') 2>/dev/null || true

BUILD_ARGS=()
if [ "$MAVEN_MIRROR" != "https://maven.aliyun.com/repository/public" ]; then
  BUILD_ARGS+=(--build-arg "MAVEN_MIRROR=$MAVEN_MIRROR")
fi

docker compose build "${BUILD_ARGS[@]}" server
say "启动服务"
if $WITH_DB; then
  grep -qE '^SPRING_PROFILES_ACTIVE=prod' .env || die "--with-db 需要在 .env 设 SPRING_PROFILES_ACTIVE=prod 和 DB_PASSWORD"
  docker compose --profile prod up -d
else
  docker compose up -d
fi

# ---------------- 健康检查 ----------------
say "等待服务就绪（最长 120s）"
for i in $(seq 1 60); do
  if curl -sf --noproxy '*' "http://127.0.0.1:8080/api/health" >/dev/null 2>&1 \
     || docker compose exec -T server sh -c 'curl -sf http://127.0.0.1:8080/api/health' >/dev/null 2>&1; then
    ok "服务端 UP"
    break
  fi
  [ "$i" = "60" ] && { docker compose logs --tail 30 server; die "健康检查超时，日志见上"; }
  sleep 2
done

say "验证 HTTPS 入口"
sleep 5   # 给 Caddy 签证书的时间
if curl -sk --noproxy '*' "https://127.0.0.1/api/health" -H "Host: $DOMAIN" | grep -q '"status":"UP"'; then
  ok "HTTPS 反代 UP"
fi

echo
ok "部署完成:  https://$DOMAIN"
echo "    App 端:   「我 → 服务器设置」填 https://$DOMAIN"
echo "    管理后台: https://$DOMAIN/admin/"
echo "    日志:     docker compose logs -f server"
echo "    升级:     git pull && ./deploy.sh"
