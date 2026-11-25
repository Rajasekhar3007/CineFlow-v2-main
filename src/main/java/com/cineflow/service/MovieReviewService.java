package com.cineflow.service;

import com.cineflow.dao.MovieReviewDAO;
import com.cineflow.model.MovieReview;
import com.cineflow.model.ReviewSentiment;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * CineFlow v3 — Service for verified movie reviews and sentiment classification.
 */
public class MovieReviewService {

    private final MovieReviewDAO movieReviewDAO;
    private final SentimentAnalyzer sentimentAnalyzer;

    public MovieReviewService() {
        this.movieReviewDAO = new MovieReviewDAO();
        this.sentimentAnalyzer = new SentimentAnalyzer();
    }

    public MovieReviewService(MovieReviewDAO movieReviewDAO, SentimentAnalyzer sentimentAnalyzer) {
        this.movieReviewDAO = movieReviewDAO;
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    public List<MovieReview> getReviewsForMovie(int movieId) throws SQLException {
        return movieReviewDAO.findByMovieId(movieId);
    }

    public Map<String, Object> getMovieReviewStats(int movieId) throws SQLException {
        return movieReviewDAO.getMovieReviewStats(movieId);
    }

    public MovieReview getUserReviewForMovie(int userId, int movieId) throws SQLException {
        return movieReviewDAO.findByUserAndMovie(userId, movieId);
    }

    public boolean canUserReviewMovie(int userId, int movieId) throws SQLException {
        Integer bookingId = movieReviewDAO.findConfirmedBookingForMovie(userId, movieId);
        return bookingId != null;
    }

    public SentimentAnalyzer.SentimentAnalysisResult previewSentiment(String text, int rating) {
        return sentimentAnalyzer.analyze(text, rating);
    }

    public MovieReview submitReview(int userId, int movieId, int rating, String reviewText) throws SQLException {
        Integer bookingId = movieReviewDAO.findConfirmedBookingForMovie(userId, movieId);
        if (bookingId == null) {
            throw new IllegalStateException("Only verified ticket holders can submit reviews for this movie.");
        }

        SentimentAnalyzer.SentimentAnalysisResult analysis = sentimentAnalyzer.analyze(reviewText, rating);
        ReviewSentiment sentiment = analysis.getSentiment();

        MovieReview review = new MovieReview();
        review.setMovieId(movieId);
        review.setUserId(userId);
        review.setBookingId(bookingId);
        review.setRating(rating);
        review.setReviewText(reviewText != null ? reviewText.trim() : "");
        review.setSentiment(sentiment);

        movieReviewDAO.saveReview(review);
        return review;
    }
}
