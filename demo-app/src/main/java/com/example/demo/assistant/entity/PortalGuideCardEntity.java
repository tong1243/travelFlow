package com.example.demo.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_guide_cards")
public class PortalGuideCardEntity extends PortalBaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String cover;

    @Column(name = "reads_text", nullable = false, length = 64)
    private String reads;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getReads() {
        return reads;
    }

    public void setReads(String reads) {
        this.reads = reads;
    }
}
