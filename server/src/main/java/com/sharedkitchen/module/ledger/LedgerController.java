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
    private final FxService fx;
    private final PaymentMethodRepository paymentMethods;
    private final LedgerPdfService pdfService;

    public LedgerController(LedgerService ledgerService, SavingService savingService,
                            FxService fx, PaymentMethodRepository paymentMethods,
                            LedgerPdfService pdfService) {
        this.ledgerService = ledgerService;
        this.savingService = savingService;
        this.fx = fx;
        this.paymentMethods = paymentMethods;
        this.pdfService = pdfService;
    }

    public record ManualEntryReq(
            @NotBlank String type,
            String category,
            @NotNull Long amountFen,
            String date,
            String remark,
            List<LedgerService.SubItemReq> subItems,
            String imageUrl, String paymentMethod, String currency) {}

    @PostMapping("/entries")
    public ApiResponse<LedgerService.LedgerEntryView> add(
            HttpServletRequest request, @PathVariable Long kitchenId,
            @RequestBody @Validated ManualEntryReq req) {
        Long userId = userId(request);
        return ApiResponse.ok(ledgerService.addManual(userId, kitchenId,
                new LedgerService.ManualEntryReq(req.type(), req.category(),
                        req.amountFen(), req.date(), req.remark(), req.subItems(),
                        req.imageUrl(), req.paymentMethod(), req.currency())));
    }

    @PutMapping("/entries/{id}")
    public ApiResponse<LedgerService.LedgerEntryView> update(
            HttpServletRequest request, @PathVariable Long kitchenId, @PathVariable Long id,
            @RequestBody @Validated ManualEntryReq req) {
        Long userId = userId(request);
        return ApiResponse.ok(ledgerService.updateManual(userId, id,
                new LedgerService.ManualEntryReq(req.type(), req.category(),
                        req.amountFen(), req.date(), req.remark(), req.subItems(),
                        req.imageUrl(), req.paymentMethod(), req.currency())));
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

    // ----- 汇率 -----

    @GetMapping("/fx")
    public ApiResponse<Map<String, Object>> fx() {
        return ApiResponse.ok(Map.of("HKD", fx.hkd(), "USD", fx.usd()));
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
