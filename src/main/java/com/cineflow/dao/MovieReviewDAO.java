package com.cineflow.dao;

import com.cineflow.model.MovieReview;
import com.cineflow.model.ReviewSentiment;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CineFlow v3 — Data Access Object for verified customer reviews and sentiment stats.
 */
public class MovieReviewDAO {

    public List<MovieReview> findByMovieId(int movieId) throws SQLException {
        String sql = "SELECT mr.*, m.title AS movie_title, u.full_name AS author_name " +
                     "FROM movie_reviews mr " +
                     "JOIN movies m ON mr.movie_id = m.id " +
                     "JOIN users u ON mr.user_id = u.id " +
                     "WHERE mr.movie_id = ? " +
                     "ORDER BY mr.created_at DESC";
        List<MovieReview> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapMovieReview(rs));
                }
            }
        }
        return list;
    }

    public MovieReview findByUserAndMovie(int userId, int movieId) throws SQLException {
        String sql = "SELECT mr.*, m.title AS movie_title, u.full_name AS author_name " +
                     "FROM movie_reviews mr " +
                     "JOIN movies m ON mr.movie_id = m.id " +
                     "JOIN users u ON mr.user_id = u.id " +
                     "WHERE mr.user_id = ? AND mr.movie_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapMovieReview(rs);
                }
            }
        }
        return null;
    }

    public Integer findConfirmedBookingForMovie(int userId, int movieId) throws SQLException {
        String sql = "SELECT b.id FROM bookings b " +
                     "JOIN showtimes s ON b.showtime_id = s.id " +
                     "WHERE b.user_id = ? AND s.movie_id = ? AND b.status = 'CONFIRMED' " +
                     "ORDER BY b.id DESC LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    public void saveReview(MovieReview review) throws SQLException {
        String sql = "INSERT INTO movie_reviews (movie_id, user_id, booking_id, rating, review_text, sentiment, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE rating = VALUES(rating), review_text = VALUES(review_text), sentiment = VALUES(sentiment), created_at = NOW()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, review.getMovieId());
            ps.setInt(2, review.getUserId());
            ps.setInt(3, review.getBookingId());
            ps.setInt(4, review.getRating());
            ps.setString(5, review.getReviewText());
            ps.setString(6, review.getSentiment().name());
            ps.executeUpdate();
        }
    }

    public Map<String, Object> getMovieReviewStats(int movieId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total_reviews, " +
                     "AVG(rating) AS avg_rating, " +
                     "SUM(CASE WHEN sentiment = 'POSITIVE' THEN 1 ELSE 0 END) AS positive_count " +
                     "FROM movie_reviews WHERE movie_id = ?";
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalReviews", 0);
        stats.put("avgRating", 0.0);
        stats.put("positivePct", 100.0);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_reviews");
                    double avg = rs.getDouble("avg_rating");
                    int pos = rs.getInt("positive_count");
                    stats.put("totalReviews", total);
                    stats.put("avgRating", Math.round(avg * 10.0) / 10.0);
                    double pct = total > 0 ? (pos * 100.0 / total) : 100.0;
                    stats.put("positivePct", Math.round(pct * 10.0) / 10.0);
                }
            }
        }
        return stats;
    }

    private MovieReview mapMovieReview(ResultSet rs) throws SQLException {
        MovieReview rev = new MovieReview();
        rev.setId(rs.getInt("id"));
        rev.setMovieId(rs.getInt("movie_id"));
        rev.setUserId(rs.getInt("user_id"));
        rev.setBookingId(rs.getInt("booking_id"));
        rev.setRating(rs.getInt("rating"));
        rev.setReviewText(rs.getString("review_text"));
        rev.setSentiment(ReviewSentiment.valueOf(rs.getString("sentiment")));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) rev.setCreatedAt(ts.toLocalDateTime());
        try { rev.setMovieTitle(rs.getString("movie_title")); } catch (Exception ignored) {}
        try { rev.setAuthorName(rs.getString("author_name")); } catch (Exception ignored) {}
        return rev;
    }
}
