package com.cineflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain entity representing an individual cryptographic ticket pass issued for a seat.
 */
public class Ticket {

    private int id;
    private int bookingId;
    private int bookingSeatId;
    private int showtimeId;
    private String token;
    private TicketStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime usedAt;
    private Integer usedByUserId;

    // Display fields
    private String seatCode;
    private SeatType seatType;
    private BigDecimal price;
    private String movieTitle;
    private String screenName;
    private LocalDateTime showtimeStart;
    private String customerName;

    public Ticket() {}

    public Ticket(int id, int bookingId, int bookingSeatId, int showtimeId, String token,
                  TicketStatus status, LocalDateTime issuedAt, LocalDateTime usedAt, Integer usedByUserId) {
        this.id = id;
        this.bookingId = bookingId;
        this.bookingSeatId = bookingSeatId;
        this.showtimeId = showtimeId;
        this.token = token;
        this.status = status;
        this.issuedAt = issuedAt;
        this.usedAt = usedAt;
        this.usedByUserId = usedByUserId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public int getBookingSeatId() { return bookingSeatId; }
    public void setBookingSeatId(int bookingSeatId) { this.bookingSeatId = bookingSeatId; }

    public int getShowtimeId() { return showtimeId; }
    public void setShowtimeId(int showtimeId) { this.showtimeId = showtimeId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }

    public Integer getUsedByUserId() { return usedByUserId; }
    public void setUsedByUserId(Integer usedByUserId) { this.usedByUserId = usedByUserId; }

    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }

    public SeatType getSeatType() { return seatType; }
    public void setSeatType(SeatType seatType) { this.seatType = seatType; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = screenName; }

    public LocalDateTime getShowtimeStart() { return showtimeStart; }
    public void setShowtimeStart(LocalDateTime showtimeStart) { this.showtimeStart = showtimeStart; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
