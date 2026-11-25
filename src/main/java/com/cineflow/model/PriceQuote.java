package com.cineflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Domain entity capturing a locked price quotation for a seat type at a specific showtime.
 */
public class PriceQuote {

    private int id;
    private int userId;
    private int showtimeId;
    private SeatType seatType;
    private BigDecimal basePrice;
    private BigDecimal finalPrice;
    private String breakdownJson;
    private List<PriceBreakdownLine> breakdownLines = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public PriceQuote() {}

    public PriceQuote(int userId, int showtimeId, SeatType seatType, BigDecimal basePrice,
                      BigDecimal finalPrice, List<PriceBreakdownLine> breakdownLines,
                      LocalDateTime expiresAt) {
        this.userId = userId;
        this.showtimeId = showtimeId;
        this.seatType = seatType;
        this.basePrice = basePrice;
        this.finalPrice = finalPrice;
        this.breakdownLines = breakdownLines != null ? new ArrayList<>(breakdownLines) : new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getShowtimeId() { return showtimeId; }
    public void setShowtimeId(int showtimeId) { this.showtimeId = showtimeId; }

    public SeatType getSeatType() { return seatType; }
    public void setSeatType(SeatType seatType) { this.seatType = seatType; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public BigDecimal getFinalPrice() { return finalPrice; }
    public void setFinalPrice(BigDecimal finalPrice) { this.finalPrice = finalPrice; }

    public String getBreakdownJson() { return breakdownJson; }
    public void setBreakdownJson(String breakdownJson) { this.breakdownJson = breakdownJson; }

    public List<PriceBreakdownLine> getBreakdownLines() {
        return Collections.unmodifiableList(breakdownLines);
    }
    public void setBreakdownLines(List<PriceBreakdownLine> lines) {
        this.breakdownLines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
