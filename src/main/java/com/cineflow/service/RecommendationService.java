package com.cineflow.service;

import com.cineflow.dao.BookingDAO;
import com.cineflow.dao.MovieDAO;
import com.cineflow.dao.UserPreferenceDAO;
import com.cineflow.model.Booking;
import com.cineflow.model.Movie;
import com.cineflow.model.MovieRecommendation;
import com.cineflow.model.UserPreference;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing user taste profiles and personalized recommendations.
 */
public class RecommendationService {

    private final UserPreferenceDAO preferenceDAO;
    private final MovieDAO movieDAO;
    private final BookingDAO bookingDAO;
    private final RecommendationEngine engine;

    public RecommendationService() {
        this.preferenceDAO = new UserPreferenceDAO();
        this.movieDAO = new MovieDAO();
        this.bookingDAO = new BookingDAO();
        this.engine = new RecommendationEngine();
    }

    public RecommendationService(UserPreferenceDAO preferenceDAO, MovieDAO movieDAO,
                                 BookingDAO bookingDAO, RecommendationEngine engine) {
        this.preferenceDAO = preferenceDAO;
        this.movieDAO = movieDAO;
        this.bookingDAO = bookingDAO;
        this.engine = engine;
    }

    public Optional<UserPreference> getUserPreference(int userId) throws SQLException {
        return preferenceDAO.findByUserId(userId);
    }

    public boolean saveUserPreference(UserPreference pref) throws SQLException {
        return preferenceDAO.saveOrUpdate(pref);
    }

    /**
     * Computes top personalised movie recommendations for a user.
     */
    public List<MovieRecommendation> getRecommendationsForUser(int userId, int limit) throws SQLException {
        List<Movie> candidates = movieDAO.findActive();
        if (candidates.isEmpty()) {
            candidates = movieDAO.findAll();
        }

        UserPreference pref = preferenceDAO.findByUserId(userId).orElse(null);

        // Build history from user's confirmed bookings
        List<RecommendationEngine.WatchedMovieRecord> history = new ArrayList<>();
        try {
            List<Booking> bookings = bookingDAO.findByUserId(userId);
            for (Booking b : bookings) {
                // Find corresponding movie to get genre and language
                for (Movie m : candidates) {
                    if (m.getTitle().equalsIgnoreCase(b.getMovieTitle())) {
                        history.add(new RecommendationEngine.WatchedMovieRecord(
                                m.getId(),
                                m.getTitle(),
                                m.getGenre(),
                                m.getLanguage(),
                                b.getBookingTime()
                        ));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        return engine.recommend(candidates, pref, history, limit);
    }
}
