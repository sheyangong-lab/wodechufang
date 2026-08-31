package com.sharedkitchen.module.foodbook;

import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodbookService {

    private final FoodbookRepository repository;
    private final KitchenMemberRepository memberRepository;

    public FoodbookService(FoodbookRepository repository, KitchenMemberRepository memberRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
    }

    private void requireMember(Long kitchenId, Long userId) {
        memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    public List<FoodbookItem> page(Long userId, Long kitchenId, String date) {
        requireMember(kitchenId, userId);
        return repository.findByKitchenIdAndPageDateOrderByIdAsc(kitchenId, validDate(date));
    }

    /** 批量保存贴纸（完成按钮：一次落库当天所有新贴纸）。 */
    @Transactional
    public List<FoodbookItem> addAll(Long userId, Long kitchenId, String date, List<FoodbookItem> items) {
        requireMember(kitchenId, userId);
        String d = validDate(date);
        List<FoodbookItem> saved = new java.util.ArrayList<>();
        for (FoodbookItem item : items) {
            item.setKitchenId(kitchenId);
            item.setPageDate(d);
            if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
                throw new BusinessException("贴纸缺少图片");
            }
            clamp(item);
            if (item.getCreatedAt() == null) item.setCreatedAt(java.time.Instant.now().toString());
            saved.add(repository.save(item));
        }
        return saved;
    }

    /** 拖动后更新位置。 */
    @Transactional
    public void updatePosition(Long userId, Long kitchenId, Long itemId, Double x, Double y) {
        requireMember(kitchenId, userId);
        FoodbookItem item = repository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "贴纸不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "贴纸不存在");
        }
        item.setX(x);
        item.setY(y);
        clamp(item);
        repository.save(item);
    }

    @Transactional
    public void remove(Long userId, Long kitchenId, Long itemId) {
        requireMember(kitchenId, userId);
        FoodbookItem item = repository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "贴纸不存在"));
        if (!item.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(404, "贴纸不存在");
        }
        repository.delete(item);
    }

    /** 位置钳制在 0~100，防止拖丢。 */
    private void clamp(FoodbookItem item) {
        double half = item.getWidth() == null ? 0 : item.getWidth() / 2;
        item.setX(Math.max(0, Math.min(100 - half, item.getX() == null ? 10 : item.getX())));
        item.setY(Math.max(0, Math.min(100, item.getY() == null ? 10 : item.getY())));
    }

    private String validDate(String date) {
        if (date == null || !date.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BusinessException("日期格式应为 YYYY-MM-DD");
        }
        return date.trim();
    }

    public record ItemView(Long id, String imageUrl, Double x, Double y, Double width, Integer zIndex) {}

    public record AddReq(String imageUrl, Double x, Double y, Double width, Integer zIndex) {}
}
