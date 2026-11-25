package com.cineflow.model;

/**
 * Enumeration representing simulated payment methods.
 */
public enum PaymentMethod {
    CASH,
    UPI,
    CARD,
    RAZORPAY;

    public String getDisplayName() {
        return switch (this) {
            case CASH -> "Cash at Box Office";
            case UPI -> "UPI";
            case CARD -> "Credit / Debit Card";
            case RAZORPAY -> "Razorpay Live Gateway";
        };
    }

    public static PaymentMethod fromString(String method) {
        if (method == null) return CARD;
        try {
            return PaymentMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CARD;
        }
    }
}

