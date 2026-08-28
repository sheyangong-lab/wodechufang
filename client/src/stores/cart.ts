import { defineStore } from 'pinia';
import type { DishSpec, DishView } from '@/api/dish';

export interface CartItem {
  dishId: number;
  name: string;
  specName: string | null;
  priceFen: number;
  quantity: number;
  imageUrl: string | null;
}

const STORAGE_KEY = 'cart';
const KITCHEN_KEY = 'cartKitchenId';

function load(): CartItem[] {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY);
    return raw ? (JSON.parse(raw as string) as CartItem[]) : [];
  } catch {
    return [];
  }
}

/**
 * 购物车（本地）。提交时服务端按菜品当前价重算，这里的价格仅用于展示。
 * 以厨房为单位：切换厨房自动清空。
 */
export const useCartStore = defineStore('cart', {
  state: () => ({
    kitchenId: (uni.getStorageSync(KITCHEN_KEY) as number) || 0,
    items: load(),
  }),
  getters: {
    count: (s) => s.items.reduce((n, i) => n + i.quantity, 0),
    totalFen: (s) => s.items.reduce((n, i) => n + i.priceFen * i.quantity, 0),
  },
  actions: {
    persist() {
      uni.setStorageSync(STORAGE_KEY, JSON.stringify(this.items));
      uni.setStorageSync(KITCHEN_KEY, this.kitchenId);
    },
    ensureKitchen(kitchenId: number) {
      if (this.kitchenId !== kitchenId) {
        this.kitchenId = kitchenId;
        this.items = [];
        this.persist();
      }
    },
    /** 加入购物车：spec 传空则用默认价。 */
    add(dish: DishView, spec?: DishSpec, quantity = 1) {
      this.ensureKitchen(dish.kitchenId);
      const specName = spec?.name || null;
      const priceFen = spec ? spec.priceFen : dish.priceFen;
      const found = this.items.find(
        (i) => i.dishId === dish.id && i.specName === specName
      );
      if (found) {
        found.quantity = Math.min(99, found.quantity + quantity);
      } else {
        this.items.push({
          dishId: dish.id,
          name: dish.name,
          specName,
          priceFen,
          quantity: Math.min(99, quantity),
          imageUrl: dish.imageUrl,
        });
      }
      this.persist();
    },
    setQuantity(dishId: number, specName: string | null, quantity: number) {
      const found = this.items.find((i) => i.dishId === dishId && i.specName === specName);
      if (!found) return;
      if (quantity <= 0) {
        this.items = this.items.filter((i) => i !== found);
      } else {
        found.quantity = Math.min(99, quantity);
      }
      this.persist();
    },
    clear() {
      this.items = [];
      this.persist();
    },
  },
});
