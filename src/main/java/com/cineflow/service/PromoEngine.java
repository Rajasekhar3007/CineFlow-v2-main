package com.cineflow.service;

import com.cineflow.model.DiscountType;
import com.cineflow.model.LoyaltyTier;
import com.cineflow.model.PromoCode;
import com.cineflow.model.UserLoyalty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CineFlow v3 — Pure, DB-free promotional coupon & loyalty rewards calculation engine.
 * Fully unit-testable without database dependencies.
 */
public class PromoEngine {

    /** 10 loyalty points = ₹ 1.00 discount */
    public static final int POINTS_PER_RUPEE = 10;
    /** Earn 1 loyalty point for every ₹ 10.00 spent */
    public static final BigDecimal RUPEES_PER_EARNED_POINT = new BigDecimal("10.00");

    public static class PromoCalculationResult {
        private final BigDecimal originalTotal;
        private final BigDecimal promoDiscount;
        private final BigDecimal loyaltyDiscount;
        private final BigDecimal totalDiscount;
        private final BigDecimal finalPayableAmount;
        private final int pointsRedeemed;
        private final int pointsEarned;
        private final String promoCode;
        private final boolean valid;
        private final String message;

        public PromoCalculationResult(BigDecimal originalTotal, BigDecimal promoDiscount,
                                      BigDecimal loyaltyDiscount, BigDecimal finalPayableAmount,
                                      int pointsRedeemed, int pointsEarned, String promoCode,
                                      boolean valid, String message) {
            this.originalTotal = originalTotal;
            this.promoDiscount = promoDiscount;
            this.loyaltyDiscount = loyaltyDiscount;
            this.totalDiscount = promoDiscount.add(loyaltyDiscount);
            this.finalPayableAmount = finalPayableAmount;
            this.pointsRedeemed = pointsRedeemed;
            this.pointsEarned = pointsEarned;
            this.promoCode = promoCode;
            this.valid = valid;
            this.message = message;
        }

        public BigDecimal getOriginalTotal() { return originalTotal; }
        public BigDecimal getPromoDiscount() { return promoDiscount; }
        public BigDecimal getLoyaltyDiscount() { return loyaltyDiscount; }
        public BigDecimal getTotalDiscount() { return totalDiscount; }
        public BigDecimal getFinalPayableAmount() { return finalPayableAmount; }
        public int getPointsRedeemed() { return pointsRedeemed; }
        public int getPointsEarned() { return pointsEarned; }
        public String getPromoCode() { return promoCode; }
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    /**
     * Calculates the combined discount from an optional promo code and redeemed loyalty points.
     */
    public PromoCalculationResult calculate(BigDecimal originalTotal,
                                            PromoCode promo,
                                            int pointsToRedeem,
                                            UserLoyalty userLoyalty) {
        if (originalTotal == null || originalTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new PromoCalculationResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, 0, null, false, "Invalid booking amount");
        }

        BigDecimal promoDiscount = BigDecimal.ZERO;
        String promoMessage = "";

        // 1. Process Promo Code
        if (promo != null) {
            if (!promo.isActive() || promo.isExpired()) {
                return new PromoCalculationResult(originalTotal, BigDecimal.ZERO, BigDecimal.ZERO,
                        originalTotal, 0, 0, promo.getCode(), false, "Promo code is expired or inactive");
            }
            if (promo.isExhausted()) {
                return new PromoCalculationResult(originalTotal, BigDecimal.ZERO, BigDecimal.ZERO,
                        originalTotal, 0, 0, promo.getCode(), false, "Promo code usage limit has been reached");
            }
            if (originalTotal.compareTo(promo.getMinBookingAmount()) < 0) {
                return new PromoCalculationResult(originalTotal, BigDecimal.ZERO, BigDecimal.ZERO,
                        originalTotal, 0, 0, promo.getCode(), false,
                        "Minimum booking amount of ₹ " + promo.getMinBookingAmount().toPlainString() + " required");
            }

            if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
                BigDecimal pct = promo.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                promoDiscount = originalTotal.multiply(pct).setScale(2, RoundingMode.HALF_UP);
                if (promo.getMaxDiscount() != null && promoDiscount.compareTo(promo.getMaxDiscount()) > 0) {
                    promoDiscount = promo.getMaxDiscount();
                }
            } else {
                promoDiscount = promo.getDiscountValue();
            }

            if (promoDiscount.compareTo(originalTotal) > 0) {
                promoDiscount = originalTotal;
            }
            promoMessage = "Promo applied: " + promo.getCode();
        }

        // 2. Process Loyalty Points Redemption
        BigDecimal afterPromo = originalTotal.subtract(promoDiscount);
        int effectivePointsRedeemed = 0;
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;

        if (pointsToRedeem > 0 && userLoyalty != null) {
            int availablePoints = userLoyalty.getPointsBalance();
            int pointsRequested = Math.min(pointsToRedeem, availablePoints);
            // Points to rupees: e.g. 100 points = ₹ 10
            BigDecimal maxRupees = new BigDecimal(pointsRequested).divide(new BigDecimal(POINTS_PER_RUPEE), 2, RoundingMode.DOWN);

            if (maxRupees.compareTo(afterPromo) > 0) {
                loyaltyDiscount = afterPromo;
                effectivePointsRedeemed = loyaltyDiscount.multiply(new BigDecimal(POINTS_PER_RUPEE)).intValue();
            } else {
                loyaltyDiscount = maxRupees;
                effectivePointsRedeemed = (pointsRequested / POINTS_PER_RUPEE) * POINTS_PER_RUPEE;
            }
        }

        BigDecimal finalPayable = afterPromo.subtract(loyaltyDiscount);
        if (finalPayable.compareTo(BigDecimal.ZERO) < 0) {
            finalPayable = BigDecimal.ZERO;
        }

        // 3. Points Earned on this transaction
        LoyaltyTier tier = userLoyalty != null ? userLoyalty.getTier() : LoyaltyTier.SILVER;
        int baseEarned = finalPayable.divide(RUPEES_PER_EARNED_POINT, 0, RoundingMode.DOWN).intValue();
        int pointsEarned = (int) Math.round(baseEarned * tier.getMultiplier());

        String successMsg = promoMessage.isEmpty() ? "Calculation complete" : promoMessage;
        if (effectivePointsRedeemed > 0) {
            successMsg += " (" + effectivePointsRedeemed + " loyalty points redeemed)";
        }

        return new PromoCalculationResult(
                originalTotal,
                promoDiscount,
                loyaltyDiscount,
                finalPayable,
                effectivePointsRedeemed,
                pointsEarned,
                promo != null ? promo.getCode() : null,
                true,
                successMsg
        );
    }
}
