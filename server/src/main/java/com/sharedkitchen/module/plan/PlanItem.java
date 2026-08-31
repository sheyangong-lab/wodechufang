package com.sharedkitchen.module.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 饮食计划条目：某天某餐段的一道菜（厨房菜谱引用或自定义菜单）。 */
@Entity
@Table(name = "plan_items")
public class PlanItem {

    public static final String TYPE_DISH = "DISH";
    public static final String TYPE_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "plan_date", nullable = false, length = 10)
    private String planDate;

    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    @Column(name = "item_type", nullable = false, length = 10)
    private String itemType = TYPE_DISH;

    @Column(name = "dish_id")
    private Long dishId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "image_url", nullable = false)
    private String imageUrl = "";

    @Column(nullable = false, length = 100)
    private String remark = "";

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long v) { kitchenId = v; }
    public String getPlanDate() { return planDate; }
    public void setPlanDate(String v) { planDate = v; }
    public Integer getSlotIndex() { return slotIndex; }
    public void setSlotIndex(Integer v) { slotIndex = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { itemType = v; }
    public Long getDishId() { return dishId; }
    public void setDishId(Long v) { dishId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String v) { imageUrl = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { remark = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
}
