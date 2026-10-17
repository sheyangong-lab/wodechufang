package com.sharedkitchen.module.ledger;

import com.sharedkitchen.common.ApiResponse;
import com.sharedkitchen.common.JwtAuthFilter;
import com.sharedkitchen.module.ledger.account.LedgerBudgetService;
import com.sharedkitchen.module.ledger.account.RecurringBillService;
import com.sharedkitchen.module.ledger.account.SavingGoalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchens/{kitchenId}/ledger")
@Validated
public class LedgerController {

    private final LedgerService ledgerService;
    private final SavingService savingService;
    private final PaymentMethodRepository paymentMethods;
    private final LedgerPdfService pdfService;
    private final LedgerBudgetService budgetService;
    private final RecurringBillService billService;
    private final SavingGoalService goalService;

    public LedgerController(LedgerService ledgerService, SavingService savingService,
                            PaymentMethodRepository paymentMethods,
                            LedgerPdfService pdfService,
                            LedgerBudgetService budgetService,
                            RecurringBillService billService,
                            SavingGoalService goalService) {
        this.ledgerService = ledgerService;
        this.savingService = savingService;
        this.paymentMethods = paymentMethods;
        this.pdfService = pdfService;
        this.budgetService = budgetService;
        this.billService = billService;
        this.goalService = goalService;
    }

    public record ManualEntryReq(
            @NotBlank String type,
            String category,
            @NotNull Long amountFen,
            String date,
            String remark,
            List<LedgerService.SubItemReq> subItems,
            String imageUrl, String paymentMethod, String currency,
            Long accountId) {}

    @PostMapping("/entries")
    public ApiResponse<LedgerService.LedgerEntryView> add(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated ManualEntryReq req) {
        Long userId = userId(request);
        return ApiResponse.ok(ledgerService.addManual(userId, kitchenId,
                new LedgerService.ManualEntryReq(req.type(), req.category(),
                        req.amountFen(), req.date(), req.remark(), req.subItems(),
                        req.imageUrl(), req.paymentMethod(), req.currency(), req.accountId())));
    }

    @PutMapping("/entries/{id}")
    public ApiResponse<LedgerService.LedgerEntryView> update(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id,
            @RequestBody @Validated ManualEntryReq req) {
        Long userId = userId(request);
        return ApiResponse.ok(ledgerService.updateManual(userId, id,
                new LedgerService.ManualEntryReq(req.type(), req.category(),
                        req.amountFen(), req.date(), req.remark(), req.subItems(),
                        req.imageUrl(), req.paymentMethod(), req.currency(), req.accountId())));
    }

    /** 账户间转账（存钱/取回/还款）：一次请求生成 TRANSFER 双行（out 在转出账户、in 在转入账户）。 */
    public record TransferReq(Long fromAccountId, Long toAccountId,
                              @NotNull Long amountFen, String date, String remark) {}

    @PostMapping("/transfers")
    public ApiResponse<List<LedgerService.LedgerEntryView>> transfer(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated TransferReq req) {
        return ApiResponse.ok(ledgerService.transfer(userId(request), kitchenId,
                new LedgerService.TransferReq(req.fromAccountId(), req.toAccountId(),
                        req.amountFen(), req.date(), req.remark())));
    }

    @GetMapping("/entries")
    public ApiResponse<List<LedgerService.LedgerEntryView>> list(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(ledgerService.list(userId(request), kitchenId, month));
    }

    /** AI 识别小票/账单：multipart 直传，服务端转 base64 调 GLM-4.6V，返回识别出的分项。 */
    @PostMapping("/recognize")
    public ApiResponse<List<GlmVisionClient.RecognizedItem>> recognize(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String filename = file == null ? "" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file == null ? null : file.getBytes();
        } catch (java.io.IOException e) {
            bytes = null;
        }
        return ApiResponse.ok(ledgerService.recognizeReceipt(userId(request), kitchenId, bytes, filename));
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

    @GetMapping(value = "/export.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> exportPdf(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) throws Exception {
        Long uid = userId(request);
        String m = ledgerService.validMonth(month);
        LedgerService.MonthSummary summary = ledgerService.summary(uid, kitchenId, m);
        List<LedgerService.LedgerEntryView> entries = ledgerService.list(uid, kitchenId, m);
        byte[] pdf = pdfService.export(m + " 账单", summary, entries);
        String fname = java.net.URLEncoder.encode("共享厨房账单-" + m + ".pdf", java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + fname)
                .header("Content-Type", "application/pdf")
                .body(pdf);
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

    // ----- 预算（P2：总预算+分类预算，每厨房每月整体覆盖） -----

    @GetMapping("/budgets")
    public ApiResponse<LedgerBudgetService.BudgetView> budgets(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestParam(required = false) String month) {
        return ApiResponse.ok(budgetService.get(userId(request), kitchenId, month));
    }

    public record BudgetItemReq(String category, Long amountFen) {}

    public record BudgetPutReq(@NotBlank String month, Long totalFen,
                               List<BudgetItemReq> items) {}

    @PutMapping("/budgets")
    public ApiResponse<LedgerBudgetService.BudgetPutView> budgetsPut(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated BudgetPutReq req) {
        return ApiResponse.ok(budgetService.put(userId(request), kitchenId,
                new LedgerBudgetService.BudgetPutReq(req.month(), req.totalFen(),
                        req.items() == null ? null : req.items().stream()
                                .map(i -> new LedgerBudgetService.BudgetItemReq(i.category(), i.amountFen()))
                                .toList())));
    }

    // ----- 周期账单（P2：固定收支自动入账） -----

    @GetMapping("/recurring-bills")
    public ApiResponse<List<RecurringBillService.BillView>> bills(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(billService.list(userId(request), kitchenId));
    }

    public record BillReq(String type, String category, Long amountFen, Long accountId,
                          String cycle, String nextDate, String remark, Integer enabled) {}

    @PostMapping("/recurring-bills")
    public ApiResponse<RecurringBillService.BillView> billCreate(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody BillReq req) {
        return ApiResponse.ok(billService.create(userId(request), kitchenId, toBillReq(req)));
    }

    @PutMapping("/recurring-bills/{id}")
    public ApiResponse<RecurringBillService.BillView> billUpdate(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id,
            @RequestBody BillReq req) {
        return ApiResponse.ok(billService.update(userId(request), kitchenId, id, toBillReq(req)));
    }

    @DeleteMapping("/recurring-bills/{id}")
    public ApiResponse<Void> billDelete(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id) {
        billService.delete(userId(request), kitchenId, id);
        return ApiResponse.ok();
    }

    /** 手动触发扫描（等价每日 8:05 定时任务）：周期账补账 + 预算超支通知；返回生成流水/通知条数。 */
    @PostMapping("/recurring-bills/scan")
    public ApiResponse<Map<String, Object>> billScan(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        billService.list(userId(request), kitchenId); // 成员权限校验（扫描本身跨厨房）
        int entries = billService.scanOnce();
        int notified = budgetService.notifyOverruns();
        return ApiResponse.ok(Map.of("entries", entries, "notified", notified));
    }

    private RecurringBillService.BillReq toBillReq(BillReq req) {
        return new RecurringBillService.BillReq(req.type(), req.category(), req.amountFen(),
                req.accountId(), req.cycle(), req.nextDate(), req.remark(), req.enabled());
    }

    // ----- 存款目标（P2：progress=全部 SAVING 账户余额之和） -----

    @GetMapping("/saving-goals")
    public ApiResponse<List<SavingGoalService.GoalView>> goals(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(goalService.list(userId(request), kitchenId));
    }

    public record GoalReq(@NotBlank String name, @NotNull Long targetFen, String deadline) {}

    @PostMapping("/saving-goals")
    public ApiResponse<SavingGoalService.GoalView> goalCreate(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated GoalReq req) {
        return ApiResponse.ok(goalService.create(userId(request), kitchenId,
                req.name(), req.targetFen(), req.deadline()));
    }

    @DeleteMapping("/saving-goals/{id}")
    public ApiResponse<Void> goalDelete(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id) {
        goalService.delete(userId(request), kitchenId, id);
        return ApiResponse.ok();
    }

    // ----- 存钱罐 -----

    @GetMapping("/savings")
    public ApiResponse<List<SavingService.SavingView>> savingsList(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(savingService.list(userId(request), kitchenId));
    }

    @GetMapping("/savings/summary")
    public ApiResponse<Map<String, Object>> savingsSummary(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        return ApiResponse.ok(savingService.summary(userId(request), kitchenId));
    }

    public record SavingReq(String name, String currency, @NotNull Long amountFen,
                            String kind, String note) {}

    @PostMapping("/savings")
    public ApiResponse<SavingService.SavingView> savingAdd(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated SavingReq req) {
        return ApiResponse.ok(savingService.add(userId(request), kitchenId, req.name(),
                req.currency(), req.amountFen(), req.kind(), req.note()));
    }

    @DeleteMapping("/savings/{id}")
    public ApiResponse<Void> savingRemove(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id) {
        savingService.remove(userId(request), id);
        return ApiResponse.ok();
    }

    // ----- 汇率（v2 固定展示值，兼容旧版 App；记账折算仍走 FxService 实时汇率） -----

    @GetMapping("/fx")
    public ApiResponse<Map<String, Object>> fx() {
        return ApiResponse.ok(Map.of("CNY", 1, "HKD", 0.92, "USD", 7.24));
    }

    // ----- 支付方式 -----

    @GetMapping("/payment-methods")
    public ApiResponse<List<Map<String, Object>>> paymentMethods(
            HttpServletRequest request, @PathVariable Long kitchenId) {
        Long uid = userId(request);
        var ms = paymentMethods.findByKitchenIdOrderBySortAscIdAsc(kitchenId);
        if (ms.isEmpty()) {
            // 懒加载默认项
            int i = 0;
            for (String n : List.of("微信", "支付宝", "现金", "银行卡")) {
                PaymentMethod m = new PaymentMethod();
                m.setKitchenId(kitchenId);
                m.setName(n);
                m.setSort(i++);
                paymentMethods.save(m);
            }
            ms = paymentMethods.findByKitchenIdOrderBySortAscIdAsc(kitchenId);
        }
        return ApiResponse.ok(ms.stream().map(m -> Map.<String, Object>of(
                "id", m.getId(), "name", m.getName())).toList());
    }

    public record PaymentMethodReq(@NotBlank String name) {}

    @PostMapping("/payment-methods")
    public ApiResponse<Map<String, Object>> paymentMethodAdd(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated PaymentMethodReq req) {
        if (paymentMethods.findByKitchenIdAndName(kitchenId, req.name().trim()).isPresent()) {
            throw new com.sharedkitchen.common.BusinessException("该支付方式已存在");
        }
        PaymentMethod m = new PaymentMethod();
        m.setKitchenId(kitchenId);
        m.setName(req.name().trim());
        m.setSort((int) paymentMethods.count());
        paymentMethods.save(m);
        return ApiResponse.ok(Map.of("id", m.getId(), "name", m.getName()));
    }

    @DeleteMapping("/payment-methods/{id}")
    public ApiResponse<Void> paymentMethodRemove(
            @PathVariable Long kitchenId, @PathVariable Long id) {
        paymentMethods.findById(id)
                .filter(m -> m.getKitchenId().equals(kitchenId))
                .ifPresent(paymentMethods::delete);
        return ApiResponse.ok();
    }
}
