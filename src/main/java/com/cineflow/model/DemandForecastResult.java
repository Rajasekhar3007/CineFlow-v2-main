package com.cineflow.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Output model for predicted demand and occupancy of an upcoming showtime.
 */
public class DemandForecastResult {
    private final int showtimeId;
    private final String movieTitle;
    private final String screenName;
    private final LocalDateTime startTime;
    private final int totalCapacity;
    private final int currentBookings;
    private final double predictedOccupancyPct;
    private final int expectedTotalBookings;
    private final DemandCategory demandCategory;
    private final double confidencePct;
    private final String explanation;

    private static final DateTimeFormatter DOW_TIME_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM • HH:mm");

    public DemandForecastResult(int showtimeId, String movieTitle, String screenName,
                                LocalDateTime startTime, int totalCapacity, int currentBookings,
                                double predictedOccupancyPct, int expectedTotalBookings,
                                DemandCategory demandCategory, double confidencePct,
                                String explanation) {
        this.showtimeId = showtimeId;
        this.movieTitle = movieTitle;
        this.screenName = screenName;
        this.startTime = startTime;
        this.totalCapacity = totalCapacity;
        this.currentBookings = currentBookings;
        this.predictedOccupancyPct = predictedOccupancyPct;
        this.expectedTotalBookings = expectedTotalBookings;
        this.demandCategory = demandCategory;
        this.confidencePct = confidencePct;
        this.explanation = explanation;
    }

    public int getShowtimeId() { return showtimeId; }
    public String getMovieTitle() { return movieTitle; }
    public String getScreenName() { return screenName; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getCurrentBookings() { return currentBookings; }
    public double getPredictedOccupancyPct() { return predictedOccupancyPct; }
    public int getExpectedTotalBookings() { return expectedTotalBookings; }
    public DemandCategory getDemandCategory() { return demandCategory; }
    public double getConfidencePct() { return confidencePct; }
    public String getExplanation() { return explanation; }

    public String getFormattedStartTime() {
        return startTime != null ? startTime.format(DOW_TIME_FMT) : "";
    }

    public String getFormattedOccupancy() {
        return String.format("%.1f%%", predictedOccupancyPct);
    }

    public String getFormattedConfidence() {
        return String.format("%.0f%%", confidencePct);
    }
}
