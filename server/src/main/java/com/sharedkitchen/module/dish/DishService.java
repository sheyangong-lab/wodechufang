package com.sharedkitchen.module.dish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharedkitchen.common.BusinessException;
import com.sharedkitchen.module.kitchen.Kitchen;
import com.sharedkitchen.module.kitchen.KitchenMember;
import com.sharedkitchen.module.kitchen.KitchenMemberRepository;
import com.sharedkitchen.module.kitchen.KitchenRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DishService {

    private final DishRepository dishRepository;
    private final KitchenRepository kitchenRepository;
    private final KitchenMemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DishService(DishRepository dishRepository,
                       KitchenRepository kitchenRepository,
                       KitchenMemberRepository memberRepository,
                       CategoryRepository categoryRepository) {
        this.dishRepository = dishRepository;
        this.kitchenRepository = kitchenRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
    }

    // ---------- 菜谱 ----------

    @Transactional
    public DishView create(Long userId, Long kitchenId, DishReq req) {
        requireOwner(kitchenId, userId);
        Dish dish = new Dish();
        apply(dish, kitchenId, req);
        dish.setCreatedAt(now());
        dish.setUpdatedAt(now());
        dishRepository.save(dish);
        return toView(dish);
    }

    @Transactional
    public DishView update(Long userId, Long dishId, DishReq req) {
        Dish dish = requireDish(dishId);
        requireOwner(dish.getKitchenId(), userId);
        apply(dish, dish.getKitchenId(), req);
        dish.setUpdatedAt(now());
        dishRepository.save(dish);
        return toView(dish);
    }

    /** 上架/下架。status: 1 上架 0 下架 */
    @Transactional
    public DishView updateStatus(Long userId, Long dishId, int status) {
        Dish dish = requireDish(dishId);
        requireOwner(dish.getKitchenId(), userId);
        dish.setStatus(status == 1 ? 1 : 0);
        dish.setUpdatedAt(now());
        dishRepository.save(dish);
        return toView(dish);
    }

    @Transactional
    public void recycle(Long userId, Long dishId) {
        Dish dish = requireDish(dishId);
        requireOwner(dish.getKitchenId(), userId);
        dish.setDeleted(1);
        dish.setUpdatedAt(now());
        dishRepository.save(dish);
    }

    @Transactional
    public DishView restore(Long userId, Long dishId) {
        Dish dish = requireDish(dishId);
        requireOwner(dish.getKitchenId(), userId);
        dish.setDeleted(0);
        dish.setUpdatedAt(now());
        dishRepository.save(dish);
        return toView(dish);
    }

    /** 点单页菜单：上架且未删除；分类/关键词可选过滤。 */
    public List<DishView> listMenu(Long userId, Long kitchenId, Long categoryId, String keyword) {
        requireMember(kitchenId, userId);
        String kw = keyword == null ? null : keyword.trim();
        return dishRepository.findByKitchenIdAndDeletedAndStatusOrderByUpdatedAtDesc(kitchenId, 0, 1)
                .stream()
                .filter(d -> categoryId == null || categoryId.equals(d.getCategoryId()))
                .filter(d -> kw == null || kw.isEmpty() || d.getName().contains(kw))
                .map(this::toView)
                .toList();
    }

    /** 修改模式：全部未删除（含下架），仅管理端。 */
    public List<DishView> listManage(Long userId, Long kitchenId) {
        requireOwner(kitchenId, userId);
        return dishRepository.findByKitchenIdAndDeletedOrderByUpdatedAtDesc(kitchenId, 0)
                .stream().map(this::toView).toList();
    }

    /** 回收站，仅管理端。 */
    public List<DishView> listRecycle(Long userId, Long kitchenId) {
        requireOwner(kitchenId, userId);
        return dishRepository.findByKitchenIdAndDeletedOrderByUpdatedAtDesc(kitchenId, 1)
                .stream().map(this::toView).toList();
    }

    public DishView detail(Long userId, Long dishId) {
        Dish dish = requireDish(dishId);
        requireMember(dish.getKitchenId(), userId);
        return toView(dish);
    }

    // ---------- 分类 ----------

    @Transactional
    public CategoryView createCategory(Long userId, Long kitchenId, String name) {
        requireOwner(kitchenId, userId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入分类名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 10) {
            throw new BusinessException("分类名最多10个字");
        }
        Category c = new Category();
        c.setKitchenId(kitchenId);
        c.setName(trimmed);
        // 新分类排到已有分类末尾
        c.setSort(categoryRepository.findByKitchenIdOrderBySortAscIdAsc(kitchenId).size());
        c.setCreatedAt(now());
        categoryRepository.save(c);
        return new CategoryView(c.getId(), c.getName(), 0);
    }

    /** 按传入 id 顺序重排分类（ids[0] 显示在最上）。 */
    @Transactional
    public void reorderCategories(Long userId, Long kitchenId, List<Long> ids) {
        requireOwner(kitchenId, userId);
        int index = 0;
        for (Long id : ids) {
            Category c = categoryRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(404, "分类不存在"));
            if (!c.getKitchenId().equals(kitchenId)) {
                throw new BusinessException(403, "无权操作该分类");
            }
            c.setSort(index++);
            categoryRepository.save(c);
        }
    }

    public List<CategoryView> listCategories(Long userId, Long kitchenId) {
        requireMember(kitchenId, userId);
        return categoryRepository.findByKitchenIdOrderBySortAscIdAsc(kitchenId).stream()
                .map(c -> new CategoryView(c.getId(), c.getName(),
                        dishRepository.countByKitchenIdAndCategoryIdAndDeleted(kitchenId, c.getId(), 0)))
                .toList();
    }

    @Transactional
    public void deleteCategory(Long userId, Long kitchenId, Long categoryId) {
        requireOwner(kitchenId, userId);
        Category c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (!c.getKitchenId().equals(kitchenId)) {
            throw new BusinessException(403, "无权操作该分类");
        }
        long used = dishRepository.countByKitchenIdAndCategoryIdAndDeleted(kitchenId, categoryId, 0);
        if (used > 0) {
            throw new BusinessException("该分类下还有 " + used + " 道菜，先移走再删除");
        }
        categoryRepository.delete(c);
    }

    /** 随机点菜：在售菜单中随机抽 count 道（选择困难症专用）。 */
    public List<DishView> randomMenu(Long userId, Long kitchenId, Long categoryId, int count) {
        requireMember(kitchenId, userId);
        int n = Math.max(1, Math.min(count, 10));
        List<Dish> menu = new java.util.ArrayList<>(dishRepository
                .findByKitchenIdAndDeletedAndStatusOrderByUpdatedAtDesc(kitchenId, 0, 1)
                .stream()
                .filter(d -> categoryId == null || categoryId.equals(d.getCategoryId()))
                .toList()); // Stream.toList() 不可变，shuffle 前必须拷贝
        java.util.Collections.shuffle(menu);
        return menu.stream().limit(n).map(this::toView).toList();
    }

    /** 广场：全平台分享到广场的菜谱（任何登录用户可浏览）。 */
    public List<DishView> listSquare(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        return dishRepository.findByShareSquareAndDeletedAndStatusOrderByUpdatedAtDesc(1, 0, 1)
                .stream()
                .filter(d -> kw == null || kw.isEmpty() || d.getName().contains(kw))
                .limit(50)
                .map(this::toView)
                .toList();
    }

    /** 克隆菜谱：把广场上的菜谱复制一份到自己厨房（校验成员权限）。 */
    @Transactional
    public DishView cloneDish(Long userId, Long targetKitchenId, Long sourceDishId) {
        Dish source = requireDish(sourceDishId);
        if (source.getShareSquare() != 1 || source.getDeleted() == 1 || source.getStatus() != 1) {
            throw new BusinessException("该菜谱未分享到广场，无法克隆");
        }
        if (source.getKitchenId().equals(targetKitchenId)) {
            throw new BusinessException("该菜谱已在你的厨房里");
        }
        // 源菜谱的分类属于源厨房，克隆到新厨房时不带分类
        DishReq req = new DishReq(
                source.getName(),
                source.getDescription(),
                source.getImageUrl(),
                source.getPriceFen(),
                parseSpecsList(source.getSpecsJson()),
                null,
                source.getRecommendStars(),
                source.getMaterials(),
                source.getSteps(),
                source.getServings(),
                source.getCookMinutes(),
                source.getDifficulty(),
                source.getCalories(),
                false);
        return create(userId, targetKitchenId, req); // create 内含成员权限校验
    }

    private List<com.sharedkitchen.module.dish.DishReq.Spec> parseSpecsList(String specsJson) {
        if (specsJson == null || specsJson.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(specsJson);
            List<com.sharedkitchen.module.dish.DishReq.Spec> specs = new java.util.ArrayList<>();
            for (var node : arr) {
                specs.add(new DishReq.Spec(node.path("name").asText(), node.path("priceFen").asLong()));
            }
            return specs;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 公共 ----------

    private void apply(Dish dish, Long kitchenId, DishReq req) {
        dish.setKitchenId(kitchenId);
        dish.setName(req.name().trim());
        dish.setDescription(req.description() == null ? "" : req.description().trim());
        dish.setImageUrl(req.imageUrl());
        if (req.priceFen() == null || req.priceFen() < 0) {
            throw new BusinessException("请输入正确的价格");
        }
        dish.setPriceFen(req.priceFen());
        dish.setSpecsJson(serializeSpecs(req.specs()));
        dish.setRecommendStars(Math.max(0, Math.min(5,
                req.recommendStars() == null ? 0 : req.recommendStars())));
        dish.setMaterials(req.materials() == null ? "" : req.materials());
        dish.setSteps(req.steps() == null ? "" : req.steps());
        dish.setServings(req.servings() == null ? "" : req.servings().trim());
        dish.setCookMinutes(req.cookMinutes());
        dish.setDifficulty(req.difficulty() == null ? "" : req.difficulty());
        dish.setCalories(req.calories() == null ? "" : req.calories().trim());
        dish.setShareSquare(Boolean.TRUE.equals(req.shareSquare()) ? 1 : 0);
        if (req.categoryId() != null) {
            Category c = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new BusinessException("所选分类不存在"));
            if (!c.getKitchenId().equals(kitchenId)) {
                throw new BusinessException("所选分类不属于当前厨房");
            }
            dish.setCategoryId(req.categoryId());
        } else {
            dish.setCategoryId(null);
        }
    }

    private String serializeSpecs(List<DishReq.Spec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(specs.stream()
                    .map(s -> new DishReq.Spec(s.name(), s.priceFen() == null ? 0 : s.priceFen()))
                    .toList());
        } catch (Exception e) {
            throw new BusinessException("规格数据有误");
        }
    }

    private DishView toView(Dish d) {
        String categoryName = d.getCategoryId() == null ? null
                : categoryRepository.findById(d.getCategoryId())
                        .map(Category::getName).orElse(null);
        return new DishView(d.getId(), d.getKitchenId(), d.getCategoryId(), categoryName,
                d.getName(), d.getDescription(), d.getImageUrl(), d.getPriceFen(),
                d.getSpecsJson(), d.getRecommendStars(), d.getMaterials(), d.getSteps(),
                d.getServings(), d.getCookMinutes(), d.getDifficulty(), d.getCalories(),
                d.getShareSquare(), d.getStatus(), d.getDeleted(), d.getUpdatedAt());
    }

    private Dish requireDish(Long dishId) {
        return dishRepository.findById(dishId)
                .orElseThrow(() -> new BusinessException(404, "菜品不存在"));
    }

    private Kitchen requireKitchen(Long kitchenId) {
        return kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new BusinessException(404, "厨房不存在"));
    }

    /** 菜单管理类操作：主账号和成员账号都可以。 */
    private void requireOwner(Long kitchenId, Long userId) {
        KitchenMember m = memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
        if (!KitchenMember.ROLE_OWNER.equals(m.getRole())
                && !KitchenMember.ROLE_MEMBER.equals(m.getRole())) {
            throw new BusinessException(403, "需要主账号或成员权限");
        }
    }

    private void requireMember(Long kitchenId, Long userId) {
        memberRepository.findByKitchenIdAndUserId(kitchenId, userId)
                .orElseThrow(() -> new BusinessException(403, "你还不是该厨房的成员"));
    }

    private String now() {
        return Instant.now().toString();
    }

    public record CategoryView(Long id, String name, long dishCount) {}
}
