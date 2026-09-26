#!/usr/bin/env bash
# ============================================================================
# 共享厨房 公网部署脚本（全程走国内镜像源：Docker 镜像 + Maven 依赖 + 运行镜像）
#
# 用法（在服务器上、项目根目录执行）：
#   chmod +x deploy.sh
#   ./deploy.sh kitchen.example.com            # 首次部署（域名模式，Let's Encrypt 证书）
#   ./deploy.sh 123.45.67.89                   # 首次部署（IP 模式，自签证书，配合
#                                              #   Lucky 等端口映射：映射 TCP 443 到本机 443）
#   ./deploy.sh kitchen.example.com --with-db  # 首次部署并用 Postgres（默认 SQLite）
#   ./deploy.sh 192.168.3.78 --port 10123      # IP 模式 + 自定义 HTTPS 端口
#                                              #   （Lucky/路由把外部该端口转发到本机）
#   ./deploy.sh kitchen.example.com --lucky /path/to/certs
#                                              # Lucky 证书模式：加载 Lucky 映射出来的
#                                              #   ACME 证书（域名.crt/域名.key），续期自动热加载
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
IP_MODE=false
LUCKY_DIR=""
LUCKY_NEXT=false
HTTPS_PORT=443
PORT_NEXT=false

say()  { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✓ %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m  ✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------- 参数解析 ----------------
for arg in "$@"; do
  case "$arg" in
    --with-db) WITH_DB=true ;;
    --lucky) LUCKY_NEXT=true ; continue ;;
    --port) PORT_NEXT=true ; continue ;;
    --help|-h) grep '^#   ' "$0" | sed 's/^#   //'; exit 0 ;;
    http*) DOMAIN="$arg" ;;
    "") ;;
    *)
      if [ "${LUCKY_NEXT:-}" = "true" ] && [ -z "$LUCKY_DIR" ]; then
        LUCKY_DIR="$arg"; LUCKY_NEXT=false
      elif [ "${PORT_NEXT:-}" = "true" ]; then
        HTTPS_PORT="$arg"; PORT_NEXT=false
      else
        [ -z "$DOMAIN" ] && DOMAIN="$arg" || die "多余参数: $arg"
      fi ;;
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
  [ -z "$DOMAIN" ] && die "用法: ./deploy.sh <域名|服务器公网IP> [--with-db]   （无参数且无 .env 时视为升级）"
fi

# IP 模式：不绑域名，自签证书（配合 Lucky 等端口映射）
if [[ "$DOMAIN" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  IP_MODE=true
  say "IP 模式：$DOMAIN（自签证书，Caddy tls internal；App 端需按提示安装CA或接受自签）"
fi

# Lucky 证书模式：Caddy 直接加载 Lucky 映射出的 ACME 证书
if [ -n "$LUCKY_DIR" ]; then
  $IP_MODE && die "--lucky 需要域名（Lucky 证书是签给域名的，IP 用不了）"
  CRT="$LUCKY_DIR/${DOMAIN}.crt"
  KEY="$LUCKY_DIR/${DOMAIN}.key"
  CRT_PEM="$LUCKY_DIR/${DOMAIN}.pem"
  # 兼容 Lucky 的 pem 命名
  if [ ! -f "$CRT" ] && [ -f "$CRT_PEM" ]; then CRT="$CRT_PEM"; fi
  [ -f "$CRT" ] || die "找不到证书文件: $CRT（在 Lucky SSL证书模块→该证书→启用映射→目录设为 $LUCKY_DIR；文件名应为 ${DOMAIN}.crt 或 ${DOMAIN}.pem）"
  [ -f "$KEY" ] || die "找不到私钥文件: $KEY"
  say "Lucky 证书模式：Caddy 加载 $CRT"
  mkdir -p deploy/lucky-certs
  # 拷入项目目录统一挂载（相对路径挂载比直接挂外部目录更可控）
  cp -f "$CRT" "deploy/lucky-certs/${DOMAIN}.crt"
  cp -f "$KEY"  "deploy/lucky-certs/${DOMAIN}.key"
  chmod 600 deploy/lucky-certs/${DOMAIN}.key
  echo "deploy/lucky-certs/" >> .gitignore
  # 往 Caddyfile 站点块注入 tls 段
  if ! grep -q "deploy/lucky-certs" deploy/Caddyfile 2>/dev/null; then
    awk -v dom="$DOMAIN" '/^\{\$DOMAIN::443\} \{$/ {
      print
      print "\t# Lucky 模式：手动证书（Caddy 监听文件变化，Lucky 凌晨续期后自动热加载）"
      print "\ttls /certs/" dom ".crt /certs/" dom ".key"
      next
    } {print}' deploy/Caddyfile > deploy/Caddyfile.tmp && mv deploy/Caddyfile.tmp deploy/Caddyfile
    say "已注入 tls 手动证书段到 deploy/Caddyfile"
  fi
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
  if $IP_MODE; then
    ok ".env 已生成（IP 模式：Lucky 里把外部端口映射到本机 TCP 443 即可）"
  else
    ok ".env 已生成（GLM_API_KEY 留空，需要票据识别时编辑 .env 后重新部署）"
  fi
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
if [ "$HTTPS_PORT" != "443" ]; then
  python3 - "$HTTPS_PORT" <<'PYPORT'
import sys
port = sys.argv[1]
p = 'docker-compose.yml'
s = open(p, encoding='utf-8').read()
old = '      - "80:80"     # ACME HTTP-01 挑战 + 跳转 HTTPS\n      - "443:443"'
new = '      - "%s:443"   # HTTPS 主入口（外部端口转发到本机该端口）' % port
if old in s:
    s = s.replace(old, new, 1)
elif ':%s:443' % port not in s:
    import re
    s2 = re.sub(r'      - "\d+:443".*\n', '      - "%s:443"   # HTTPS 主入口\n' % port, s, count=1)
    assert s2 != s, 'no port line matched'
    s = s2
open(p, 'w', encoding='utf-8').write(s)
print('HTTPS 端口 -> %s' % port)
PYPORT
fi
if [ -n "$LUCKY_DIR" ]; then
  # 把证书目录挂进 caddy 容器 /certs
  python3 - <<'PYIN2'
import re
p = 'docker-compose.yml'
s = open(p, encoding='utf-8').read()
if 'lucky-certs' not in s:
    old = """    volumes:
      - ./deploy/Caddyfile:/etc/caddy/Caddyfile:ro"""
    new = """    volumes:
      - ./deploy/Caddyfile:/etc/caddy/Caddyfile:ro
      - ./deploy/lucky-certs:/certs:ro"""
    assert old in s, 'compose-anchor'
    s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)
    print('compose 已挂载 lucky-certs')
PYIN2
fi
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
$IP_MODE || sleep 8   # 域名模式给 ACME 挑战留时间
if curl -sk --noproxy '*' "https://127.0.0.1/api/health" -H "Host: $DOMAIN" | grep -q '"status":"UP"'; then
  ok "HTTPS 反代 UP"
fi

echo
SUFFIX=""
[ "$HTTPS_PORT" != "443" ] && SUFFIX=":$HTTPS_PORT"
ok "部署完成:  https://$DOMAIN$SUFFIX"
if [ -n "$LUCKY_DIR" ]; then
  echo "    ── Lucky 证书模式 ──"
  echo "    Lucky: SSL证书模块对该证书启用映射→目录指向 $LUCKY_DIR（每天凌晨自动续期）"
  echo "    Caddy 监听证书文件变化自动热加载, 无需重启"
  echo "    App 填: https://$DOMAIN（443 或 Lucky 映射的外部端口）"
elif $IP_MODE; then
  echo "    ── IP 模式（自签证书）──"
  echo "    Lucky: 把外部端口(如 8443)映射到本机 TCP 443；App 填 https://$DOMAIN:外部端口"
  echo "    自签CA导出(推荐,免警告): docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt > sharedkitchen-root.crt"
  echo "    手机上: 设置→安全→安装证书→CA证书 装入该文件即可静默信任"
  echo "    （不装CA也行: App内浏览器首次访问会提示不安全,功能不受影响）"
fi
echo "    App 端:   「我 → 服务器设置」填 https://$DOMAIN$SUFFIX"
echo "    管理后台: https://$DOMAIN$SUFFIX/admin/"
echo "    日志:     docker compose logs -f server"
echo "    升级:     git pull && ./deploy.sh"
