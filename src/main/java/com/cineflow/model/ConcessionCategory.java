package com.cineflow.model;

/**
 * CineFlow v3 — Categories for F&B theater concessions.
 */
public enum ConcessionCategory {
    SNACK("Snacks & Popcorn", "🍿"),
    BEVERAGE("Chilled Beverages", "🥤"),
    COMBO("Value Combos", "🍱"),
    DESSERT("Sweets & Desserts", "🍰");

    private final String displayName;
    private final String emoji;

    ConcessionCategory(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }
}
