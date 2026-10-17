package com.sharedkitchen.module.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 账本流水：type = INCOME(收入) / EXPENSE(支出) / REFUND(退款冲销) / TRANSFER(账户间转账)；金额为「分」正数。 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public static final String TYPE_INCOME = "INCOME";
    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_TRANSFER = "TRANSFER";

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

    /** 账单图片（/files/xxx.png 相对地址）；无图时为 null。 */
    @Column(name = "image_url")
    private String imageUrl;

    /** 支付方式（微信/支付宝/现金/银行卡…自定义）；可空。 */
    @Column(name = "payment_method")
    private String paymentMethod;

    /** 原币种 CNY/HKD/USD（默认 CNY）。 */
    @Column(nullable = false, length = 3)
    private String currency = "CNY";

    /** 原币种金额（分）；外币记录原额，amountFen 为按汇率折算后的人民币分。 */
    @Column(name = "orig_amount_fen")
    private Long origAmountFen;

    /** 分费用明细 JSON 数组文本（如 [{"name":"蔬菜","amountFen":3000}]）；无分项时为 null。 */
    @Column(name = "sub_items")
    private String subItems;

    /** 所属账户（accounts.id）；未指定时为 null。 */
    @Column(name = "account_id")
    private Long accountId;

    /** 转账方向 'in'/'out'；仅 TRANSFER 流水使用。 */
    @Column(name = "transfer_dir", length = 3)
    private String transferDir;

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

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String v) { this.imageUrl = v; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String v) { this.paymentMethod = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public Long getOrigAmountFen() { return origAmountFen; }
    public void setOrigAmountFen(Long v) { this.origAmountFen = v; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getTransferDir() { return transferDir; }
    public void setTransferDir(String transferDir) { this.transferDir = transferDir; }
}
