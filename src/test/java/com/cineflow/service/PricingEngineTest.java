package com.cineflow.service;

import com.cineflow.model.PriceBreakdownLine;
import com.cineflow.model.PriceQuote;
import com.cineflow.model.PricingRule;
import com.cineflow.model.PricingRuleType;
import com.cineflow.model.SeatType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {

    private List<PricingRule> defaultRules;
    private final BigDecimal basePrice = new BigDecimal("250.00");

    @BeforeEach
    void setUp() {
        defaultRules = new ArrayList<>();
        defaultRules.add(new PricingRule(1, "Low Occupancy Discount", PricingRuleType.OCCUPANCY,
                new BigDecimal("0.0"), new BigDecimal("30.0"), new BigDecimal("-10.00"), 10, true, "Low demand"));
        defaultRules.add(new PricingRule(2, "Medium Demand Surge", PricingRuleType.OCCUPANCY,
                new BigDecimal("60.0"), new BigDecimal("80.0"), new BigDecimal("10.00"), 20, true, "Medium demand"));
        defaultRules.add(new PricingRule(3, "High Demand Surge", PricingRuleType.OCCUPANCY,
                new BigDecimal("80.01"), new BigDecimal("100.0"), new BigDecimal("25.00"), 30, true, "High demand"));
        defaultRules.add(new PricingRule(4, "Early Bird Special", PricingRuleType.TIME_TO_SHOW,
                new BigDecimal("168.0"), new BigDecimal("720.0"), new BigDecimal("-10.00"), 40, true, "Early bird"));
        defaultRules.add(new PricingRule(6, "Weekend Prime Surge", PricingRuleType.DAY_TYPE,
                new BigDecimal("5"), new BigDecimal("7"), new BigDecimal("15.00"), 60, true, "Weekend surge"));
    }

    @Test
    @DisplayName("Should apply low occupancy discount when occupancy is below 30%")
    void testLowOccupancyDiscount() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0); // Monday
        LocalDateTime showtime = now.plusDays(2); // Wednesday, ~48 hrs

        // 10 seats sold out of 50 = 20% occupancy (< 30%)
        PriceQuote quote = PricingEngine.quote(basePrice, SeatType.REGULAR, showtime, now, 50, 10,
                defaultRules, new BigDecimal("-0.20"), new BigDecimal("0.50"));

        assertNotNull(quote);
        // Base 250 - 10% = 225.00
        assertEquals(new BigDecimal("225.00"), quote.getFinalPrice());
        assertFalse(quote.getBreakdownLines().isEmpty());
        assertEquals("Low Occupancy Discount", quote.getBreakdownLines().get(0).getRuleName());
    }

    @Test
    @DisplayName("Should apply high demand surge when occupancy exceeds 80%")
    void testHighDemandSurge() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0); // Monday
        LocalDateTime showtime = now.plusDays(2);

        // 45 seats sold out of 50 = 90% occupancy (> 80%)
        PriceQuote quote = PricingEngine.quote(basePrice, SeatType.REGULAR, showtime, now, 50, 45,
                defaultRules, new BigDecimal("-0.20"), new BigDecimal("0.50"));

        assertNotNull(quote);
        // Base 250 + 25% = 312.50 -> rounded to nearest 5 -> 315.00
        assertEquals(new BigDecimal("315.00"), quote.getFinalPrice());
    }

    @Test
    @DisplayName("Should stack multiple rules and clamp to floor/cap")
    void testRuleStackingAndClamping() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        // Saturday = Day 6 (Weekend +15%)
        LocalDateTime showtime = LocalDateTime.of(2026, 11, 28, 19, 0);

        // 45/50 = 90% occupancy (+25%) + Weekend (+15%) = +40%
        PriceQuote quote = PricingEngine.quote(basePrice, SeatType.REGULAR, showtime, now, 50, 45,
                defaultRules, new BigDecimal("-0.20"), new BigDecimal("0.30")); // cap at +30%

        // Clamped to +30%: 250 * 1.30 = 325.00
        assertEquals(new BigDecimal("325.00"), quote.getFinalPrice());
    }

    @Test
    @DisplayName("Should round monetary values to nearest 5")
    void testRoundingToNearestFive() {
        assertEquals(new BigDecimal("250.00"), PricingEngine.roundToNearestFive(new BigDecimal("248.00")));
        assertEquals(new BigDecimal("245.00"), PricingEngine.roundToNearestFive(new BigDecimal("247.49")));
        assertEquals(new BigDecimal("250.00"), PricingEngine.roundToNearestFive(new BigDecimal("252.10")));
    }

    @Test
    @DisplayName("Should enforce minimum price floor of 50.00")
    void testMinimumPriceFloor() {
        BigDecimal lowBase = new BigDecimal("40.00");
        LocalDateTime now = LocalDateTime.of(2026, 11, 23, 12, 0);
        LocalDateTime showtime = now.plusDays(2);

        PriceQuote quote = PricingEngine.quote(lowBase, SeatType.REGULAR, showtime, now, 50, 5,
                defaultRules, new BigDecimal("-0.50"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("50.00"), quote.getFinalPrice());
    }
}
