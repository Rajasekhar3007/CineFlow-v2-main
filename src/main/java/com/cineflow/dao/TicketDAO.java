package com.cineflow.dao;

import com.cineflow.model.SeatType;
import com.cineflow.model.Ticket;
import com.cineflow.model.TicketStatus;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for 'tickets' table.
 */
public class TicketDAO {

    /**
     * Inserts a ticket record within an existing JDBC transaction connection.
     */
    public int createTicketInTransaction(Ticket ticket, Connection conn) throws SQLException {
        String sql = "INSERT INTO tickets (booking_id, booking_seat_id, showtime_id, token, status, issued_at) " +
                     "VALUES (?, ?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, ticket.getBookingId());
            ps.setInt(2, ticket.getBookingSeatId());
            ps.setInt(3, ticket.getShowtimeId());
            ps.setString(4, ticket.getToken());
            ps.setString(5, ticket.getStatus().name());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    ticket.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to obtain generated ticket ID.");
    }

    /**
     * Updates the cryptographic token for a ticket record within an active transaction.
     */
    public void updateTokenInTransaction(int ticketId, String token, Connection conn) throws SQLException {
        String sql = "UPDATE tickets SET token = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
    }

    /**
     * Atomic gate admission query.
     * Updates ticket status from VALID to USED in a single atomic SQL statement.
     *
     * @return true if exactly 1 row was updated (admitted), false otherwise.
     */
    public boolean atomicValidateAndAdmit(String token, int adminUserId) throws SQLException {
        String sql = "UPDATE tickets SET status = 'USED', used_at = NOW(), used_by_user_id = ? " +
                     "WHERE token = ? AND status = 'VALID'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, adminUserId);
            ps.setString(2, token);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Marks all tickets for a booking as CANCELLED within a cancellation transaction.
     */
    public void cancelTicketsForBookingInTransaction(int bookingId, Connection conn) throws SQLException {
        String sql = "UPDATE tickets SET status = 'CANCELLED' WHERE booking_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            ps.executeUpdate();
        }
    }

    public List<Ticket> findByBookingId(int bookingId) throws SQLException {
        String sql = "SELECT t.*, s.row_label, s.seat_number, s.seat_type, bs.price, " +
                     "m.title AS movie_title, sc.name AS screen_name, st.start_time, u.full_name AS customer_name " +
                     "FROM tickets t " +
                     "JOIN booking_seats bs ON t.booking_seat_id = bs.id " +
                     "JOIN seats s ON bs.seat_id = s.id " +
                     "JOIN showtimes st ON t.showtime_id = st.id " +
                     "JOIN movies m ON st.movie_id = m.id " +
                     "JOIN screens sc ON st.screen_id = sc.id " +
                     "JOIN bookings b ON t.booking_id = b.id " +
                     "JOIN users u ON b.user_id = u.id " +
                     "WHERE t.booking_id = ? ORDER BY s.row_label ASC, s.seat_number ASC";

        List<Ticket> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTicket(rs));
                }
            }
        }
        return list;
    }

    public Optional<Ticket> findByToken(String token) throws SQLException {
        String sql = "SELECT t.*, s.row_label, s.seat_number, s.seat_type, bs.price, " +
                     "m.title AS movie_title, sc.name AS screen_name, st.start_time, u.full_name AS customer_name " +
                     "FROM tickets t " +
                     "JOIN booking_seats bs ON t.booking_seat_id = bs.id " +
                     "JOIN seats s ON bs.seat_id = s.id " +
                     "JOIN showtimes st ON t.showtime_id = st.id " +
                     "JOIN movies m ON st.movie_id = m.id " +
                     "JOIN screens sc ON st.screen_id = sc.id " +
                     "JOIN bookings b ON t.booking_id = b.id " +
                     "JOIN users u ON b.user_id = u.id " +
                     "WHERE t.token = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTicket(rs));
                }
            }
        }
        return Optional.empty();
    }

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        Ticket t = new Ticket();
        t.setId(rs.getInt("id"));
        t.setBookingId(rs.getInt("booking_id"));
        t.setBookingSeatId(rs.getInt("booking_seat_id"));
        t.setShowtimeId(rs.getInt("showtime_id"));
        t.setToken(rs.getString("token"));
        t.setStatus(TicketStatus.valueOf(rs.getString("status")));
        t.setIssuedAt(rs.getTimestamp("issued_at").toLocalDateTime());
        Timestamp uTs = rs.getTimestamp("used_at");
        if (uTs != null) t.setUsedAt(uTs.toLocalDateTime());
        int usedBy = rs.getInt("used_by_user_id");
        if (!rs.wasNull()) t.setUsedByUserId(usedBy);

        t.setSeatCode(rs.getString("row_label") + rs.getInt("seat_number"));
        t.setSeatType(SeatType.valueOf(rs.getString("seat_type")));
        t.setPrice(rs.getBigDecimal("price"));
        t.setMovieTitle(rs.getString("movie_title"));
        t.setScreenName(rs.getString("screen_name"));
        t.setShowtimeStart(rs.getTimestamp("start_time").toLocalDateTime());
        t.setCustomerName(rs.getString("customer_name"));
        return t;
    }
}
