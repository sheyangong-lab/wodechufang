package com.sharedkitchen.module.ledger.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 账本账户（三钱包）：type = DISPOSABLE(零钱) / SAVING(存款) / CREDIT(信用额度负债)。 */
@Entity
@Table(name = "accounts")
public class Account {

    public static final String TYPE_DISPOSABLE = "DISPOSABLE";
    public static final String TYPE_SAVING = "SAVING";
    public static final String TYPE_CREDIT = "CREDIT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String type;

    /** 期初余额（分）；账户余额 = initialFen + 流水累计。 */
    @Column(name = "initial_fen", nullable = false)
    private Long initialFen = 0L;

    /** 信用额度（分）；仅 CREDIT 账户使用，其余为 null。 */
    @Column(name = "credit_limit_fen")
    private Long creditLimitFen;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer deleted = 0;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getInitialFen() { return initialFen; }
    public void setInitialFen(Long initialFen) { this.initialFen = initialFen; }
    public Long getCreditLimitFen() { return creditLimitFen; }
    public void setCreditLimitFen(Long creditLimitFen) { this.creditLimitFen = creditLimitFen; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
