package com.sharedkitchen.module.ledger;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens/{kitchenId}/ledger")
@Validated
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    public record ManualEntryReq(
            @NotBlank String type,
            String category,
            @NotNull Long amountFen,
            String date,
            String remark) {}

    @PostMapping("/entries")
    public ApiResponse<LedgerService.LedgerEntryView> add(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated ManualEntryReq req) {
        Long userId = userId(request);
        return ApiResponse.ok(ledgerService.addManual(userId, kitchenId,
                new LedgerService.ManualEntryReq(req.type(), req.category(),
                        req.amountFen(), req.date(), req.remark())));
    }

    @GetMapping("/entries")
    public ApiResponse<List<LedgerService.LedgerEntryView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(ledgerService.list(userId(request), kitchenId, month));
    }

    @DeleteMapping("/entries/{id}")
    public ApiResponse<Void> delete(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id) {
        ledgerService.deleteManual(userId(request), id);
        return ApiResponse.ok();
    }

    @GetMapping("/summary")
    public ApiResponse<LedgerService.MonthSummary> summary(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(ledgerService.summary(userId(request), kitchenId, month));
    }

    @GetMapping("/dish-stats")
    public ApiResponse<List<LedgerService.DishStat>> dishStats(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(ledgerService.dishStats(userId(request), kitchenId, month));
    }

    public record CategoryReq(@NotBlank String type, @NotBlank String name) {}

    // ----- 账本分类（每厨房可自定义） -----

    @GetMapping("/categories")
    public ApiResponse<List<LedgerService.LedgerCategoryView>> categories(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(ledgerService.listCategories(userId(request), kitchenId));
    }

    @PostMapping("/categories")
    public ApiResponse<LedgerService.LedgerCategoryView> createCategory(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated CategoryReq req) {
        return ApiResponse.ok(ledgerService.createCategory(
                userId(request), kitchenId, req.type(), req.name()));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<Void> deleteCategory(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @PathVariable Long categoryId) {
        ledgerService.deleteCategory(userId(request), kitchenId, categoryId);
        return ApiResponse.ok();
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, String>> export(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        String url = ledgerService.exportExcel(userId(request), kitchenId, month);
        return ApiResponse.ok(Map.of("url", url));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
    }
}
