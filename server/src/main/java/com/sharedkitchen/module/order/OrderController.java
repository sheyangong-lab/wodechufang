package com.sharedkitchen.module.order;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import com.sharedkitchen.module.dish.DishService;
import com.sharedkitchen.module.dish.DishView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class OrderController {

    private final OrderService orderService;
    private final DishService dishService;

    public OrderController(OrderService orderService, DishService dishService) {
        this.orderService = orderService;
        this.dishService = dishService;
    }

    public record CreateReq(String remark, String dineDate,
                            @NotNull List<OrderService.OrderReq.ItemReq> items) {}

    @PostMapping("/kitchens/{kitchenId}/orders")
    public ApiResponse<OrderService.OrderView> create(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated CreateReq req) {
        Long userId = userId(request);
        OrderService.OrderReq inner = new OrderService.OrderReq(
                req.remark(), req.dineDate(), req.items());
        return ApiResponse.ok(orderService.create(userId, kitchenId, inner));
    }

    @GetMapping("/kitchens/{kitchenId}/orders")
    public ApiResponse<List<OrderService.OrderView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false, defaultValue = "received") String role,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.list(userId(request), kitchenId, role, date, status));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderService.OrderView> detail(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(userId(request), id));
    }

    @PostMapping("/orders/{id}/complete")
    public ApiResponse<OrderService.OrderView> complete(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(orderService.complete(userId(request), id));
    }

    @PostMapping("/orders/{id}/refund-request")
    public ApiResponse<OrderService.OrderView> requestRefund(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(orderService.requestRefund(userId(request), id));
    }

    @PostMapping("/orders/{id}/refund-approve")
    public ApiResponse<OrderService.OrderView> approveRefund(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(orderService.approveRefund(userId(request), id));
    }

    @PostMapping("/orders/{id}/refund-reject")
    public ApiResponse<OrderService.OrderView> rejectRefund(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(orderService.rejectRefund(userId(request), id));
    }

    /** 随机点菜：从在售菜单随机抽 count 道。 */
    @GetMapping("/kitchens/{kitchenId}/random-dishes")
    public ApiResponse<List<DishView>> randomDishes(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "1") int count) {
        return ApiResponse.ok(dishService.randomMenu(userId(request), kitchenId, categoryId, count));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
