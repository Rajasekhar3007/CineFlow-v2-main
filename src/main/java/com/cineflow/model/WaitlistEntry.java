package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * Represents a customer queue entry for a sold-out showtime.
 */
public class WaitlistEntry {
    private int id;
    private int userId;
    private int showtimeId;
    private int seatsWanted;
    private SeatPreference seatPreference;
    private WaitlistStatus status;
    private LocalDateTime positionTs;
    private LocalDateTime offeredAt;
    private LocalDateTime offerExpiresAt;
    private LocalDateTime createdAt;

    // Transient display fields joined from movies/showtimes/screens/users
    private String movieTitle;
    private String screenName;
    private LocalDateTime showtimeStartTime;
    private String username;
    private int queuePosition = 1;

    public WaitlistEntry() {
        this.seatsWanted = 1;
        this.seatPreference = SeatPreference.BEST_OVERALL;
        this.status = WaitlistStatus.WAITING;
    }

    public WaitlistEntry(int userId, int showtimeId, int seatsWanted, SeatPreference seatPreference) {
        this.userId = userId;
        this.showtimeId = showtimeId;
        this.seatsWanted = seatsWanted;
        this.seatPreference = seatPreference != null ? seatPreference : SeatPreference.BEST_OVERALL;
        this.status = WaitlistStatus.WAITING;
        this.positionTs = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getShowtimeId() { return showtimeId; }
    public void setShowtimeId(int showtimeId) { this.showtimeId = showtimeId; }

    public int getSeatsWanted() { return seatsWanted; }
    public void setSeatsWanted(int seatsWanted) { this.seatsWanted = seatsWanted; }

    public SeatPreference getSeatPreference() { return seatPreference; }
    public void setSeatPreference(SeatPreference seatPreference) { this.seatPreference = seatPreference; }

    public WaitlistStatus getStatus() { return status; }
    public void setStatus(WaitlistStatus status) { this.status = status; }

    public LocalDateTime getPositionTs() { return positionTs; }
    public void setPositionTs(LocalDateTime positionTs) { this.positionTs = positionTs; }

    public LocalDateTime getOfferedAt() { return offeredAt; }
    public void setOfferedAt(LocalDateTime offeredAt) { this.offeredAt = offeredAt; }

    public LocalDateTime getOfferExpiresAt() { return offerExpiresAt; }
    public void setOfferExpiresAt(LocalDateTime offerExpiresAt) { this.offerExpiresAt = offerExpiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }

    public LocalDateTime getShowtimeStartTime() { return showtimeStartTime; }
    public void setShowtimeStartTime(LocalDateTime showtimeStartTime) { this.showtimeStartTime = showtimeStartTime; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getQueuePosition() { return queuePosition; }
    public void setQueuePosition(int queuePosition) { this.queuePosition = queuePosition; }
}
