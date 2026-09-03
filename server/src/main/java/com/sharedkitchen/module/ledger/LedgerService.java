package com.sharedkitchen.module.ledger;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.user.UserRepository;
import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private static final String EXPORT_DIR = "./data/exports";
    /** 新厨房首次打开账本时懒加载的默认分类。 */
    private static final List<String> DEFAULT_EXPENSE_CATEGORIES =
            List.of("食材采购", "厨房设备", "水电燃气", "其他");
    private static final List<String> DEFAULT_INCOME_CATEGORIES =
            List.of("菜品销售", "其他收入");

    private final LedgerEntryRepository ledgerRepository;
    private final LedgerCategoryRepository categoryRepository;
    private final KitchenMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final GlmVisionClient glmVision;

    public LedgerService(LedgerEntryRepository ledgerRepository,
                         LedgerCategoryRepository categoryRepository,
                         KitchenMemberRepository memberRepository,
                         UserRepository userRepository,
                         ObjectMapper objectMapper,
                         GlmVisionClient glmVision) {
        this.ledgerRepository = ledgerRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.glmVision = glmVision;
    }

    // ---------- 账本分类（每厨房可自定义） ----------

    /** 分类列表；厨房首次使用时懒加载默认分类。 */
    @Transactional
    public List<LedgerCategoryView> listCategories(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        if (categoryRepository.countByKitchenId(kitchenId) == 0) {
            seedDefaultCategories(kitchenId);
        }
        return categoryRepository.findByKitchenIdOrderByTypeAscIdAsc(kitchenId).stream()
                .map(c -> new LedgerCategoryView(c.getId(), c.getType(), c.getName()))
                .toList();
    }

    @Transactional
    public LedgerCategoryView createCategory(Long userId, Long kitchenId, String type, String name) {
        requireMember(kitchenId, userId);
        if (!LedgerCategory.TYPE_INCOME.equals(type) && !LedgerCategory.TYPE_EXPENSE.equals(type)) {
            throw new BusinessException("分类类型只能是收入或支出");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入分类名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 10) {
            throw new BusinessException("分类名最多10个字");
        }
        boolean exists = categoryRepository.findByKitchenIdOrderByTypeAscIdAsc(kitchenId).stream()
                .anyMatch(c -> c.getType().equals(type) && c.getName().equals(trimmed));
        if (exists) {
            throw new BusinessException("该分类已存在");
        }
        if (categoryRepository.countByKitchenId(kitchenId) >= 40) {
            throw new BusinessException("分类最多 40 个");
        }
        LedgerCategory c = new LedgerCategory();
        c.setKitchenId(kitchenId);
        c.setType(type);
        c.setName(trimmed);
        c.setCreatedAt(now());
        categoryRepository.save(c);
        return new LedgerCategoryView(c.getId(), c.getType(), c.getName());
    }

    /** 删除分类；历史流水按名称留存，不受影响。 */
    @Transactional
    public void deleteCategory(Long userId, Long kitchenId, Long categoryId) {
        requireMember(kitchenId, userId);
        LedgerCategory c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (!c.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该分类");
        }
        categoryRepository.delete(c);
    }

    /** 手动记一笔（主账号/成员账号）。 */
    @Transactional
    public LedgerEntryView addManual(Long userId, Long kitchenId, ManualEntryReq req) {
        requireMember(kitchenId, userId);
        if (!LedgerEntry.TYPE_INCOME.equals(req.type())
                && !LedgerEntry.TYPE_EXPENSE.equals(req.type())) {
            throw new BusinessException("类型只能是收入或支出");
        }
        validCategory(kitchenId, req.type(), req.category());
        if (req.amountFen() == null || req.amountFen() <= 0) {
            throw new BusinessException("金额必须大于 0");
        }
        String date = validDate(req.date());
        List<SubItemView> subItems = validSubItems(req.subItems(), req.amountFen());
        LedgerEntry e = new LedgerEntry();
        e.setKitchenId(kitchenId);
        e.setType(req.type());
        e.setSource(LedgerEntry.SOURCE_MANUAL);
        e.setCategory(req.category() == null || req.category().isBlank() ? "其他" : req.category().trim());
        e.setAmountFen(req.amountFen());
        e.setSubItems(toSubItemsJson(subItems));
        e.setDineDate(date);
        e.setRemark(req.remark() == null ? "" : req.remark().trim());
        e.setCreatedBy(userId);
        e.setCreatedAt(now());
        ledgerRepository.save(e);
        return toView(e);
    }

    /** 删除流水（账本独立，任何来源均可删）。 */
    @Transactional
    public void deleteManual(Long userId, Long entryId) {
        LedgerEntry e = ledgerRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(404, "流水不存在"));
        requireMember(e.getKitchenId(), userId);
        ledgerRepository.delete(e);
    }

    /**
     * AI 识别小票/账单：图片直接转发 GLM-4.6V（base64 内联），返回识别出的分项与金额。
     * 图片仅在内存中转换，不落盘存储。
     */
    public List<GlmVisionClient.RecognizedItem> recognizeReceipt(Long userId, Long kitchenId, byte[] imageBytes, String filename) {
        requireMember(kitchenId, userId);
        if (!glmVision.enabled()) {
            throw new BusinessException("未配置 GLM API Key（服务端环境变量 GLM_API_KEY）");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("请选择图片");
        }
        String lower = filename == null ? "" : filename.toLowerCase();
        String mime;
        if (lower.endsWith(".png")) mime = "image/png";
        else if (lower.endsWith(".webp")) mime = "image/webp";
        else mime = "image/jpeg";
        byte[] payload = imageBytes;
        // >512KB 一律压缩：缩到长边 1600 转 JPEG，省 token 省流量（正常压缩图无需重编）
        if (imageBytes.length > 512 * 1024 && !mime.equals("image/webp")) {
            byte[] scaled = downscaleJpeg(imageBytes);
            if (scaled.length < imageBytes.length) {
                payload = scaled;
                mime = "image/jpeg";
            }
        }
        String content = glmVision.chat(GlmVisionClient.RECEIPT_PROMPT,
                java.util.Base64.getEncoder().encodeToString(payload), mime);
        List<GlmVisionClient.RecognizedItem> items = GlmVisionClient.parseItems(content, objectMapper);
        if (items.isEmpty()) {
            throw new BusinessException("AI 没有识别出消费项目，试试更清晰的照片");
        }
        return items;
    }

    /** ImageIO 缩图：长边压到 1600px，JPEG 质量 0.85。 */
    private byte[] downscaleJpeg(byte[] src) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(src));
            if (img == null) return src; // 解不出来就原样发
            int w = img.getWidth(), h = img.getHeight();
            double scale = Math.min(1.0, 1600.0 / Math.max(w, h));
            int tw = Math.max(1, (int) Math.round(w * scale));
            int th = Math.max(1, (int) Math.round(h * scale));
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(tw, th, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, tw, th, null);
            g.dispose();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "jpg", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            return src;
        }
    }

    /** 月度流水列表。 */
    public List<LedgerEntryView> list(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        return ledgerRepository
                .findByKitchenIdAndDineDateStartingWithOrderByDineDateDescCreatedAtDesc(
                        kitchenId, validMonth(month))
                .stream().map(this::toView).toList();
    }

    /** 月度汇总 + 每日收支序列。 */
    public MonthSummary summary(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        String m = validMonth(month);
        List<LedgerEntry> entries = ledgerRepository
                .findByKitchenIdAndDineDateStartingWithOrderByDineDateDescCreatedAtDesc(kitchenId, m);
        long income = 0, refund = 0, expense = 0;
        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (LedgerEntry e : entries) {
            long amount = e.getAmountFen();
            if (LedgerEntry.TYPE_INCOME.equals(e.getType())) {
                income += amount;
            } else if (LedgerEntry.TYPE_REFUND.equals(e.getType())) {
                refund += amount;
            } else {
                expense += amount;
            }
            long[] day = byDay.computeIfAbsent(e.getDineDate(), k -> new long[2]);
            if (LedgerEntry.TYPE_EXPENSE.equals(e.getType())) {
                day[1] += amount;
            } else {
                day[0] += amount; // 收入；退款在日序列里计为负收入由前端用 income-refund 呈现，这里只累计毛收入
            }
        }
        List<DayPoint> days = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(k -> new DayPoint(k.getKey(), k.getValue()[0], k.getValue()[1]))
                .toList();
        return new MonthSummary(income, refund, expense, income - refund - expense, days);
    }

    /** 生成月度账本 Excel（4 Sheet），返回下载相对 URL。 */
    public String exportExcel(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        String m = validMonth(month);
        MonthSummary s = summary(userId, kitchenId, m);
        List<LedgerEntryView> entries = list(userId, kitchenId, m);

        List<List<String>> summaryRows = List.of(
                List.of("期间收入合计(元)", fen(s.income())),
                List.of("期间退款合计(元)", fen(s.refund())),
                List.of("期间支出合计(元)", fen(s.expense())),
                List.of("期间结余(元)", fen(s.balance())),
                List.of("生成时间", java.time.LocalDateTime.now().toString()));
        List<List<String>> incomeRows = entries.stream()
                .filter(e -> LedgerEntry.TYPE_INCOME.equals(e.type())
                        || LedgerEntry.TYPE_REFUND.equals(e.type()))
                .map(e -> List.of(e.date(), e.type().equals("REFUND") ? "退款冲销" : "菜品销售",
                        e.orderId() == null ? "" : String.valueOf(e.orderId()),
                        fen(e.type().equals("REFUND") ? -e.amountFen() : e.amountFen()),
                        remarkWithSubItems(e)))
                .toList();
        List<List<String>> expenseRows = entries.stream()
                .filter(e -> LedgerEntry.TYPE_EXPENSE.equals(e.type()))
                .map(e -> List.of(e.date(), e.category(),
                        "", fen(e.amountFen()), remarkWithSubItems(e)))
                .toList();
        try {
            File dir = new File(EXPORT_DIR);
            if (!dir.exists()) dir.mkdirs();
            String filename = "ledger-" + kitchenId + "-" + m.replace("-", "") + ".xlsx";
            File file = new File(dir, filename);
            com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(file.getAbsolutePath())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .build();
            com.alibaba.excel.write.metadata.WriteSheet sheet1 =
                    EasyExcel.writerSheet("汇总").head(List.of(List.of("项目", "金额/值"))).build();
            excelWriter.write(summaryRows, sheet1);
            com.alibaba.excel.write.metadata.WriteSheet sheet2 =
                    EasyExcel.writerSheet(1, "收入明细").head(List.of(List.of("日期", "来源", "订单号", "金额(元)", "备注"))).build();
            excelWriter.write(incomeRows.isEmpty() ? List.of(List.of("", "", "", "", "")) : incomeRows, sheet2);
            com.alibaba.excel.write.metadata.WriteSheet sheet3 =
                    EasyExcel.writerSheet(2, "支出明细").head(List.of(List.of("日期", "分类", "订单号", "金额(元)", "备注"))).build();
            excelWriter.write(expenseRows.isEmpty() ? List.of(List.of("", "", "", "", "")) : expenseRows, sheet3);
            excelWriter.finish();
            return "/files/exports/" + filename;
        } catch (Exception e) {
            throw new BusinessException("账本导出失败，请重试");
        }
    }

    private String fen(Long amountFen) {
        return java.math.BigDecimal.valueOf(amountFen == null ? 0 : amountFen, 2).toPlainString();
    }

    /** Excel 备注列：有分项时追加「分项：蔬菜 30.00 / 肉 50.00」。 */
    private String remarkWithSubItems(LedgerEntryView e) {
        if (e.subItems() == null || e.subItems().isEmpty()) return e.remark();
        String detail = e.subItems().stream()
                .map(s -> s.name() + " " + fen(s.amountFen()))
                .reduce((a, b) -> a + " / " + b).orElse("");
        String prefix = e.remark().isBlank() ? "" : e.remark() + "；";
        return prefix + "分项：" + detail;
    }

    /** 分类须属于该厨房该类型；不传时按类型落到默认分类。 */
    private void validCategory(Long kitchenId, String type, String category) {
        if (category == null || category.isBlank()) {
            return; // 服务端落默认值
        }
        if (categoryRepository.countByKitchenId(kitchenId) == 0) {
            seedDefaultCategories(kitchenId);
        }
        boolean allowed = categoryRepository.findByKitchenIdOrderByTypeAscIdAsc(kitchenId).stream()
                .anyMatch(c -> c.getType().equals(type) && c.getName().equals(category.trim()));
        if (!allowed) {
            throw new BusinessException("不支持的分类，可在分类管理中添加");
        }
    }

    private void seedDefaultCategories(Long kitchenId) {
        String t = now();
        for (String name : DEFAULT_EXPENSE_CATEGORIES) {
            LedgerCategory c = new LedgerCategory();
            c.setKitchenId(kitchenId);
            c.setType(LedgerCategory.TYPE_EXPENSE);
            c.setName(name);
            c.setCreatedAt(t);
            categoryRepository.save(c);
        }
        for (String name : DEFAULT_INCOME_CATEGORIES) {
            LedgerCategory c = new LedgerCategory();
            c.setKitchenId(kitchenId);
            c.setType(LedgerCategory.TYPE_INCOME);
            c.setName(name);
            c.setCreatedAt(t);
            categoryRepository.save(c);
        }
    }

    private String validDate(String date) {
        if (date == null || date.isBlank()) return LocalDate.now().toString();
        if (!date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        return date.trim();
    }

    /**
     * 分费用校验：每项名称非空（≤20字）、金额>0，且分项合计必须等于总金额。
     * 返回规范化（去空白）后的分项列表；未传/传空返回 null。
     */
    private List<SubItemView> validSubItems(List<SubItemReq> raw, long totalFen) {
        if (raw == null || raw.isEmpty()) return null;
        List<SubItemView> list = new ArrayList<>();
        long sum = 0;
        for (SubItemReq s : raw) {
            if (s == null || s.name() == null || s.name().isBlank()) {
                throw new BusinessException("分费用名称不能为空");
            }
            if (s.amountFen() == null || s.amountFen() <= 0) {
                throw new BusinessException("分费用金额必须大于 0");
            }
            String name = s.name().trim();
            if (name.length() > 20) {
                throw new BusinessException("分费用名称最多20个字");
            }
            if (list.size() >= 20) {
                throw new BusinessException("分费用最多 20 项");
            }
            list.add(new SubItemView(name, s.amountFen()));
            sum += s.amountFen();
        }
        if (sum != totalFen) {
            throw new BusinessException("分费用合计与总金额不一致，请检查");
        }
        return list;
    }

    private String toSubItemsJson(List<SubItemView> subItems) {
        if (subItems == null) return null;
        try {
            return objectMapper.writeValueAsString(subItems);
        } catch (Exception ex) {
            throw new BusinessException("分费用保存失败，请重试");
        }
    }

    private List<SubItemView> parseSubItems(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SubItemView.class));
        } catch (Exception ex) {
            return null; // 历史脏数据不阻塞展示
        }
    }

    private String validMonth(String month) {
        if (month == null || month.isBlank()) return LocalDate.now().toString().substring(0, 7);
        if (!month.trim().matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
        return month.trim();
    }

    private void requireMember(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(m.getRole())
                && !KitchenMember.ROLE_MEMBER.equals(m.getRole())) {
            throw new BusinessException(403, "需要主账号或成员权限");
        }
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    private LedgerEntryView toView(LedgerEntry e) {
        return new LedgerEntryView(e.getId(), e.getType(), e.getSource(), e.getOrderId(),
                e.getCategory(), e.getAmountFen(), e.getDineDate(), e.getRemark(),
                parseSubItems(e.getSubItems()), e.getCreatedAt());
    }

    public record ManualEntryReq(String type, String category,
                                 Long amountFen, String date, String remark,
                                 List<SubItemReq> subItems) {}

    public record SubItemReq(String name, Long amountFen) {}

    public record LedgerCategoryView(Long id, String type, String name) {}

    public record LedgerEntryView(Long id, String type, String source, Long orderId,
                                  String category, Long amountFen, String date,
                                  String remark, List<SubItemView> subItems, String createdAt) {}

    public record SubItemView(String name, Long amountFen) {}

    public record MonthSummary(long income, long refund, long expense, long balance,
                               List<DayPoint> days) {}

    public record DayPoint(String date, long incomeFen, long expenseFen) {}

}
