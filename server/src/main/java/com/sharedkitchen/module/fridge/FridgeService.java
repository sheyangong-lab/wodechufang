package com.sharedkitchen.module.fridge;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.dish.Dish;
import com.sharedkitchen.module.dish.DishRepository;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FridgeService {

    /** 快过期阈值：剩余 ≤3 天 */
    public static final int EXPIRING_DAYS = 3;

    private final FridgeItemRepository itemRepository;
    private final FridgeCategoryRepository categoryRepository;
    private final KitchenMemberRepository memberRepository;
    private final DishRepository dishRepository;
    private final NotificationRepository notificationRepository;

    public FridgeService(FridgeItemRepository itemRepository,
                         FridgeCategoryRepository categoryRepository,
                         KitchenMemberRepository memberRepository,
                         DishRepository dishRepository,
                         NotificationRepository notificationRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.memberRepository = memberRepository;
        this.dishRepository = dishRepository;
        this.notificationRepository = notificationRepository;
    }

    // ---------- 类别 ----------

    public List<FridgeCategoryView> listCategories(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return categoryRepository.findByKitchenIdOrderBySortAscIdAsc(kitchenId).stream()
                .map(c -> new FridgeCategoryView(c.getId(), c.getName()))
                .toList();
    }

    @Transactional
    public FridgeCategoryView createCategory(Long userId, Long kitchenId, String name) {
        requireManager(kitchenId, userId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入类别名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 10) {
            throw new BusinessException("类别名最多10个字");
        }
        if (categoryRepository.countByKitchenId(kitchenId) >= 20) {
            throw new BusinessException("类别最多 20 个");
        }
        FridgeCategory c = new FridgeCategory();
        c.setKitchenId(kitchenId);
        c.setName(trimmed);
        c.setSort(categoryRepository.findByKitchenIdOrderBySortAscIdAsc(kitchenId).size());
        c.setCreatedAt(now());
        categoryRepository.save(c);
        return new FridgeCategoryView(c.getId(), c.getName());
    }

    /** 按传入 id 顺序重排类别。 */
    @Transactional
    public void reorderCategories(Long userId, Long kitchenId, List<Long> ids) {
        requireManager(kitchenId, userId);
        int index = 0;
        for (Long id : ids) {
            FridgeCategory c = categoryRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(404, "类别不存在"));
            if (!c.getKitchenId().equals(kitchenId)) {
                throw new BusinessException(403, "无权操作该类别");
            }
            c.setSort(index++);
            categoryRepository.save(c);
        }
    }

    @Transactional
    public void deleteCategory(Long userId, Long kitchenId, Long categoryId) {
        requireManager(kitchenId, userId);
        FridgeCategory c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(404, "类别不存在"));
        if (!c.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该类别");
        }
        long used = itemRepository
                .findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0).stream()
                .filter(i -> categoryId.equals(i.getCategoryId()))
                .count();
        if (used > 0) {
            throw new BusinessException("该类别下还有 " + used + " 种食材，先移走再删除");
        }
        categoryRepository.delete(c);
    }

    // ---------- 食材 ----------

    /** 批量放入食材。 */
    @Transactional
    public List<FridgeItemView> createItems(Long userId, Long kitchenId, List<ItemReq> items) {
        requireManager(kitchenId, userId);
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少填写一种食材");
        }
        if (items.size() > 20) {
            throw new BusinessException("一次最多放入 20 种食材");
        }
        List<FridgeItemView> views = new java.util.ArrayList<>();
        for (ItemReq req : items) {
            if (req.name() == null || req.name().isBlank()) {
                throw new BusinessException("食材名称不能为空");
            }
            if (req.shelfLifeValue() == null || req.shelfLifeValue() < 1) {
                throw new BusinessException("「" + req.name().trim() + "」保质期至少 1 天");
            }
            FridgeItem item = new FridgeItem();
            item.setKitchenId(kitchenId);
            item.setName(req.name().trim());
            item.setCategoryId(req.categoryId());
            item.setImageUrl(req.imageUrl() == null ? "" : req.imageUrl().trim());
            item.setProducedDate(validDate(req.producedDate()));
            item.setShelfLifeValue(req.shelfLifeValue());
            item.setShelfLifeUnit(validUnit(req.shelfLifeUnit()));
            item.setQuantity(req.quantity() == null ? "" : req.quantity().trim());
            item.setRemark(req.remark() == null ? "" : req.remark().trim());
            item.setCreatedAt(now());
            itemRepository.save(item);
            views.add(toView(item));
        }
        return views;
    }

    /** 修改食材（名称/数量/类别/保质期等，按传入字段更新）。 */
    @Transactional
    public FridgeItemView updateItem(Long userId, Long kitchenId, Long itemId, ItemUpdateReq req) {
        requireManager(kitchenId, userId);
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "食材不存在"));
        if (!item.getKitchenId().equals(kitchenId) || item.getDeleted() == 1) {
            throw new BusinessException(404, "食材不存在");
        }
        if (req.name() != null && !req.name().isBlank()) {
            if (req.name().trim().length() > 30) {
                throw new BusinessException("食材名称最多30个字");
            }
            item.setName(req.name().trim());
        }
        if (req.categoryId() != null) {
            item.setCategoryId(req.categoryId());
        }
        if (req.producedDate() != null) {
            item.setProducedDate(validDate(req.producedDate()));
        }
        if (req.shelfLifeValue() != null) {
            if (req.shelfLifeValue() < 1) {
                throw new BusinessException("保质期至少 1 天");
            }
            item.setShelfLifeValue(req.shelfLifeValue());
        }
        if (req.shelfLifeUnit() != null) {
            item.setShelfLifeUnit(validUnit(req.shelfLifeUnit()));
        }
        if (req.quantity() != null) {
            item.setQuantity(req.quantity().trim());
        }
        if (req.remark() != null) {
            item.setRemark(req.remark().trim());
        }
        itemRepository.save(item);
        return toView(item);
    }

    /** 删除单个食材（软删，与清仓同口径）。 */
    @Transactional
    public void deleteItem(Long userId, Long kitchenId, Long itemId) {
        requireManager(kitchenId, userId);
        FridgeItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "食材不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "食材不存在");
        }
        item.setDeleted(1);
        itemRepository.save(item);
    }

    /** 三态+分类+关键词筛选。state: all/fresh/expiring/expired */
    public List<FridgeItemView> listItems(Long userId, Long kitchenId, Long categoryId,
                                          String keyword, String state) {
        requireMember(kitchenId, userId);
        List<FridgeItem> items;
        String kw = keyword == null ? null : keyword.trim();
        if (categoryId != null && kw != null && !kw.isEmpty()) {
            items = itemRepository.findByKitchenIdAndDeletedAndCategoryIdAndNameContainingOrderByCreatedAtDesc(
                    kitchenId, 0, categoryId, kw);
        } else if (categoryId != null) {
            items = itemRepository.findByKitchenIdAndDeletedAndCategoryIdOrderByCreatedAtDesc(
                    kitchenId, 0, categoryId);
        } else if (kw != null && !kw.isEmpty()) {
            items = itemRepository.findByKitchenIdAndDeletedAndNameContainingOrderByCreatedAtDesc(
                    kitchenId, 0, kw);
        } else {
            items = itemRepository.findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0);
        }
        return items.stream()
                .map(this::toView)
                .filter(v -> state == null || "all".equals(state) || state.equals(v.state()))
                .sorted(Comparator.comparingInt(FridgeItemView::daysLeft))
                .toList();
    }

    public FridgeStateSummary stateSummary(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        List<FridgeItemView> all = itemRepository
                .findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0)
                .stream().map(this::toListingView).toList();
        return new FridgeStateSummary(
                all.stream().filter(v -> "fresh".equals(v.state())).count(),
                all.stream().filter(v -> "expiring".equals(v.state())).count(),
                all.stream().filter(v -> "expired".equals(v.state())).count());
    }

    /** 清仓：批量软删。 */
    @Transactional
    public int clearItems(Long userId, Long kitchenId, List<Long> ids) {
        requireManager(kitchenId, userId);
        List<FridgeItem> all = itemRepository.findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0);
        int n = 0;
        for (FridgeItem item : all) {
            if (ids == null || ids.isEmpty() || ids.contains(item.getId())) {
                item.setDeleted(1);
                itemRepository.save(item);
                n++;
            }
        }
        return n;
    }

    /** 匹配菜谱：食材名去匹配菜谱用料。 */
    public List<MatchedDish> matchRecipes(Long userId, Long kitchenId, Long itemId, String ingredient) {
        requireMember(kitchenId, userId);
        String keyword = ingredient;
        if ((keyword == null || keyword.isBlank()) && itemId != null) {
            FridgeItem item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new BusinessException(404, "食材不存在"));
            keyword = item.getName();
        }
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("请选择食材或输入食材名");
        }
        String kw = keyword.trim();
        return dishRepository.findByKitchenIdAndDeletedAndStatusOrderByUpdatedAtDesc(kitchenId, 0, 1)
                .stream()
                .filter(d -> d.getMaterials() != null && d.getMaterials().contains(kw))
                .map(d -> new MatchedDish(d.getId(), d.getName(), d.getImageUrl(),
                        d.getPriceFen(), extractMaterialLine(d.getMaterials(), kw)))
                .toList();
    }

    // ---------- 临期通知 ----------

    /** 扫描临期食材并生成通知（同一天同厨房去重）。返回本次提醒的食材数。 */
    @Transactional
    public int checkExpiry(Long kitchenId) {
        LocalDate today = LocalDate.now();
        List<String> expiring = itemRepository
                .findByKitchenIdAndDeletedOrderByCreatedAtDesc(kitchenId, 0)
                .stream()
                .map(this::toListingView)
                .filter(v -> "expiring".equals(v.state()) || "expired".equals(v.state()))
                .sorted(Comparator.comparingInt(FridgeItemView::daysLeft))
                .map(v -> {
                    String when = v.daysLeft() < 0 ? "已过期"
                            : v.daysLeft() == 0 ? "今天过期" : v.daysLeft() + "天后过期";
                    return v.name() + "（" + when + "）";
                })
                .toList();
        if (expiring.isEmpty()) {
            return 0;
        }
        boolean existsToday = notificationRepository
                .findTop50ByKitchenIdOrderByCreatedAtDesc(kitchenId).stream()
                .anyMatch(n -> Notification.TYPE_FRIDGE_EXPIRY.equals(n.getType())
                        && n.getCreatedAt() != null && n.getCreatedAt().startsWith(today.toString()));
        if (existsToday) {
            return expiring.size();
        }
        Notification n = new Notification();
        n.setKitchenId(kitchenId);
        n.setType(Notification.TYPE_FRIDGE_EXPIRY);
        n.setTitle("食材临期提醒");
        n.setContent(String.join("、", expiring.stream().limit(10).toList())
                + (expiring.size() > 10 ? " 等 " + expiring.size() + " 种食材需要尽快处理" : "，注意保质期"));
        n.setCreatedAt(now());
        notificationRepository.save(n);
        return expiring.size();
    }

    public List<NotificationView> listNotifications(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return notificationRepository.findTop50ByKitchenIdOrderByCreatedAtDesc(kitchenId).stream()
                .map(n -> new NotificationView(n.getId(), n.getType(), n.getTitle(),
                        n.getContent(), n.getIsRead(), n.getCreatedAt()))
                .toList();
    }

    public long unreadCount(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return notificationRepository.countByKitchenIdAndIsRead(kitchenId, 0);
    }

    @Transactional
    public void markRead(Long userId, Long kitchenId, Long notificationId) {
        requireMember(kitchenId, userId);
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(404, "通知不存在"));
        if (!n.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作");
        }
        n.setIsRead(1);
        notificationRepository.save(n);
    }

    // ---------- 私有 ----------

    private FridgeItemView toListingView(FridgeItem i) {
        return toView(i);
    }

    private FridgeItemView toView(FridgeItem i) {
        LocalDate base = parseDate(i.getProducedDate() != null ? i.getProducedDate()
                : (i.getCreatedAt() != null ? i.getCreatedAt().substring(0, 10) : now().substring(0, 10)));
        long days = unitDays(i.getShelfLifeUnit(), i.getShelfLifeValue());
        LocalDate expire = base.plusDays(days);
        int daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), expire);
        String state = daysLeft < 0 ? "expired" : daysLeft <= EXPIRING_DAYS ? "expiring" : "fresh";
        return new FridgeItemView(i.getId(), i.getKitchenId(), i.getCategoryId(), i.getName(),
                i.getImageUrl(), i.getProducedDate(), i.getShelfLifeValue(), i.getShelfLifeUnit(),
                i.getQuantity(), i.getRemark(), expire.toString(), daysLeft, state);
    }

    private long unitDays(String unit, Integer value) {
        int v = value == null ? 1 : value;
        return switch (unit) {
            case FridgeItem.UNIT_WEEK -> v * 7L;
            case FridgeItem.UNIT_MONTH -> v * 30L;
            case FridgeItem.UNIT_YEAR -> v * 365L;
            default -> (long) v;
        };
    }

    private String validDate(String date) {
        if (date == null || date.isBlank()) return null;
        if (!date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("生产日期格式应为 YYYY-MM-DD");
        }
        return date.trim();
    }

    private String validUnit(String unit) {
        if (unit == null) return FridgeItem.UNIT_DAY;
        return switch (unit) {
            case FridgeItem.UNIT_WEEK -> FridgeItem.UNIT_WEEK;
            case FridgeItem.UNIT_MONTH -> FridgeItem.UNIT_MONTH;
            case FridgeItem.UNIT_YEAR -> FridgeItem.UNIT_YEAR;
            default -> FridgeItem.UNIT_DAY;
        };
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private String extractMaterialLine(String materials, String keyword) {
        for (String line : materials.split("\n")) {
            if (line.contains(keyword)) return line.trim();
        }
        return "";
    }

    private void requireMember(Long kitchenId, Long userId) {
        memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    private void requireManager(Long kitchenId, Long userId) {
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

    public record ItemReq(String name, Long categoryId, String imageUrl, String producedDate,
                          Integer shelfLifeValue, String shelfLifeUnit,
                          String quantity, String remark) {}

    public record ItemUpdateReq(String name, Long categoryId, String producedDate,
                                Integer shelfLifeValue, String shelfLifeUnit,
                                String quantity, String remark) {}

    public record FridgeItemView(Long id, Long kitchenId, Long categoryId, String name,
                                 String imageUrl, String producedDate, Integer shelfLifeValue,
                                 String shelfLifeUnit, String quantity, String remark,
                                 String expireDate, int daysLeft, String state) {}

    public record FridgeCategoryView(Long id, String name) {}

    public record FridgeStateSummary(long fresh, long expiring, long expired) {}

    public record MatchedDish(Long id, String name, String imageUrl, Long priceFen, String materialLine) {}

    public record NotificationView(Long id, String type, String title, String content,
                                   Integer isRead, String createdAt) {}
}
