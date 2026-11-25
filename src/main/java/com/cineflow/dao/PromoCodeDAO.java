package com.cineflow.dao;

import com.cineflow.model.DiscountType;
import com.cineflow.model.PromoCode;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CineFlow v3 — Data Access Object for promotional discount coupon codes.
 */
public class PromoCodeDAO {

    public PromoCode findByCode(String code) throws SQLException {
        if (code == null || code.trim().isEmpty()) return null;
        String sql = "SELECT * FROM promo_codes WHERE UPPER(TRIM(code)) = UPPER(TRIM(?)) AND active = TRUE";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapPromoCode(rs);
                }
            }
        }
        return null;
    }

    public List<PromoCode> findAllActive() throws SQLException {
        String sql = "SELECT * FROM promo_codes WHERE active = TRUE AND valid_until > NOW() ORDER BY discount_value DESC";
        List<PromoCode> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapPromoCode(rs));
            }
        }
        return list;
    }

    public boolean incrementUsage(int promoId) throws SQLException {
        String sql = "UPDATE promo_codes SET used_count = used_count + 1 WHERE id = ? AND used_count < usage_limit";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            return ps.executeUpdate() > 0;
        }
    }

    private PromoCode mapPromoCode(ResultSet rs) throws SQLException {
        PromoCode p = new PromoCode();
        p.setId(rs.getInt("id"));
        p.setCode(rs.getString("code"));
        p.setDiscountType(DiscountType.valueOf(rs.getString("discount_type")));
        p.setDiscountValue(rs.getBigDecimal("discount_value"));
        p.setMinBookingAmount(rs.getBigDecimal("min_booking_amount"));
        p.setMaxDiscount(rs.getBigDecimal("max_discount"));
        Timestamp ts = rs.getTimestamp("valid_until");
        if (ts != null) p.setValidUntil(ts.toLocalDateTime());
        p.setUsageLimit(rs.getInt("usage_limit"));
        p.setUsedCount(rs.getInt("used_count"));
        p.setActive(rs.getBoolean("active"));
        return p;
    }
}
