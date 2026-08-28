package com.sharedkitchen.module.dish;

public record DishView(
        Long id,
        Long kitchenId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        Long priceFen,
        String specsJson,
        Integer recommendStars,
        String materials,
        String steps,
        String servings,
        Integer cookMinutes,
        String difficulty,
        String calories,
        Integer shareSquare,
        Integer status,
        Integer deleted,
        String updatedAt
) {}
