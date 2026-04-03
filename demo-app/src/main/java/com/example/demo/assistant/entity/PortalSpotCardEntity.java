package com.example.demo.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portal_spot_cards")
public class PortalSpotCardEntity extends PortalBaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(nullable = false, length = 64)
    private String price;

    @Column(nullable = false, length = 32)
    private String rating;

    @Column(nullable = false, length = 500)
    private String image;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
