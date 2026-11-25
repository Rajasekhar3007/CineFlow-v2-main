package com.cineflow.service;

import com.cineflow.dao.LoyaltyDAO;
import com.cineflow.dao.PromoCodeDAO;
import com.cineflow.model.PromoCode;
import com.cineflow.model.UserLoyalty;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * CineFlow v3 — Service for promotional discounts, voucher codes, and loyalty reward points.
 */
public class LoyaltyService {

    private final LoyaltyDAO loyaltyDAO;
    private final PromoCodeDAO promoCodeDAO;
    private final PromoEngine promoEngine;

    public LoyaltyService() {
        this.loyaltyDAO = new LoyaltyDAO();
        this.promoCodeDAO = new PromoCodeDAO();
        this.promoEngine = new PromoEngine();
    }

    public LoyaltyService(LoyaltyDAO loyaltyDAO, PromoCodeDAO promoCodeDAO, PromoEngine promoEngine) {
        this.loyaltyDAO = loyaltyDAO;
        this.promoCodeDAO = promoCodeDAO;
        this.promoEngine = promoEngine;
    }

    public UserLoyalty getUserLoyalty(int userId) throws SQLException {
        return loyaltyDAO.findByUserId(userId);
    }

    public List<PromoCode> getActivePromoCodes() throws SQLException {
        return promoCodeDAO.findAllActive();
    }

    public PromoCode getPromoCode(String code) throws SQLException {
        return promoCodeDAO.findByCode(code);
    }

    /**
     * Evaluates discount with validation and calculation.
     */
    public PromoEngine.PromoCalculationResult calculateDiscount(BigDecimal total,
                                                                String promoCodeStr,
                                                                int pointsToRedeem,
                                                                int userId) throws SQLException {
        PromoCode promo = null;
        if (promoCodeStr != null && !promoCodeStr.trim().isEmpty()) {
            promo = promoCodeDAO.findByCode(promoCodeStr.trim());
            if (promo == null) {
                return new PromoEngine.PromoCalculationResult(
                        total, BigDecimal.ZERO, BigDecimal.ZERO, total, 0, 0,
                        promoCodeStr, false, "Invalid or expired promo code"
                );
            }
        }

        UserLoyalty loyalty = userId > 0 ? loyaltyDAO.findByUserId(userId) : null;
        return promoEngine.calculate(total, promo, pointsToRedeem, loyalty);
    }

    /**
     * Finalizes loyalty points deduction, point earnings, and promo code usage count upon confirmed booking.
     */
    public void recordUsageAndRewards(int userId,
                                      String promoCodeStr,
                                      int pointsRedeemed,
                                      int pointsEarned) throws SQLException {
        if (userId <= 0) return;

        if (promoCodeStr != null && !promoCodeStr.trim().isEmpty()) {
            PromoCode promo = promoCodeDAO.findByCode(promoCodeStr.trim());
            if (promo != null) {
                promoCodeDAO.incrementUsage(promo.getId());
            }
        }

        if (pointsRedeemed > 0) {
            loyaltyDAO.deductPoints(userId, pointsRedeemed);
        }

        if (pointsEarned > 0) {
            loyaltyDAO.addPoints(userId, pointsEarned);
        }
    }
}
