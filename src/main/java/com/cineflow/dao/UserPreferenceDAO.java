package com.cineflow.dao;

import com.cineflow.model.UserPreference;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Data Access Object for 'user_preferences' table.
 */
public class UserPreferenceDAO {

    public Optional<UserPreference> findByUserId(int userId) throws SQLException {
        String sql = "SELECT * FROM user_preferences WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UserPreference p = new UserPreference();
                    p.setUserId(rs.getInt("user_id"));
                    p.setGenresCsv(rs.getString("genres"));
                    p.setLanguagesCsv(rs.getString("languages"));
                    p.setMinRating(rs.getDouble("min_rating"));
                    Timestamp ts = rs.getTimestamp("updated_at");
                    if (ts != null) p.setUpdatedAt(ts.toLocalDateTime());
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    public boolean saveOrUpdate(UserPreference pref) throws SQLException {
        String sql = "INSERT INTO user_preferences (user_id, genres, languages, min_rating, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "genres = VALUES(genres), languages = VALUES(languages), " +
                     "min_rating = VALUES(min_rating), updated_at = VALUES(updated_at)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, pref.getUserId());
            ps.setString(2, pref.getGenresCsv());
            ps.setString(3, pref.getLanguagesCsv());
            ps.setDouble(4, pref.getMinRating());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            return ps.executeUpdate() > 0;
        }
    }
}
