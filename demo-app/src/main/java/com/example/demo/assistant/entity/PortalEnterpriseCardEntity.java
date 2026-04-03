package com.example.demo.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_enterprise_cards")
public class PortalEnterpriseCardEntity extends PortalBaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 500)
    private String description;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
