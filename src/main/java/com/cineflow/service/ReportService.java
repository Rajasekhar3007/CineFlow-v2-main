package com.cineflow.service;

import com.cineflow.dao.ReportDAO;
import com.cineflow.model.DemandForecastResult;
import com.cineflow.model.HistoricalShowtimeData;
import com.cineflow.model.OccupancyReportItem;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service providing aggregated financial and performance reporting.
 */
public class ReportService {

    private final ReportDAO reportDAO;

    public ReportService() {
        this.reportDAO = new ReportDAO();
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public BigDecimal getTotalRevenue() throws SQLException {
        return reportDAO.getTotalRevenue();
    }

    public int getTodayBookingsCount() throws SQLException {
        return reportDAO.getTodayBookingsCount();
    }

    public Map<LocalDate, BigDecimal> getSevenDayRevenue() throws SQLException {
        return reportDAO.getSevenDayRevenue();
    }

    public Map<String, Integer> getTopMoviesByTicketSales() throws SQLException {
        return reportDAO.getTopMoviesByTicketSales();
    }

    public List<OccupancyReportItem> getShowtimeOccupancy() throws SQLException {
        return reportDAO.getShowtimeOccupancy();
    }

    public BigDecimal getDynamicPricingUplift() throws SQLException {
        return reportDAO.getDynamicPricingUplift();
    }

    public Map<String, Integer> getWaitlistStats() throws SQLException {
        return reportDAO.getWaitlistStats();
    }

    public Map<String, Integer> getGateAdmissionStats() throws SQLException {
        return reportDAO.getGateAdmissionStats();
    }

    public int getTotalTicketsSold() throws SQLException {
        return reportDAO.getTotalTicketsSold();
    }

    public int getTotalBookingsCount() throws SQLException {
        return reportDAO.getTotalBookingsCount();
    }

    public double getCancellationRate() throws SQLException {
        return reportDAO.getCancellationRate();
    }

    public BigDecimal getAverageTicketPrice() throws SQLException {
        return reportDAO.getAverageTicketPrice();
    }

    public double getOverallOccupancyPercentage() throws SQLException {
        return reportDAO.getOverallOccupancyPercentage();
    }

    public Map<Integer, Integer> getPeakBookingHours() throws SQLException {
        return reportDAO.getPeakBookingHours();
    }

    public Map<String, BigDecimal> getWeekdayVsWeekendRevenue() throws SQLException {
        return reportDAO.getWeekdayVsWeekendRevenue();
    }

    /**
     * Executes demand forecasting across all upcoming scheduled showtimes
     * using the pure deterministic DemandForecastingEngine and historical data.
     */
    public List<DemandForecastResult> getUpcomingDemandForecasts() throws SQLException {
        List<HistoricalShowtimeData> history = reportDAO.getHistoricalShowtimes();
        List<HistoricalShowtimeData> upcoming = reportDAO.getUpcomingShowtimesForForecast();
        Map<Integer, String> screenNames = reportDAO.getScreenNamesMap();

        List<DemandForecastResult> forecasts = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (HistoricalShowtimeData s : upcoming) {
            String screenName = screenNames.getOrDefault(s.getScreenId(), "Auditorium " + s.getScreenId());
            DemandForecastResult f = DemandForecastingEngine.forecast(
                    s.getShowtimeId(),
                    s.getMovieTitle(),
                    s.getGenre(),
                    s.getRating(),
                    screenName,
                    s.getStartTime(),
                    s.getTotalCapacity(),
                    s.getBookedSeats(),
                    now,
                    history
            );
            forecasts.add(f);
        }
        return forecasts;
    }
}

