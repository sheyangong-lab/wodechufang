package com.sharedkitchen.module.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 饮食计划餐段配置：每厨房自定义（如 早餐/午餐/下午茶/晚餐/夜宵）。 */
@Entity
@Table(name = "plan_config")
public class PlanConfig {

    @Id
    @Column(name = "kitchen_id")
    private Long kitchenId;

    @Column(name = "slots_json", nullable = false, columnDefinition = "TEXT")
    private String slotsJson;

    @Column(name = "updated_at", nullable = false)
    private String updatedAt;

    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long v) { kitchenId = v; }
    public String getSlotsJson() { return slotsJson; }
    public void setSlotsJson(String v) { slotsJson = v; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String v) { updatedAt = v; }
}
