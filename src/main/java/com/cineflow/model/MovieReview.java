package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * CineFlow v3 — Verified Customer Movie Review with AI/Lexical Sentiment.
 */
public class MovieReview {

    private int id;
    private int movieId;
    private int userId;
    private int bookingId;
    private int rating; // 1 to 5
    private String reviewText;
    private ReviewSentiment sentiment;
    private LocalDateTime createdAt;

    // Joined fields for UI
    private String movieTitle;
    private String authorName;

    public MovieReview() {
        this.rating = 5;
        this.sentiment = ReviewSentiment.POSITIVE;
        this.createdAt = LocalDateTime.now();
    }

    public MovieReview(int id, int movieId, int userId, int bookingId, int rating,
                       String reviewText, ReviewSentiment sentiment, LocalDateTime createdAt) {
        this.id = id;
        this.movieId = movieId;
        this.userId = userId;
        this.bookingId = bookingId;
        this.rating = rating;
        this.reviewText = reviewText;
        this.sentiment = sentiment != null ? sentiment : ReviewSentiment.POSITIVE;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMovieId() {
        return movieId;
    }

    public void setMovieId(int movieId) {
        this.movieId = movieId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getReviewText() {
        return reviewText;
    }

    public void setReviewText(String reviewText) {
        this.reviewText = reviewText;
    }

    public ReviewSentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(ReviewSentiment sentiment) {
        this.sentiment = sentiment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public void setMovieTitle(String movieTitle) {
        this.movieTitle = movieTitle;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }
}
