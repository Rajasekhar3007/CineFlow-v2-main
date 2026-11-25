package com.cineflow.service;

import com.cineflow.dao.BookingDAO;
import com.cineflow.dao.TicketDAO;
import com.cineflow.model.Booking;
import com.cineflow.model.Payment;
import com.cineflow.model.PaymentMethod;
import com.cineflow.model.Ticket;
import com.cineflow.util.DBConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RescheduleIntegrationTest {

    @Test
    @DisplayName("Smart Ticket Rescheduling Transaction Atomically Swaps Seats and Re-Issues Tickets")
    void testRescheduleTransactionSuccess() {
        BookingDAO bookingDAO = new BookingDAO();
        TicketDAO ticketDAO = new TicketDAO();

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Pick customer john_doe (id 2 or first customer)
            int userId = 2;

            // Pick 2 distinct upcoming showtimes
            ResultSet rsShow = stmt.executeQuery(
                    "SELECT id FROM showtimes WHERE start_time > NOW() + INTERVAL 3 HOUR ORDER BY id ASC LIMIT 2");
            if (!rsShow.next()) return;
            int showtime1 = rsShow.getInt("id");
            if (!rsShow.next()) return;
            int showtime2 = rsShow.getInt("id");

            // Pick 2 seats in screen for showtime1
            ResultSet rsSeats1 = stmt.executeQuery(
                    "SELECT s.id FROM seats s JOIN showtimes st ON s.screen_id = st.screen_id " +
                    "WHERE st.id = " + showtime1 + " " +
                    "AND s.id NOT IN (SELECT seat_id FROM booking_seats WHERE showtime_id = " + showtime1 + ") LIMIT 2");
            if (!rsSeats1.next()) return;
            int seatA = rsSeats1.getInt("id");
            if (!rsSeats1.next()) return;
            int seatB = rsSeats1.getInt("id");

            // Pick 2 seats in screen for showtime2
            ResultSet rsSeats2 = stmt.executeQuery(
                    "SELECT s.id FROM seats s JOIN showtimes st ON s.screen_id = st.screen_id " +
                    "WHERE st.id = " + showtime2 + " " +
                    "AND s.id NOT IN (SELECT seat_id FROM booking_seats WHERE showtime_id = " + showtime2 + ") LIMIT 2");
            if (!rsSeats2.next()) return;
            int seatC = rsSeats2.getInt("id");
            if (!rsSeats2.next()) return;
            int seatD = rsSeats2.getInt("id");

            // 2. Insert test holds for showtime1
            stmt.executeUpdate("DELETE FROM seat_holds WHERE user_id = " + userId);
            stmt.executeUpdate("INSERT INTO seat_holds (showtime_id, seat_id, user_id, held_until) VALUES " +
                    "(" + showtime1 + ", " + seatA + ", " + userId + ", NOW() + INTERVAL 5 MINUTE), " +
                    "(" + showtime1 + ", " + seatB + ", " + userId + ", NOW() + INTERVAL 5 MINUTE)");

            // 3. Create initial booking
            Booking b = new Booking();
            b.setUserId(userId);
            b.setShowtimeId(showtime1);
            b.setTotalAmount(new BigDecimal("400.00"));
            com.cineflow.model.Payment p = new com.cineflow.model.Payment();
            p.setMethod(PaymentMethod.UPI);
            p.setAmount(new BigDecimal("400.00"));
            p.setStatus(com.cineflow.model.PaymentStatus.PAID);

            boolean booked = bookingDAO.executeBookingTransaction(
                    b, List.of(seatA, seatB),
                    List.of(new BigDecimal("200.00"), new BigDecimal("200.00")), p);
            assertTrue(booked);
            int bookingId = b.getId();
            assertTrue(bookingId > 0);

            // 4. Execute Reschedule to showtime2 with seatC and seatD
            BigDecimal fareDiff = new BigDecimal("40.00"); // e.g. +₹40
            boolean rescheduled = bookingDAO.executeRescheduleTransaction(
                    bookingId, userId, showtime2,
                    List.of(seatC, seatD),
                    List.of(new BigDecimal("220.00"), new BigDecimal("220.00")),
                    fareDiff, PaymentMethod.UPI
            );
            assertTrue(rescheduled);

            // 5. Verify state consistency
            // Old seats must now be free
            ResultSet rsOld = stmt.executeQuery(
                    "SELECT COUNT(*) FROM booking_seats WHERE showtime_id = " + showtime1 + " AND seat_id IN (" + seatA + ", " + seatB + ")");
            assertTrue(rsOld.next());
            assertEquals(0, rsOld.getInt(1), "Original seats should be released");

            // New seats must be booked
            ResultSet rsNew = stmt.executeQuery(
                    "SELECT COUNT(*) FROM booking_seats WHERE booking_id = " + bookingId + " AND showtime_id = " + showtime2);
            assertTrue(rsNew.next());
            assertEquals(2, rsNew.getInt(1), "Replacement seats should be assigned");

            // Booking record updated
            ResultSet rsB = stmt.executeQuery(
                    "SELECT showtime_id, total_amount, reschedule_count FROM bookings WHERE id = " + bookingId);
            assertTrue(rsB.next());
            assertEquals(showtime2, rsB.getInt("showtime_id"));
            assertEquals(1, rsB.getInt("reschedule_count"));
            assertEquals(new BigDecimal("440.00"), rsB.getBigDecimal("total_amount"));

            // Ticket audit record created
            ResultSet rsAudit = stmt.executeQuery(
                    "SELECT COUNT(*) FROM ticket_reschedules WHERE booking_id = " + bookingId);
            assertTrue(rsAudit.next());
            assertEquals(1, rsAudit.getInt(1));

            // Fresh valid tickets re-issued
            List<Ticket> newTickets = ticketDAO.findByBookingId(bookingId);
            assertEquals(2, newTickets.size());
            for (Ticket t : newTickets) {
                assertEquals(showtime2, t.getShowtimeId());
                assertTrue(t.getToken().startsWith("CF1."));
            }

        } catch (Exception ex) {
            fail("Reschedule transaction failed with exception: " + ex.getMessage());
        }
    }
}
