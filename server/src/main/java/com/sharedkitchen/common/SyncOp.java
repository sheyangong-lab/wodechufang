package com.sharedkitchen.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 已处理的同步操作（X-Op-Id → 成功响应），供设备直连同步的幂等重放。 */
@Entity
@Table(name = "sync_ops")
public class SyncOp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "op_id", nullable = false, unique = true)
    private String opId;

    @Column(name = "kitchen_id")
    private Long kitchenId;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public Long getId() { return id; }
    public String getOpId() { return opId; }
    public void setOpId(String v) { this.opId = v; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long v) { this.kitchenId = v; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer v) { this.status = v; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String v) { this.responseBody = v; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { this.createdAt = v; }
}
