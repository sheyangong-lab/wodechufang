package com.sharedkitchen.module.vip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 会员套餐（月卡/季卡/年卡），后台可增改。 */
@Entity
@Table(name = "vip_plans")
public class VipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "price_fen", nullable = false)
    private Long priceFen;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
