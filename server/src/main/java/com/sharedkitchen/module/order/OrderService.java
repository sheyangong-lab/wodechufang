package com.sharedkitchen.module.order;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.dish.Dish;
import com.sharedkitchen.module.dish.DishRepository;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.user.User;
import com.sharedkitchen.module.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final DishRepository dishRepository;
    private final KitchenMemberRepository memberRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository itemRepository,
                        DishRepository dishRepository,
                        KitchenMemberRepository memberRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.dishRepository = dishRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** 下单：服务端按菜品当前价重算（快照校验），忽略客户端传来的任何价格。 */
    @Transactional
    public OrderView create(Long userId, Long kitchenId, OrderReq req) {
        requireMember(kitchenId, userId);
        if (req.items() == null || req.items().isEmpty()) {
            throw new BusinessException("购物车是空的");
        }
        if (req.items().size() > 50) {
            throw new BusinessException("一次最多下 50 个菜品项");
        }
        String dineDate = (req.dineDate() == null || req.dineDate().isBlank())
                ? LocalDate.now().toString() : req.dineDate().trim();
        if (!dineDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("用餐日期格式应为 YYYY-MM-DD");
        }
        String remark = req.remark() == null ? "" : req.remark().trim();
        if (remark.length() > 100) {
            throw new BusinessException("备注最多100个字");
        }

        Order order = new Order();
        order.setKitchenId(kitchenId);
        order.setBuyerId(userId);
        order.setRemark(remark);
        order.setDineDate(dineDate);
        order.setCreatedAt(now());
        order.setUpdatedAt(now());

        long total = 0;
        List<OrderItem> items = new java.util.ArrayList<>();
        for (OrderReq.ItemReq item : req.items()) {
            if (item.quantity() == null || item.quantity() < 1 || item.quantity() > 99) {
                throw new BusinessException("菜品数量应在 1-99 之间");
            }
            Dish dish = dishRepository.findById(item.dishId())
                    .orElseThrow(() -> new BusinessException("菜品不存在或已删除"));
            if (!dish.getKitchenId().equals(kitchenId) || dish.getDeleted() == 1 || dish.getStatus() != 1) {
                throw new BusinessException("「" + dish.getName() + "」已下架或删除，请刷新菜单");
            }
            long unitPrice = dish.getPriceFen();
            String specName = null;
            if (item.specName() != null && !item.specName().isBlank()) {
                specName = item.specName().trim();
                // 多规格：以服务端规格价为准；找不到该规格则报错
                Long specPrice = findSpecPrice(dish, specName);
                if (specPrice == null) {
                    throw new BusinessException("「" + dish.getName() + "」不存在规格 " + specName);
                }
                unitPrice = specPrice;
            }
            OrderItem oi = new OrderItem();
            oi.setDishId(dish.getId());
            oi.setDishName(dish.getName());
            oi.setSpecName(specName);
            oi.setPriceFen(unitPrice);
            oi.setQuantity(item.quantity());
            total += unitPrice * item.quantity();
            items.add(oi);
        }
        order.setTotalFen(total);
        orderRepository.save(order);
        items.forEach(oi -> oi.setOrderId(order.getId()));
        itemRepository.saveAll(items);
        User buyer = userRepository.findById(userId).orElse(null);
        return toView(order, buyer);
    }

    /** 双视角列表：received=厨房全部订单（成员可见），mine=我下过的单。 */
    public List<OrderView> list(Long userId, Long kitchenId, String role,
                                String dineDate, String status) {
        requireMember(kitchenId, userId);
        Long buyerId = "mine".equals(role) ? userId : null;
        List<Order> orders;
        boolean hasDate = dineDate != null && !dineDate.isBlank();
        boolean hasStatus = status != null && !status.isBlank() && !"ALL".equals(status);
        if (buyerId == null) {
            orders = hasDate && hasStatus
                    ? orderRepository.findByKitchenIdAndDineDateAndStatusOrderByCreatedAtDesc(kitchenId, dineDate, status)
                    : hasDate
                            ? orderRepository.findByKitchenIdAndDineDateOrderByCreatedAtDesc(kitchenId, dineDate)
                            : hasStatus
                                    ? orderRepository.findByKitchenIdAndStatusOrderByCreatedAtDesc(kitchenId, status)
                                    : orderRepository.findByKitchenIdOrderByCreatedAtDesc(kitchenId);
        } else {
            orders = hasDate && hasStatus
                    ? orderRepository.findByKitchenIdAndBuyerIdAndDineDateAndStatusOrderByCreatedAtDesc(kitchenId, buyerId, dineDate, status)
                    : hasDate
                            ? orderRepository.findByKitchenIdAndBuyerIdAndDineDateOrderByCreatedAtDesc(kitchenId, buyerId, dineDate)
                            : hasStatus
                                    ? orderRepository.findByKitchenIdAndBuyerIdAndStatusOrderByCreatedAtDesc(kitchenId, buyerId, status)
                                    : orderRepository.findByKitchenIdAndBuyerIdOrderByCreatedAtDesc(kitchenId, buyerId);
        }
        return toViews(orders);
    }

    public OrderView detail(Long userId, Long orderId) {
        Order order = requireOrder(orderId);
        requireMember(order.getKitchenId(), userId);
        return toViews(List.of(order)).get(0);
    }

    // ---------- 状态流转 ----------

    @Transactional
    public OrderView complete(Long userId, Long orderId) {
        Order order = requireOrder(orderId);
        requireOwner(order.getKitchenId(), userId);
        requireStatus(order, Order.ST_PENDING, Order.ST_REFUND_REQUESTED);
        order.setStatus(Order.ST_COMPLETED);
        order.setUpdatedAt(now());
        orderRepository.save(order);
        return toViews(List.of(order)).get(0);
    }

    @Transactional
    public OrderView requestRefund(Long userId, Long orderId) {
        Order order = requireOrder(orderId);
        if (!order.getBuyerId().equals(userId)) {
            throw new BusinessException(403, "只能操作自己的订单");
        }
        requireStatus(order, Order.ST_PENDING);
        order.setStatus(Order.ST_REFUND_REQUESTED);
        order.setUpdatedAt(now());
        orderRepository.save(order);
        return toViews(List.of(order)).get(0);
    }

    @Transactional
    public OrderView approveRefund(Long userId, Long orderId) {
        Order order = requireOrder(orderId);
        requireOwner(order.getKitchenId(), userId);
        requireStatus(order, Order.ST_REFUND_REQUESTED);
        order.setStatus(Order.ST_REFUNDED);
        order.setUpdatedAt(now());
        orderRepository.save(order);
        return toViews(List.of(order)).get(0);
    }

    @Transactional
    public OrderView rejectRefund(Long userId, Long orderId) {
        Order order = requireOrder(orderId);
        requireOwner(order.getKitchenId(), userId);
        requireStatus(order, Order.ST_REFUND_REQUESTED);
        order.setStatus(Order.ST_PENDING);
        order.setUpdatedAt(now());
        orderRepository.save(order);
        return toViews(List.of(order)).get(0);
    }

    // ---------- 私有 ----------

    private void requireStatus(Order order, String... allowed) {
        for (String s : allowed) {
            if (order.getStatus().equals(s)) return;
        }
        throw new BusinessException("当前订单状态不支持该操作");
    }

    private Long findSpecPrice(Dish dish, String specName) {
        if (dish.getSpecsJson() == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode arr =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(dish.getSpecsJson());
            for (var node : arr) {
                if (specName.equals(node.path("name").asText())) {
                    return node.path("priceFen").asLong();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private OrderView toView(Order o, User buyer) {
        List<OrderItem> items = itemRepository.findByOrderIdIn(List.of(o.getId()));
        return new OrderView(o.getId(), o.getKitchenId(), o.getBuyerId(),
                buyer == null ? "" : buyer.getNickname(),
                o.getStatus(), o.getRemark(), o.getTotalFen(), o.getDineDate(), o.getCreatedAt(),
                items.stream().map(i -> new OrderItemView(i.getDishId(), i.getDishName(),
                        i.getSpecName(), i.getPriceFen(), i.getQuantity())).toList());
    }

    private List<OrderView> toViews(List<Order> orders) {
        List<Long> ids = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrder = ids.isEmpty() ? Map.of()
                : itemRepository.findByOrderIdIn(ids).stream()
                        .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, User> usersById = orders.isEmpty() ? Map.of()
                : userRepository.findAllById(orders.stream().map(Order::getBuyerId).toList())
                        .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .map(o -> new OrderView(o.getId(), o.getKitchenId(), o.getBuyerId(),
                        usersById.containsKey(o.getBuyerId())
                                ? usersById.get(o.getBuyerId()).getNickname() : "",
                        o.getStatus(), o.getRemark(), o.getTotalFen(), o.getDineDate(), o.getCreatedAt(),
                        itemsByOrder.getOrDefault(o.getId(), List.of()).stream()
                                .map(i -> new OrderItemView(i.getDishId(), i.getDishName(),
                                        i.getSpecName(), i.getPriceFen(), i.getQuantity()))
                                .toList()))
                .toList();
    }

    private void requireMember(Long kitchenId, Long userId) {
        memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    private void requireOwner(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(m.getRole())) {
            throw new BusinessException(403, "只有店长可以处理订单");
        }
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
    }

    private String now() {
        return Instant.now().toString();
    }

    public record OrderReq(String remark, String dineDate, List<ItemReq> items) {
        public record ItemReq(@jakarta.validation.constraints.NotNull Long dishId, String specName, Integer quantity) {}
    }

    public record OrderItemView(Long dishId, String dishName, String specName, Long priceFen, Integer quantity) {}

    public record OrderView(
            Long id, Long kitchenId, Long buyerId, String buyerNickname,
            String status, String remark, Long totalFen, String dineDate, String createdAt,
            List<OrderItemView> items) {}
}
