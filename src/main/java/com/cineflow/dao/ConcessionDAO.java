package com.cineflow.dao;

import com.cineflow.model.BookingConcession;
import com.cineflow.model.Concession;
import com.cineflow.model.ConcessionCategory;
import com.cineflow.model.ConcessionPickupStatus;
import com.cineflow.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CineFlow v3 — Data Access Object for F&B theater concessions and orders.
 */
public class ConcessionDAO {

    public List<Concession> getAllActive() throws SQLException {
        String sql = "SELECT * FROM concessions WHERE active = TRUE ORDER BY category, name";
        List<Concession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapConcession(rs));
            }
        }
        return list;
    }

    public List<Concession> getAll() throws SQLException {
        String sql = "SELECT * FROM concessions ORDER BY id ASC";
        List<Concession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapConcession(rs));
            }
        }
        return list;
    }

    public Concession findById(int id) throws SQLException {
        String sql = "SELECT * FROM concessions WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapConcession(rs);
                }
            }
        }
        return null;
    }

    public List<BookingConcession> findByBookingId(int bookingId) throws SQLException {
        String sql = "SELECT bc.*, c.name AS concession_name, c.category " +
                     "FROM booking_concessions bc " +
                     "JOIN concessions c ON bc.concession_id = c.id " +
                     "WHERE bc.booking_id = ? ORDER BY bc.id ASC";
        List<BookingConcession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BookingConcession item = new BookingConcession();
                    item.setId(rs.getInt("id"));
                    item.setBookingId(rs.getInt("booking_id"));
                    item.setConcessionId(rs.getInt("concession_id"));
                    item.setConcessionName(rs.getString("concession_name"));
                    item.setCategory(ConcessionCategory.valueOf(rs.getString("category")));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setTotalPrice(rs.getBigDecimal("total_price"));
                    item.setPickupStatus(ConcessionPickupStatus.valueOf(rs.getString("pickup_status")));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public List<BookingConcession> findAllOrders() throws SQLException {
        String sql = "SELECT bc.*, c.name AS concession_name, c.category, " +
                     "u.full_name AS customer_name, m.title AS movie_title, s.name AS screen_name " +
                     "FROM booking_concessions bc " +
                     "JOIN concessions c ON bc.concession_id = c.id " +
                     "JOIN bookings b ON bc.booking_id = b.id " +
                     "JOIN users u ON b.user_id = u.id " +
                     "JOIN showtimes st ON b.showtime_id = st.id " +
                     "JOIN movies m ON st.movie_id = m.id " +
                     "JOIN screens s ON st.screen_id = s.id " +
                     "ORDER BY bc.id DESC";
        List<BookingConcession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BookingConcession item = new BookingConcession();
                item.setId(rs.getInt("id"));
                item.setBookingId(rs.getInt("booking_id"));
                item.setConcessionId(rs.getInt("concession_id"));
                item.setConcessionName(rs.getString("concession_name"));
                item.setCategory(ConcessionCategory.valueOf(rs.getString("category")));
                item.setQuantity(rs.getInt("quantity"));
                item.setUnitPrice(rs.getBigDecimal("unit_price"));
                item.setTotalPrice(rs.getBigDecimal("total_price"));
                item.setPickupStatus(ConcessionPickupStatus.valueOf(rs.getString("pickup_status")));
                item.setCustomerName(rs.getString("customer_name"));
                item.setMovieTitle(rs.getString("movie_title"));
                item.setScreenName(rs.getString("screen_name"));
                list.add(item);
            }
        }
        return list;
    }

    public void addItemsToBooking(int bookingId, List<BookingConcession> items) throws SQLException {
        if (items == null || items.isEmpty()) return;
        String sql = "INSERT INTO booking_concessions (booking_id, concession_id, quantity, unit_price, total_price, pickup_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (BookingConcession item : items) {
                    ps.setInt(1, bookingId);
                    ps.setInt(2, item.getConcessionId());
                    ps.setInt(3, item.getQuantity());
                    ps.setBigDecimal(4, item.getUnitPrice());
                    ps.setBigDecimal(5, item.getTotalPrice());
                    ps.setString(6, item.getPickupStatus().name());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public boolean updatePickupStatus(int bookingConcessionId, ConcessionPickupStatus status) throws SQLException {
        String sql = "UPDATE booking_concessions SET pickup_status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, bookingConcessionId);
            return ps.executeUpdate() > 0;
        }
    }

    private Concession mapConcession(ResultSet rs) throws SQLException {
        return new Concession(
                rs.getInt("id"),
                rs.getString("name"),
                ConcessionCategory.valueOf(rs.getString("category")),
                rs.getBigDecimal("price"),
                rs.getString("description"),
                rs.getBoolean("active")
        );
    }
}
