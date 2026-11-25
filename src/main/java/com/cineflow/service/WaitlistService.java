package com.cineflow.service;

import com.cineflow.dao.*;
import com.cineflow.model.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CineFlow v2 Waitlist Service.
 * Manages FIFO queueing for sold-out showtimes, automated seat offers,
 * offer expiry sweeping, and transactional claim / decline actions.
 */
public class WaitlistService {

    private static final WaitlistService INSTANCE = new WaitlistService();

    private final WaitlistDAO waitlistDAO;
    private final NotificationService notificationService;
    private final SeatHoldDAO seatHoldDAO;
    private final SeatAllocationService seatAllocationService;
    private final ShowtimeDAO showtimeDAO;

    public WaitlistService() {
        this.waitlistDAO = new WaitlistDAO();
        this.notificationService = NotificationService.getInstance();
        this.seatHoldDAO = new SeatHoldDAO();
        this.seatAllocationService = new SeatAllocationService();
        this.showtimeDAO = new ShowtimeDAO();
    }

    public WaitlistService(WaitlistDAO waitlistDAO, NotificationService notificationService,
                           SeatHoldDAO seatHoldDAO, SeatAllocationService seatAllocationService,
                           ShowtimeDAO showtimeDAO) {
        this.waitlistDAO = waitlistDAO;
        this.notificationService = notificationService;
        this.seatHoldDAO = seatHoldDAO;
        this.seatAllocationService = seatAllocationService;
        this.showtimeDAO = showtimeDAO;
    }

    public static WaitlistService getInstance() {
        return INSTANCE;
    }

    /**
     * Enqueues a user onto the waitlist for a specific showtime.
     */
    public synchronized WaitlistEntry joinWaitlist(int userId, int showtimeId, int seatsWanted,
                                                  SeatPreference preference) throws SQLException {
        Optional<WaitlistEntry> existing = waitlistDAO.getActiveByShowtimeAndUser(showtimeId, userId);
        if (existing.isPresent()) {
            throw new IllegalStateException("You already have an active waitlist entry for this showtime.");
        }

        WaitlistEntry entry = new WaitlistEntry(userId, showtimeId, seatsWanted, preference);
        int entryId = waitlistDAO.createEntry(entry);
        entry.setId(entryId);

        // Notify user of waitlist placement
        notificationService.sendNotification(
                userId,
                NotificationType.SYSTEM,
                "Waitlist Confirmed",
                "You have joined the waitlist for " + seatsWanted + " seat(s). You will be notified the instant seats become free.",
                "WAITLIST",
                entryId
        );

        // Immediately check if seats happen to be available right now
        processQueueForShowtime(showtimeId);

        return waitlistDAO.findById(entryId).orElse(entry);
    }

    /**
     * Cancels an active waitlist entry.
     */
    public synchronized boolean leaveWaitlist(int waitlistId, int userId) throws SQLException {
        Optional<WaitlistEntry> opt = waitlistDAO.findById(waitlistId);
        if (opt.isEmpty()) return false;

        WaitlistEntry entry = opt.get();
        if (entry.getUserId() != userId) {
            throw new SecurityException("Unauthorized access to waitlist entry.");
        }

        if (entry.getStatus() == WaitlistStatus.WAITING || entry.getStatus() == WaitlistStatus.OFFERED) {
            waitlistDAO.updateStatus(waitlistId, WaitlistStatus.CANCELLED);
            if (entry.getStatus() == WaitlistStatus.OFFERED) {
                seatHoldDAO.deleteHoldsForUser(entry.getShowtimeId(), userId);
                processQueueForShowtime(entry.getShowtimeId());
            }
            return true;
        }
        return false;
    }

    /**
     * Evaluates the FIFO waitlist queue for a showtime and allocates free seats to the first eligible requester.
     */
    public synchronized void processQueueForShowtime(int showtimeId) {
        try {
            List<WaitlistEntry> waiting = waitlistDAO.findWaitingByShowtime(showtimeId);
            if (waiting.isEmpty()) return;

            for (WaitlistEntry entry : waiting) {
                List<SeatSuggestion> suggestions = seatAllocationService.findBestSeats(
                        showtimeId,
                        entry.getSeatsWanted(),
                        entry.getSeatPreference(),
                        entry.getUserId()
                );

                if (!suggestions.isEmpty()) {
                    SeatSuggestion best = suggestions.get(0);
                    List<Seat> seats = best.getSeats();

                    // Offer valid for 10 minutes
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime expiresAt = now.plusMinutes(10);

                    // Place holds for this customer
                    for (Seat s : seats) {
                        seatHoldDAO.createHold(showtimeId, s.getId(), entry.getUserId(), expiresAt);
                    }

                    // Mark entry as OFFERED
                    waitlistDAO.setOffered(entry.getId(), now, expiresAt);

                    // Send notification
                    notificationService.sendNotification(
                            entry.getUserId(),
                            NotificationType.WAITLIST_OFFER,
                            "🎟 Seats Available for You!",
                            "Great news! " + seats.size() + " seat(s) have opened up and are reserved for you for 10 minutes. Click to claim your booking!",
                            "WAITLIST",
                            entry.getId()
                    );
                    break; // Process one offer at a time per showtime
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing waitlist queue for showtime " + showtimeId + ": " + e.getMessage());
        }
    }

    /**
     * Scans for expired seat offers, frees holds, marks entries EXPIRED, and promotes the next in line.
     */
    public synchronized void expireOffers() {
        try {
            List<WaitlistEntry> expired = waitlistDAO.findExpiredOffers();
            for (WaitlistEntry entry : expired) {
                waitlistDAO.updateStatus(entry.getId(), WaitlistStatus.EXPIRED);
                seatHoldDAO.deleteHoldsForUser(entry.getShowtimeId(), entry.getUserId());

                notificationService.sendNotification(
                        entry.getUserId(),
                        NotificationType.OFFER_EXPIRED,
                        "Waitlist Offer Expired",
                        "Your seat offer has expired and has been offered to the next person in line.",
                        "WAITLIST",
                        entry.getId()
                );

                // Offer freed seats to next waiting person
                processQueueForShowtime(entry.getShowtimeId());
            }
        } catch (Exception e) {
            System.err.println("Error expiring waitlist offers: " + e.getMessage());
        }
    }

    /**
     * Sweeps all showtimes with active waiting lists.
     */
    public synchronized void processAllWaitingQueues() {
        try {
            List<Integer> showtimeIds = waitlistDAO.findActiveShowtimeIds();
            for (int id : showtimeIds) {
                processQueueForShowtime(id);
            }
        } catch (Exception e) {
            System.err.println("Error sweeping waiting queues: " + e.getMessage());
        }
    }

    /**
     * Customer claims an active offer, transitioning into checkout.
     */
    public synchronized boolean claimOffer(int waitlistId, int userId) throws SQLException {
        Optional<WaitlistEntry> opt = waitlistDAO.findById(waitlistId);
        if (opt.isEmpty()) return false;

        WaitlistEntry entry = opt.get();
        if (entry.getUserId() != userId) {
            throw new SecurityException("Unauthorized claim attempt.");
        }

        if (entry.getStatus() != WaitlistStatus.OFFERED) {
            return false;
        }

        if (entry.getOfferExpiresAt() != null && entry.getOfferExpiresAt().isBefore(LocalDateTime.now())) {
            expireOffers();
            return false;
        }

        return waitlistDAO.updateStatus(waitlistId, WaitlistStatus.CLAIMED);
    }

    /**
     * Customer declines an active offer, immediately offering to the next user in line.
     */
    public synchronized boolean declineOffer(int waitlistId, int userId) throws SQLException {
        Optional<WaitlistEntry> opt = waitlistDAO.findById(waitlistId);
        if (opt.isEmpty()) return false;

        WaitlistEntry entry = opt.get();
        if (entry.getUserId() != userId) {
            throw new SecurityException("Unauthorized decline attempt.");
        }

        if (entry.getStatus() == WaitlistStatus.OFFERED) {
            waitlistDAO.updateStatus(waitlistId, WaitlistStatus.DECLINED);
            seatHoldDAO.deleteHoldsForUser(entry.getShowtimeId(), userId);
            processQueueForShowtime(entry.getShowtimeId());
            return true;
        }
        return false;
    }

    public List<WaitlistEntry> getCustomerWaitlist(int userId) throws SQLException {
        return waitlistDAO.findByUserId(userId);
    }

    public List<WaitlistEntry> getAllWaitlistEntries(String filter) throws SQLException {
        return waitlistDAO.findAll(filter);
    }
}
