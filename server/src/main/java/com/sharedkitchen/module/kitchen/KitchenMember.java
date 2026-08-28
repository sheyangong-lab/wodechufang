package com.sharedkitchen.module.kitchen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 厨房成员：role = OWNER(店长) / MEMBER(成员) / CUSTOMER(顾客)。 */
@Entity
@Table(name = "kitchen_members")
public class KitchenMember {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String role = ROLE_MEMBER;

    @Column(nullable = false, length = 50)
    private String remark = "";

    @Column(name = "joined_at", nullable = false)
    private String joinedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKitchenId() { return kitchenId; }
    public void setKitchenId(Long kitchenId) { this.kitchenId = kitchenId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }
}
