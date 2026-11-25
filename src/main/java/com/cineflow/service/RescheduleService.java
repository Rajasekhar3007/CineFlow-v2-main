package com.cineflow.service;

import com.cineflow.dao.BookingDAO;
import com.cineflow.dao.ShowtimeDAO;
import com.cineflow.model.Booking;
import com.cineflow.model.BookingStatus;
import com.cineflow.model.PaymentMethod;
import com.cineflow.model.Showtime;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing smart ticket rescheduling operations and policies.
 */
public class RescheduleService {

    private final BookingDAO bookingDAO;
    private final ShowtimeDAO showtimeDAO;

    public RescheduleService() {
        this.bookingDAO = new BookingDAO();
        this.showtimeDAO = new ShowtimeDAO();
    }

    public RescheduleService(BookingDAO bookingDAO, ShowtimeDAO showtimeDAO) {
        this.bookingDAO = bookingDAO;
        this.showtimeDAO = showtimeDAO;
    }

    /**
     * Validates whether a booking is eligible for smart rescheduling.
     */
    public void validateEligibility(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be null.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED bookings can be rescheduled.");
        }
        if (booking.getRescheduleCount() >= 2) {
            throw new IllegalStateException("This booking has already reached the maximum limit of 2 reschedules.");
        }
        if (booking.getShowtimeStart() == null) {
            throw new IllegalStateException("Showtime start time is unavailable.");
        }
        LocalDateTime cutoffTime = LocalDateTime.now().plusHours(2);
        if (booking.getShowtimeStart().isBefore(cutoffTime)) {
            throw new IllegalStateException("Rescheduling is only permitted at least 2 hours prior to the showtime start.");
        }
    }

    /**
     * Retrieves future alternative showtimes for the same movie, excluding the current showtime.
     */
    public List<Showtime> getAlternativeShowtimes(int movieId, int currentShowtimeId) throws SQLException {
        List<Showtime> allUpcoming = showtimeDAO.findByMovieId(movieId);
        List<Showtime> alternatives = new ArrayList<>();
        for (Showtime st : allUpcoming) {
            if (st.getId() != currentShowtimeId && st.getStartTime().isAfter(LocalDateTime.now())) {
                alternatives.add(st);
            }
        }
        return alternatives;
    }

    /**
     * Executes the ACID ticket rescheduling transaction.
     */
    public boolean rescheduleBooking(
            int bookingId,
            int userId,
            int newShowtimeId,
            List<Integer> newSeatIds,
            List<BigDecimal> newSeatPrices,
            BigDecimal fareDifference,
            PaymentMethod diffPaymentMethod) throws SQLException {

        return bookingDAO.executeRescheduleTransaction(
                bookingId, userId, newShowtimeId, newSeatIds,
                newSeatPrices, fareDifference, diffPaymentMethod);
    }
}
