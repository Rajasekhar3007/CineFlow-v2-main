package com.cineflow.model;

import java.math.BigDecimal;

/**
 * CineFlow v3 — Order line item for F&B concessions linked to a movie ticket booking.
 */
public class BookingConcession {

    private int id;
    private int bookingId;
    private int concessionId;
    private String concessionName;
    private ConcessionCategory category;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private ConcessionPickupStatus pickupStatus;

    // Additional display fields for Admin / Staff view
    private String customerName;
    private String movieTitle;
    private String screenName;

    public BookingConcession() {
        this.quantity = 1;
        this.unitPrice = BigDecimal.ZERO;
        this.totalPrice = BigDecimal.ZERO;
        this.pickupStatus = ConcessionPickupStatus.PENDING;
    }

    public BookingConcession(int id, int bookingId, int concessionId, String concessionName,
                             ConcessionCategory category, int quantity, BigDecimal unitPrice,
                             BigDecimal totalPrice, ConcessionPickupStatus pickupStatus) {
        this.id = id;
        this.bookingId = bookingId;
        this.concessionId = concessionId;
        this.concessionName = concessionName;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.pickupStatus = pickupStatus != null ? pickupStatus : ConcessionPickupStatus.PENDING;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getConcessionId() {
        return concessionId;
    }

    public void setConcessionId(int concessionId) {
        this.concessionId = concessionId;
    }

    public String getConcessionName() {
        return concessionName;
    }

    public void setConcessionName(String concessionName) {
        this.concessionName = concessionName;
    }

    public ConcessionCategory getCategory() {
        return category;
    }

    public void setCategory(ConcessionCategory category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public ConcessionPickupStatus getPickupStatus() {
        return pickupStatus;
    }

    public void setPickupStatus(ConcessionPickupStatus pickupStatus) {
        this.pickupStatus = pickupStatus;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public void setMovieTitle(String movieTitle) {
        this.movieTitle = movieTitle;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }
}
