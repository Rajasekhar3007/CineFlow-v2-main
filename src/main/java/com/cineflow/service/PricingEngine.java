package com.cineflow.service;

import com.cineflow.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CineFlow v2 Pure Rule-Based Dynamic Pricing Engine.
 * Fully explainable, testable in isolation without MySQL.
 */
public class PricingEngine {

    private static final BigDecimal MIN_TICKET_PRICE = new BigDecimal("50.00");
    private static final BigDecimal FIVE = new BigDecimal("5.00");

    /**
     * Computes a dynamic price quotation based on active rules, demand, and showtime schedule.
     */
    public static PriceQuote quote(BigDecimal basePrice, SeatType seatType, LocalDateTime showtimeStart,
                                   LocalDateTime now, int totalSeats, int soldAndHeldSeats,
                                   List<PricingRule> rules, BigDecimal floorPct, BigDecimal capPct) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Base price must be greater than zero.");
        }
        if (showtimeStart == null || now == null) {
            throw new IllegalArgumentException("Showtime and current timestamp must be non-null.");
        }
        if (floorPct == null) floorPct = new BigDecimal("-0.20");
        if (capPct == null) capPct = new BigDecimal("0.50");

        double occupancyPct = totalSeats > 0 ? (soldAndHeldSeats * 100.0) / totalSeats : 0.0;
        double hoursToShow = Duration.between(now, showtimeStart).toMinutes() / 60.0;
        int dayOfWeekVal = showtimeStart.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday
        int hourOfDay = showtimeStart.getHour();

        List<PriceBreakdownLine> breakdown = new ArrayList<>();
        BigDecimal totalAdjustmentPct = BigDecimal.ZERO;

        List<PricingRule> activeRules = rules != null ? rules.stream()
                .filter(PricingRule::isActive)
                .sorted(Comparator.comparingInt(PricingRule::getPriority))
                .toList() : List.of();

        for (PricingRule rule : activeRules) {
            boolean applies = false;
            String reason = "";

            switch (rule.getRuleType()) {
                case OCCUPANCY:
                    if (rule.getMinValue() != null && rule.getMaxValue() != null) {
                        double min = rule.getMinValue().doubleValue();
                        double max = rule.getMaxValue().doubleValue();
                        if (occupancyPct >= min && occupancyPct <= max) {
                            applies = true;
                            reason = String.format("Current auditorium occupancy is %.1f%% (%d/%d seats)",
                                    occupancyPct, soldAndHeldSeats, totalSeats);
                        }
                    }
                    break;

                case TIME_TO_SHOW:
                    if (rule.getMinValue() != null && rule.getMaxValue() != null) {
                        double min = rule.getMinValue().doubleValue();
                        double max = rule.getMaxValue().doubleValue();
                        if (hoursToShow >= min && hoursToShow <= max) {
                            // Check special rule: last-minute clearance only if low occupancy (< 40%)
                            if (rule.getAdjustmentPct().compareTo(BigDecimal.ZERO) < 0 && hoursToShow <= 3.0) {
                                if (occupancyPct < 40.0) {
                                    applies = true;
                                    reason = String.format("Showtime starts in %.1f hrs with low occupancy (%.1f%%)",
                                            hoursToShow, occupancyPct);
                                }
                            } else {
                                applies = true;
                                reason = String.format("Showtime starts in %.1f hours", hoursToShow);
                            }
                        }
                    }
                    break;

                case DAY_TYPE:
                    if (rule.getMinValue() != null && rule.getMaxValue() != null) {
                        int minDay = rule.getMinValue().intValue();
                        int maxDay = rule.getMaxValue().intValue();
                        if (dayOfWeekVal >= minDay && dayOfWeekVal <= maxDay) {
                            applies = true;
                            reason = "Weekend / prime day schedule (" + showtimeStart.getDayOfWeek() + ")";
                        }
                    }
                    break;

                case TIME_OF_DAY:
                    if (rule.getMinValue() != null && rule.getMaxValue() != null) {
                        int minH = rule.getMinValue().intValue();
                        int maxH = rule.getMaxValue().intValue();
                        // Weekday check if matinee
                        if (dayOfWeekVal <= 4 && minH >= 9 && maxH <= 17 && hourOfDay >= minH && hourOfDay < maxH) {
                            applies = true;
                            reason = String.format("Weekday matinee slot (%02d:00)", hourOfDay);
                        } else if (hourOfDay >= minH && hourOfDay <= maxH) {
                            applies = true;
                            reason = String.format("Prime evening time slot (%02d:00)", hourOfDay);
                        }
                    }
                    break;
            }

            if (applies) {
                totalAdjustmentPct = totalAdjustmentPct.add(rule.getAdjustmentPct());
                BigDecimal lineDelta = basePrice.multiply(rule.getAdjustmentPct())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                breakdown.add(new PriceBreakdownLine(
                        rule.getName(), rule.getRuleType(), rule.getAdjustmentPct(), lineDelta, reason
                ));
            }
        }

        // Clamp total adjustment percentage between [floor, cap] (e.g. -20% to +50%)
        BigDecimal floorLimit = floorPct.multiply(BigDecimal.valueOf(100));
        BigDecimal capLimit = capPct.multiply(BigDecimal.valueOf(100));

        BigDecimal clampedAdjustmentPct = totalAdjustmentPct;
        if (clampedAdjustmentPct.compareTo(floorLimit) < 0) {
            clampedAdjustmentPct = floorLimit;
        } else if (clampedAdjustmentPct.compareTo(capLimit) > 0) {
            clampedAdjustmentPct = capLimit;
        }

        // Calculate unrounded price: base * (1 + clampedPct / 100)
        BigDecimal multiplier = BigDecimal.ONE.add(
                clampedAdjustmentPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
        BigDecimal calculated = basePrice.multiply(multiplier);

        // Round to the nearest ₹5
        BigDecimal finalPrice = roundToNearestFive(calculated);

        // Never fall below minimum price ₹50.00
        if (finalPrice.compareTo(MIN_TICKET_PRICE) < 0) {
            finalPrice = MIN_TICKET_PRICE;
        }

        PriceQuote quote = new PriceQuote();
        quote.setSeatType(seatType);
        quote.setBasePrice(basePrice.setScale(2, RoundingMode.HALF_UP));
        quote.setFinalPrice(finalPrice.setScale(2, RoundingMode.HALF_UP));
        quote.setBreakdownLines(breakdown);
        quote.setBreakdownJson(serializeBreakdown(breakdown, clampedAdjustmentPct));
        quote.setCreatedAt(now);
        quote.setExpiresAt(now.plusMinutes(5));

        return quote;
    }

    /**
     * Rounds a monetary amount to the nearest ₹5.
     */
    public static BigDecimal roundToNearestFive(BigDecimal amount) {
        BigDecimal divided = amount.divide(FIVE, 0, RoundingMode.HALF_UP);
        return divided.multiply(FIVE).setScale(2, RoundingMode.HALF_UP);
    }

    private static String serializeBreakdown(List<PriceBreakdownLine> lines, BigDecimal effectivePct) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"effectivePct\":").append(effectivePct).append(",\"rules\":[");
        for (int i = 0; i < lines.size(); i++) {
            PriceBreakdownLine line = lines.get(i);
            sb.append("{\"rule\":\"").append(line.getRuleName()).append("\",")
              .append("\"type\":\"").append(line.getRuleType()).append("\",")
              .append("\"pct\":").append(line.getAdjustmentPct()).append(",")
              .append("\"delta\":").append(line.getAmountDelta()).append("}");
            if (i < lines.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}
