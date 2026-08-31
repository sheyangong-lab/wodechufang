/**
 * sync-core 合并算法单测：node --experimental-strip-types scripts/test-sync-core.ts
 */
import {
  computeMerge,
  applyResolution,
  type Op,
} from '/tmp/sync-core.mjs';

let pass = 0, fail = 0;
const ok = (name: string, cond: boolean, extra = '') => {
  if (cond) { pass++; console.log('  ✓', name); }
  else { fail++; console.log('  ✗', name, extra); }
};

const op = (id: string, method: Op['method'], url: string, body?: Record<string, unknown>): Op => ({
  opId: id, ts: Date.now() + Math.floor(Math.random() * 1000), method, url, body, desc: id,
});

// 场景1：不同实体（鸡蛋改数量 vs 番茄改备注）→ 自动合并，无冲突
const l1 = [op('a1', 'PUT', '/api/kitchens/1/fridge/items/10', { quantity: '6个' })];
const r1 = [op('b1', 'PUT', '/api/kitchens/1/fridge/items/11', { quantity: '2斤' })];
const m1 = computeMerge(l1, r1);
ok('不同实体无冲突', m1.conflicts.length === 0);
ok('对端操作进入远端合并集', m1.remoteOps.length === 1 && m1.remoteOps[0].opId === 'b1');

// 场景2：同一实体完全相同修改 → 去重，不进冲突
const same = [op('a2', 'PUT', '/api/kitchens/1/fridge/items/10', { quantity: '6个' })];
const same2 = [op('b2', 'PUT', '/api/kitchens/1/fridge/items/10', { quantity: '6个' })];
const m2 = computeMerge(same, same2);
ok('同内容去重', m2.conflicts.length === 0 && m2.deduped === 1);

// 场景3：同一实体不同修改 → 冲突，选本机/选对端
const l3 = [op('a3', 'PUT', '/api/kitchens/1/fridge/items/10', { quantity: '6个' })];
const r3 = [op('b3', 'PUT', '/api/kitchens/1/fridge/items/10', { quantity: '8个' })];
const m3 = computeMerge(l3, r3);
ok('同实体异改动出冲突', m3.conflicts.length === 1);
const key = m3.conflicts[0].key;
// 选本机：本机留 a3，对端队列里也补上 a3、丢弃 b3
const resLocal = applyResolution({ localOps: m3.localOps, remoteOps: m3.remoteOps, conflicts: m3.conflicts }, l3, { [key]: 'local' });
ok('选本机后双方队列都含胜者', resLocal.localOps.some(o => o.opId === 'a3') && resLocal.remoteOps.some(o => o.opId === 'a3'));
ok('败者b3已从对端队列移除', !resLocal.remoteOps.some(o => o.opId === 'b3'));
// 选对端
const resRemote = applyResolution({ localOps: m3.localOps, remoteOps: m3.remoteOps, conflicts: m3.conflicts }, l3, { [key]: 'remote' });
ok('选对端后胜者b3进双方队列', resLocal.localOps !== undefined && resRemote.localOps.some(o => o.opId === 'b3') && resRemote.remoteOps.some(o => o.opId === 'b3'));
ok('败者a3已从本机队列移除', !resRemote.localOps.some(o => o.opId === 'a3'));

// 场景4：POST 新建不同实体 → 自动合并不冲突；POST 相同内容（离线两端都建同名）→ 各自保留
const l4 = [op('a4', 'POST', '/api/kitchens/1/ledger/entries', { amountFen: 100 })];
const r4 = [op('b4', 'POST', '/api/kitchens/1/ledger/entries', { amountFen: 200 })];
const m4 = computeMerge(l4, r4);
ok('POST不冲突', m4.conflicts.length === 0 && m4.remoteOps.length === 1);

// 场景5：删除vs修改同一实体 → 冲突
const l5 = [op('a5', 'DELETE', '/api/kitchens/1/dishes/7')];
const r5 = [op('b5', 'PUT', '/api/kitchens/1/dishes/7', { name: '改名' })];
const m5 = computeMerge(l5, r5);
ok('删除vs修改出冲突', m5.conflicts.length === 1 && m5.conflicts[0].local.method === 'DELETE');

console.log(`\n== 合并算法: ${pass} 通过, ${fail} 失败 ==`);
process.exit(fail ? 1 : 0);
