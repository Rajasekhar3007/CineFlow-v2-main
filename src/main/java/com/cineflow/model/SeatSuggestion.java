package com.cineflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Encapsulates an AI-assisted ranked seat group recommendation produced by Smart Pick.
 */
public class SeatSuggestion {

    private final List<Seat> seats;
    private final double score;
    private final String reason;
    private final boolean contiguous;
    private final boolean splitGroup;
    private final boolean notEnoughSeats;

    public SeatSuggestion(List<Seat> seats, double score, String reason,
                          boolean contiguous, boolean splitGroup, boolean notEnoughSeats) {
        this.seats = seats != null ? new ArrayList<>(seats) : new ArrayList<>();
        this.score = score;
        this.reason = reason;
        this.contiguous = contiguous;
        this.splitGroup = splitGroup;
        this.notEnoughSeats = notEnoughSeats;
    }

    public static SeatSuggestion notEnoughSeats(String reason) {
        return new SeatSuggestion(Collections.emptyList(), 0.0, reason, false, false, true);
    }

    public List<Seat> getSeats() { return Collections.unmodifiableList(seats); }
    public double getScore() { return score; }
    public String getReason() { return reason; }
    public boolean isContiguous() { return contiguous; }
    public boolean isSplitGroup() { return splitGroup; }
    public boolean isNotEnoughSeats() { return notEnoughSeats; }

    public String getSeatCodesSummary() {
        if (seats.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(seats.get(i).getSeatCode());
        }
        return sb.toString();
    }
}
