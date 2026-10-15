package com.sharedkitchen.module.admin;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 运行时配置（app_settings 表）：后台可改，热生效（每次读取都查库，量小无压力）。 */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    @Id
    private String key;

    private String value;

    private String updatedAt;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
