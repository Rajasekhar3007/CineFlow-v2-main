package com.cineflow.dao;

import com.cineflow.model.PriceQuote;
import com.cineflow.model.SeatType;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Data Access Object for 'price_quotes' table.
 * Handles persistence and validation of time-locked price quotes.
 */
public class PriceQuoteDAO {

    public boolean saveQuote(PriceQuote quote) throws SQLException {
        String sql = "INSERT INTO price_quotes (user_id, showtime_id, seat_type, base_price, final_price, breakdown_json, expires_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, quote.getUserId());
            ps.setInt(2, quote.getShowtimeId());
            ps.setString(3, quote.getSeatType().name());
            ps.setBigDecimal(4, quote.getBasePrice());
            ps.setBigDecimal(5, quote.getFinalPrice());
            ps.setString(6, quote.getBreakdownJson());
            ps.setTimestamp(7, Timestamp.valueOf(quote.getExpiresAt()));

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) quote.setId(keys.getInt(1));
                }
                return true;
            }
            return false;
        }
    }

    public Optional<PriceQuote> getActiveQuote(int userId, int showtimeId, SeatType seatType) throws SQLException {
        String sql = "SELECT * FROM price_quotes WHERE user_id = ? AND showtime_id = ? AND seat_type = ? " +
                     "AND expires_at > NOW() ORDER BY id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, showtimeId);
            ps.setString(3, seatType.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PriceQuote q = new PriceQuote();
                    q.setId(rs.getInt("id"));
                    q.setUserId(rs.getInt("user_id"));
                    q.setShowtimeId(rs.getInt("showtime_id"));
                    q.setSeatType(SeatType.valueOf(rs.getString("seat_type")));
                    q.setBasePrice(rs.getBigDecimal("base_price"));
                    q.setFinalPrice(rs.getBigDecimal("final_price"));
                    q.setBreakdownJson(rs.getString("breakdown_json"));
                    q.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    q.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                    return Optional.of(q);
                }
            }
        }
        return Optional.empty();
    }

    public int deleteExpiredQuotes() throws SQLException {
        String sql = "DELETE FROM price_quotes WHERE expires_at < NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }
}
