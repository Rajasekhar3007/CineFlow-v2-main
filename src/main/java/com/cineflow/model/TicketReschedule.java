package com.cineflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit record representing a smart ticket rescheduling event.
 */
public class TicketReschedule {
    private int id;
    private int bookingId;
    private int oldShowtimeId;
    private int newShowtimeId;
    private String oldSeats;
    private String newSeats;
    private BigDecimal fareDifference;
    private LocalDateTime rescheduledAt;

    public TicketReschedule() {}

    public TicketReschedule(int bookingId, int oldShowtimeId, int newShowtimeId,
                            String oldSeats, String newSeats, BigDecimal fareDifference) {
        this.bookingId = bookingId;
        this.oldShowtimeId = oldShowtimeId;
        this.newShowtimeId = newShowtimeId;
        this.oldSeats = oldSeats;
        this.newSeats = newSeats;
        this.fareDifference = fareDifference;
        this.rescheduledAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public int getOldShowtimeId() { return oldShowtimeId; }
    public void setOldShowtimeId(int oldShowtimeId) { this.oldShowtimeId = oldShowtimeId; }

    public int getNewShowtimeId() { return newShowtimeId; }
    public void setNewShowtimeId(int newShowtimeId) { this.newShowtimeId = newShowtimeId; }

    public String getOldSeats() { return oldSeats; }
    public void setOldSeats(String oldSeats) { this.oldSeats = oldSeats; }

    public String getNewSeats() { return newSeats; }
    public void setNewSeats(String newSeats) { this.newSeats = newSeats; }

    public BigDecimal getFareDifference() { return fareDifference; }
    public void setFareDifference(BigDecimal fareDifference) { this.fareDifference = fareDifference; }

    public LocalDateTime getRescheduledAt() { return rescheduledAt; }
    public void setRescheduledAt(LocalDateTime rescheduledAt) { this.rescheduledAt = rescheduledAt; }
}
