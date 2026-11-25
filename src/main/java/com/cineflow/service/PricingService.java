package com.cineflow.service;

import com.cineflow.dao.*;
import com.cineflow.model.*;
import com.cineflow.util.AppConfig;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating dynamic price quotations, quote locking, and rule configuration.
 */
public class PricingService {

    private final PricingRuleDAO ruleDAO;
    private final PriceQuoteDAO quoteDAO;
    private final ShowtimeDAO showtimeDAO;
    private final ScreenDAO screenDAO;
    private final BookingDAO bookingDAO;
    private final SeatHoldDAO seatHoldDAO;

    public PricingService() {
        this.ruleDAO = new PricingRuleDAO();
        this.quoteDAO = new PriceQuoteDAO();
        this.showtimeDAO = new ShowtimeDAO();
        this.screenDAO = new ScreenDAO();
        this.bookingDAO = new BookingDAO();
        this.seatHoldDAO = new SeatHoldDAO();
    }

    public PricingService(PricingRuleDAO ruleDAO, PriceQuoteDAO quoteDAO, ShowtimeDAO showtimeDAO,
                          ScreenDAO screenDAO, BookingDAO bookingDAO, SeatHoldDAO seatHoldDAO) {
        this.ruleDAO = ruleDAO;
        this.quoteDAO = quoteDAO;
        this.showtimeDAO = showtimeDAO;
        this.screenDAO = screenDAO;
        this.bookingDAO = bookingDAO;
        this.seatHoldDAO = seatHoldDAO;
    }

    /**
     * Computes and persists a time-locked quote for a given user, showtime, and seat category.
     */
    public PriceQuote calculateAndLockQuote(int userId, int showtimeId, SeatType seatType) throws SQLException {
        Optional<Showtime> stOpt = showtimeDAO.findById(showtimeId);
        if (stOpt.isEmpty()) {
            throw new IllegalArgumentException("Showtime not found: " + showtimeId);
        }
        Showtime showtime = stOpt.get();

        int screenId = showtime.getScreenId();
        Optional<Screen> screenOpt = screenDAO.findById(screenId);
        int totalSeats = screenOpt.map(Screen::getTotalSeats).orElse(50);

        int bookedSeats = bookingDAO.getBookedSeatIdsForShowtime(showtimeId).size();
        int heldSeats = seatHoldDAO.getActiveHeldSeatIds(showtimeId, userId).size();
        int totalOccupied = bookedSeats + heldSeats;

        BigDecimal basePrice = seatType == SeatType.PREMIUM
                ? showtime.getPricePremium()
                : showtime.getPriceRegular();

        List<PricingRule> rules = ruleDAO.getActiveRules();
        LocalDateTime now = LocalDateTime.now();

        PriceQuote quote = PricingEngine.quote(
                basePrice, seatType, showtime.getStartTime(), now, totalSeats, totalOccupied,
                rules, AppConfig.getPricingFloor(), AppConfig.getPricingCap()
        );

        quote.setUserId(userId);
        quote.setShowtimeId(showtimeId);
        quote.setExpiresAt(now.plusMinutes(AppConfig.getHoldMinutes()));

        if (userId > 0) {
            quoteDAO.saveQuote(quote);
        }

        return quote;
    }

    /**
     * Fetches current quote for display without saving (e.g. for catalog / showtime cards).
     */
    public PriceQuote getPreviewQuote(Showtime showtime, SeatType seatType) throws SQLException {
        int showtimeId = showtime.getId();
        Optional<Screen> screenOpt = screenDAO.findById(showtime.getScreenId());
        int totalSeats = screenOpt.map(Screen::getTotalSeats).orElse(50);

        int bookedSeats = bookingDAO.getBookedSeatIdsForShowtime(showtimeId).size();
        int heldSeats = seatHoldDAO.getActiveHeldSeatIds(showtimeId, -1).size();
        int totalOccupied = bookedSeats + heldSeats;

        BigDecimal basePrice = seatType == SeatType.PREMIUM
                ? showtime.getPricePremium()
                : showtime.getPriceRegular();

        List<PricingRule> rules = ruleDAO.getActiveRules();
        LocalDateTime now = LocalDateTime.now();

        return PricingEngine.quote(
                basePrice, seatType, showtime.getStartTime(), now, totalSeats, totalOccupied,
                rules, AppConfig.getPricingFloor(), AppConfig.getPricingCap()
        );
    }

    /**
     * Checks if a user has an active, valid locked quote; recalculates if expired.
     */
    public PriceQuote getOrRecalculateQuote(int userId, int showtimeId, SeatType seatType) throws SQLException {
        Optional<PriceQuote> existing = quoteDAO.getActiveQuote(userId, showtimeId, seatType);
        if (existing.isPresent() && !existing.get().isExpired()) {
            return existing.get();
        }
        return calculateAndLockQuote(userId, showtimeId, seatType);
    }

    public List<PricingRule> getAllRules() throws SQLException {
        return ruleDAO.getAllRules();
    }

    public boolean saveRule(PricingRule rule) throws SQLException {
        if (rule.getId() > 0) {
            return ruleDAO.updateRule(rule);
        }
        return ruleDAO.createRule(rule);
    }

    public boolean toggleRuleActive(int id, boolean active) throws SQLException {
        return ruleDAO.toggleActive(id, active);
    }

    public boolean deleteRule(int id) throws SQLException {
        return ruleDAO.deleteRule(id);
    }
}
