package com.cineflow.service;

import com.cineflow.dao.EntryScanDAO;
import com.cineflow.dao.TicketDAO;
import com.cineflow.model.ScanResult;
import com.cineflow.model.Ticket;
import com.cineflow.model.TicketStatus;
import com.cineflow.util.DBConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TicketScanningConcurrencyTest {

    private final TicketService ticketService = new TicketService();

    @Test
    @DisplayName("Concurrent scans of the same ticket token: exactly 1 admits, 1 is rejected as ALREADY_USED")
    void testConcurrentTicketScanningRaceCondition() throws Exception {
        // Create a valid cryptographic token for ticket ID 9999
        String secret = com.cineflow.util.AppConfig.getTicketSecret();
        String testToken = TicketTokenCodec.generateToken(9999, 1, secret);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO tickets (id, booking_id, booking_seat_id, showtime_id, token, status, issued_at) " +
                     "VALUES (9999, 1, 1, 1, ?, 'VALID', NOW()) " +
                     "ON DUPLICATE KEY UPDATE token=VALUES(token), status='VALID', used_at=NULL, used_by_user_id=NULL")) {
            ps.setString(1, testToken);
            ps.executeUpdate();
        }

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        AtomicInteger admittedCount = new AtomicInteger(0);
        AtomicInteger alreadyUsedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int scannerAdminId = 1;
            executor.submit(() -> {
                try {
                    barrier.await(); // Synchronize thread starting line
                    TicketService.EntryValidationResult res = ticketService.validateAndAdmit(testToken, scannerAdminId);
                    if (res.result() == ScanResult.ADMITTED) {
                        admittedCount.incrementAndGet();
                    } else if (res.result() == ScanResult.ALREADY_USED) {
                        alreadyUsedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent redemption must terminate promptly");
        executor.shutdown();

        // Strict assertion: exactly one scanner admitted the guest, the other was rejected
        assertEquals(1, admittedCount.get(), "Exactly one scan must be ADMITTED");
        assertEquals(1, alreadyUsedCount.get(), "Concurrent second scan must be rejected as ALREADY_USED");

        // Clean up test ticket and scan log
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tickets WHERE id = 9999")) {
            ps.executeUpdate();
        }
    }
}
