package com.cineflow.service;

import com.cineflow.model.DemandCategory;
import com.cineflow.model.DemandForecastResult;
import com.cineflow.model.HistoricalShowtimeData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DemandForecastingEngineTest {

    private List<HistoricalShowtimeData> createMockHistory() {
        List<HistoricalShowtimeData> list = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusDays(14);
        // 5 Sci-Fi historical records with ~70% occupancy
        list.add(new HistoricalShowtimeData(1, 101, "Interstellar", "Sci-Fi", 8.7, 1, base.plusDays(1), 100, 75));
        list.add(new HistoricalShowtimeData(2, 101, "Interstellar", "Sci-Fi", 8.7, 1, base.plusDays(2), 100, 80));
        list.add(new HistoricalShowtimeData(3, 102, "Dune", "Sci-Fi", 8.5, 1, base.plusDays(3), 100, 70));
        list.add(new HistoricalShowtimeData(4, 103, "Oppenheimer", "Drama", 8.9, 1, base.plusDays(4), 100, 60));
        list.add(new HistoricalShowtimeData(5, 104, "Comedy Night", "Comedy", 6.5, 1, base.plusDays(5), 100, 45));
        return list;
    }

    @Test
    @DisplayName("Friday Prime Evening Screening Forecasts High Demand")
    void testFridayPrimeEveningHighDemand() {
        List<HistoricalShowtimeData> history = createMockHistory();
        // Friday at 19:30
        LocalDateTime showtime = LocalDateTime.now().plusDays(2).withHour(19).withMinute(30);
        while (showtime.getDayOfWeek() != java.time.DayOfWeek.FRIDAY) {
            showtime = showtime.plusDays(1);
        }

        DemandForecastResult res = DemandForecastingEngine.forecast(
                99, "Dune Part Two", "Sci-Fi", 8.8, "IMAX Screen 1",
                showtime, 100, 20, LocalDateTime.now(), history
        );

        assertNotNull(res);
        assertEquals(99, res.getShowtimeId());
        assertEquals(DemandCategory.HIGH, res.getDemandCategory());
        assertTrue(res.getPredictedOccupancyPct() >= 75.0, "Expected >= 75% for prime Friday Sci-Fi");
        assertTrue(res.getExpectedTotalBookings() >= 75);
        assertTrue(res.getConfidencePct() >= 60.0);
        assertTrue(res.getExplanation().contains("Friday"));
    }

    @Test
    @DisplayName("Monday Morning Screening Forecasts Low or Moderate Demand")
    void testMondayMorningLowDemand() {
        List<HistoricalShowtimeData> history = createMockHistory();
        // Monday at 10:00 AM
        LocalDateTime showtime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        while (showtime.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            showtime = showtime.plusDays(1);
        }

        DemandForecastResult res = DemandForecastingEngine.forecast(
                100, "Indie Doc", "Documentary", 6.2, "Screen 3",
                showtime, 80, 5, LocalDateTime.now(), history
        );

        assertNotNull(res);
        assertTrue(res.getDemandCategory() == DemandCategory.LOW || res.getDemandCategory() == DemandCategory.MEDIUM);
        assertTrue(res.getPredictedOccupancyPct() < 70.0);
    }

    @Test
    @DisplayName("Confirmed Bookings Act as Minimum Floor")
    void testConfirmedBookingsAsFloor() {
        List<HistoricalShowtimeData> history = createMockHistory();
        LocalDateTime showtime = LocalDateTime.now().plusDays(1).withHour(11).withMinute(0);

        // Already 85 seats booked out of 100
        DemandForecastResult res = DemandForecastingEngine.forecast(
                101, "Low Key Film", "Drama", 5.5, "Screen 2",
                showtime, 100, 85, LocalDateTime.now(), history
        );

        assertNotNull(res);
        assertTrue(res.getPredictedOccupancyPct() >= 85.0, "Forecast must never be lower than actual confirmed seats");
        assertEquals(85, res.getExpectedTotalBookings());
        assertEquals(DemandCategory.HIGH, res.getDemandCategory());
    }

    @Test
    @DisplayName("Mean Absolute Error (MAE) Validation Split")
    void testMAEComputation() {
        List<Double> actuals = List.of(75.0, 80.0, 60.0, 45.0);
        List<Double> preds   = List.of(72.0, 84.0, 65.0, 40.0);

        // Errors: |75-72|=3, |80-84|=4, |60-65|=5, |45-40|=5. Mean = (3+4+5+5)/4 = 4.25 -> 4.3
        double mae = DemandForecastingEngine.computeMAE(actuals, preds);
        assertEquals(4.3, mae, 0.1);
    }
}
