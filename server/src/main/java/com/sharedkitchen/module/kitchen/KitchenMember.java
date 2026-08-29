package com.sharedkitchen.module.kitchen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 厨房成员：role = OWNER(主账号) / MEMBER(成员账号)，主账号可授予成员全权限。 */
@Entity
@Table(name = "kitchen_members")
public class KitchenMember {

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kitchen_id", nullable = false)
    private Long kitchenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String role = ROLE_MEMBER;

    /** 自定义名字（展示用，空=用账号昵称） */
    @Column(nullable = false, length = 20)
    private String alias = "";

    /** 自定义职称（如：主厨/采购员，空=按角色显示） */
    @Column(nullable = false, length = 10)
    private String title = "";

    /** 全权限：1=可代主账号做厨房管理（改信息等；解散/成员管理仍主账号专属） */
    @Column(name = "full_access", nullable = false)
    private Integer fullAccess = 0;

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
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getFullAccess() { return fullAccess; }
    public void setFullAccess(Integer fullAccess) { this.fullAccess = fullAccess; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }

    /** 是否主账号。 */
    public boolean isOwner() { return ROLE_OWNER.equals(role); }

    /** 是否拥有厨房管理全权限（主账号 或 被授予全权限的成员）。 */
    public boolean hasFullAccess() {
        return isOwner() || (fullAccess != null && fullAccess == 1);
    }
}
