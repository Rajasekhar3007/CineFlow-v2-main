package com.cineflow.service;

import com.cineflow.model.Movie;
import com.cineflow.model.MovieRecommendation;
import com.cineflow.model.UserPreference;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CineFlow v2 — Personalised Recommendation Engine.
 * Implements exponential recency decay, composite multi-attribute scoring,
 * diversity re-ranking (genre anti-clustering), and dynamic human explanation generation.
 * Pure logic — completely decoupled from JDBC for fast unit testing.
 */
public class RecommendationEngine {

    private static final double LAMBDA = Math.log(2) / 30.0; // Half-life = 30 days

    public static class WatchedMovieRecord {
        private final int movieId;
        private final String title;
        private final String genre;
        private final String language;
        private final LocalDateTime watchedAt;

        public WatchedMovieRecord(int movieId, String title, String genre, String language, LocalDateTime watchedAt) {
            this.movieId = movieId;
            this.title = title;
            this.genre = genre != null ? genre.trim() : "";
            this.language = language != null ? language.trim() : "";
            this.watchedAt = watchedAt != null ? watchedAt : LocalDateTime.now();
        }

        public int getMovieId() { return movieId; }
        public String getTitle() { return title; }
        public String getGenre() { return genre; }
        public String getLanguage() { return language; }
        public LocalDateTime getWatchedAt() { return watchedAt; }
    }

    /**
     * Computes ranked movie recommendations for a customer.
     *
     * @param candidates Available movies in catalog
     * @param preference Explicit user taste preferences (can be null)
     * @param history Past watched movie history (can be null or empty)
     * @param limit Max recommendations to return
     * @return List of MovieRecommendations with match percentage and explanation
     */
    public List<MovieRecommendation> recommend(List<Movie> candidates,
                                              UserPreference preference,
                                              List<WatchedMovieRecord> history,
                                              int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Double> genreWeights = new HashMap<>();
        Map<String, Double> languageWeights = new HashMap<>();
        Set<Integer> recentlyWatchedMovieIds = new HashSet<>();
        WatchedMovieRecord mostRecentWatched = null;

        // 1. Process viewing history with exponential recency decay: W(t) = e^(-lambda * deltaDays)
        if (history != null && !history.isEmpty()) {
            for (WatchedMovieRecord record : history) {
                double daysAgo = Math.max(0, Duration.between(record.getWatchedAt(), now).toHours() / 24.0);
                double weight = Math.exp(-LAMBDA * daysAgo);

                if (!record.getGenre().isEmpty()) {
                    genreWeights.merge(record.getGenre().toUpperCase(), weight, Double::sum);
                }
                if (!record.getLanguage().isEmpty()) {
                    languageWeights.merge(record.getLanguage().toUpperCase(), weight, Double::sum);
                }
                if (daysAgo <= 14) {
                    recentlyWatchedMovieIds.add(record.getMovieId());
                }
                if (mostRecentWatched == null || record.getWatchedAt().isAfter(mostRecentWatched.getWatchedAt())) {
                    mostRecentWatched = record;
                }
            }
        }

        // 2. Score each candidate movie
        List<ScoredCandidate> scoredList = new ArrayList<>();

        for (Movie m : candidates) {
            String genre = m.getGenre() != null ? m.getGenre().trim().toUpperCase() : "";
            String lang = m.getLanguage() != null ? m.getLanguage().trim().toUpperCase() : "";
            double rating = m.getRating() > 0 ? m.getRating() : 7.0;

            // Genre Affinity G(m)
            double genreScore = 0.2;
            boolean explicitGenreMatch = false;
            if (preference != null && preference.getGenres() != null) {
                for (String prefG : preference.getGenres()) {
                    if (prefG.equalsIgnoreCase(genre)) {
                        genreScore = 1.0;
                        explicitGenreMatch = true;
                        break;
                    }
                }
            }
            if (!explicitGenreMatch && genreWeights.containsKey(genre)) {
                genreScore = Math.min(1.0, 0.4 + (genreWeights.get(genre) * 0.25));
            }

            // Language Affinity L(m)
            double langScore = 0.3;
            boolean explicitLangMatch = false;
            if (preference != null && preference.getLanguages() != null) {
                for (String prefL : preference.getLanguages()) {
                    if (prefL.equalsIgnoreCase(lang)) {
                        langScore = 1.0;
                        explicitLangMatch = true;
                        break;
                    }
                }
            }
            if (!explicitLangMatch && languageWeights.containsKey(lang)) {
                langScore = Math.min(1.0, 0.5 + (languageWeights.get(lang) * 0.2));
            }

            // Rating Score R(m)
            double ratingScore = Math.min(1.0, rating / 10.0);

            // Trending / Popularity T(m)
            double trendingScore = Math.min(1.0, (rating >= 8.0 ? 0.9 : 0.6));

            // Preference Threshold Bonus P(m)
            double minRatingPref = (preference != null && preference.getMinRating() > 0) ? preference.getMinRating() : 7.0;
            double prefBonus = rating >= minRatingPref ? 1.0 : 0.3;

            // Composite Weighted Formulation:
            // Score = 0.40 * G(m) + 0.20 * L(m) + 0.20 * R(m) + 0.10 * T(m) + 0.10 * P(m)
            double composite = (0.40 * genreScore) +
                               (0.20 * langScore) +
                               (0.20 * ratingScore) +
                               (0.10 * trendingScore) +
                               (0.10 * prefBonus);

            // Small penalty if already watched very recently to encourage discovery
            if (recentlyWatchedMovieIds.contains(m.getId())) {
                composite *= 0.70;
            }

            int matchPct = (int) Math.round(Math.min(99, Math.max(50, composite * 100)));

            // Dynamic human-readable explanation
            String explanation = buildExplanation(m, explicitGenreMatch, explicitLangMatch,
                    genreWeights.containsKey(genre), mostRecentWatched, rating);

            scoredList.add(new ScoredCandidate(m, composite, matchPct, explanation, genre));
        }

        // Sort by composite score descending
        scoredList.sort((a, b) -> Double.compare(b.composite, a.composite));

        // 3. Diversity Re-ranking (Genre Anti-Clustering: max 2 per genre in top recommendations)
        List<MovieRecommendation> result = new ArrayList<>();
        Map<String, Integer> genreCounts = new HashMap<>();

        for (ScoredCandidate sc : scoredList) {
            int currentCount = genreCounts.getOrDefault(sc.genre, 0);
            if (currentCount < 2 || result.size() >= limit - 1) {
                result.add(new MovieRecommendation(sc.movie, sc.composite, sc.matchPct, sc.explanation));
                genreCounts.put(sc.genre, currentCount + 1);
                if (result.size() >= limit) break;
            }
        }

        // If limit not filled due to aggressive filtering, backfill
        if (result.size() < limit && result.size() < scoredList.size()) {
            for (ScoredCandidate sc : scoredList) {
                if (result.stream().noneMatch(r -> r.getMovie().getId() == sc.movie.getId())) {
                    result.add(new MovieRecommendation(sc.movie, sc.composite, sc.matchPct, sc.explanation));
                    if (result.size() >= limit) break;
                }
            }
        }

        return result;
    }

    private String buildExplanation(Movie m, boolean explicitGenre, boolean explicitLang,
                                    boolean histGenre, WatchedMovieRecord recentWatched, double rating) {
        if (histGenre && recentWatched != null && recentWatched.getGenre().equalsIgnoreCase(m.getGenre())) {
            return "Because you enjoyed " + recentWatched.getTitle();
        } else if (explicitGenre && explicitLang) {
            return "Matches your " + m.getGenre() + " & " + m.getLanguage() + " taste";
        } else if (explicitGenre) {
            return "Matches your preference for " + m.getGenre() + " films";
        } else if (rating >= 8.5) {
            return "Critically acclaimed " + m.getGenre() + " (★ " + String.format("%.1f", rating) + ")";
        } else if (explicitLang) {
            return "Top pick in " + m.getLanguage();
        } else {
            return "Trending multiplex highlight";
        }
    }

    private static class ScoredCandidate {
        final Movie movie;
        final double composite;
        final int matchPct;
        final String explanation;
        final String genre;

        ScoredCandidate(Movie movie, double composite, int matchPct, String explanation, String genre) {
            this.movie = movie;
            this.composite = composite;
            this.matchPct = matchPct;
            this.explanation = explanation;
            this.genre = genre;
        }
    }
}
