package com.cineflow.service;

import com.cineflow.model.ConcessionCategory;
import com.cineflow.model.ConcessionPickupStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 3 — Concession Models & Status Unit Tests")
class ConcessionServiceTest {

    @Test
    @DisplayName("ConcessionCategory enums have valid display names and emojis")
    void testCategoryProperties() {
        for (ConcessionCategory cat : ConcessionCategory.values()) {
            assertNotNull(cat.getDisplayName());
            assertNotNull(cat.getEmoji());
            assertFalse(cat.getDisplayName().isEmpty());
        }
        assertEquals("🍿", ConcessionCategory.SNACK.getEmoji());
        assertEquals("🥤", ConcessionCategory.BEVERAGE.getEmoji());
        assertEquals("🍱", ConcessionCategory.COMBO.getEmoji());
        assertEquals("🍰", ConcessionCategory.DESSERT.getEmoji());
    }

    @Test
    @DisplayName("ConcessionPickupStatus provides clear color hex and human readable labels")
    void testPickupStatusProperties() {
        for (ConcessionPickupStatus status : ConcessionPickupStatus.values()) {
            assertNotNull(status.getLabel());
            assertNotNull(status.getColorHex());
            assertTrue(status.getColorHex().startsWith("#"));
        }

        assertEquals("Order Received", ConcessionPickupStatus.PENDING.getLabel());
        assertEquals("Preparing at Kitchen", ConcessionPickupStatus.PREPARING.getLabel());
        assertEquals("Ready for Pickup", ConcessionPickupStatus.READY.getLabel());
        assertEquals("Collected by Guest", ConcessionPickupStatus.COLLECTED.getLabel());
    }
}
