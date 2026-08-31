package com.sharedkitchen.module.basket;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.dish.Dish;
import com.sharedkitchen.module.dish.DishRepository;
import com.sharedkitchen.module.fridge.FridgeItem;
import com.sharedkitchen.module.fridge.FridgeItemRepository;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.order.Order;
import com.sharedkitchen.module.order.OrderItem;
import com.sharedkitchen.module.order.OrderItemRepository;
import com.sharedkitchen.module.order.OrderRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BasketService {

    private final BasketRepository basketRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DishRepository dishRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final KitchenMemberRepository memberRepository;

    public BasketService(BasketRepository basketRepository,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         DishRepository dishRepository,
                         FridgeItemRepository fridgeItemRepository,
                         KitchenMemberRepository memberRepository) {
        this.basketRepository = basketRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.dishRepository = dishRepository;
        this.fridgeItemRepository = fridgeItemRepository;
        this.memberRepository = memberRepository;
    }

    public List<BasketItem> list(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return basketRepository.findByKitchenIdOrderByCheckedAscCreatedAtAsc(kitchenId);
    }

    /** 手动加一项。 */
    @Transactional
    public BasketItem add(Long userId, Long kitchenId, String name, String quantity) {
        requireMember(kitchenId, userId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 30) {
            throw new BusinessException("名称最多30个字");
        }
        if (basketRepository.findByKitchenIdAndName(kitchenId, trimmed).isPresent()) {
            throw new BusinessException("菜篮里已有「" + trimmed + "」");
        }
        BasketItem item = new BasketItem();
        item.setKitchenId(kitchenId);
        item.setName(trimmed);
        item.setQuantity(quantity == null ? "" : quantity.trim());
        item.setSource(BasketItem.SOURCE_MANUAL);
        item.setCreatedAt(now());
        basketRepository.save(item);
        return item;
    }

    /** 勾选/取消勾选（已买到）。 */
    @Transactional
    public void toggle(Long userId, Long kitchenId, Long itemId) {
        requireMember(kitchenId, userId);
        BasketItem item = basketRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "条目不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "条目不存在");
        }
        item.setChecked(item.getChecked() == 1 ? 0 : 1);
        basketRepository.save(item);
    }

    @Transactional
    public void remove(Long userId, Long kitchenId, Long itemId) {
        requireMember(kitchenId, userId);
        BasketItem item = basketRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "条目不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "条目不存在");
        }
        basketRepository.delete(item);
    }

    /** 清掉已勾选的（已买到）。 */
    @Transactional
    public int clearChecked(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        List<BasketItem> items = basketRepository.findByKitchenIdOrderByCheckedAscCreatedAtAsc(kitchenId);
        List<BasketItem> checked = items.stream().filter(i -> i.getChecked() == 1).toList();
        basketRepository.deleteAll(checked);
        return checked.size();
    }

    public record GenerateResult(int added, int matched, List<BasketItemView> addedItems) {}

    /**
     * 从下单记录生成菜篮：提取指定日期所有订单菜品的用料，
     * 与冰箱现有食材比对，缺的加入菜篮（菜篮已有的不重复加）。
     */
    @Transactional
    public GenerateResult generate(Long userId, Long kitchenId, String date) {
        requireMember(kitchenId, userId);
        String dineDate = (date == null || date.isBlank()) ? LocalDate.now().toString() : date.trim();

        // 1. 当日订单 → 全部订单项 → 对应菜品的用料
        List<Order> orders = orderRepository.findByKitchenIdAndDineDateOrderByCreatedAtDesc(kitchenId, dineDate);
        Map<String, List<String>> need = new LinkedHashMap<>(); // 用料名 -> 数量列表
        int dishCount = 0;
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(Order::getId).toList();
            for (OrderItem item : orderItemRepository.findByOrderIdIn(orderIds)) {
                if (item.getDishId() == null) continue;
                Dish dish = dishRepository.findById(item.getDishId()).orElse(null);
                if (dish == null || dish.getMaterials() == null || dish.getMaterials().isBlank()) continue;
                dishCount++;
                parseMaterials(dish.getMaterials(), need);
            }
        }

        // 2. 冰箱现有食材名
        List<String> fridgeNames = fridgeItemRepository
                .findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0)
                .stream().map(FridgeItem::getName).toList();

        // 3. 比对：冰箱名与用料名互相包含即视为已有
        List<BasketItem> existing = basketRepository.findByKitchenIdOrderByCheckedAscCreatedAtAsc(kitchenId);
        List<String> basketNames = new ArrayList<>(
                existing.stream().map(BasketItem::getName).toList()); // toList() 不可变，需拷贝

        int matched = 0;
        List<BasketItem> added = new ArrayList<>();
        String t = now();
        for (Map.Entry<String, List<String>> e : need.entrySet()) {
            String mat = e.getKey();
            boolean inFridge = fridgeNames.stream().anyMatch(f ->
                    f.contains(mat) || mat.contains(f));
            if (inFridge) {
                matched++;
                continue;
            }
            if (basketNames.contains(mat)) continue; // 菜篮已有
            BasketItem item = new BasketItem();
            item.setKitchenId(kitchenId);
            item.setName(mat);
            item.setQuantity(String.join(" + ", e.getValue()));
            item.setSource(BasketItem.SOURCE_AUTO);
            item.setCreatedAt(t);
            basketRepository.save(item);
            added.add(item);
            basketNames.add(mat);
        }
        List<BasketItemView> views = added.stream()
                .map(i -> new BasketItemView(i.getId(), i.getName(), i.getQuantity(), 0, i.getSource()))
                .toList();
        return new GenerateResult(added.size(), matched, views);
    }

    /** 解析用料文本「鸡蛋:3个\n番茄:2个」，同名合并数量。 */
    private void parseMaterials(String materials, Map<String, List<String>> out) {
        for (String line : materials.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String name;
            String qty = "";
            int idx = trimmed.indexOf('：');
            if (idx == -1) idx = trimmed.indexOf(':');
            if (idx > 0) {
                name = trimmed.substring(0, idx).trim();
                qty = trimmed.substring(idx + 1).trim();
            } else {
                name = trimmed;
            }
            if (name.isEmpty() || name.length() > 20) continue;
            List<String> qtys = out.computeIfAbsent(name, k -> new ArrayList<>());
            if (!qty.isEmpty() && !qtys.contains(qty)) qtys.add(qty);
        }
    }

    private void requireMember(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    public record BasketItemView(Long id, String name, String quantity, Integer checked, String source) {}
}
