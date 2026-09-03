package com.sharedkitchen.module.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 账本流水：type = INCOME(收入) / EXPENSE(支出) / REFUND(退款冲销)；金额为「分」正数。 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public static final String TYPE_INCOME = "INCOME";
    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_REFUND = "REFUND";

    public static final String SOURCE_ORDER = "ORDER";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, length = 10)
    private String source = SOURCE_MANUAL;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private String category = "";

    @Column(name = "amount_fen", nullable = false)
    private Long amountFen;

    @Column(name = "dine_date", nullable = false, length = 10)
    private String dineDate;

    @Column(nullable = false)
    private String remark = "";

    /** 分费用明细 JSON 数组文本（如 [{"name":"蔬菜","amountFen":3000}]）；无分项时为 null。 */
    @Column(name = "sub_items")
    private String subItems;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getDineDate() { return dineDate; }
    public void setDineDate(String dineDate) { this.dineDate = dineDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getSubItems() { return subItems; }
    public void setSubItems(String subItems) { this.subItems = subItems; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
