package com.cineflow.service;

import com.cineflow.model.Booking;
import com.cineflow.model.BookingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class RescheduleServiceTest {

    @Test
    @DisplayName("Eligible Booking Passes Validation")
    void testEligibleBookingPasses() {
        RescheduleService service = new RescheduleService();
        Booking b = new Booking(1, 10, 20, LocalDateTime.now().minusDays(1),
                new BigDecimal("250.00"), BookingStatus.CONFIRMED);
        // Showtime starts tomorrow (well > 2 hours)
        b.setShowtimeStart(LocalDateTime.now().plusDays(1));
        b.setRescheduleCount(0);

        assertDoesNotThrow(() -> service.validateEligibility(b));
    }

    @Test
    @DisplayName("Cancelled Booking Rejected For Rescheduling")
    void testCancelledBookingRejected() {
        RescheduleService service = new RescheduleService();
        Booking b = new Booking(1, 10, 20, LocalDateTime.now().minusDays(1),
                new BigDecimal("250.00"), BookingStatus.CANCELLED);
        b.setShowtimeStart(LocalDateTime.now().plusDays(1));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.validateEligibility(b));
        assertTrue(ex.getMessage().contains("CONFIRMED"));
    }

    @Test
    @DisplayName("Booking With 2 Previous Reschedules Hits Limit")
    void testRescheduleLimitExceeded() {
        RescheduleService service = new RescheduleService();
        Booking b = new Booking(1, 10, 20, LocalDateTime.now().minusDays(1),
                new BigDecimal("250.00"), BookingStatus.CONFIRMED);
        b.setShowtimeStart(LocalDateTime.now().plusDays(1));
        b.setRescheduleCount(2);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.validateEligibility(b));
        assertTrue(ex.getMessage().contains("maximum limit"));
    }

    @Test
    @DisplayName("Booking Within 2 Hours of Showtime Rejected")
    void testWithinTwoHoursCutoffRejected() {
        RescheduleService service = new RescheduleService();
        Booking b = new Booking(1, 10, 20, LocalDateTime.now().minusDays(1),
                new BigDecimal("250.00"), BookingStatus.CONFIRMED);
        // Starts in 45 minutes
        b.setShowtimeStart(LocalDateTime.now().plusMinutes(45));
        b.setRescheduleCount(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.validateEligibility(b));
        assertTrue(ex.getMessage().contains("2 hours"));
    }
}
