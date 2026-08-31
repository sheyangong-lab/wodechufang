#!/usr/bin/env bash
# 发布 APK 更新包：拷贝到服务器 ./data/apk/ 并生成 version.json
# 用法: ./scripts/publish-apk.sh <apk路径> <versionName> <versionCode> [更新说明]
# 示例: ./scripts/publish-apk.sh client/android/app/build/outputs/apk/debug/app-debug.apk 1.2.0 102 "修复若干问题"
set -euo pipefail

APK="${1:?用法: publish-apk.sh <apk> <versionName> <versionCode> [notes]}"
VNAME="${2:?缺少 versionName}"
VCODE="${3:?缺少 versionCode}"
NOTES="${4:-功能与问题修复}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$ROOT/server/data/apk"
mkdir -p "$DIR"

BASENAME=$(basename "$APK" .apk)
TARGET="$DIR/app-$VNAME.apk"
cp "$APK" "$TARGET"

SIZE=$(stat -c %s "$TARGET" 2>/dev/null || stat -f %z "$TARGET")
cat > "$DIR/version.json" << EOF
{
  "versionName": "$VNAME",
  "versionCode": $VCODE,
  "fileName": "app-$VNAME.apk",
  "notes": "$NOTES",
  "size": $SIZE,
  "publishedAt": "$(date -Iseconds)"
}
EOF
echo "✓ 已发布: $TARGET ($SIZE 字节)"
echo "  客户端检查更新接口: /api/app/version"
echo "  下载地址: /files/apk/app-$VNAME.apk"
