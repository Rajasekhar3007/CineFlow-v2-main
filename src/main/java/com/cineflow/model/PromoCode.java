package com.cineflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CineFlow v3 — Promotional discount coupon code.
 */
public class PromoCode {

    private int id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minBookingAmount;
    private BigDecimal maxDiscount;
    private LocalDateTime validUntil;
    private int usageLimit;
    private int usedCount;
    private boolean active;

    public PromoCode() {
        this.discountType = DiscountType.PERCENTAGE;
        this.discountValue = BigDecimal.ZERO;
        this.minBookingAmount = BigDecimal.ZERO;
        this.usageLimit = 100;
        this.usedCount = 0;
        this.active = true;
    }

    public PromoCode(int id, String code, DiscountType discountType, BigDecimal discountValue,
                     BigDecimal minBookingAmount, BigDecimal maxDiscount, LocalDateTime validUntil,
                     int usageLimit, int usedCount, boolean active) {
        this.id = id;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minBookingAmount = minBookingAmount != null ? minBookingAmount : BigDecimal.ZERO;
        this.maxDiscount = maxDiscount;
        this.validUntil = validUntil;
        this.usageLimit = usageLimit;
        this.usedCount = usedCount;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinBookingAmount() {
        return minBookingAmount;
    }

    public void setMinBookingAmount(BigDecimal minBookingAmount) {
        this.minBookingAmount = minBookingAmount;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public int getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(int usageLimit) {
        this.usageLimit = usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return validUntil != null && LocalDateTime.now().isAfter(validUntil);
    }

    public boolean isExhausted() {
        return usedCount >= usageLimit;
    }
}
