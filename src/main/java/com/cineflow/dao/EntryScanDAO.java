package com.cineflow.dao;

import com.cineflow.model.EntryScan;
import com.cineflow.model.ScanResult;
import com.cineflow.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for 'entry_scans' audit log table.
 */
public class EntryScanDAO {

    public boolean recordScan(EntryScan scan) throws SQLException {
        String sql = "INSERT INTO entry_scans (ticket_id, token_hash, result, scanned_at, scanned_by_user_id) " +
                     "VALUES (?, ?, ?, NOW(), ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (scan.getTicketId() != null) {
                ps.setInt(1, scan.getTicketId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, scan.getTokenHash());
            ps.setString(3, scan.getResult().name());
            ps.setInt(4, scan.getScannedByUserId());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) scan.setId(keys.getInt(1));
                }
                return true;
            }
            return false;
        }
    }

    public List<EntryScan> getRecentScans(int limit) throws SQLException {
        String sql = "SELECT es.*, u.full_name AS admin_name, m.title AS movie_title, s.row_label, s.seat_number " +
                     "FROM entry_scans es " +
                     "JOIN users u ON es.scanned_by_user_id = u.id " +
                     "LEFT JOIN tickets t ON es.ticket_id = t.id " +
                     "LEFT JOIN showtimes st ON t.showtime_id = st.id " +
                     "LEFT JOIN movies m ON st.movie_id = m.id " +
                     "LEFT JOIN booking_seats bs ON t.booking_seat_id = bs.id " +
                     "LEFT JOIN seats s ON bs.seat_id = s.id " +
                     "ORDER BY es.id DESC LIMIT ?";

        List<EntryScan> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EntryScan es = new EntryScan();
                    es.setId(rs.getInt("id"));
                    int tId = rs.getInt("ticket_id");
                    if (!rs.wasNull()) es.setTicketId(tId);
                    es.setTokenHash(rs.getString("token_hash"));
                    es.setResult(ScanResult.valueOf(rs.getString("result")));
                    es.setScannedAt(rs.getTimestamp("scanned_at").toLocalDateTime());
                    es.setScannedByUserId(rs.getInt("scanned_by_user_id"));
                    es.setScannerAdminName(rs.getString("admin_name"));
                    es.setMovieTitle(rs.getString("movie_title"));

                    String rLabel = rs.getString("row_label");
                    if (rLabel != null) {
                        es.setSeatCode(rLabel + rs.getInt("seat_number"));
                    }
                    list.add(es);
                }
            }
        }
        return list;
    }
}
