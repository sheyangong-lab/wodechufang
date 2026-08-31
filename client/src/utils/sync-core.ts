/**
 * 点对点同步的合并算法（纯函数，可独立单测）。
 * 规则：
 * - POST（新建）永不冲突：不同实体直接合并，同内容去重
 * - PUT/DELETE 按目标实体（路径）分组：两边都改过同一实体 → 冲突，逐条核对选边
 * - 冲突裁决后：胜者的操作保留在双方队列（服务器按 opId 幂等去重），败者丢弃
 */

export interface Op {
  opId: string;
  ts: number;
  method: 'POST' | 'PUT' | 'DELETE';
  url: string;
  body?: Record<string, unknown>;
  desc: string;
}

/** 实体键：按路径定位实体（修改与删除同实体互为冲突）；POST 新建不带服务器 id，永不冲突 */
export function entityKeyOf(op: Op): string | null {
  const path = op.url.split('?')[0];
  if (op.method === 'POST') return null;
  return path;
}

function contentHash(op: Op): string {
  return JSON.stringify([op.method, op.url.split('?')[0], op.body ?? null]);
}

export interface MergeConflict {
  key: string;
  local: Op;
  remote: Op;
}

export interface MergeResult {
  /** 本机队列最终内容 */
  localOps: Op[];
  /** 对端队列最终内容（发 APPLY 给对端） */
  remoteOps: Op[];
  /** 需要人工核对的冲突（同实体两边都改） */
  conflicts: MergeConflict[];
  /** 自动合并的本机操作（给 UI 展示） */
  autoMergedLocal: Op[];
  /** 自动合并的对端操作 */
  autoMergedRemote: Op[];
  /** 两边内容完全一致的重复操作，自动只保留一份 */
  deduped: number;
}

export function computeMerge(localOps: Op[], remoteOps: Op[]): MergeResult {
  const localKeys = new Map<string, Op>();
  localOps.forEach((o) => {
    const k = entityKeyOf(o);
    if (k) localKeys.set(k, o);
  });

  const remoteKeep: Op[] = [];
  const conflicts: MergeConflict[] = [];
  const remoteKeys = new Set<string>();
  let deduped = 0;

  remoteOps.forEach((r) => {
    const k = entityKeyOf(r);
    const twin = k ? localKeys.get(k) : undefined;
    if (k && twin) {
      // 同实体：内容一致 → 自动去重（只留本机一份）；不一致 → 冲突
      if (contentHash(twin) === contentHash(r)) {
        deduped++;
        remoteKeys.add(k);
        return;
      }
      conflicts.push({ key: k, local: twin, remote: r });
      remoteKeys.add(k);
      return;
    }
    if (k) {
      // POST 无键；PUT/DELETE 仅对端有 → 自动合并到对端队列
      remoteKeys.add(k);
    }
    remoteKeep.push(r);
  });

  // 冲突涉及的实体标记后，本机队列里这些操作先移除（裁决后按选择回填）
  const conflictLocalIds = new Set(conflicts.map((c) => c.local.opId));
  const localKeep = localOps.filter((o) => !conflictLocalIds.has(o.opId));

  const autoMergedLocal: Op[] = [];
  const autoMergedRemote = remoteKeep.slice();
  void autoMergedLocal;

  return {
    localOps: localKeep,
    remoteOps: remoteKeep,
    conflicts,
    autoMergedLocal,
    autoMergedRemote,
    deduped,
  };
}

/**
 * 应用冲突裁决结果。
 * @param choices key → 'local' | 'remote'
 * 胜者操作复制到双方队列（服务器幂等去重），败者从对应队列移除。
 */
export function applyResolution(
  before: { localOps: Op[]; remoteOps: Op[]; conflicts: MergeConflict[] },
  originalLocal: Op[],
  choices: Record<string, 'local' | 'remote'>
): { localOps: Op[]; remoteOps: Op[] } {
  let localOps = before.localOps.slice();
  let remoteOps = before.remoteOps.slice();

  before.conflicts.forEach((c) => {
    const winner = choices[c.key] === 'remote' ? c.remote : c.local;
    const loserOpId = choices[c.key] === 'remote' ? c.local.opId : c.remote.opId;
    // 败者从其原属队列移除
    localOps = localOps.filter((o) => o.opId !== loserOpId);
    remoteOps = remoteOps.filter((o) => o.opId !== loserOpId);
    // 胜者复制进双方队列（幂等头兜底重复回放）
    const inLocal = localOps.some((o) => o.opId === winner.opId);
    const inRemote = remoteOps.some((o) => o.opId === winner.opId);
    if (!inLocal) localOps.push(winner);
    if (!inRemote) remoteOps.push(winner);
  });

  localOps.sort((a, b) => a.ts - b.ts);
  remoteOps.sort((a, b) => a.ts - b.ts);
  void originalLocal;
  return { localOps, remoteOps };
}
