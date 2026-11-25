package com.cineflow.model;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Customer movie taste preferences for personalised recommendation scoring.
 */
public class UserPreference {
    private int userId;
    private Set<String> genres;
    private Set<String> languages;
    private double minRating;
    private LocalDateTime updatedAt;

    public UserPreference() {
        this.genres = new LinkedHashSet<>();
        this.languages = new LinkedHashSet<>();
        this.minRating = 7.0;
        this.updatedAt = LocalDateTime.now();
    }

    public UserPreference(int userId, Collection<String> genres, Collection<String> languages, double minRating) {
        this.userId = userId;
        this.genres = new LinkedHashSet<>(genres != null ? genres : Collections.emptyList());
        this.languages = new LinkedHashSet<>(languages != null ? languages : Collections.emptyList());
        this.minRating = minRating > 0 ? minRating : 7.0;
        this.updatedAt = LocalDateTime.now();
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public Set<String> getGenres() { return genres; }
    public void setGenres(Set<String> genres) { this.genres = genres != null ? genres : new LinkedHashSet<>(); }

    public Set<String> getLanguages() { return languages; }
    public void setLanguages(Set<String> languages) { this.languages = languages != null ? languages : new LinkedHashSet<>(); }

    public double getMinRating() { return minRating; }
    public void setMinRating(double minRating) { this.minRating = minRating; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getGenresCsv() {
        return String.join(",", genres);
    }

    public void setGenresCsv(String csv) {
        genres.clear();
        if (csv != null && !csv.isBlank()) {
            for (String g : csv.split(",")) {
                if (!g.trim().isEmpty()) genres.add(g.trim());
            }
        }
    }

    public String getLanguagesCsv() {
        return String.join(",", languages);
    }

    public void setLanguagesCsv(String csv) {
        languages.clear();
        if (csv != null && !csv.isBlank()) {
            for (String l : csv.split(",")) {
                if (!l.trim().isEmpty()) languages.add(l.trim());
            }
        }
    }
}
