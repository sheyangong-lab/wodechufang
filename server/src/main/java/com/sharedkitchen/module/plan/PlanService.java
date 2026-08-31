package com.sharedkitchen.module.plan;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    public static final List<String> DEFAULT_SLOTS =
            List.of("早餐", "午餐", "下午茶", "晚餐", "夜宵");
    private static final int MAX_SLOTS = 8;

    private final PlanItemRepository itemRepository;
    private final PlanConfigRepository configRepository;
    private final KitchenMemberRepository memberRepository;

    public PlanService(PlanItemRepository itemRepository,
                       PlanConfigRepository configRepository,
                       KitchenMemberRepository memberRepository) {
        this.itemRepository = itemRepository;
        this.configRepository = configRepository;
        this.memberRepository = memberRepository;
    }

    private void requireMember(Long kitchenId, Long userId) {
        memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    /** 餐段名列表（无配置时给默认五餐）。 */
    public List<String> slots(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return loadSlots(kitchenId);
    }

    private List<String> loadSlots(Long kitchenId) {
        return configRepository.findByKitchenId(kitchenId)
                .map(c -> {
                    try {
                        List<String> list = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(c.getSlotsJson(),
                                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                        if (!list.isEmpty()) return list;
                    } catch (Exception ignore) { /* 坏数据回默认 */ }
                    return new ArrayList<>(DEFAULT_SLOTS);
                })
                .orElse(new ArrayList<>(DEFAULT_SLOTS));
    }

    /** 更新餐段配置（1~8 段，每段名 1~6 个字）。 */
    @Transactional
    public List<String> updateSlots(Long userId, Long kitchenId, List<String> slots) {
        requireMember(kitchenId, userId);
        if (slots == null || slots.isEmpty() || slots.size() > MAX_SLOTS) {
            throw new BusinessException("餐段数量 1~8 个");
        }
        List<String> cleaned = slots.stream()
                .map(s -> s == null ? "" : s.trim())
                .peek(s -> {
                    if (s.isEmpty() || s.length() > 6) {
                        throw new BusinessException("餐段名 1~6 个字");
                    }
                })
                .toList();
        PlanConfig config = configRepository.findByKitchenId(kitchenId)
                .orElseGet(() -> {
                    PlanConfig c = new PlanConfig();
                    c.setKitchenId(kitchenId);
                    return c;
                });
        try {
            config.setSlotsJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(cleaned));
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败");
        }
        config.setUpdatedAt(now());
        configRepository.save(config);
        return cleaned;
    }

    /** 某天的计划（items 带餐段名）。 */
    public PlanDay day(Long userId, Long kitchenId, String date) {
        requireMember(kitchenId, userId);
        List<String> slots = loadSlots(kitchenId);
        String d = validDate(date);
        List<PlanItem> items = itemRepository
                .findByKitchenIdAndPlanDateOrderBySlotIndexAscCreatedAtAsc(kitchenId, d);
        List<PlanItemView> views = items.stream()
                .map(i -> new PlanItemView(i.getId(), i.getSlotIndex(), i.getItemType(), i.getDishId(),
                        i.getName(), i.getImageUrl(), i.getRemark()))
                .toList();
        return new PlanDay(d, slots, views);
    }

    /** 添加计划条目。 */
    @Transactional
    public PlanItemView addItem(Long userId, Long kitchenId, AddItemReq req) {
        requireMember(kitchenId, userId);
        List<String> slots = loadSlots(kitchenId);
        if (req.slotIndex() == null || req.slotIndex() < 0 || req.slotIndex() >= slots.size()) {
            throw new BusinessException("餐段不存在");
        }
        PlanItem item = new PlanItem();
        item.setKitchenId(kitchenId);
        item.setPlanDate(validDate(req.date()));
        item.setSlotIndex(req.slotIndex());
        if (PlanItem.TYPE_CUSTOM.equals(req.itemType())) {
            item.setItemType(PlanItem.TYPE_CUSTOM);
            if (req.name() == null || req.name().isBlank()) {
                throw new BusinessException("请输入菜单名称");
            }
            item.setName(req.name().trim());
        } else {
            item.setItemType(PlanItem.TYPE_DISH);
            if (req.dishId() == null) {
                throw new BusinessException("请选择菜谱");
            }
            if (req.name() == null || req.name().isBlank()) {
                throw new BusinessException("请输入菜名");
            }
            item.setDishId(req.dishId());
            item.setName(req.name().trim());
        }
        item.setImageUrl(req.imageUrl() == null ? "" : req.imageUrl().trim());
        item.setRemark(req.remark() == null ? "" : req.remark().trim());
        item.setCreatedAt(now());
        itemRepository.save(item);
        return new PlanItemView(item.getId(), item.getSlotIndex(), item.getItemType(),
                item.getDishId(), item.getName(), item.getImageUrl(), item.getRemark());
    }

    @Transactional
    public void removeItem(Long userId, Long kitchenId, Long itemId) {
        requireMember(kitchenId, userId);
        PlanItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "条目不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "条目不存在");
        }
        itemRepository.delete(item);
    }

    private String validDate(String date) {
        if (date == null || !date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        return date.trim();
    }

    private String now() {
        return java.time.Instant.now().toString();
    }

    public record PlanItemView(Long id, Integer slotIndex, String itemType, Long dishId,
                               String name, String imageUrl, String remark) {}

    public record PlanDay(String date, List<String> slots, List<PlanItemView> items) {}

    public record AddItemReq(String date, Integer slotIndex, String itemType, Long dishId,
                             String name, String imageUrl, String remark) {}
}
