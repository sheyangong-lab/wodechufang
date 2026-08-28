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

export interface DishStat {
  name: string;
  quantity: number;
  salesFen: number;
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
  dishStats: (kitchenId: number, month: string) =>
    request<DishStat[]>({ url: `/api/kitchens/${kitchenId}/ledger/dish-stats?month=${month}` }),
  categoriesOf: (kitchenId: number) =>
    request<{ expense: string[]; income: string[] }>({
      url: `/api/kitchens/${kitchenId}/ledger/categories`,
    }),
  categoriesOf: (kitchenId: number) =>
    request<{ expense: string[]; income: string[] }>({
      url: `/api/kitchens/${kitchenId}/ledger/categories`,
    }),
  export: (kitchenId: number, month: string) =>
    request<{ url: string }>({
      url: `/api/kitchens/${kitchenId}/ledger/export?month=${month}`,
    }),
};

/** 收支汇总口径：结余 = 收入 - 退款 - 支出 */
export function balanceText(s: MonthSummary): string {
  return fen(s.balance);
}

export function fen(value: number): string {
  return (value / 100).toFixed(2);
}
