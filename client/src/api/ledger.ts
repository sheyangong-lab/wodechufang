import { request } from '@/utils/request';

export type LedgerType = 'INCOME' | 'EXPENSE' | 'REFUND';

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
}

export interface SavingView {
  id: number;
  name: string;
  currency: 'CNY' | 'HKD' | 'USD';
  amountFen: number;
  kind: 'DEPOSIT' | 'WITHDRAW';
  note: string;
  createdAt: string;
}

export interface SavingSummary {
  balanceCnyFen: number;
  balanceHkdFen: number;
  balanceUsdFen: number;
  recent: SavingView[];
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
    }
  ) =>
    request<LedgerEntryView>({
      url: `/api/kitchens/${kitchenId}/ledger/entries/${id}`,
      method: 'PUT',
      data,
    }),
  summary: (kitchenId: number, month: string) =>
    request<MonthSummary>({ url: `/api/kitchens/${kitchenId}/ledger/summary?month=${month}` }),

  // ----- 存钱罐 -----
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
