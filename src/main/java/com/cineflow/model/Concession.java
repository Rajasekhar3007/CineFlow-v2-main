package com.cineflow.model;

import java.math.BigDecimal;

/**
 * CineFlow v3 — F&B Concession Item.
 */
public class Concession {

    private int id;
    private String name;
    private ConcessionCategory category;
    private BigDecimal price;
    private String description;
    private boolean active;

    public Concession() {
        this.price = BigDecimal.ZERO;
        this.active = true;
    }

    public Concession(int id, String name, ConcessionCategory category, BigDecimal price, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price != null ? price : BigDecimal.ZERO;
        this.description = description;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConcessionCategory getCategory() {
        return category;
    }

    public void setCategory(ConcessionCategory category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return name + " (₹ " + price.toPlainString() + ")";
    }
}
