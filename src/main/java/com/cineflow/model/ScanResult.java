package com.cineflow.model;

public enum ScanResult {
    ADMITTED("Admitted", "Access granted. Welcome to CineFlow!"),
    ALREADY_USED("Already Used", "Ticket has already been redeemed."),
    CANCELLED("Ticket Cancelled", "This reservation was cancelled and refunded."),
    INVALID("Invalid Ticket", "Tampered or unrecognized cryptographic ticket token."),
    WRONG_SHOW("Wrong Screening", "Ticket is not for this auditorium or screening."),
    OUTSIDE_WINDOW("Outside Entry Window", "Admission allowed strictly 60 min before to 30 min after start.");

    private final String title;
    private final String description;

    ScanResult(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
