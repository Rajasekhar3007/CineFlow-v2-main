package com.cineflow.service;

import com.cineflow.dao.BookingDAO;
import com.cineflow.dao.SeatHoldDAO;
import com.cineflow.model.Booking;
import com.cineflow.model.BookingStatus;
import com.cineflow.model.Payment;
import com.cineflow.model.PaymentStatus;
import com.cineflow.model.SeatType;
import com.cineflow.util.AppConfig;

import java.math.BigDecimal;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing temporary seat holds, ACID booking transactions,
 * and customer ticket cancellations.
 */
public class BookingService {

    private final BookingDAO bookingDAO;
    private final SeatHoldDAO seatHoldDAO;

    public BookingService() {
        this.bookingDAO = new BookingDAO();
        this.seatHoldDAO = new SeatHoldDAO();
    }

    public BookingService(BookingDAO bookingDAO, SeatHoldDAO seatHoldDAO) {
        this.bookingDAO = bookingDAO;
        this.seatHoldDAO = seatHoldDAO;
    }

    /**
     * Reserves temporary 5-minute holds for selected seats.
     */
    public void holdSeats(int showtimeId, List<Integer> seatIds, int userId) throws ValidationException, SQLException {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new ValidationException("Please select at least one seat.");
        }

        int holdMinutes = AppConfig.getHoldMinutes();
        LocalDateTime heldUntil = LocalDateTime.now().plusMinutes(holdMinutes);

        // Check if any seat is already booked or held by another user
        List<Integer> bookedSeatIds = bookingDAO.getBookedSeatIdsForShowtime(showtimeId);
        for (int seatId : seatIds) {
            if (bookedSeatIds.contains(seatId)) {
                throw new ValidationException("One or more selected seats have already been booked.");
            }
            if (seatHoldDAO.isSeatHeldByOther(showtimeId, seatId, userId)) {
                throw new ValidationException("One or more selected seats are currently on hold by another customer.");
            }
        }

        // Apply holds
        for (int seatId : seatIds) {
            seatHoldDAO.createHold(showtimeId, seatId, userId, heldUntil);
        }

        // Lock pricing quotes for checkout duration
        if (userId > 0) {
            try {
                PricingService pricingService = new PricingService();
                pricingService.calculateAndLockQuote(userId, showtimeId, SeatType.REGULAR);
                pricingService.calculateAndLockQuote(userId, showtimeId, SeatType.PREMIUM);
            } catch (Exception e) {
                System.err.println("[BookingService] Could not pre-lock price quotes: " + e.getMessage());
            }
        }
    }

    /**
     * Releases active holds for a specific user and showtime (e.g. upon checkout cancellation).
     */
    public void releaseUserHolds(int showtimeId, int userId) {
        try {
            seatHoldDAO.deleteHoldsForUser(showtimeId, userId);
        } catch (SQLException e) {
            System.err.println("Could not release user holds: " + e.getMessage());
        }
    }

    /**
     * Executes the comprehensive JDBC booking transaction with SELECT ... FOR UPDATE.
     */
    public boolean confirmBooking(Booking booking, List<Integer> seatIds,
                                  List<BigDecimal> seatPrices, Payment payment) throws SQLException {
        return bookingDAO.executeBookingTransaction(booking, seatIds, seatPrices, payment);
    }

    /**
     * Cancels a booking if the showtime has not yet started.
     * Automatically triggers the FIFO Waitlist queue for any freed seats.
     */
    public boolean cancelBooking(int bookingId, int userId) throws SQLException {
        int showtimeId = -1;
        try {
            java.util.Optional<Booking> opt = bookingDAO.findById(bookingId);
            if (opt.isPresent()) {
                showtimeId = opt.get().getShowtimeId();
            }
        } catch (Exception ignored) {}

        boolean ok = bookingDAO.cancelBookingTransaction(bookingId, userId);
        if (ok && showtimeId > 0) {
            final int stId = showtimeId;
            com.cineflow.ui.common.AsyncUi.run(() -> {
                WaitlistService.getInstance().processQueueForShowtime(stId);
                return null;
            }, res -> {}, err -> {});
        }
        return ok;
    }

    public List<Integer> getBookedSeats(int showtimeId) throws SQLException {
        return bookingDAO.getBookedSeatIdsForShowtime(showtimeId);
    }

    public void processPendingRefunds() throws SQLException, InterruptedException {
        RazorpayService razorpay = new RazorpayService();
        for (BookingDAO.PendingRefund pending : bookingDAO.findDueRefunds()) {
            if (!bookingDAO.claimDueRefund(pending.paymentId())) {
                continue;
            }
            try {
                RazorpayService.Refund refund = razorpay.refundPayment(
                        pending.transactionId(), pending.gatewayRefundId());
                PaymentStatus status = switch (refund.status()) {
                    case "processed" -> PaymentStatus.REFUNDED;
                    case "failed" -> PaymentStatus.REFUND_FAILED;
                    default -> PaymentStatus.REFUND_PENDING;
                };
                bookingDAO.updateGatewayRefund(pending.paymentId(), refund.id(), status);
            } catch (IOException e) {
                // A request that timed out may still have reached Razorpay. Retry with its original key.
                System.err.println("[BookingService] Refund payment " + pending.paymentId() + ": " + e.getMessage());
            }
        }
    }

    public List<Integer> getHeldSeatsByOthers(int showtimeId, int currentUserId) throws SQLException {
        return seatHoldDAO.getActiveHeldSeatIds(showtimeId, currentUserId);
    }

    public List<Booking> getCustomerBookings(int userId) throws SQLException {
        return bookingDAO.findByUserId(userId);
    }

    public List<Booking> getFilteredAdminBookings(LocalDate start, LocalDate end,
                                                 Integer movieId, BookingStatus status, String username) throws SQLException {
        return bookingDAO.findAllJoined(start, end, movieId, status, username);
    }
}

