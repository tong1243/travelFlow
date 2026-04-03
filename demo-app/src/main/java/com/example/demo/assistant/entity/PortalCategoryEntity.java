package com.example.demo.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_categories")
public class PortalCategoryEntity extends PortalBaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 255)
    private String keyword;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
