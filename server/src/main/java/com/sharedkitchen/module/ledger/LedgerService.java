package com.sharedkitchen.module.ledger;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.order.Order;
import com.sharedkitchen.module.order.OrderItem;
import com.sharedkitchen.module.order.OrderItemRepository;
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
    private final OrderItemRepository orderItemRepository;
    private final com.sharedkitchen.module.order.OrderRepository orderRepository;
    private final UserRepository userRepository;

    public LedgerService(LedgerEntryRepository ledgerRepository,
                         LedgerCategoryRepository categoryRepository,
                         KitchenMemberRepository memberRepository,
                         OrderItemRepository orderItemRepository,
                         com.sharedkitchen.module.order.OrderRepository orderRepository,
                         UserRepository userRepository) {
        this.ledgerRepository = ledgerRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /** 订单完成 → 自动收入流水（由 OrderService 在同一事务内调用）。 */
    @Transactional
    public void recordOrderIncome(Order order) {
        if (ledgerRepository.findByOrderIdAndType(order.getId(), LedgerEntry.TYPE_INCOME).isPresent()) {
            return; // 幂等：唯一约束兜底 + 显式检查
        }
        LedgerEntry e = new LedgerEntry();
        e.setKitchenId(order.getKitchenId());
        e.setType(LedgerEntry.TYPE_INCOME);
        e.setSource(LedgerEntry.SOURCE_ORDER);
        e.setOrderId(order.getId());
        e.setCategory("菜品销售");
        e.setAmountFen(order.getTotalFen());
        e.setDineDate(order.getDineDate());
        e.setRemark("订单 #" + order.getId());
        e.setCreatedBy(order.getBuyerId());
        e.setCreatedAt(now());
        ledgerRepository.save(e);
    }

    /** 退单 → 自动冲销流水。 */
    @Transactional
    public void recordOrderRefund(Order order) {
        if (ledgerRepository.findByOrderIdAndType(order.getId(), LedgerEntry.TYPE_REFUND).isPresent()) {
            return;
        }
        LedgerEntry e = new LedgerEntry();
        e.setKitchenId(order.getKitchenId());
        e.setType(LedgerEntry.TYPE_REFUND);
        e.setSource(LedgerEntry.SOURCE_ORDER);
        e.setOrderId(order.getId());
        e.setCategory("菜品销售");
        e.setAmountFen(order.getTotalFen());
        e.setDineDate(LocalDate.now().toString());
        e.setRemark("订单 #" + order.getId() + " 退款");
        e.setCreatedBy(order.getBuyerId());
        e.setCreatedAt(now());
        ledgerRepository.save(e);
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

    private void seedDefaultCategories(Long kitchenId) {
        String t = now();
        List<String> all = new ArrayList<>(DEFAULT_EXPENSE_CATEGORIES);
        for (String name : all) {
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
        LedgerEntry e = new LedgerEntry();
        e.setKitchenId(kitchenId);
        e.setType(req.type());
        e.setSource(LedgerEntry.SOURCE_MANUAL);
        e.setCategory(req.category() == null || req.category().isBlank() ? "其他" : req.category().trim());
        e.setAmountFen(req.amountFen());
        e.setDineDate(date);
        e.setRemark(req.remark() == null ? "" : req.remark().trim());
        e.setCreatedBy(userId);
        e.setCreatedAt(now());
        ledgerRepository.save(e);
        return toView(e);
    }

    /** 删除手动流水（订单自动流水不可删，只能退单冲销）。 */
    @Transactional
    public void deleteManual(Long userId, Long entryId) {
        LedgerEntry e = ledgerRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(404, "流水不存在"));
        requireMember(e.getKitchenId(), userId);
        if (LedgerEntry.SOURCE_ORDER.equals(e.getSource())) {
            throw new BusinessException("订单流水不可删除，退款请走订单退单流程");
        }
        ledgerRepository.delete(e);
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

    /** 菜品销售统计（当月已完成订单）。 */
    public List<DishStat> dishStats(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        String m = validMonth(month);
        Map<String, long[]> byDish = new LinkedHashMap<>(); // name -> [qty, salesFen]
        for (Order order : completedOrdersOfMonth(kitchenId, m)) {
            for (OrderItem item : orderItemRepository.findByOrderIdIn(List.of(order.getId()))) {
                long[] agg = byDish.computeIfAbsent(item.getDishName(), k -> new long[2]);
                agg[0] += item.getQuantity();
                agg[1] += item.getPriceFen() * item.getQuantity();
            }
        }
        return byDish.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .map(k -> new DishStat(k.getKey(), k.getValue()[0], k.getValue()[1]))
                .toList();
    }

    /** 生成月度账本 Excel（4 Sheet），返回下载相对 URL。 */
    public String exportExcel(Long userId, Long kitchenId, String month) {
        requireMember(kitchenId, userId);
        String m = validMonth(month);
        MonthSummary s = summary(userId, kitchenId, m);
        List<LedgerEntryView> entries = list(userId, kitchenId, m);
        List<DishStat> stats = dishStats(userId, kitchenId, m);

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
                        e.remark()))
                .toList();
        List<List<String>> expenseRows = entries.stream()
                .filter(e -> LedgerEntry.TYPE_EXPENSE.equals(e.type()))
                .map(e -> List.of(e.date(), e.category(),
                        "", fen(e.amountFen()), e.remark()))
                .toList();
        long totalSales = stats.stream().mapToLong(DishStat::salesFen).sum();
        List<List<String>> statRows = stats.stream()
                .map(d -> List.of(d.name(), String.valueOf(d.quantity()),
                        fen(d.salesFen()),
                        totalSales == 0 ? "0%" : (d.salesFen() * 100 / totalSales) + "%"))
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
            com.alibaba.excel.write.metadata.WriteSheet sheet4 =
                    EasyExcel.writerSheet(3, "菜品销售").head(List.of(List.of("菜品", "销量", "销售额(元)", "占比"))).build();
            excelWriter.write(statRows.isEmpty() ? List.of(List.of("", "", "", "")) : statRows, sheet4);
            excelWriter.finish();
            return "/files/exports/" + filename;
        } catch (Exception e) {
            throw new BusinessException("账本导出失败，请重试");
        }
    }

    private List<Order> completedOrdersOfMonth(Long kitchenId, String month) {
        YearMonth ym = YearMonth.parse(month);
        String from = ym.atDay(1).toString();
        String to = ym.atEndOfMonth().toString();
        return orderRepository
                .findByKitchenIdAndStatusOrderByCreatedAtDesc(kitchenId, "COMPLETED")
                .stream()
                .filter(o -> o.getDineDate().compareTo(from) >= 0
                        && o.getDineDate().compareTo(to) <= 0)
                .toList();
    }

    private String fen(Long amountFen) {
        return java.math.BigDecimal.valueOf(amountFen == null ? 0 : amountFen, 2).toPlainString();
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

    private String validDate(String date) {
        if (date == null || date.isBlank()) return LocalDate.now().toString();
        if (!date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        return date.trim();
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
                e.getCategory(), e.getAmountFen(), e.getDineDate(), e.getRemark(), e.getCreatedAt());
    }

    public record ManualEntryReq(String type, String category,
                                 Long amountFen, String date, String remark) {}

    public record LedgerCategoryView(Long id, String type, String name) {}

    public record LedgerEntryView(Long id, String type, String source, Long orderId,
                                  String category, Long amountFen, String date,
                                  String remark, String createdAt) {}

    public record MonthSummary(long income, long refund, long expense, long balance,
                               List<DayPoint> days) {}

    public record DayPoint(String date, long incomeFen, long expenseFen) {}

    public record DishStat(String name, long quantity, long salesFen) {}
}
