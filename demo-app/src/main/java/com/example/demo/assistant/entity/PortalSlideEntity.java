package com.example.demo.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_slides")
public class PortalSlideEntity extends PortalBaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 128)
    private String subtitle;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 500)
    private String image;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
