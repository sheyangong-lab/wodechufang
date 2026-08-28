package com.sharedkitchen.module.dish;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 菜谱创建/编辑请求。价格一律「分」。
 * 多规格：specs 为 [{name, priceFen}]；不开启多规格则传 null/空。
 */
public record DishReq(
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull Long priceFen,
        List<Spec> specs,
        Long categoryId,
        Integer recommendStars,
        String materials,
        String steps,
        String servings,
        Integer cookMinutes,
        String difficulty,
        String calories,
        Boolean shareSquare
) {
    public record Spec(String name, Long priceFen) {}
}
