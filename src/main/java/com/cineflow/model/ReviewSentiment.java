package com.cineflow.model;

/**
 * CineFlow v3 — Sentiment classification for movie audience reviews.
 */
public enum ReviewSentiment {
    POSITIVE("Positive", "🟢", "#10B981"),
    NEUTRAL("Neutral", "🟡", "#F59E0B"),
    NEGATIVE("Critical / Negative", "🔴", "#EF4444");

    private final String label;
    private final String badge;
    private final String colorHex;

    ReviewSentiment(String label, String badge, String colorHex) {
        this.label = label;
        this.badge = badge;
        this.colorHex = colorHex;
    }

    public String getLabel() {
        return label;
    }

    public String getBadge() {
        return badge;
    }

    public String getColorHex() {
        return colorHex;
    }
}
