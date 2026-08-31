import { request } from '@/utils/request';

export type LedgerType = 'INCOME' | 'EXPENSE' | 'REFUND';

export interface LedgerEntryView {
  id: number;
  type: LedgerType;
  source: 'ORDER' | 'MANUAL';
  orderId: number | null;
  category: string;
  amountFen: number;
  date: string;
  remark: string;
  createdAt: string;
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
    data: { type: 'INCOME' | 'EXPENSE'; category: string; amountFen: number; date?: string; remark?: string }
  ) =>
    request<LedgerEntryView>({
      url: `/api/kitchens/${kitchenId}/ledger/entries`, method: 'POST', data,
    }),
  remove: (kitchenId: number, id: number) =>
    request<void>({ url: `/api/kitchens/${kitchenId}/ledger/entries/${id}`, method: 'DELETE' }),
  summary: (kitchenId: number, month: string) =>
    request<MonthSummary>({ url: `/api/kitchens/${kitchenId}/ledger/summary?month=${month}` }),
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
