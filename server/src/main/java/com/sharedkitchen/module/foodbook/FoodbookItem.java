package com.sharedkitchen.module.foodbook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 食本贴纸：某天手账页上的一张抠图/照片，x/y/width 为页面百分比位置。 */
@Entity
@Table(name = "foodbook_items")
public class FoodbookItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "page_date", nullable = false, length = 10)
    private String pageDate;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private Double x = 10.0;

    @Column(nullable = false)
    private Double y = 10.0;

    @Column(nullable = false)
    private Double width = 40.0;

    @Column(name = "z_index", nullable = false)
    private Integer zIndex = 1;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long v) { kitchenId = v; }
    public String getPageDate() { return pageDate; }
    public void setPageDate(String v) { pageDate = v; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String v) { imageUrl = v; }
    public Double getX() { return x; }
    public void setX(Double v) { x = v; }
    public Double getY() { return y; }
    public void setY(Double v) { y = v; }
    public Double getWidth() { return width; }
    public void setWidth(Double v) { width = v; }
    public Integer getZIndex() { return zIndex; }
    public void setZIndex(Integer v) { zIndex = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
}
