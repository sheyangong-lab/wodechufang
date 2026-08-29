#!/usr/bin/env bash
# 共享厨房后端回归冒烟测试：一条命令验证 M1-M7 全链路。
# 用法: ./scripts/smoke-test.sh [BASE]   (BASE 默认 http://localhost:8080)
# 前置: 后端已启动 (dev profile, SQLite)。测试数据使用随机手机号，不污染既有账号。
set -u
BASE="${1:-http://localhost:8080}"
J="Content-Type: application/json"
PASS=0; FAIL=0

ok()  { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad() { FAIL=$((FAIL+1)); echo "  ✗ $1"; }
check() { # check "描述" "实际" "期望子串"
  if echo "$2" | grep -Fq "$3"; then ok "$1"; else bad "$1 (期望含[$3], 实际: $(echo "$2" | head -c 160))"; fi
}
tok() { echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['data']['token'])" 2>/dev/null; }

echo "== 共享厨房冒烟测试 → $BASE =="

# 0. 健康
R=$(curl -s --max-time 5 "$BASE/api/health")
check "健康检查" "$R" '"code":0'

# 1. 注册两个随机用户
PA="139$(python3 -c "import random;print(f'{random.randint(0,99999999):08d}')")"
PB="138$(python3 -c "import random;print(f'{random.randint(0,99999999):08d}')")"
TA=$(tok "$(curl -s -X POST $BASE/api/auth/register -H "$J" -d "{\"phone\":\"$PA\",\"smsCode\":\"1234\"}")")
TB=$(tok "$(curl -s -X POST $BASE/api/auth/register -H "$J" -d "{\"phone\":\"$PB\",\"smsCode\":\"1234\"}")")
[ -n "$TA" ] && ok "注册店长A" || bad "注册店长A"
[ -n "$TB" ] && ok "注册家人B" || bad "注册家人B"
HA="Authorization: Bearer $TA"; HB="Authorization: Bearer $TB"

# 2. 厨房: 创建/加入/详情
K=$(curl -s -X POST $BASE/api/kitchens -H "$J" -H "$HA" -d '{"name":"冒烟测试厨房"}')
check "创建厨房" "$K" '"myRole":"OWNER"'
KID=$(echo "$K" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
CODE=$(echo "$K" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['code'])")
R=$(curl -s -X POST $BASE/api/kitchens/join -H "$J" -H "$HB" -d "{\"code\":\"$CODE\"}")
check "B凭码加入(家人)" "$R" '"myRole":"MEMBER"'
R=$(curl -s $BASE/api/kitchens/$KID -H "$HA")
check "厨房详情含成员列表" "$R" '"members"'
R=$(curl -s -o /dev/null -w "%{http_code}" $BASE/api/kitchens/$KID -H "$HB" -X POST)
R=$(curl -s $BASE/api/kitchens/$KID -H "Authorization: Bearer $(tok "$(curl -s -X POST $BASE/api/auth/register -H "$J" -d "{\"phone\":\"137$(python3 -c "import random;print(f'{random.randint(0,99999999):08d}')")\",\"smsCode\":\"1234\"}")")")
check "非成员访问403" "$R" '"code":403'

# 3. 分类 + 菜谱(分/多规格)
R=$(curl -s -X POST $BASE/api/kitchens/$KID/categories -H "$J" -H "$HA" -d '{"name":"荤菜"}')
check "建分类" "$R" '"name":"荤菜"'
CID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
D=$(curl -s -X POST $BASE/api/kitchens/$KID/dishes -H "$J" -H "$HA" -d '{"name":"番茄炒蛋","priceFen":1280,"categoryId":'"$CID"',"recommendStars":3,"materials":"鸡蛋:3个\n番茄:2个","steps":"1、打蛋\n2、炒蛋","specs":[{"name":"小份","priceFen":1000},{"name":"大份","priceFen":1500}]}')
check "建菜谱(多规格/分存储)" "$D" '"priceFen":1280'
DID=$(echo "$D" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
R=$(curl -s "$BASE/api/kitchens/$KID/random-dishes?count=1" -H "$HA")
check "随机点菜(全部)" "$R" '"name":"番茄炒蛋"'
R=$(curl -s "$BASE/api/kitchens/$KID/random-dishes?categoryId=$CID&count=3" -H "$HB")
check "随机点菜(按分类)" "$R" '"name":"番茄炒蛋"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/dishes -H "$J" -H "$HB" -d '{"name":"越权菜","priceFen":100}')
check "家人建菜谱应403(顾客概念已移除,家人可行)" "$R" '"code":0'

# 4. 订单: 下单快照/完成/退单/筛选
O=$(curl -s -X POST $BASE/api/kitchens/$KID/orders -H "$J" -H "$HB" -d '{"remark":"少辣","items":[{"dishId":'"$DID"',"specName":"大份","quantity":2}]}')
check "下单按规格价重算(1500x2=3000分)" "$O" '"totalFen":3000'
OID=$(echo "$O" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
R=$(curl -s -X POST $BASE/api/orders/$OID/refund-request -H "$HB")
check "买家申请退单" "$R" '"status":"REFUND_REQUESTED"'
R=$(curl -s -X POST $BASE/api/orders/$OID/refund-approve -H "$HA")
check "店长同意退单" "$R" '"status":"REFUNDED"'
O2=$(curl -s -X POST $BASE/api/kitchens/$KID/orders -H "$J" -H "$HB" -d '{"items":[{"dishId":'"$DID"',"quantity":1}]}')
OID2=$(echo "$O2" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
R=$(curl -s -X POST $BASE/api/orders/$OID2/complete -H "$HA")
check "完成订单" "$R" '"status":"COMPLETED"'

# 5. 账本: 自动入账/手动支出/自定义分类/汇总/导出
R=$(curl -s $BASE/api/kitchens/$KID/ledger/summary?month=$(date +%Y-%m) -H "$HA")
check "账本含自动收入(完成单1280分)" "$R" '"income":1280'
check "账本含退款冲销3000分" "$R" '"refund":3000'
R=$(curl -s $BASE/api/kitchens/$KID/ledger/categories -H "$HA")
check "账本分类懒加载默认(食材采购)" "$R" '"name":"食材采购"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/categories -H "$J" -H "$HA" -d '{"type":"EXPENSE","name":"宠物开销"}')
check "账本新建自定义分类" "$R" '"name":"宠物开销"'
LCID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/entries -H "$J" -H "$HA" -d '{"type":"EXPENSE","category":"宠物开销","amountFen":5000,"remark":"买菜"}')
check "自定义分类记支出" "$R" '"category":"宠物开销"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/ledger/entries -H "$J" -H "$HA" -d '{"type":"EXPENSE","category":"不存在的分类","amountFen":500}')
check "乱填分类应400" "$R" '"code":400'
R=$(curl -s -X DELETE $BASE/api/kitchens/$KID/ledger/categories/$LCID -H "$HA")
check "删除自定义分类" "$R" '"code":0'
R=$(curl -s $BASE/api/kitchens/$KID/ledger/export?month=$(date +%Y-%m) -H "$HA")
XURL=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['url'])" 2>/dev/null)
curl -s -o /tmp/smoke-ledger.xlsx "$BASE$XURL"
SHEETS=$(python3 -c "import zipfile;print(len([n for n in zipfile.ZipFile('/tmp/smoke-ledger.xlsx').namelist() if 'worksheets/sheet' in n]))" 2>/dev/null || echo 0)
[ "$SHEETS" = "4" ] && ok "Excel导出4 Sheet有效" || bad "Excel导出 (Sheet数=$SHEETS)"

# 6. 冰箱: 三态/图片/匹配/临期
TODAY=$(date +%F); AGO8=$(date -d "-8 days" +%F 2>/dev/null || date -v-8d +%F)
R=$(curl -s -X POST $BASE/api/kitchens/$KID/fridge/items -H "$J" -H "$HA" -d '{"items":[{"name":"鸡蛋","imageUrl":"/files/smoke-egg.jpg","shelfLifeValue":2,"shelfLifeUnit":"DAY","quantity":"12个"},{"name":"牛奶","producedDate":"'"$AGO8"'","shelfLifeValue":7,"shelfLifeUnit":"DAY"}]}')
check "放入食材(临期+已过期)" "$R" '"state":"expiring"'
check "食材照片回传" "$R" '"imageUrl":"/files/smoke-egg.jpg"'
check "过期态计算" "$R" '"state":"expired"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/fridge/match -H "$J" -H "$HA" -d '{"ingredient":"鸡蛋"}')
check "匹配菜谱命中" "$R" '"materialLine":"鸡蛋:3个"'
R=$(curl -s -X POST $BASE/api/kitchens/$KID/fridge/check-expiry -H "$HA")
if echo "$R" | grep -Eq '"data":[1-9]'; then ok "临期扫描生成通知(非零)"; else bad "临期扫描生成通知 (实际: $(echo "$R" | head -c 100))"; fi

# 7. 后台: 登录/概览/封禁解封（VIP 已下线，全功能免费）
ADMIN_BASE="${BASE}"
LR=$(curl -s -X POST $ADMIN_BASE/api/admin/login -H "$J" -d '{"username":"admin","password":"admin123"}')
ATOK=$(echo "$LR" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$ATOK" ] && ok "后台登录" || bad "后台登录"
R=$(curl -s $BASE/api/kitchens/$KID/vip/redeem -X POST -H "$J" -H "$HA" -d '{"code":"X"}')
check "VIP接口已下线(404)" "$R" '"code":404'

# 7.5 成员账号: 自定义名字/职称/全权限
MEMBER_UID=$(curl -s $BASE/api/kitchens/$KID -H "$HA" | python3 -c "import sys,json;print([m['userId'] for m in json.load(sys.stdin)['data']['members'] if m['role']=='MEMBER'][0])")
R=$(curl -s -X PUT $BASE/api/kitchens/$KID/members/$MEMBER_UID -H "$J" -H "$HA" -d '{"alias":"老王家的","title":"主厨","fullAccess":1}')
check "主账号设置成员名字/职称/全权限" "$R" '"alias":"老王家的"'
R=$(curl -s $BASE/api/kitchens/$KID -H "$HB")
check "成员列表回读alias/title/fullAccess" "$R" '"title":"主厨"'
R=$(curl -s -X PUT $BASE/api/kitchens/$KID -H "$J" -H "$HB" -d '{"name":"成员改名厨房"}')
check "全权限成员可改厨房信息" "$R" '"name":"成员改名厨房"'
R=$(curl -s -X PUT $BASE/api/kitchens/$KID/members/$MEMBER_UID -H "$J" -H "$HA" -d '{"fullAccess":0}')
R=$(curl -s -X PUT $BASE/api/kitchens/$KID -H "$J" -H "$HB" -d '{"name":"越权改名"}')
check "收回全权限后成员改厨房信息应403" "$R" '"code":403'
R=$(curl -s -X PUT $BASE/api/kitchens/$KID/members/$MEMBER_UID -H "$J" -H "$HB" -d '{"alias":"自己改名"}')
check "成员可改自己的名字" "$R" '"alias":"自己改名"'

# 8. 后台: 概览/封禁解封
R=$(curl -s $BASE/api/admin/overview -H "Authorization: Bearer $ATOK")
check "后台概览" "$R" '"users":'
R=$(curl -s -X POST $BASE/api/admin/kitchens/$KID/ban -H "$J" -H "Authorization: Bearer $ATOK" -d '{"ban":true}')
check "后台封禁厨房" "$R" '"status":0'
R=$(curl -s -X POST $BASE/api/admin/kitchens/$KID/ban -H "$J" -H "Authorization: Bearer $ATOK" -d '{"ban":false}')
check "后台解封厨房" "$R" '"status":1'

echo "== 结果: $PASS 通过, $FAIL 失败 =="
[ $FAIL -eq 0 ] && echo "SMOKE PASS" || echo "SMOKE FAIL"
exit $FAIL
