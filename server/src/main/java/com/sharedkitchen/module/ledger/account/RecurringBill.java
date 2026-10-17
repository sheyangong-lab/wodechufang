package com.sharedkitchen.module.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 周期账单：房租/水电/工资等固定收支，到期由定时任务自动生成账本流水；cycle = MONTHLY / YEARLY。 */
@Entity
@Table(name = "recurring_bills")
public class RecurringBill {

    public static final String TYPE_INCOME = "INCOME";
    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String CYCLE_MONTHLY = "MONTHLY";
    public static final String CYCLE_YEARLY = "YEARLY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    /** INCOME / EXPENSE（直接落 LedgerEntry.type）。 */
    @Column(nullable = false, length = 10)
    private String type;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "amount_fen", nullable = false)
    private Long amountFen;

    /** 入账账户（accounts.id）；可空 = 不挂账户。 */
    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 10)
    private String cycle;

    /** 下次入账日期 YYYY-MM-DD；扫描时 while next_date <= today 逐周期补账并推进。 */
    @Column(name = "next_date", nullable = false, length = 10)
    private String nextDate;

    @Column(nullable = false)
    private String remark = "";

    /** 最近一次自动生成的流水 id；供前端跳转。 */
    @Column(name = "last_entry_id")
    private Long lastEntryId;

    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getCycle() { return cycle; }
    public void setCycle(String cycle) { this.cycle = cycle; }
    public String getNextDate() { return nextDate; }
    public void setNextDate(String nextDate) { this.nextDate = nextDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getLastEntryId() { return lastEntryId; }
    public void setLastEntryId(Long lastEntryId) { this.lastEntryId = lastEntryId; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
