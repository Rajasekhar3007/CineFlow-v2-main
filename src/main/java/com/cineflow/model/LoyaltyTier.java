package com.cineflow.model;

/**
 * CineFlow v3 — Customer loyalty tier hierarchy.
 */
public enum LoyaltyTier {
    SILVER("Silver Member", 1.0, "#94A3B8"),
    GOLD("Gold VIP", 1.25, "#F59E0B"),
    PLATINUM("Platinum Elite", 1.5, "#E2E8F0");

    private final String displayName;
    private final double multiplier;
    private final String colorHex;

    LoyaltyTier(String displayName, double multiplier, String colorHex) {
        this.displayName = displayName;
        this.multiplier = multiplier;
        this.colorHex = colorHex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public String getColorHex() {
        return colorHex;
    }
}
