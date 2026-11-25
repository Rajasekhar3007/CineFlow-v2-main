package com.cineflow.model;

/**
 * Customer seating preference for automatic Smart Pick seat allocation.
 */
public enum SeatPreference {
    BEST_OVERALL("Best Overall", "Optimal audio-visual balance"),
    CENTER("Centre Sweet-spot", "Maximum screen alignment"),
    FRONT("Front Rows", "Close-up immersive viewing"),
    BACK("Back Rows", "Elevated panoramic sightlines"),
    PREMIUM_ONLY("Premium Tier", "VIP leather recliner seating");

    private final String displayName;
    private final String description;

    SeatPreference(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    @Override
    public String toString() { return displayName; }
}
