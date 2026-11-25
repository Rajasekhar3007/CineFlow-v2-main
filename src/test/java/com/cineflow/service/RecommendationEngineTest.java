package com.cineflow.service;

import com.cineflow.model.Movie;
import com.cineflow.model.MovieRecommendation;
import com.cineflow.model.UserPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {

    private RecommendationEngine engine;
    private List<Movie> catalog;

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine();
        catalog = new ArrayList<>();

        catalog.add(createMovie(1, "Interstellar", "Sci-Fi", "English", 8.7));
        catalog.add(createMovie(2, "Inception", "Sci-Fi", "English", 8.8));
        catalog.add(createMovie(3, "The Matrix", "Sci-Fi", "English", 8.7));
        catalog.add(createMovie(4, "The Dark Knight", "Action", "English", 9.0));
        catalog.add(createMovie(5, "Dangal", "Drama", "Hindi", 8.4));
        catalog.add(createMovie(6, "Parasite", "Thriller", "Korean", 8.5));
    }

    private Movie createMovie(int id, String title, String genre, String language, double rating) {
        Movie m = new Movie();
        m.setId(id);
        m.setTitle(title);
        m.setGenre(genre);
        m.setLanguage(language);
        m.setRating(rating);
        m.setDurationMinutes(150);
        m.setActive(true);
        return m;
    }

    @Test
    @DisplayName("Should rank movies matching explicit user preferences higher")
    void testExplicitPreferencesRanking() {
        UserPreference pref = new UserPreference(1, Set.of("Action"), Set.of("English"), 8.0);
        List<MovieRecommendation> recs = engine.recommend(catalog, pref, null, 5);

        assertNotNull(recs);
        assertFalse(recs.isEmpty());
        // The Dark Knight (Action, English, 9.0) should be #1
        assertEquals("The Dark Knight", recs.get(0).getMovie().getTitle());
        assertTrue(recs.get(0).getMatchPercentage() >= 85);
        assertTrue(recs.get(0).getExplanation().toLowerCase().contains("action"));
    }

    @Test
    @DisplayName("Should apply recency decay and recommend based on viewing history")
    void testViewingHistoryWithRecencyDecay() {
        List<RecommendationEngine.WatchedMovieRecord> history = new ArrayList<>();
        // User watched Interstellar 2 days ago
        history.add(new RecommendationEngine.WatchedMovieRecord(
                1, "Interstellar", "Sci-Fi", "English", LocalDateTime.now().minusDays(2)
        ));

        // User watched a drama 90 days ago
        history.add(new RecommendationEngine.WatchedMovieRecord(
                99, "Old Drama", "Drama", "Hindi", LocalDateTime.now().minusDays(90)
        ));

        List<MovieRecommendation> recs = engine.recommend(catalog, null, history, 5);

        assertNotNull(recs);
        // Recent Sci-Fi should have higher weight than 90-day old Drama
        assertTrue(recs.get(0).getMovie().getGenre().equalsIgnoreCase("Sci-Fi") ||
                   recs.get(0).getMovie().getTitle().equals("The Dark Knight"));
    }

    @Test
    @DisplayName("Should enforce diversity re-ranking to avoid genre mono-culture")
    void testDiversityReRanking() {
        UserPreference pref = new UserPreference(1, Set.of("Sci-Fi"), Set.of("English"), 7.0);
        List<MovieRecommendation> recs = engine.recommend(catalog, pref, null, 5);

        // Even though catalog has 3 top Sci-Fi movies, diversity ensures non-Sci-Fi movies enter top 4
        long sciFiCount = recs.stream().limit(3)
                .filter(r -> r.getMovie().getGenre().equalsIgnoreCase("Sci-Fi"))
                .count();
        assertTrue(sciFiCount <= 2, "No single genre should completely monopolize top results");
    }

    @Test
    @DisplayName("Should generate meaningful explanation strings")
    void testExplanations() {
        UserPreference pref = new UserPreference(1, Set.of("Drama"), Set.of("Hindi"), 8.0);
        List<MovieRecommendation> recs = engine.recommend(catalog, pref, null, 3);

        MovieRecommendation top = recs.get(0);
        assertNotNull(top.getExplanation());
        assertFalse(top.getExplanation().isEmpty());
    }

    @Test
    @DisplayName("Empty candidates should return empty recommendations gracefully")
    void testEmptyCandidates() {
        List<MovieRecommendation> recs = engine.recommend(new ArrayList<>(), null, null, 5);
        assertNotNull(recs);
        assertTrue(recs.isEmpty());
    }
}
