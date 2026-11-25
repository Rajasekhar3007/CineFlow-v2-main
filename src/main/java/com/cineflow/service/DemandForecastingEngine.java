package com.cineflow.service;

import com.cineflow.model.DemandCategory;
import com.cineflow.model.DemandForecastResult;
import com.cineflow.model.HistoricalShowtimeData;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure deterministic, explainable demand forecasting engine.
 * Computes predicted occupancy, expected bookings, demand category,
 * confidence intervals, and explainability tags without database dependencies.
 */
public class DemandForecastingEngine {

    /**
     * Forecasts demand for an upcoming showtime based on historical attributes,
     * day of the week, screening hour, movie rating, and current booking velocity.
     */
    public static DemandForecastResult forecast(
            int showtimeId,
            String movieTitle,
            String genre,
            double rating,
            String screenName,
            LocalDateTime startTime,
            int totalCapacity,
            int currentBookings,
            LocalDateTime predictionTime,
            List<HistoricalShowtimeData> history) {

        if (totalCapacity <= 0) totalCapacity = 100;
        if (predictionTime == null) predictionTime = LocalDateTime.now();

        // 1. Base Genre Baseline Occupancy (default 52% if no history)
        double genreBaseline = 0.52;
        int genreSamples = 0;
        if (history != null && !history.isEmpty()) {
            double genreSum = 0;
            for (HistoricalShowtimeData h : history) {
                if (genre != null && genre.equalsIgnoreCase(h.getGenre())) {
                    genreSum += h.getOccupancyRatio();
                    genreSamples++;
                }
            }
            if (genreSamples > 0) {
                genreBaseline = genreSum / genreSamples;
            } else {
                // Overall general average across all history
                double totalSum = 0;
                for (HistoricalShowtimeData h : history) {
                    totalSum += h.getOccupancyRatio();
                }
                genreBaseline = totalSum / history.size();
            }
        }

        // 2. Day of Week Multiplier (Weekend surges)
        DayOfWeek dow = startTime.getDayOfWeek();
        double dowMultiplier = switch (dow) {
            case FRIDAY -> 1.25;
            case SATURDAY -> 1.38;
            case SUNDAY -> 1.32;
            case THURSDAY -> 1.05;
            default -> 0.88; // Mon, Tue, Wed
        };

        // 3. Time of Day Multiplier (Prime evening slots)
        int hour = startTime.getHour();
        double timeMultiplier;
        if (hour >= 18 && hour <= 21) {
            timeMultiplier = 1.28; // Prime evening
        } else if (hour >= 14 && hour < 18) {
            timeMultiplier = 1.05; // Afternoon matinee
        } else if (hour >= 22) {
            timeMultiplier = 0.95; // Late night
        } else {
            timeMultiplier = 0.80; // Morning / early afternoon
        }

        // 4. Rating Multiplier (Critical reception)
        double ratingMultiplier = 1.0;
        if (rating >= 8.5) {
            ratingMultiplier = 1.15;
        } else if (rating >= 7.5) {
            ratingMultiplier = 1.06;
        } else if (rating < 6.0 && rating > 0.0) {
            ratingMultiplier = 0.85;
        }

        // 5. Booking Velocity Multiplier (momentum)
        long hoursUntilShow = Duration.between(predictionTime, startTime).toHours();
        double velocityMultiplier = 1.0;
        double currentRatio = (double) currentBookings / totalCapacity;
        if (hoursUntilShow > 0) {
            double daysLeft = Math.max(0.5, hoursUntilShow / 24.0);
            double dailyVelocity = currentRatio / (7.0 - Math.min(6.5, daysLeft));
            if (dailyVelocity > 0.08) {
                velocityMultiplier = 1.20;
            } else if (dailyVelocity > 0.04) {
                velocityMultiplier = 1.10;
            }
        }

        // 6. Combined Prediction Calculation
        double rawOccupancy = genreBaseline * dowMultiplier * timeMultiplier * ratingMultiplier * velocityMultiplier;

        // Current actual confirmed bookings form the absolute minimum floor
        double currentOccupancy = (double) currentBookings / totalCapacity;
        double predictedOccupancy = Math.max(currentOccupancy, Math.min(0.98, rawOccupancy));
        double predictedPct = Math.round(predictedOccupancy * 1000.0) / 10.0;

        int expectedBookings = (int) Math.round(predictedOccupancy * totalCapacity);
        expectedBookings = Math.max(currentBookings, Math.min(totalCapacity, expectedBookings));

        // 7. Demand Categorization
        DemandCategory category;
        if (predictedPct >= 75.0) {
            category = DemandCategory.HIGH;
        } else if (predictedPct >= 40.0) {
            category = DemandCategory.MEDIUM;
        } else {
            category = DemandCategory.LOW;
        }

        // 8. Confidence Score based on historical sample size
        int sampleCount = history != null ? history.size() : 0;
        double confidence = Math.min(96.0, 50.0 + Math.min(46.0, sampleCount * 3.0 + genreSamples * 2.0));

        // 9. Structured Human-Readable Explanation
        StringBuilder expl = new StringBuilder();
        String dayName = dow.name().substring(0, 1) + dow.name().substring(1).toLowerCase();
        expl.append(dayName).append(" ").append(String.format("%02d:%02d", startTime.getHour(), startTime.getMinute()));
        if (dowMultiplier > 1.1) expl.append(" weekend surge");
        if (timeMultiplier > 1.1) expl.append(" (prime slot)");
        if (ratingMultiplier > 1.0) expl.append(" + high ").append(rating).append("★ rating");
        if (velocityMultiplier > 1.0) expl.append(" + strong booking velocity");
        expl.append(" (").append((int) Math.round(confidence)).append("% confidence)");

        return new DemandForecastResult(
            showtimeId, movieTitle, screenName, startTime, totalCapacity,
            currentBookings, predictedPct, expectedBookings, category, confidence, expl.toString()
        );
    }

    /**
     * Computes the Mean Absolute Error (MAE) between historical predictions and actual observed values.
     */
    public static double computeMAE(List<Double> actuals, List<Double> predicted) {
        if (actuals == null || predicted == null || actuals.isEmpty() || actuals.size() != predicted.size()) {
            return 0.0;
        }
        double sumError = 0.0;
        for (int i = 0; i < actuals.size(); i++) {
            sumError += Math.abs(actuals.get(i) - predicted.get(i));
        }
        return Math.round((sumError / actuals.size()) * 10.0) / 10.0;
    }
}
