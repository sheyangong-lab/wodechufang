package com.sharedkitchen.module.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 存钱记录：kind = DEPOSIT(存入) / WITHDRAW(取出)；余额=存入-取出。 */
@Entity
@Table(name = "savings")
public class Saving {

    public static final String KIND_DEPOSIT = "DEPOSIT";
    public static final String KIND_WITHDRAW = "WITHDRAW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency = "CNY";

    @Column(name = "amount_fen", nullable = false)
    private Long amountFen;

    @Column(nullable = false, length = 10)
    private String kind = KIND_DEPOSIT;

    @Column(nullable = false)
    private String note = "";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    @Column(nullable = false)
    private Integer deleted = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
