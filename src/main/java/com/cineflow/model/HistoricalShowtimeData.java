package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * Historical showtime training/baseline record for demand forecasting.
 */
public class HistoricalShowtimeData {
    private int showtimeId;
    private int movieId;
    private String movieTitle;
    private String genre;
    private double rating;
    private int screenId;
    private LocalDateTime startTime;
    private int totalCapacity;
    private int bookedSeats;

    public HistoricalShowtimeData(int showtimeId, int movieId, String movieTitle, String genre,
                                  double rating, int screenId, LocalDateTime startTime,
                                  int totalCapacity, int bookedSeats) {
        this.showtimeId = showtimeId;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.genre = genre;
        this.rating = rating;
        this.screenId = screenId;
        this.startTime = startTime;
        this.totalCapacity = totalCapacity;
        this.bookedSeats = bookedSeats;
    }

    public int getShowtimeId() { return showtimeId; }
    public int getMovieId() { return movieId; }
    public String getMovieTitle() { return movieTitle; }
    public String getGenre() { return genre; }
    public double getRating() { return rating; }
    public int getScreenId() { return screenId; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getTotalCapacity() { return totalCapacity; }
    public int getBookedSeats() { return bookedSeats; }

    public double getOccupancyRatio() {
        if (totalCapacity <= 0) return 0.0;
        return (double) bookedSeats / totalCapacity;
    }
}
