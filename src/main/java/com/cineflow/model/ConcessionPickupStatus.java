package com.cineflow.model;

/**
 * CineFlow v3 — Kitchen / Concession counter pickup status.
 */
public enum ConcessionPickupStatus {
    PENDING("Order Received", "#F59E0B"),
    PREPARING("Preparing at Kitchen", "#3B82F6"),
    READY("Ready for Pickup", "#10B981"),
    COLLECTED("Collected by Guest", "#6B7280");

    private final String label;
    private final String colorHex;

    ConcessionPickupStatus(String label, String colorHex) {
        this.label = label;
        this.colorHex = colorHex;
    }

    public String getLabel() {
        return label;
    }

    public String getColorHex() {
        return colorHex;
    }
}
