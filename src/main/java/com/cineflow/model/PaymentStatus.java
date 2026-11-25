package com.cineflow.model;

/**
 * Enumeration representing payment transaction states.
 */
public enum PaymentStatus {
    PAID,
    REFUNDED,
    REFUND_PENDING,
    REFUND_FAILED;

    public static PaymentStatus fromString(String status) {
        if (status == null) return PAID;
        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PAID;
        }
    }
}

