package com.sharedkitchen.module.fridge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fridge_items")
public class FridgeItem {

    public static final String UNIT_DAY = "DAY";
    public static final String UNIT_WEEK = "WEEK";
    public static final String UNIT_MONTH = "MONTH";
    public static final String UNIT_YEAR = "YEAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 30)
    private String name;

    /** 生产日期 YYYY-MM-DD；空则按入库日算 */
    @Column(name = "produced_date", length = 10)
    private String producedDate;

    @Column(name = "shelf_life_value", nullable = false)
    private Integer shelfLifeValue = 1;

    @Column(name = "shelf_life_unit", nullable = false, length = 6)
    private String shelfLifeUnit = UNIT_DAY;

    /** 数量+单位文本，如 100g / 3个 */
    @Column(nullable = false)
    private String quantity = "";

    /** 食材照片（/files/ 相对路径），拍照或相册上传 */
    @Column(name = "image_url", nullable = false)
    private String imageUrl = "";

    @Column(nullable = false, length = 50)
    private String remark = "";

    @Column(nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProducedDate() { return producedDate; }
    public void setProducedDate(String producedDate) { this.producedDate = producedDate; }
    public Integer getShelfLifeValue() { return shelfLifeValue; }
    public void setShelfLifeValue(Integer shelfLifeValue) { this.shelfLifeValue = shelfLifeValue; }
    public String getShelfLifeUnit() { return shelfLifeUnit; }
    public void setShelfLifeUnit(String shelfLifeUnit) { this.shelfLifeUnit = shelfLifeUnit; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
