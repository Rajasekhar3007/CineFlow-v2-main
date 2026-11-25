package com.cineflow.util;

import java.math.BigDecimal;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * CineFlow v2 Central Configuration Reader.
 * Safely resolves application runtime parameters from db.properties with robust fallbacks.
 */
public class AppConfig {

    private static final Preferences PREFS = Preferences.userNodeForPackage(AppConfig.class);
    private static final String PREF_REDUCED_MOTION = "cineflow.reduced_motion";

    private static String getProperty(String key, String defaultValue) {
        Properties props = DBConnection.getProperties();
        if (props != null && props.containsKey(key)) {
            String val = props.getProperty(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return defaultValue;
    }

    public static String getTicketSecret() {
        return getProperty("ticket.secret", "cineflow-v2-production-hmac-secret-key-32b");
    }

    public static int getHoldMinutes() {
        try {
            return Integer.parseInt(getProperty("hold.minutes", "5"));
        } catch (NumberFormatException e) {
            return 5;
        }
    }

    public static int getWaitlistOfferMinutes() {
        try {
            return Integer.parseInt(getProperty("waitlist.offer.minutes", "10"));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public static BigDecimal getPricingFloor() {
        try {
            return new BigDecimal(getProperty("pricing.floor", "-0.20"));
        } catch (Exception e) {
            return new BigDecimal("-0.20");
        }
    }

    public static BigDecimal getPricingCap() {
        try {
            return new BigDecimal(getProperty("pricing.cap", "0.50"));
        } catch (Exception e) {
            return new BigDecimal("0.50");
        }
    }

    public static int getEntryWindowBeforeMinutes() {
        try {
            return Integer.parseInt(getProperty("entry.window.before.minutes", "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    public static int getEntryWindowAfterMinutes() {
        try {
            return Integer.parseInt(getProperty("entry.window.after.minutes", "30"));
        } catch (NumberFormatException e) {
            return 30;
        }
    }

    public static String getRazorpayKeyId() {
        return getProperty("razorpay.key.id", "rzp_test_unconfigured");
    }

    public static String getRazorpayKeySecret() {
        return getProperty("razorpay.key.secret", "cineflow-local-demo-only");
    }

    public static boolean isRazorpayConfigured() {
        String keyId = getRazorpayKeyId();
        String secret = getRazorpayKeySecret();
        return (keyId.startsWith("rzp_test_") || keyId.startsWith("rzp_live_"))
                && !"rzp_test_unconfigured".equals(keyId)
                && !"cineflow-local-demo-only".equals(secret)
                && !"YOUR_RAZORPAY_KEY_SECRET".equals(secret);
    }

    public static boolean isReducedMotion() {
        return PREFS.getBoolean(PREF_REDUCED_MOTION, false);
    }

    public static void setReducedMotion(boolean enabled) {
        PREFS.putBoolean(PREF_REDUCED_MOTION, enabled);
    }
}
