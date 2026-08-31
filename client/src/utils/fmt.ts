/**
 * 时间工具 —— 服务器存 UTC ISO（Instant.now()），展示一律转设备本地时区
 * （约定 UTC+8）。默认日期/月份也用本地时间，不能用 toISOString（UTC 会差 8 小时）。
 */

const pad = (n: number) => String(n).padStart(2, '0');

/** UTC ISO → 本地 YYYY-MM-DD HH:mm（秒可选） */
export function fmtDateTime(iso: string, withSeconds = false): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const base = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return withSeconds ? `${base}:${pad(d.getSeconds())}` : base;
}

/** UTC ISO → 本地 YYYY-MM-DD */
export function fmtDate(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 本地今天 YYYY-MM-DD */
export function todayStr(d = new Date()): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** 本地当月 YYYY-MM */
export function monthStr(d = new Date()): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`;
}
