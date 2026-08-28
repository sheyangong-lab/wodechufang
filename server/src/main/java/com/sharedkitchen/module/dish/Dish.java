package com.sharedkitchen.module.dish;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dishes")
public class Dish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    /** 默认价，单位：分。多规格时为「起价/原价」语义由前端决定，服务端只存分。 */
    @Column(name = "price_fen", nullable = false)
    private Long priceFen = 0L;

    /** JSON 数组文本：[{"name":"小份","priceFen":800}]；null=未开多规格 */
    @Column(name = "specs_json")
    private String specsJson;

    @Column(name = "recommend_stars", nullable = false)
    private Integer recommendStars = 0;

    @Column(nullable = false)
    private String materials = "";

    @Column(nullable = false)
    private String steps = "";

    @Column(nullable = false)
    private String servings = "";

    @Column(name = "cook_minutes")
    private Integer cookMinutes;

    @Column(nullable = false)
    private String difficulty = "";

    @Column(nullable = false)
    private String calories = "";

    @Column(name = "share_square", nullable = false)
    private Integer shareSquare = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getSpecsJson() { return specsJson; }
    public void setSpecsJson(String specsJson) { this.specsJson = specsJson; }
    public Integer getRecommendStars() { return recommendStars; }
    public void setRecommendStars(Integer recommendStars) { this.recommendStars = recommendStars; }
    public String getMaterials() { return materials; }
    public void setMaterials(String materials) { this.materials = materials; }
    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }
    public String getServings() { return servings; }
    public void setServings(String servings) { this.servings = servings; }
    public Integer getCookMinutes() { return cookMinutes; }
    public void setCookMinutes(Integer cookMinutes) { this.cookMinutes = cookMinutes; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getCalories() { return calories; }
    public void setCalories(String calories) { this.calories = calories; }
    public Integer getShareSquare() { return shareSquare; }
    public void setShareSquare(Integer shareSquare) { this.shareSquare = shareSquare; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
