package com.cineflow.dao;

import com.cineflow.model.PricingRule;
import com.cineflow.model.PricingRuleType;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'pricing_rules' table.
 */
public class PricingRuleDAO {

    public List<PricingRule> getAllRules() throws SQLException {
        String sql = "SELECT * FROM pricing_rules ORDER BY priority ASC";
        List<PricingRule> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public List<PricingRule> getActiveRules() throws SQLException {
        String sql = "SELECT * FROM pricing_rules WHERE active = TRUE ORDER BY priority ASC";
        List<PricingRule> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public boolean createRule(PricingRule rule) throws SQLException {
        String sql = "INSERT INTO pricing_rules (name, rule_type, min_value, max_value, adjustment_pct, priority, active, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, rule.getName());
            ps.setString(2, rule.getRuleType().name());
            ps.setBigDecimal(3, rule.getMinValue());
            ps.setBigDecimal(4, rule.getMaxValue());
            ps.setBigDecimal(5, rule.getAdjustmentPct());
            ps.setInt(6, rule.getPriority());
            ps.setBoolean(7, rule.isActive());
            ps.setString(8, rule.getDescription());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) rule.setId(keys.getInt(1));
                }
                return true;
            }
            return false;
        }
    }

    public boolean updateRule(PricingRule rule) throws SQLException {
        String sql = "UPDATE pricing_rules SET name = ?, rule_type = ?, min_value = ?, max_value = ?, " +
                     "adjustment_pct = ?, priority = ?, active = ?, description = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rule.getName());
            ps.setString(2, rule.getRuleType().name());
            ps.setBigDecimal(3, rule.getMinValue());
            ps.setBigDecimal(4, rule.getMaxValue());
            ps.setBigDecimal(5, rule.getAdjustmentPct());
            ps.setInt(6, rule.getPriority());
            ps.setBoolean(7, rule.isActive());
            ps.setString(8, rule.getDescription());
            ps.setInt(9, rule.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean toggleActive(int id, boolean active) throws SQLException {
        String sql = "UPDATE pricing_rules SET active = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteRule(int id) throws SQLException {
        String sql = "DELETE FROM pricing_rules WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private PricingRule mapResultSet(ResultSet rs) throws SQLException {
        PricingRule r = new PricingRule();
        r.setId(rs.getInt("id"));
        r.setName(rs.getString("name"));
        r.setRuleType(PricingRuleType.valueOf(rs.getString("rule_type")));
        r.setMinValue(rs.getBigDecimal("min_value"));
        r.setMaxValue(rs.getBigDecimal("max_value"));
        r.setAdjustmentPct(rs.getBigDecimal("adjustment_pct"));
        r.setPriority(rs.getInt("priority"));
        r.setActive(rs.getBoolean("active"));
        r.setDescription(rs.getString("description"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    }
}
