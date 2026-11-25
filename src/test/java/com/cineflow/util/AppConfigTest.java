package com.cineflow.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    @DisplayName("Should resolve default CineFlow v2 configuration properties")
    void testDefaultConfigValues() {
        assertNotNull(AppConfig.getTicketSecret());
        assertFalse(AppConfig.getTicketSecret().isEmpty());

        assertEquals(5, AppConfig.getHoldMinutes());
        assertEquals(10, AppConfig.getWaitlistOfferMinutes());

        assertNotNull(AppConfig.getPricingFloor());
        assertTrue(AppConfig.getPricingFloor().compareTo(BigDecimal.ZERO) < 0);

        assertNotNull(AppConfig.getPricingCap());
        assertTrue(AppConfig.getPricingCap().compareTo(BigDecimal.ZERO) > 0);

        assertEquals(60, AppConfig.getEntryWindowBeforeMinutes());
        assertEquals(30, AppConfig.getEntryWindowAfterMinutes());

        assertNotNull(AppConfig.getRazorpayKeyId());
        assertTrue(AppConfig.getRazorpayKeyId().startsWith("rzp_test_"));

        assertNotNull(AppConfig.getRazorpayKeySecret());
        assertFalse(AppConfig.getRazorpayKeySecret().isBlank());
    }

    @Test
    @DisplayName("Should allow toggling reduced motion preference")
    void testReducedMotionToggle() {
        boolean original = AppConfig.isReducedMotion();
        AppConfig.setReducedMotion(!original);
        assertEquals(!original, AppConfig.isReducedMotion());
        AppConfig.setReducedMotion(original);
        assertEquals(original, AppConfig.isReducedMotion());
    }
}
