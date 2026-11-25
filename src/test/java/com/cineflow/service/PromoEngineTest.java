package com.cineflow.service;

import com.cineflow.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature 4 — PromoEngine Unit Tests")
class PromoEngineTest {

    private PromoEngine engine;
    private UserLoyalty silverLoyalty;
    private UserLoyalty goldLoyalty;
    private UserLoyalty platinumLoyalty;

    @BeforeEach
    void setUp() {
        engine = new PromoEngine();

        silverLoyalty = new UserLoyalty(1, 200, LoyaltyTier.SILVER, LocalDateTime.now());
        goldLoyalty = new UserLoyalty(2, 600, LoyaltyTier.GOLD, LocalDateTime.now());
        platinumLoyalty = new UserLoyalty(3, 1500, LoyaltyTier.PLATINUM, LocalDateTime.now());
    }

    @Test
    @DisplayName("Should apply percentage discount with max discount cap correctly")
    void testPercentageDiscountWithCap() {
        PromoCode promo = new PromoCode(1, "CINEFLOW20", DiscountType.PERCENTAGE,
                new BigDecimal("20.00"), new BigDecimal("300.00"), new BigDecimal("100.00"),
                LocalDateTime.now().plusDays(10), 100, 5, true);

        // 20% of 600 is 120, capped at 100
        PromoEngine.PromoCalculationResult res = engine.calculate(new BigDecimal("600.00"), promo, 0, silverLoyalty);

        assertTrue(res.isValid());
        assertEquals(new BigDecimal("100.00"), res.getPromoDiscount());
        assertEquals(new BigDecimal("500.00"), res.getFinalPayableAmount());
        assertEquals(0, res.getPointsRedeemed());
        // Silver earns 1 pt per 10 rs: 500 / 10 = 50 pts
        assertEquals(50, res.getPointsEarned());
    }

    @Test
    @DisplayName("Should apply flat discount correctly")
    void testFlatDiscount() {
        PromoCode promo = new PromoCode(2, "BLOCKBUSTER50", DiscountType.FLAT,
                new BigDecimal("50.00"), new BigDecimal("250.00"), new BigDecimal("50.00"),
                LocalDateTime.now().plusDays(10), 100, 5, true);

        PromoEngine.PromoCalculationResult res = engine.calculate(new BigDecimal("300.00"), promo, 0, silverLoyalty);

        assertTrue(res.isValid());
        assertEquals(new BigDecimal("50.00"), res.getPromoDiscount());
        assertEquals(new BigDecimal("250.00"), res.getFinalPayableAmount());
        assertEquals(25, res.getPointsEarned());
    }

    @Test
    @DisplayName("Should reject promo when order total is below minimum required")
    void testMinBookingAmountRejection() {
        PromoCode promo = new PromoCode(1, "VIP100", DiscountType.FLAT,
                new BigDecimal("100.00"), new BigDecimal("500.00"), new BigDecimal("100.00"),
                LocalDateTime.now().plusDays(10), 100, 5, true);

        PromoEngine.PromoCalculationResult res = engine.calculate(new BigDecimal("350.00"), promo, 0, silverLoyalty);

        assertFalse(res.isValid());
        assertEquals(new BigDecimal("0.00"), res.getPromoDiscount().setScale(2));
        assertEquals(new BigDecimal("350.00"), res.getFinalPayableAmount());
        assertTrue(res.getMessage().contains("Minimum booking amount"));
    }

    @Test
    @DisplayName("Should redeem loyalty points and calculate tier multipliers")
    void testLoyaltyPointsRedemptionAndTierEarning() {
        // Gold member has 600 points, redeems 300 points (300 / 10 = ₹ 30 discount)
        PromoEngine.PromoCalculationResult res = engine.calculate(new BigDecimal("400.00"), null, 300, goldLoyalty);

        assertTrue(res.isValid());
        assertEquals(new BigDecimal("30.00"), res.getLoyaltyDiscount());
        assertEquals(new BigDecimal("370.00"), res.getFinalPayableAmount());
        assertEquals(300, res.getPointsRedeemed());

        // Gold tier has 1.25x multiplier: base = 370 / 10 = 37. 37 * 1.25 = 46.25 -> 46
        assertEquals(46, res.getPointsEarned());
    }

    @Test
    @DisplayName("Should combine promo and loyalty discount without exceeding total")
    void testCombinedDiscountsNotExceedingTotal() {
        PromoCode promo = new PromoCode(1, "FLAT200", DiscountType.FLAT,
                new BigDecimal("200.00"), new BigDecimal("100.00"), null,
                LocalDateTime.now().plusDays(5), 10, 0, true);

        // Order total is 250. Promo takes 200 -> 50 left. User requests 1000 points (₹100), but only ₹50 remains
        PromoEngine.PromoCalculationResult res = engine.calculate(new BigDecimal("250.00"), promo, 1000, platinumLoyalty);

        assertTrue(res.isValid());
        assertEquals(new BigDecimal("200.00"), res.getPromoDiscount());
        assertEquals(new BigDecimal("50.00"), res.getLoyaltyDiscount());
        assertEquals(new BigDecimal("0.00"), res.getFinalPayableAmount());
        assertEquals(500, res.getPointsRedeemed()); // 50 * 10 = 500 points
    }

    @Test
    @DisplayName("Should reject expired or exhausted promo codes")
    void testExpiredAndExhaustedPromo() {
        PromoCode expired = new PromoCode(1, "OLD20", DiscountType.FLAT,
                new BigDecimal("20.00"), BigDecimal.ZERO, null,
                LocalDateTime.now().minusDays(1), 100, 10, true);

        PromoEngine.PromoCalculationResult resExpired = engine.calculate(new BigDecimal("200.00"), expired, 0, silverLoyalty);
        assertFalse(resExpired.isValid());
        assertTrue(resExpired.getMessage().contains("expired"));

        PromoCode exhausted = new PromoCode(2, "LIMIT10", DiscountType.FLAT,
                new BigDecimal("20.00"), BigDecimal.ZERO, null,
                LocalDateTime.now().plusDays(5), 10, 10, true);

        PromoEngine.PromoCalculationResult resExhausted = engine.calculate(new BigDecimal("200.00"), exhausted, 0, silverLoyalty);
        assertFalse(resExhausted.isValid());
        assertTrue(resExhausted.getMessage().contains("limit"));
    }
}
