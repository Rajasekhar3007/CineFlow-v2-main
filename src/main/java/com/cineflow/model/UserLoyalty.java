package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * CineFlow v3 — User loyalty points balance and VIP tier.
 */
public class UserLoyalty {

    private int userId;
    private int pointsBalance;
    private LoyaltyTier tier;
    private LocalDateTime updatedAt;

    public UserLoyalty() {
        this.pointsBalance = 0;
        this.tier = LoyaltyTier.SILVER;
        this.updatedAt = LocalDateTime.now();
    }

    public UserLoyalty(int userId, int pointsBalance, LoyaltyTier tier, LocalDateTime updatedAt) {
        this.userId = userId;
        this.pointsBalance = pointsBalance;
        this.tier = tier != null ? tier : LoyaltyTier.SILVER;
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getPointsBalance() {
        return pointsBalance;
    }

    public void setPointsBalance(int pointsBalance) {
        this.pointsBalance = pointsBalance;
    }

    public LoyaltyTier getTier() {
        return tier;
    }

    public void setTier(LoyaltyTier tier) {
        this.tier = tier;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
