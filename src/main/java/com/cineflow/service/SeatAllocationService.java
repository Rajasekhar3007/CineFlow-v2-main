package com.cineflow.service;

import com.cineflow.dao.*;
import com.cineflow.model.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Service managing automated best-seat recommendation and allocation workflows.
 */
public class SeatAllocationService {

    private final SeatDAO seatDAO;
    private final ScreenDAO screenDAO;
    private final ShowtimeDAO showtimeDAO;
    private final BookingDAO bookingDAO;
    private final SeatHoldDAO seatHoldDAO;

    public SeatAllocationService() {
        this.seatDAO = new SeatDAO();
        this.screenDAO = new ScreenDAO();
        this.showtimeDAO = new ShowtimeDAO();
        this.bookingDAO = new BookingDAO();
        this.seatHoldDAO = new SeatHoldDAO();
    }

    public SeatAllocationService(SeatDAO seatDAO, ScreenDAO screenDAO, ShowtimeDAO showtimeDAO,
                                 BookingDAO bookingDAO, SeatHoldDAO seatHoldDAO) {
        this.seatDAO = seatDAO;
        this.screenDAO = screenDAO;
        this.showtimeDAO = showtimeDAO;
        this.bookingDAO = bookingDAO;
        this.seatHoldDAO = seatHoldDAO;
    }

    /**
     * Finds ranked seat recommendations for a given customer and showtime.
     *
     * @param showtimeId Identifier of the selected screening
     * @param count Number of contiguous or adjacent seats requested (1-10)
     * @param preference Customer seating category and positioning preference
     * @param userId Current user ID (user's own active holds count as available)
     * @return Ranked list of up to 3 candidate SeatSuggestions
     * @throws SQLException If database access fails
     */
    public List<SeatSuggestion> findBestSeats(int showtimeId, int count,
                                             SeatPreference preference, int userId) throws SQLException {
        Optional<Showtime> stOpt = showtimeDAO.findById(showtimeId);
        if (stOpt.isEmpty()) {
            throw new IllegalArgumentException("Showtime not found: " + showtimeId);
        }
        Showtime showtime = stOpt.get();

        Optional<Screen> screenOpt = screenDAO.findById(showtime.getScreenId());
        int totalRows = screenOpt.map(Screen::getTotalRows).orElse(5);
        int seatsPerRow = screenOpt.map(Screen::getSeatsPerRow).orElse(10);

        List<Seat> allSeats = seatDAO.findByScreenId(showtime.getScreenId());

        // Unavailable set = Booked seats U Seats held by other users
        Set<Integer> unavailable = new HashSet<>(bookingDAO.getBookedSeatIdsForShowtime(showtimeId));
        unavailable.addAll(seatHoldDAO.getActiveHeldSeatIds(showtimeId, userId));

        return SeatScorer.suggestSeats(allSeats, unavailable, count, preference, totalRows, seatsPerRow);
    }
}
