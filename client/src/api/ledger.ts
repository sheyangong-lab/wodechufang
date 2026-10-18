import { request } from '@/utils/request';

export type LedgerType = 'INCOME' | 'EXPENSE' | 'REFUND' | 'TRANSFER';

export interface LedgerSubItem {
  name: string;
  amountFen: number;
}

export interface LedgerEntryView {
  id: number;
  type: LedgerType;
  source: 'ORDER' | 'MANUAL';
  orderId: number | null;
  category: string;
  amountFen: number;
  date: string;
  remark: string;
  /** 分费用明细（总费用=amountFen，由分费用求和）；无分项时为 null */
  subItems: LedgerSubItem[] | null;
  createdAt: string;
  /** 账单图片（/files/xxx 相对地址） */
  imageUrl: string | null;
  /** 支付方式 */
  paymentMethod: string | null;
  /** 原币种 CNY/HKD/USD */
  currency: string;
  /** 原币种金额（分）；仅外币有值 */
  origAmountFen: number | null;
  /** 记账账户 id（未挂账户为 null）；TRANSFER 双行 out 挂转出账户、in 挂转入账户 */
  accountId: number | null;
  /** TRANSFER 行方向 'in' | 'out'；普通收支行为 null */
  transferDir: string | null;
}

/**
 * 兼容桩：旧多币种存钱罐已下线（P2 改为账户三钱包 + 存款目标），
 * 类型名保留防旧引用编译错；下方旧 savings 方法仍指向后端桩端点。
 */
export type SavingView = any;
export type SavingSummary = any;

// ===== P2：账户 / 资产 / 转账 / 预算 / 周期账 / 存款目标 =====

export type AccountType = 'DISPOSABLE' | 'SAVING' | 'CREDIT';

export interface AccountView {
  id: number;
  name: string;
  type: AccountType;
  initialFen: number;
  creditLimitFen: number | null;
  sort: number;
  /** 余额（分）= initial + 流水汇总（CREDIT 组为负表示欠款） */
  balanceFen: number;
}

export interface AssetsView {
  disposableFen: number;
  savingFen: number;
  /** 信用卡负债（正数） */
  creditDebtFen: number;
  creditLimitFen: number;
  creditRemainFen: number;
  netAssetFen: number;
  /** 当月总预算/已用（无总预算时为 null）；budgetWarn=已用≥80% */
  budgetTotalFen: number | null;
  budgetUsedFen: number | null;
  budgetWarn: boolean;
}

/** 账户间转账（存钱/取回/还款）：一次请求生成 TRANSFER 双行 */
export interface TransferData {
  fromAccountId: number;
  toAccountId: number;
  amountFen: number;
  date?: string;
  remark?: string;
}

export interface BudgetTotalView {
  budgetFen: number | null;
  usedFen: number | null;
  pct: number | null;
}

export interface BudgetItemView {
  category: string;
  budgetFen: number | null;
  usedFen: number | null;
  pct: number | null;
}

export interface BudgetView {
  month: string;
  total: BudgetTotalView;
  items: BudgetItemView[];
}

export interface BudgetItemPut {
  category: string;
  amountFen: number | null;
}

export interface BudgetPutData {
  month: string;
  totalFen?: number | null;
  items?: BudgetItemPut[];
}

export interface BillView {
  id: number;
  type: string;
  category: string;
  amountFen: number;
  accountId: number | null;
  cycle: string;
  nextDate: string;
  remark: string;
  lastEntryId: number | null;
  enabled: number;
}

/** enabled 传 0/1；PUT 只更新传入（非 null）字段 */
export interface BillData {
  type: string;
  category: string;
  amountFen: number;
  accountId?: number | null;
  cycle?: string;
  nextDate?: string;
  remark?: string;
  enabled?: number;
}

export interface SavingGoalView {
  id: number;
  name: string;
  targetFen: number;
  deadline: string | null;
  progressFen: number;
  pct: number;
}

export interface SavingGoalData {
  name: string;
  targetFen: number;
  deadline?: string;
}

export interface DayPoint {
  date: string;
  incomeFen: number;
  expenseFen: number;
}

export interface MonthSummary {
  income: number;
  refund: number;
  expense: number;
  balance: number;
  days: DayPoint[];
}

export interface LedgerCategoryView {
  id: number;
  type: 'INCOME' | 'EXPENSE';
  name: string;
}

export const ledgerApi = {
  list: (kitchenId: number, month: string) =>
    request<LedgerEntryView[]>({
      url: `/api/kitchens/${kitchenId}/ledger/entries?month=${month}`,
    }),
  add: (
    kitchenId: number,
    data: {
      type: 'INCOME' | 'EXPENSE';
      category: string;
      amountFen: number;
      date?: string;
      remark?: string;
      subItems?: LedgerSubItem[];
      imageUrl?: string;
      paymentMethod?: string;
      currency?: 'CNY' | 'HKD' | 'USD';
      accountId?: number | null;
    }
  ) =>
    request<LedgerEntryView>({
      url: `/api/kitchens/${kitchenId}/ledger/entries`, method: 'POST', data,
    }),
  remove: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/entries/${id}`, method: 'DELETE' }),
  update: (
    kitchenId: number,
    id: number,
    data: {
      type: 'INCOME' | 'EXPENSE';
      category: string;
      amountFen: number;
      date?: string;
      remark?: string;
      subItems?: LedgerSubItem[];
      imageUrl?: string;
      paymentMethod?: string;
      currency?: 'CNY' | 'HKD' | 'USD';
      accountId?: number | null;
    }
  ) =>
    request<LedgerEntryView>({
      url: `/api/kitchens/${kitchenId}/ledger/entries/${id}`,
      method: 'PUT',
      data,
    }),
  summary: (kitchenId: number, month: string) =>
    request<MonthSummary>({ url: `/api/kitchens/${kitchenId}/ledger/summary?month=${month}` }),

  // ----- 账户（三钱包）与资产总览 -----
  accounts: (kitchenId: number) =>
    request<AccountView[]>({ url: `/api/kitchens/${kitchenId}/ledger/accounts` }),
  createAccount: (
    kitchenId: number,
    data: { name: string; type: AccountType; initialFen?: number; creditLimitFen?: number }
  ) =>
    request<AccountView>({ url: `/api/kitchens/${kitchenId}/ledger/accounts`, method: 'POST', data }),
  updateAccount: (
    kitchenId: number,
    id: number,
    data: { name?: string; sort?: number; initialFen?: number; creditLimitFen?: number }
  ) =>
    request<AccountView>({ url: `/api/kitchens/${kitchenId}/ledger/accounts/${id}`, method: 'PUT', data }),
  deleteAccount: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/accounts/${id}`, method: 'DELETE' }),
  assets: (kitchenId: number) =>
    request<AssetsView>({ url: `/api/kitchens/${kitchenId}/ledger/assets` }),

  /** 账户间转账（存钱/取回/还款）：返回 TRANSFER 双行 */
  transfer: (kitchenId: number, data: TransferData) =>
    request<LedgerEntryView[]>({ url: `/api/kitchens/${kitchenId}/ledger/transfers`, method: 'POST', data }),

  // ----- 预算（每厨房每月整体覆盖） -----
  budgets: (kitchenId: number, month: string) =>
    request<BudgetView>({ url: `/api/kitchens/${kitchenId}/ledger/budgets?month=${month}` }),
  putBudgets: (kitchenId: number, data: BudgetPutData) =>
    request<{ month: string; totalFen: number | null; items: BudgetItemPut[] }>({
      url: `/api/kitchens/${kitchenId}/ledger/budgets`, method: 'PUT', data,
    }),

  // ----- 周期账单（固定收支自动入账） -----
  recurringBills: (kitchenId: number) =>
    request<BillView[]>({ url: `/api/kitchens/${kitchenId}/ledger/recurring-bills` }),
  addRecurringBill: (kitchenId: number, data: BillData) =>
    request<BillView>({ url: `/api/kitchens/${kitchenId}/ledger/recurring-bills`, method: 'POST', data }),
  updateRecurringBill: (kitchenId: number, id: number, data: BillData) =>
    request<BillView>({ url: `/api/kitchens/${kitchenId}/ledger/recurring-bills/${id}`, method: 'PUT', data }),
  removeRecurringBill: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/recurring-bills/${id}`, method: 'DELETE' }),
  /** 手动触发扫描（等价每日定时）：返回生成流水/通知条数 */
  scanRecurringBills: (kitchenId: number) =>
    request<{ entries: number; notified: number }>({
      url: `/api/kitchens/${kitchenId}/ledger/recurring-bills/scan`, method: 'POST',
    }),

  // ----- 存款目标（progress=全部 SAVING 账户余额之和） -----
  savingGoals: (kitchenId: number) =>
    request<SavingGoalView[]>({ url: `/api/kitchens/${kitchenId}/ledger/saving-goals` }),
  addSavingGoal: (kitchenId: number, data: SavingGoalData) =>
    request<SavingGoalView>({ url: `/api/kitchens/${kitchenId}/ledger/saving-goals`, method: 'POST', data }),
  removeSavingGoal: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/saving-goals/${id}`, method: 'DELETE' }),

  // ----- 旧存钱罐（后端已桩化返回空，仅保签名防旧引用编译错） -----
  savingsList: (kitchenId: number) =>
    request<SavingView[]>({ url: `/api/kitchens/${kitchenId}/ledger/savings` }),
  savingsSummary: (kitchenId: number) =>
    request<SavingSummary>({ url: `/api/kitchens/${kitchenId}/ledger/savings/summary` }),
  savingAdd: (
    kitchenId: number,
    data: { name: string; currency: 'CNY' | 'HKD' | 'USD'; amountFen: number; kind: 'DEPOSIT' | 'WITHDRAW'; note?: string }
  ) =>
    request<SavingView>({
      url: `/api/kitchens/${kitchenId}/ledger/savings`,
      method: 'POST',
      data,
    }),
  savingRemove: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/savings/${id}`, method: 'DELETE' }),

  // ----- 汇率 -----
  fx: (kitchenId: number) =>
    request<{ HKD: number; USD: number }>({ url: `/api/kitchens/${kitchenId}/ledger/fx` }),

  // ----- 支付方式 -----
  paymentMethods: (kitchenId: number) =>
    request<{ id: number; name: string }[]>({ url: `/api/kitchens/${kitchenId}/ledger/payment-methods` }),
  paymentMethodAdd: (kitchenId: number, name: string) =>
    request<{ id: number; name: string }>({
      url: `/api/kitchens/${kitchenId}/ledger/payment-methods`,
      method: 'POST',
      data: { name },
    }),
  paymentMethodRemove: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/payment-methods/${id}`, method: 'DELETE' }),

  // ----- PDF 导出（返回完整 URL，页面内 window.open） -----
  exportPdfUrl: (kitchenId: number, month: string) =>
    `/api/kitchens/${kitchenId}/ledger/export.pdf?month=${month}`,
  /** 分类列表（厨房首次使用时服务端自动补默认分类） */
  categories: (kitchenId: number) =>
    request<LedgerCategoryView[]>({
      url: `/api/kitchens/${kitchenId}/ledger/categories`,
    }),
  createCategory: (kitchenId: number, type: 'INCOME' | 'EXPENSE', name: string) =>
    request<LedgerCategoryView>({
      url: `/api/kitchens/${kitchenId}/ledger/categories`, method: 'POST', data: { type, name },
    }),
  deleteCategory: (kitchenId: number, categoryId: number) =>
    request<void>({
      url: `/api/kitchens/${kitchenId}/ledger/categories/${categoryId}`, method: 'DELETE',
    }),
  export: (kitchenId: number, month: string) =>
    request<{ url: string }>({
      url: `/api/kitchens/${kitchenId}/ledger/export?month=${month}`,
    }),
};

/** 分类按收支分成两组，供记一笔/分类管理使用 */
export function groupCategories(list: LedgerCategoryView[]): {
  expense: LedgerCategoryView[];
  income: LedgerCategoryView[];
} {
  return {
    expense: list.filter((c) => c.type === 'EXPENSE'),
    income: list.filter((c) => c.type === 'INCOME'),
  };
}

/** 收支汇总口径：结余 = 收入 - 退款 - 支出 */
export function balanceText(s: MonthSummary): string {
  return fen(s.balance);
}

export function fen(value: number): string {
  return (value / 100).toFixed(2);
}
