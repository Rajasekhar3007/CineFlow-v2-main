package com.cineflow.dao;

import com.cineflow.model.LoyaltyTier;
import com.cineflow.model.UserLoyalty;
import com.cineflow.util.DBConnection;

import java.sql.*;

/**
 * CineFlow v3 — Data Access Object for customer loyalty rewards and tiers.
 */
public class LoyaltyDAO {

    public UserLoyalty findByUserId(int userId) throws SQLException {
        String sql = "SELECT * FROM user_loyalty WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUserLoyalty(rs);
                }
            }
        }
        // If not found, provision default SILVER account with 0 points
        return provisionDefaultLoyalty(userId);
    }

    public UserLoyalty provisionDefaultLoyalty(int userId) throws SQLException {
        String sql = "INSERT IGNORE INTO user_loyalty (user_id, points_balance, tier, updated_at) " +
                     "VALUES (?, 0, 'SILVER', NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
        UserLoyalty loyalty = new UserLoyalty();
        loyalty.setUserId(userId);
        loyalty.setPointsBalance(0);
        loyalty.setTier(LoyaltyTier.SILVER);
        return loyalty;
    }

    public void addPoints(int userId, int pointsToAdd) throws SQLException {
        if (pointsToAdd <= 0) return;
        String sql = "INSERT INTO user_loyalty (user_id, points_balance, tier, updated_at) " +
                     "VALUES (?, ?, 'SILVER', NOW()) " +
                     "ON DUPLICATE KEY UPDATE points_balance = points_balance + ?, updated_at = NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, pointsToAdd);
            ps.setInt(3, pointsToAdd);
            ps.executeUpdate();
        }
        evaluateAndUpgradeTier(userId);
    }

    public boolean deductPoints(int userId, int pointsToDeduct) throws SQLException {
        if (pointsToDeduct <= 0) return true;
        String sql = "UPDATE user_loyalty SET points_balance = points_balance - ?, updated_at = NOW() " +
                     "WHERE user_id = ? AND points_balance >= ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pointsToDeduct);
            ps.setInt(2, userId);
            ps.setInt(3, pointsToDeduct);
            return ps.executeUpdate() > 0;
        }
    }

    public void evaluateAndUpgradeTier(int userId) throws SQLException {
        UserLoyalty loyalty = findByUserId(userId);
        if (loyalty == null) return;
        LoyaltyTier targetTier = LoyaltyTier.SILVER;
        if (loyalty.getPointsBalance() >= 1000) {
            targetTier = LoyaltyTier.PLATINUM;
        } else if (loyalty.getPointsBalance() >= 500) {
            targetTier = LoyaltyTier.GOLD;
        }
        if (targetTier != loyalty.getTier()) {
            String sql = "UPDATE user_loyalty SET tier = ? WHERE user_id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, targetTier.name());
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }
    }

    private UserLoyalty mapUserLoyalty(ResultSet rs) throws SQLException {
        UserLoyalty loyalty = new UserLoyalty();
        loyalty.setUserId(rs.getInt("user_id"));
        loyalty.setPointsBalance(rs.getInt("points_balance"));
        loyalty.setTier(LoyaltyTier.valueOf(rs.getString("tier")));
        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) loyalty.setUpdatedAt(ts.toLocalDateTime());
        return loyalty;
    }
}
