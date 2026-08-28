package com.sharedkitchen.module.kitchen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kitchens")
public class Kitchen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private Integer level = 0;

    @Column(name = "dish_quota", nullable = false)
    private Integer dishQuota = 50;

    @Column(name = "category_quota", nullable = false)
    private Integer categoryQuota = 5;

    @Column(name = "vip_expire_at")
    private String vipExpireAt;

    @Column(nullable = false)
    private String announcement = "";

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getDishQuota() { return dishQuota; }
    public void setDishQuota(Integer dishQuota) { this.dishQuota = dishQuota; }
    public Integer getCategoryQuota() { return categoryQuota; }
    public void setCategoryQuota(Integer categoryQuota) { this.categoryQuota = categoryQuota; }
    public String getVipExpireAt() { return vipExpireAt; }
    public void setVipExpireAt(String vipExpireAt) { this.vipExpireAt = vipExpireAt; }
    public String getAnnouncement() { return announcement; }
    public void setAnnouncement(String announcement) { this.announcement = announcement; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
