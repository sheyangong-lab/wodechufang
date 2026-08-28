package com.sharedkitchen.module.vip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 兑换码：UNUSED 未使用 / USED 已核销 / DISABLED 已作废。 */
@Entity
@Table(name = "redeem_codes")
public class RedeemCode {

    public static final String ST_UNUSED = "UNUSED";
    public static final String ST_USED = "USED";
    public static final String ST_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(nullable = false, length = 10)
    private String status = ST_UNUSED;

    @Column(nullable = false)
    private String batch = "";

    @Column(name = "used_by")
    private Long usedBy;

    @Column(name = "used_kitchen_id")
    private Long usedKitchenId;

    @Column(name = "used_at")
    private String usedAt;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public Long getUsedBy() { return usedBy; }
    public void setUsedBy(Long usedBy) { this.usedBy = usedBy; }
    public Long getUsedKitchenId() { return usedKitchenId; }
    public void setUsedKitchenId(Long usedKitchenId) { this.usedKitchenId = usedKitchenId; }
    public String getUsedAt() { return usedAt; }
    public void setUsedAt(String usedAt) { this.usedAt = usedAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
