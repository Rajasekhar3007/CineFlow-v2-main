package com.cineflow.dao;

import com.cineflow.model.*;
import com.cineflow.service.TicketTokenCodec;
import com.cineflow.util.AppConfig;
import com.cineflow.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for 'bookings', 'booking_seats', and 'payments' tables.
 * Implements strict ACID JDBC transactions with SELECT ... FOR UPDATE.
 */
public class BookingDAO {

    /**
     * Executes the comprehensive JDBC booking transaction as specified in Step 9:
     * 1. Obtain connection.
     * 2. Disable auto-commit.
     * 3. Lock relevant seat records using SELECT ... FOR UPDATE.
     * 4. Check whether seats are already booked.
     * 5. Check whether another customer owns active holds.
     * 6. Verify current customer's holds have not expired.
     * 7. Insert booking.
     * 8. Insert all booking_seats records.
     * 9. Insert payment.
     * 10. Delete current customer's holds.
     * 11. Commit transaction.
     */
    public boolean executeBookingTransaction(Booking booking, List<Integer> seatIds,
            List<BigDecimal> seatPrices, Payment payment) throws SQLException {
        if (seatIds == null || seatIds.isEmpty() || seatIds.size() != seatPrices.size()) {
            throw new IllegalArgumentException("Invalid seat selection for booking transaction.");
        }

        Connection conn = null;
        try {
            // 1. Obtain database connection
            conn = DBConnection.getConnection();

            // 2. Disable auto-commit to begin JDBC transaction
            conn.setAutoCommit(false);

            // 3. Lock the relevant seat records using SELECT ... FOR UPDATE
            String inPlaceholders = String.join(",", Collections.nCopies(seatIds.size(), "?"));
            String lockSql = "SELECT id FROM seats WHERE id IN (" + inPlaceholders + ") FOR UPDATE";

            try (PreparedStatement psLock = conn.prepareStatement(lockSql)) {
                for (int i = 0; i < seatIds.size(); i++) {
                    psLock.setInt(i + 1, seatIds.get(i));
                }
                psLock.executeQuery(); // Locks matching rows in InnoDB
            }

            // 4. Check whether any seat is already booked for this showtime
            String checkBookedSql = "SELECT bs.seat_id, s.row_label, s.seat_number " +
                    "FROM booking_seats bs " +
                    "JOIN seats s ON bs.seat_id = s.id " +
                    "JOIN bookings b ON bs.booking_id = b.id " +
                    "WHERE bs.showtime_id = ? AND b.status = 'CONFIRMED' AND bs.seat_id IN (" + inPlaceholders + ")";

            try (PreparedStatement psCheckBooked = conn.prepareStatement(checkBookedSql)) {
                psCheckBooked.setInt(1, booking.getShowtimeId());
                for (int i = 0; i < seatIds.size(); i++) {
                    psCheckBooked.setInt(i + 2, seatIds.get(i));
                }
                try (ResultSet rs = psCheckBooked.executeQuery()) {
                    if (rs.next()) {
                        String seatCode = rs.getString("row_label") + rs.getInt("seat_number");
                        throw new SQLException("Seat " + seatCode + " has already been booked by another customer.");
                    }
                }
            }

            // 5. Check whether another customer owns active holds on these seats
            String checkHoldsSql = "SELECT sh.seat_id, s.row_label, s.seat_number " +
                    "FROM seat_holds sh " +
                    "JOIN seats s ON sh.seat_id = s.id " +
                    "WHERE sh.showtime_id = ? AND sh.user_id <> ? AND sh.held_until > NOW() " +
                    "AND sh.seat_id IN (" + inPlaceholders + ")";

            try (PreparedStatement psCheckHolds = conn.prepareStatement(checkHoldsSql)) {
                psCheckHolds.setInt(1, booking.getShowtimeId());
                psCheckHolds.setInt(2, booking.getUserId());
                for (int i = 0; i < seatIds.size(); i++) {
                    psCheckHolds.setInt(i + 3, seatIds.get(i));
                }
                try (ResultSet rs = psCheckHolds.executeQuery()) {
                    if (rs.next()) {
                        String seatCode = rs.getString("row_label") + rs.getInt("seat_number");
                        throw new SQLException("Seat " + seatCode + " is currently held by another customer.");
                    }
                }
            }

            // 6. Verify current customer's holds have not expired
            String verifyHoldSql = "SELECT COUNT(*) FROM seat_holds " +
                    "WHERE showtime_id = ? AND user_id = ? AND held_until >= NOW() " +
                    "AND seat_id IN (" + inPlaceholders + ")";

            try (PreparedStatement psVerify = conn.prepareStatement(verifyHoldSql)) {
                psVerify.setInt(1, booking.getShowtimeId());
                psVerify.setInt(2, booking.getUserId());
                for (int i = 0; i < seatIds.size(); i++) {
                    psVerify.setInt(i + 3, seatIds.get(i));
                }
                try (ResultSet rs = psVerify.executeQuery()) {
                    if (rs.next() && rs.getInt(1) < seatIds.size()) {
                        throw new SQLException("Your temporary seat hold has expired. Please select seats again.");
                    }
                }
            }

            // 7. Insert booking into 'bookings' table
            String insertBookingSql = "INSERT INTO bookings (user_id, showtime_id, booking_time, total_amount, status, pricing_snapshot) "
                    +
                    "VALUES (?, ?, NOW(), ?, 'CONFIRMED', ?)";

            int bookingId;
            try (PreparedStatement psBooking = conn.prepareStatement(insertBookingSql,
                    Statement.RETURN_GENERATED_KEYS)) {
                psBooking.setInt(1, booking.getUserId());
                psBooking.setInt(2, booking.getShowtimeId());
                psBooking.setBigDecimal(3, booking.getTotalAmount());
                psBooking.setString(4, booking.getPricingSnapshot());
                psBooking.executeUpdate();

                try (ResultSet keys = psBooking.getGeneratedKeys()) {
                    if (keys.next()) {
                        bookingId = keys.getInt(1);
                        booking.setId(bookingId);
                    } else {
                        throw new SQLException("Failed to retrieve generated booking ID.");
                    }
                }
            }

            // 8. Insert all booking_seats records and issue tamper-proof tickets
            String insertSeatsSql = "INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES (?, ?, ?, ?)";
            TicketDAO ticketDAO = new TicketDAO();
            String ticketSecret = AppConfig.getTicketSecret();

            try (PreparedStatement psSeats = conn.prepareStatement(insertSeatsSql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < seatIds.size(); i++) {
                    psSeats.setInt(1, bookingId);
                    psSeats.setInt(2, booking.getShowtimeId());
                    psSeats.setInt(3, seatIds.get(i));
                    psSeats.setBigDecimal(4, seatPrices.get(i));
                    psSeats.executeUpdate();

                    int bookingSeatId;
                    try (ResultSet bKeys = psSeats.getGeneratedKeys()) {
                        if (bKeys.next()) {
                            bookingSeatId = bKeys.getInt(1);
                        } else {
                            throw new SQLException("Failed to obtain generated booking_seat ID.");
                        }
                    }

                    // Issue unique digital pass
                    Ticket ticket = new Ticket();
                    ticket.setBookingId(bookingId);
                    ticket.setBookingSeatId(bookingSeatId);
                    ticket.setShowtimeId(booking.getShowtimeId());
                    ticket.setStatus(TicketStatus.VALID);
                    ticket.setToken("TEMP_" + System.nanoTime());

                    int ticketId = ticketDAO.createTicketInTransaction(ticket, conn);
                    String signedToken = TicketTokenCodec.generateToken(ticketId, booking.getShowtimeId(),
                            ticketSecret);
                    ticketDAO.updateTokenInTransaction(ticketId, signedToken, conn);
                }
            }

            // 9. Insert payment record into 'payments' table
            String insertPaymentSql = "INSERT INTO payments (booking_id, method, amount, transaction_id, order_id, status, paid_at) VALUES (?, ?, ?, ?, ?, 'PAID', NOW())";
            try (PreparedStatement psPayment = conn.prepareStatement(insertPaymentSql,
                    Statement.RETURN_GENERATED_KEYS)) {
                psPayment.setInt(1, bookingId);
                psPayment.setString(2, payment.getMethod() != null ? payment.getMethod().name() : "UPI");
                psPayment.setBigDecimal(3, payment.getAmount());
                psPayment.setString(4, payment.getTransactionId());
                psPayment.setString(5, payment.getOrderId());
                psPayment.executeUpdate();

                try (ResultSet keys = psPayment.getGeneratedKeys()) {
                    if (keys.next()) {
                        payment.setId(keys.getInt(1));
                    }
                }
            }

            // 10. Delete current customer's holds
            String deleteHoldsSql = "DELETE FROM seat_holds WHERE showtime_id = ? AND user_id = ?";
            try (PreparedStatement psDel = conn.prepareStatement(deleteHoldsSql)) {
                psDel.setInt(1, booking.getShowtimeId());
                psDel.setInt(2, booking.getUserId());
                psDel.executeUpdate();
            }

            // 11. Commit transaction
            conn.commit();
            return true;

        } catch (SQLException ex) {
            // Roll back changes on any failure
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw ex;
        } finally {
            // Restore auto-commit before closing/returning connection
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("Failed to restore auto-commit: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * Cancels a booking, releases its seats, and records its refunds in one transaction.
     */
    public boolean cancelBookingTransaction(int bookingId, int userId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Verify eligibility: booking exists, belongs to user, and showtime hasn't
            // started
            String verifySql = "SELECT b.id, s.start_time, b.status " +
                    "FROM bookings b " +
                    "JOIN showtimes s ON b.showtime_id = s.id " +
                    "WHERE b.id = ? AND b.user_id = ? FOR UPDATE";

            try (PreparedStatement psVerify = conn.prepareStatement(verifySql)) {
                psVerify.setInt(1, bookingId);
                psVerify.setInt(2, userId);
                try (ResultSet rs = psVerify.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Booking not found or access denied.");
                    }
                    if ("CANCELLED".equals(rs.getString("status"))) {
                        throw new SQLException("Booking is already cancelled.");
                    }
                    Timestamp start = rs.getTimestamp("start_time");
                    if (start.before(Timestamp.valueOf(LocalDateTime.now()))) {
                        throw new SQLException("Cancellation is not permitted after the showtime has started.");
                    }
                }
            }

            // Update booking status
            String updateBooking = "UPDATE bookings SET status = 'CANCELLED' WHERE id = ?";
            try (PreparedStatement psB = conn.prepareStatement(updateBooking)) {
                psB.setInt(1, bookingId);
                psB.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE booking_seats SET released_at = NOW() WHERE booking_id = ? AND released_at IS NULL")) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }

            // Live gateway refunds are submitted after commit by the retryable background job.
            String queueRefund = """
                    UPDATE payments SET status = 'REFUND_PENDING', refund_next_attempt_at = NOW()
                    WHERE booking_id = ? AND status = 'PAID' AND method = 'RAZORPAY'
                      AND transaction_id REGEXP '^pay_[A-Za-z0-9]+$'
                    """;
            try (PreparedStatement ps = conn.prepareStatement(queueRefund)) {
                ps.setInt(1, bookingId);
                ps.executeUpdate();
            }

            String updatePayment = "UPDATE payments SET status = 'REFUNDED' WHERE booking_id = ? AND status = 'PAID'";
            try (PreparedStatement psP = conn.prepareStatement(updatePayment)) {
                psP.setInt(1, bookingId);
                psP.executeUpdate();
            }

            // Update tickets to CANCELLED in the same transaction
            TicketDAO ticketDAO = new TicketDAO();
            ticketDAO.cancelTicketsForBookingInTransaction(bookingId, conn);

            conn.commit();
            return true;

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public record PendingRefund(int paymentId, String transactionId, String gatewayRefundId) {}

    public List<PendingRefund> findDueRefunds() throws SQLException {
        String sql = """
                SELECT id, transaction_id, gateway_refund_id FROM payments
                WHERE status = 'REFUND_PENDING' AND refund_next_attempt_at <= NOW()
                ORDER BY refund_next_attempt_at, id LIMIT 20
                """;
        List<PendingRefund> refunds = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                refunds.add(new PendingRefund(rs.getInt("id"), rs.getString("transaction_id"),
                        rs.getString("gateway_refund_id")));
            }
        }
        return refunds;
    }

    public boolean claimDueRefund(int paymentId) throws SQLException {
        String sql = """
                UPDATE payments SET refund_next_attempt_at = NOW() + INTERVAL 2 MINUTE
                WHERE id = ? AND status = 'REFUND_PENDING' AND refund_next_attempt_at <= NOW()
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            return ps.executeUpdate() == 1;
        }
    }

    public void updateGatewayRefund(int paymentId, String refundId, PaymentStatus status) throws SQLException {
        String sql = """
                UPDATE payments SET gateway_refund_id = ?, status = ?,
                    refund_next_attempt_at = CASE WHEN ? = 'REFUND_PENDING'
                        THEN NOW() + INTERVAL 2 MINUTE ELSE NULL END
                WHERE id = ? AND status = 'REFUND_PENDING'
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, refundId);
            ps.setString(2, status.name());
            ps.setString(3, status.name());
            ps.setInt(4, paymentId);
            ps.executeUpdate();
        }
    }

    public List<Integer> getBookedSeatIdsForShowtime(int showtimeId) throws SQLException {
        String sql = "SELECT bs.seat_id FROM booking_seats bs " +
                "JOIN bookings b ON bs.booking_id = b.id " +
                "WHERE bs.showtime_id = ? AND b.status = 'CONFIRMED'";
        List<Integer> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, showtimeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getInt("seat_id"));
                }
            }
        }
        return list;
    }

    public List<Booking> findByUserId(int userId) throws SQLException {
        String sql = "SELECT b.id, b.user_id, b.showtime_id, s.movie_id as movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, " +
                "u.username, u.full_name as customer_name, m.title as movie_title, " +
                "sc.name as screen_name, s.start_time as showtime_start, " +
                "GROUP_CONCAT(CONCAT(se.row_label, se.seat_number) ORDER BY se.row_label, se.seat_number SEPARATOR ', ') as seat_numbers, "
                +
                "p.method as payment_method, " +
                "(SELECT CASE MAX(CASE rp.status WHEN 'REFUND_FAILED' THEN 3 WHEN 'REFUND_PENDING' THEN 2 " +
                "WHEN 'REFUNDED' THEN 1 ELSE 0 END) WHEN 3 THEN 'REFUND_FAILED' WHEN 2 THEN 'REFUND_PENDING' " +
                "WHEN 1 THEN 'REFUNDED' END FROM payments rp WHERE rp.booking_id = b.id) as refund_status " +
                "FROM bookings b " +
                "JOIN users u ON b.user_id = u.id " +
                "JOIN showtimes s ON b.showtime_id = s.id " +
                "JOIN movies m ON s.movie_id = m.id " +
                "JOIN screens sc ON s.screen_id = sc.id " +
                "JOIN booking_seats bs ON b.id = bs.booking_id " +
                "JOIN seats se ON bs.seat_id = se.id " +
                "LEFT JOIN payments p ON b.id = p.booking_id " +
                "WHERE b.user_id = ? " +
                "GROUP BY b.id, b.user_id, b.showtime_id, s.movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, " +
                "u.username, u.full_name, m.title, sc.name, s.start_time, p.method " +
                "ORDER BY b.booking_time DESC";

        List<Booking> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Booking booking = mapResultSetToBooking(rs);
                    String refundStatus = rs.getString("refund_status");
                    if (booking.getStatus() == BookingStatus.CANCELLED && refundStatus != null) {
                        booking.setRefundStatus(PaymentStatus.valueOf(refundStatus));
                    }
                    list.add(booking);
                }
            }
        }
        return list;
    }

    public Optional<Booking> findById(int id) throws SQLException {
        String sql = "SELECT b.id, b.user_id, b.showtime_id, s.movie_id as movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, " +
                "u.username, u.full_name as customer_name, m.title as movie_title, " +
                "sc.name as screen_name, s.start_time as showtime_start, " +
                "GROUP_CONCAT(CONCAT(se.row_label, se.seat_number) ORDER BY se.row_label, se.seat_number SEPARATOR ', ') as seat_numbers, "
                +
                "p.method as payment_method " +
                "FROM bookings b " +
                "JOIN users u ON b.user_id = u.id " +
                "JOIN showtimes s ON b.showtime_id = s.id " +
                "JOIN movies m ON s.movie_id = m.id " +
                "JOIN screens sc ON s.screen_id = sc.id " +
                "JOIN booking_seats bs ON b.id = bs.booking_id " +
                "JOIN seats se ON bs.seat_id = se.id " +
                "LEFT JOIN payments p ON b.id = p.booking_id " +
                "WHERE b.id = ? " +
                "GROUP BY b.id, b.user_id, b.showtime_id, s.movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, " +
                "u.username, u.full_name, m.title, sc.name, s.start_time, p.method";

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBooking(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Booking> findAllJoined(LocalDate startDate, LocalDate endDate,
            Integer movieId, BookingStatus status, String username) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT b.id, b.user_id, b.showtime_id, s.movie_id as movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, " +
                        "u.username, u.full_name as customer_name, m.title as movie_title, " +
                        "sc.name as screen_name, s.start_time as showtime_start, " +
                        "GROUP_CONCAT(CONCAT(se.row_label, se.seat_number) ORDER BY se.row_label, se.seat_number SEPARATOR ', ') as seat_numbers, "
                        +
                        "p.method as payment_method " +
                        "FROM bookings b " +
                        "JOIN users u ON b.user_id = u.id " +
                        "JOIN showtimes s ON b.showtime_id = s.id " +
                        "JOIN movies m ON s.movie_id = m.id " +
                        "JOIN screens sc ON s.screen_id = sc.id " +
                        "JOIN booking_seats bs ON b.id = bs.booking_id " +
                        "JOIN seats se ON bs.seat_id = se.id " +
                        "LEFT JOIN payments p ON b.id = p.booking_id " +
                        "WHERE 1=1");

        List<Object> params = new ArrayList<>();

        if (startDate != null) {
            sql.append(" AND DATE(b.booking_time) >= ?");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND DATE(b.booking_time) <= ?");
            params.add(Date.valueOf(endDate));
        }
        if (movieId != null && movieId > 0) {
            sql.append(" AND s.movie_id = ?");
            params.add(movieId);
        }
        if (status != null) {
            sql.append(" AND b.status = ?");
            params.add(status.name());
        }
        if (username != null && !username.trim().isEmpty()) {
            sql.append(" AND u.username LIKE ?");
            params.add("%" + username.trim() + "%");
        }

        sql.append(" GROUP BY b.id, b.user_id, b.showtime_id, s.movie_id, b.booking_time, b.total_amount, b.status, b.reschedule_count, ")
                .append("u.username, u.full_name, m.title, sc.name, s.start_time, p.method ")
                .append("ORDER BY b.booking_time DESC");

        List<Booking> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToBooking(rs));
                }
            }
        }
        return list;
    }

    private Booking mapResultSetToBooking(ResultSet rs) throws SQLException {
        Booking b = new Booking(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getInt("showtime_id"),
                rs.getTimestamp("booking_time").toLocalDateTime(),
                rs.getBigDecimal("total_amount"),
                BookingStatus.fromString(rs.getString("status")));
        b.setUsername(rs.getString("username"));
        b.setCustomerName(rs.getString("customer_name"));
        b.setMovieTitle(rs.getString("movie_title"));
        b.setScreenName(rs.getString("screen_name"));
        b.setShowtimeStart(rs.getTimestamp("showtime_start").toLocalDateTime());
        b.setSeatNumbers(rs.getString("seat_numbers"));
        b.setPaymentMethod(PaymentMethod.fromString(rs.getString("payment_method")));
        try {
            b.setTransactionId(rs.getString("transaction_id"));
        } catch (Exception ignored) {}
        try {
            b.setMovieId(rs.getInt("movie_id"));
        } catch (Exception ignored) {}
        try {
            b.setRescheduleCount(rs.getInt("reschedule_count"));
        } catch (Exception ignored) {}
        return b;
    }

    /**
     * Executes an ACID Smart Ticket Rescheduling transaction:
     * 1. Lock original booking with SELECT ... FOR UPDATE.
     * 2. Validate eligibility: CONFIRMED, showtime >= 2 hours in future, reschedule_count < 2.
     * 3. Lock replacement seats in new showtime.
     * 4. Check replacement seats are not already booked.
     * 5. Atomically replace booking_seats and re-issue tickets with HMAC-SHA256 tokens.
     * 6. Log audit entry into ticket_reschedules.
     * 7. Process payment difference adjustment.
     * 8. Commit transaction.
     */
    public boolean executeRescheduleTransaction(
            int bookingId,
            int userId,
            int newShowtimeId,
            List<Integer> newSeatIds,
            List<BigDecimal> newSeatPrices,
            BigDecimal fareDifference,
            PaymentMethod diffPaymentMethod) throws SQLException {

        if (newSeatIds == null || newSeatIds.isEmpty() || newSeatIds.size() != newSeatPrices.size()) {
            throw new IllegalArgumentException("Invalid seat selection for rescheduling.");
        }

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Verify and lock original booking
            int oldShowtimeId;
            String oldSeatsStr;
            int currentRescheduleCount;
            String verifyBookingSql = "SELECT b.id, b.showtime_id, b.status, b.reschedule_count, s.start_time, " +
                    "GROUP_CONCAT(CONCAT(se.row_label, se.seat_number) ORDER BY se.row_label, se.seat_number SEPARATOR ', ') as seats_str " +
                    "FROM bookings b " +
                    "JOIN showtimes s ON b.showtime_id = s.id " +
                    "JOIN booking_seats bs ON b.id = bs.booking_id " +
                    "JOIN seats se ON bs.seat_id = se.id " +
                    "WHERE b.id = ? AND b.user_id = ? " +
                    "GROUP BY b.id, b.showtime_id, b.status, b.reschedule_count, s.start_time " +
                    "FOR UPDATE";

            try (PreparedStatement psV = conn.prepareStatement(verifyBookingSql)) {
                psV.setInt(1, bookingId);
                psV.setInt(2, userId);
                try (ResultSet rs = psV.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Booking #" + bookingId + " not found or unauthorized.");
                    }
                    if (!"CONFIRMED".equals(rs.getString("status"))) {
                        throw new SQLException("Only CONFIRMED bookings can be rescheduled.");
                    }
                    currentRescheduleCount = rs.getInt("reschedule_count");
                    if (currentRescheduleCount >= 2) {
                        throw new SQLException("This ticket has reached the maximum limit of 2 reschedules.");
                    }
                    Timestamp start = rs.getTimestamp("start_time");
                    if (start.before(Timestamp.valueOf(LocalDateTime.now().plusHours(2)))) {
                        throw new SQLException("Rescheduling is only permitted at least 2 hours before showtime.");
                    }
                    oldShowtimeId = rs.getInt("showtime_id");
                    oldSeatsStr = rs.getString("seats_str");
                }
            }

            // Verify tickets are not already used at gate
            String checkTicketStatus = "SELECT COUNT(*) FROM tickets WHERE booking_id = ? AND status <> 'VALID'";
            try (PreparedStatement psT = conn.prepareStatement(checkTicketStatus)) {
                psT.setInt(1, bookingId);
                try (ResultSet rs = psT.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Tickets for this booking have already been scanned or invalidated.");
                    }
                }
            }

            // 2. Lock replacement seats in new showtime
            String inPlaceholders = String.join(",", Collections.nCopies(newSeatIds.size(), "?"));
            String lockSeatsSql = "SELECT id, row_label, seat_number FROM seats WHERE id IN (" + inPlaceholders + ") FOR UPDATE";
            List<String> newSeatLabels = new ArrayList<>();
            try (PreparedStatement psL = conn.prepareStatement(lockSeatsSql)) {
                for (int i = 0; i < newSeatIds.size(); i++) {
                    psL.setInt(i + 1, newSeatIds.get(i));
                }
                try (ResultSet rs = psL.executeQuery()) {
                    while (rs.next()) {
                        newSeatLabels.add(rs.getString("row_label") + rs.getInt("seat_number"));
                    }
                }
            }
            String newSeatsStr = String.join(", ", newSeatLabels);

            // 3. Verify replacement seats are not already booked for the new showtime
            String checkNewBookedSql = "SELECT bs.seat_id, s.row_label, s.seat_number " +
                    "FROM booking_seats bs " +
                    "JOIN seats s ON bs.seat_id = s.id " +
                    "JOIN bookings b ON bs.booking_id = b.id " +
                    "WHERE bs.showtime_id = ? AND b.status = 'CONFIRMED' AND bs.seat_id IN (" + inPlaceholders + ")";
            try (PreparedStatement psCB = conn.prepareStatement(checkNewBookedSql)) {
                psCB.setInt(1, newShowtimeId);
                for (int i = 0; i < newSeatIds.size(); i++) {
                    psCB.setInt(i + 2, newSeatIds.get(i));
                }
                try (ResultSet rs = psCB.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Seat " + rs.getString("row_label") + rs.getInt("seat_number") +
                                " in the selected showtime is already booked. Please choose other seats.");
                    }
                }
            }

            // 4. Delete old tickets and old booking_seats (releases old seats atomically)
            String deleteOldTickets = "DELETE FROM tickets WHERE booking_id = ?";
            try (PreparedStatement psDT = conn.prepareStatement(deleteOldTickets)) {
                psDT.setInt(1, bookingId);
                psDT.executeUpdate();
            }

            String deleteOldSeats = "DELETE FROM booking_seats WHERE booking_id = ?";
            try (PreparedStatement psDS = conn.prepareStatement(deleteOldSeats)) {
                psDS.setInt(1, bookingId);
                psDS.executeUpdate();
            }

            // 5. Insert new booking_seats
            String insertSeatsSql = "INSERT INTO booking_seats (booking_id, showtime_id, seat_id, price) VALUES (?, ?, ?, ?)";
            List<Integer> generatedBookingSeatIds = new ArrayList<>();
            try (PreparedStatement psIS = conn.prepareStatement(insertSeatsSql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < newSeatIds.size(); i++) {
                    psIS.setInt(1, bookingId);
                    psIS.setInt(2, newShowtimeId);
                    psIS.setInt(3, newSeatIds.get(i));
                    psIS.setBigDecimal(4, newSeatPrices.get(i));
                    psIS.executeUpdate();

                    try (ResultSet gk = psIS.getGeneratedKeys()) {
                        if (gk.next()) {
                            generatedBookingSeatIds.add(gk.getInt(1));
                        }
                    }
                }
            }

            // 6. Update booking record with new showtime, total amount, and increment reschedule_count
            String updateBookingSql = "UPDATE bookings SET showtime_id = ?, total_amount = total_amount + ?, " +
                    "reschedule_count = reschedule_count + 1 WHERE id = ?";
            try (PreparedStatement psUB = conn.prepareStatement(updateBookingSql)) {
                psUB.setInt(1, newShowtimeId);
                psUB.setBigDecimal(2, fareDifference != null ? fareDifference : BigDecimal.ZERO);
                psUB.setInt(3, bookingId);
                psUB.executeUpdate();
            }

            // 7. Audit log in ticket_reschedules
            String auditSql = "INSERT INTO ticket_reschedules (booking_id, old_showtime_id, new_showtime_id, old_seats, new_seats, fare_difference) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psA = conn.prepareStatement(auditSql)) {
                psA.setInt(1, bookingId);
                psA.setInt(2, oldShowtimeId);
                psA.setInt(3, newShowtimeId);
                psA.setString(4, oldSeatsStr != null ? oldSeatsStr : "N/A");
                psA.setString(5, newSeatsStr);
                psA.setBigDecimal(6, fareDifference != null ? fareDifference : BigDecimal.ZERO);
                psA.executeUpdate();
            }

            // 8. Payment adjustment if fare difference != 0
            if (fareDifference != null && fareDifference.compareTo(BigDecimal.ZERO) != 0) {
                String paySql = "INSERT INTO payments (booking_id, method, amount, transaction_id, order_id, status) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psP = conn.prepareStatement(paySql)) {
                    psP.setInt(1, bookingId);
                    psP.setString(2, diffPaymentMethod != null ? diffPaymentMethod.name() : "UPI");
                    psP.setBigDecimal(3, fareDifference.abs());
                    psP.setString(4, null);
                    psP.setString(5, null);
                    psP.setString(6, fareDifference.compareTo(BigDecimal.ZERO) > 0 ? "PAID" : "REFUNDED");
                    psP.executeUpdate();
                }
            }

            // 9. Re-issue fresh digital tickets with HMAC-SHA256 tokens
            TicketDAO ticketDAO = new TicketDAO();
            String ticketSecret = AppConfig.getTicketSecret();
            for (int i = 0; i < generatedBookingSeatIds.size(); i++) {
                int bookingSeatId = generatedBookingSeatIds.get(i);
                com.cineflow.model.Ticket t = new com.cineflow.model.Ticket();
                t.setBookingId(bookingId);
                t.setBookingSeatId(bookingSeatId);
                t.setShowtimeId(newShowtimeId);
                t.setStatus(com.cineflow.model.TicketStatus.VALID);
                t.setToken("TEMP_" + System.nanoTime());

                int ticketId = ticketDAO.createTicketInTransaction(t, conn);
                String validToken = TicketTokenCodec.generateToken(ticketId, newShowtimeId, ticketSecret);
                ticketDAO.updateTokenInTransaction(ticketId, validToken, conn);
            }

            // 10. Clear any temporary holds
            String clearHoldsSql = "DELETE FROM seat_holds WHERE showtime_id = ? AND user_id = ?";
            try (PreparedStatement psCH = conn.prepareStatement(clearHoldsSql)) {
                psCH.setInt(1, newShowtimeId);
                psCH.setInt(2, userId);
                psCH.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException ex) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }
}
