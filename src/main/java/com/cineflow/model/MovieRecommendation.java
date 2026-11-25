package com.cineflow.model;

/**
 * Represents a personalized movie recommendation with match percentage and human-readable explanation.
 */
public class MovieRecommendation {
    private Movie movie;
    private double score;
    private int matchPercentage;
    private String explanation;

    public MovieRecommendation(Movie movie, double score, int matchPercentage, String explanation) {
        this.movie = movie;
        this.score = score;
        this.matchPercentage = matchPercentage;
        this.explanation = explanation;
    }

    public Movie getMovie() { return movie; }
    public double getScore() { return score; }
    public int getMatchPercentage() { return matchPercentage; }
    public String getExplanation() { return explanation; }
}
