package com.cineflow.dao;

import com.cineflow.model.SeatPreference;
import com.cineflow.model.WaitlistEntry;
import com.cineflow.model.WaitlistStatus;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for CineFlow v2 Waitlist Entries.
 */
public class WaitlistDAO {

    public int createEntry(WaitlistEntry entry) throws SQLException {
        String sql = "INSERT INTO waitlist_entries (user_id, showtime_id, seats_wanted, seat_preference, status, position_ts) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, entry.getUserId());
            ps.setInt(2, entry.getShowtimeId());
            ps.setInt(3, entry.getSeatsWanted());
            ps.setString(4, entry.getSeatPreference().name());
            ps.setString(5, entry.getStatus().name());
            ps.setTimestamp(6, Timestamp.valueOf(entry.getPositionTs() != null ? entry.getPositionTs() : LocalDateTime.now()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    entry.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    public Optional<WaitlistEntry> findById(int id) throws SQLException {
        String sql = "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                     "FROM waitlist_entries w " +
                     "JOIN showtimes s ON w.showtime_id = s.id " +
                     "JOIN movies m ON s.movie_id = m.id " +
                     "JOIN screens sc ON s.screen_id = sc.id " +
                     "JOIN users u ON w.user_id = u.id " +
                     "WHERE w.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<WaitlistEntry> getActiveByShowtimeAndUser(int showtimeId, int userId) throws SQLException {
        String sql = "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                     "FROM waitlist_entries w " +
                     "JOIN showtimes s ON w.showtime_id = s.id " +
                     "JOIN movies m ON s.movie_id = m.id " +
                     "JOIN screens sc ON s.screen_id = sc.id " +
                     "JOIN users u ON w.user_id = u.id " +
                     "WHERE w.showtime_id = ? AND w.user_id = ? AND w.status IN ('WAITING', 'OFFERED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<WaitlistEntry> findByUserId(int userId) throws SQLException {
        String sql = "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                     "FROM waitlist_entries w " +
                     "JOIN showtimes s ON w.showtime_id = s.id " +
                     "JOIN movies m ON s.movie_id = m.id " +
                     "JOIN screens sc ON s.screen_id = sc.id " +
                     "JOIN users u ON w.user_id = u.id " +
                     "WHERE w.user_id = ? " +
                     "ORDER BY w.created_at DESC";
        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WaitlistEntry entry = mapResultSet(rs);
                    if (entry.getStatus() == WaitlistStatus.WAITING) {
                        entry.setQueuePosition(countAheadInQueue(entry.getShowtimeId(), entry.getPositionTs()) + 1);
                    }
                    list.add(entry);
                }
            }
        }
        return list;
    }

    public List<WaitlistEntry> findWaitingByShowtime(int showtimeId) throws SQLException {
        String sql = "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                     "FROM waitlist_entries w " +
                     "JOIN showtimes s ON w.showtime_id = s.id " +
                     "JOIN movies m ON s.movie_id = m.id " +
                     "JOIN screens sc ON s.screen_id = sc.id " +
                     "JOIN users u ON w.user_id = u.id " +
                     "WHERE w.showtime_id = ? AND w.status = 'WAITING' " +
                     "ORDER BY w.position_ts ASC";
        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                int pos = 1;
                while (rs.next()) {
                    WaitlistEntry entry = mapResultSet(rs);
                    entry.setQueuePosition(pos++);
                    list.add(entry);
                }
            }
        }
        return list;
    }

    public List<Integer> findActiveShowtimeIds() throws SQLException {
        String sql = "SELECT DISTINCT showtime_id FROM waitlist_entries WHERE status = 'WAITING'";
        List<Integer> ids = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    public List<WaitlistEntry> findExpiredOffers() throws SQLException {
        String sql = "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                     "FROM waitlist_entries w " +
                     "JOIN showtimes s ON w.showtime_id = s.id " +
                     "JOIN movies m ON s.movie_id = m.id " +
                     "JOIN screens sc ON s.screen_id = sc.id " +
                     "JOIN users u ON w.user_id = u.id " +
                     "WHERE w.status = 'OFFERED' AND w.offer_expires_at <= NOW()";
        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public boolean updateStatus(int id, WaitlistStatus status) throws SQLException {
        String sql = "UPDATE waitlist_entries SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setOffered(int id, LocalDateTime offeredAt, LocalDateTime expiresAt) throws SQLException {
        String sql = "UPDATE waitlist_entries SET status = 'OFFERED', offered_at = ?, offer_expires_at = ? WHERE id = ? AND status = 'WAITING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(offeredAt));
            ps.setTimestamp(2, Timestamp.valueOf(expiresAt));
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int countAheadInQueue(int showtimeId, LocalDateTime positionTs) throws SQLException {
        String sql = "SELECT COUNT(*) FROM waitlist_entries " +
                     "WHERE showtime_id = ? AND status = 'WAITING' AND position_ts < ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showtimeId);
            ps.setTimestamp(2, Timestamp.valueOf(positionTs));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<WaitlistEntry> findAll(String statusFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT w.*, m.title AS movie_title, sc.name AS screen_name, s.start_time AS show_time, u.username " +
                "FROM waitlist_entries w " +
                "JOIN showtimes s ON w.showtime_id = s.id " +
                "JOIN movies m ON s.movie_id = m.id " +
                "JOIN screens sc ON s.screen_id = sc.id " +
                "JOIN users u ON w.user_id = u.id "
        );
        if (statusFilter != null && !statusFilter.equalsIgnoreCase("ALL")) {
            sql.append("WHERE w.status = ? ");
        }
        sql.append("ORDER BY w.created_at DESC LIMIT 200");

        List<WaitlistEntry> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (statusFilter != null && !statusFilter.equalsIgnoreCase("ALL")) {
                ps.setString(1, statusFilter.toUpperCase());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private WaitlistEntry mapResultSet(ResultSet rs) throws SQLException {
        WaitlistEntry entry = new WaitlistEntry();
        entry.setId(rs.getInt("id"));
        entry.setUserId(rs.getInt("user_id"));
        entry.setShowtimeId(rs.getInt("showtime_id"));
        entry.setSeatsWanted(rs.getInt("seats_wanted"));
        entry.setSeatPreference(SeatPreference.valueOf(rs.getString("seat_preference")));
        entry.setStatus(WaitlistStatus.valueOf(rs.getString("status")));

        Timestamp posTs = rs.getTimestamp("position_ts");
        if (posTs != null) entry.setPositionTs(posTs.toLocalDateTime());

        Timestamp offTs = rs.getTimestamp("offered_at");
        if (offTs != null) entry.setOfferedAt(offTs.toLocalDateTime());

        Timestamp expTs = rs.getTimestamp("offer_expires_at");
        if (expTs != null) entry.setOfferExpiresAt(expTs.toLocalDateTime());

        Timestamp crTs = rs.getTimestamp("created_at");
        if (crTs != null) entry.setCreatedAt(crTs.toLocalDateTime());

        // Joined fields
        try { entry.setMovieTitle(rs.getString("movie_title")); } catch (SQLException ignored) {}
        try { entry.setScreenName(rs.getString("screen_name")); } catch (SQLException ignored) {}
        try {
            Timestamp st = rs.getTimestamp("show_time");
            if (st != null) entry.setShowtimeStartTime(st.toLocalDateTime());
        } catch (SQLException ignored) {}
        try { entry.setUsername(rs.getString("username")); } catch (SQLException ignored) {}

        return entry;
    }
}
