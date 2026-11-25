package com.cineflow.service;

import com.cineflow.dao.EntryScanDAO;
import com.cineflow.dao.TicketDAO;
import com.cineflow.model.EntryScan;
import com.cineflow.model.ScanResult;
import com.cineflow.model.Ticket;
import com.cineflow.model.TicketStatus;
import com.cineflow.util.AppConfig;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing ticket verification, atomic gate admissions, and entry audit logs.
 */
public class TicketService {

    private final TicketDAO ticketDAO;
    private final EntryScanDAO scanDAO;

    public TicketService() {
        this.ticketDAO = new TicketDAO();
        this.scanDAO = new EntryScanDAO();
    }

    public TicketService(TicketDAO ticketDAO, EntryScanDAO scanDAO) {
        this.ticketDAO = ticketDAO;
        this.scanDAO = scanDAO;
    }

    public List<Ticket> getTicketsForBooking(int bookingId) throws SQLException {
        return ticketDAO.findByBookingId(bookingId);
    }

    /**
     * Validates a ticket presentation at the Entry Gate.
     * Guaranteed atomic admission: two concurrent scans of the same ticket admit exactly once.
     */
    public EntryValidationResult validateAndAdmit(String rawToken, int adminUserId) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return new EntryValidationResult(ScanResult.INVALID, null, "Empty ticket token presented.");
        }
        String token = rawToken.trim();
        String tokenHash = TicketTokenCodec.hashToken(token);
        LocalDateTime now = LocalDateTime.now();

        // 1. Signature format pre-check
        int ticketId = TicketTokenCodec.extractTicketId(token);
        if (ticketId <= 0) {
            recordScan(null, tokenHash, ScanResult.INVALID, adminUserId);
            return new EntryValidationResult(ScanResult.INVALID, null, "Unrecognized or malformed ticket format.");
        }

        try {
            // 2. Perform atomic gate admission update: VALID -> USED
            boolean admitted = ticketDAO.atomicValidateAndAdmit(token, adminUserId);

            if (admitted) {
                Optional<Ticket> ticketOpt = ticketDAO.findByToken(token);
                Ticket ticket = ticketOpt.orElse(null);
                Integer tId = ticket != null ? ticket.getId() : ticketId;

                recordScan(tId, tokenHash, ScanResult.ADMITTED, adminUserId);
                return new EntryValidationResult(ScanResult.ADMITTED, ticket, "Access granted! Seat: " +
                        (ticket != null ? ticket.getSeatCode() : ""));
            }

            // 3. Admission failed — Inspect database to determine specific rejection cause
            Optional<Ticket> ticketOpt = ticketDAO.findByToken(token);
            if (ticketOpt.isEmpty()) {
                recordScan(null, tokenHash, ScanResult.INVALID, adminUserId);
                return new EntryValidationResult(ScanResult.INVALID, null, "Ticket not found in system or forged token.");
            }

            Ticket ticket = ticketOpt.get();

            // Cryptographic signature check with ticket's actual showtime
            boolean sigValid = TicketTokenCodec.verifySignature(token, ticket.getShowtimeId(), AppConfig.getTicketSecret());
            if (!sigValid) {
                recordScan(ticket.getId(), tokenHash, ScanResult.INVALID, adminUserId);
                return new EntryValidationResult(ScanResult.INVALID, ticket, "Tampered cryptographic signature.");
            }

            if (ticket.getStatus() == TicketStatus.USED) {
                recordScan(ticket.getId(), tokenHash, ScanResult.ALREADY_USED, adminUserId);
                String usedTimeStr = ticket.getUsedAt() != null ? ticket.getUsedAt().toString().replace("T", " ") : "earlier";
                return new EntryValidationResult(ScanResult.ALREADY_USED, ticket,
                        "Ticket was already redeemed on " + usedTimeStr);
            }

            if (ticket.getStatus() == TicketStatus.CANCELLED) {
                recordScan(ticket.getId(), tokenHash, ScanResult.CANCELLED, adminUserId);
                return new EntryValidationResult(ScanResult.CANCELLED, ticket,
                        "This reservation was cancelled and refunded.");
            }

            // Check entry window: [start - beforeMin, start + afterMin]
            LocalDateTime start = ticket.getShowtimeStart();
            int beforeMin = AppConfig.getEntryWindowBeforeMinutes();
            int afterMin = AppConfig.getEntryWindowAfterMinutes();

            if (start != null) {
                if (now.isBefore(start.minusMinutes(beforeMin))) {
                    recordScan(ticket.getId(), tokenHash, ScanResult.OUTSIDE_WINDOW, adminUserId);
                    return new EntryValidationResult(ScanResult.OUTSIDE_WINDOW, ticket,
                            "Screening has not opened yet. Entry begins 60 min before start.");
                }
                if (now.isAfter(start.plusMinutes(afterMin))) {
                    recordScan(ticket.getId(), tokenHash, ScanResult.OUTSIDE_WINDOW, adminUserId);
                    return new EntryValidationResult(ScanResult.OUTSIDE_WINDOW, ticket,
                            "Entry window expired (closed 30 min after start).");
                }
            }

            recordScan(ticket.getId(), tokenHash, ScanResult.INVALID, adminUserId);
            return new EntryValidationResult(ScanResult.INVALID, ticket, "Admission denied.");

        } catch (SQLException e) {
            System.err.println("[TicketService] Database scan error: " + e.getMessage());
            return new EntryValidationResult(ScanResult.INVALID, null, "Database error: " + e.getMessage());
        }
    }

    public List<EntryScan> getRecentScans(int limit) throws SQLException {
        return scanDAO.getRecentScans(limit);
    }

    private void recordScan(Integer ticketId, String tokenHash, ScanResult result, int adminUserId) {
        try {
            scanDAO.recordScan(new EntryScan(0, ticketId, tokenHash, result, LocalDateTime.now(), adminUserId));
        } catch (SQLException e) {
            System.err.println("[TicketService] Failed to record audit scan: " + e.getMessage());
        }
    }

    public record EntryValidationResult(ScanResult result, Ticket ticket, String message) {}
}
