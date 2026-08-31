package com.sharedkitchen.module.basket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 菜篮：按下单用料与冰箱比对生成的采购清单（checked=已买到）。 */
@Entity
@Table(name = "basket_items")
public class BasketItem {

    public static final String SOURCE_AUTO = "AUTO";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 30)
    private String quantity = "";

    @Column(nullable = false)
    private Integer checked = 0;

    @Column(nullable = false, length = 10)
    private String source = SOURCE_MANUAL;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long v) { kitchenId = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String v) { quantity = v; }
    public Integer getChecked() { return checked; }
    public void setChecked(Integer v) { checked = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
}
