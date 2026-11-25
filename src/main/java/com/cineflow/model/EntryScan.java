package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * Audit log entry capturing an entry validation attempt at the cinema gate.
 */
public class EntryScan {

    private int id;
    private Integer ticketId;
    private String tokenHash;
    private ScanResult result;
    private LocalDateTime scannedAt;
    private int scannedByUserId;

    // Display fields
    private String movieTitle;
    private String seatCode;
    private String scannerAdminName;

    public EntryScan() {}

    public EntryScan(int id, Integer ticketId, String tokenHash, ScanResult result,
                     LocalDateTime scannedAt, int scannedByUserId) {
        this.id = id;
        this.ticketId = ticketId;
        this.tokenHash = tokenHash;
        this.result = result;
        this.scannedAt = scannedAt;
        this.scannedByUserId = scannedByUserId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getTicketId() { return ticketId; }
    public void setTicketId(Integer ticketId) { this.ticketId = ticketId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public ScanResult getResult() { return result; }
    public void setResult(ScanResult result) { this.result = result; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }

    public int getScannedByUserId() { return scannedByUserId; }
    public void setScannedByUserId(int scannedByUserId) { this.scannedByUserId = scannedByUserId; }

    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }

    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }

    public String getScannerAdminName() { return scannerAdminName; }
    public void setScannerAdminName(String scannerAdminName) { this.scannerAdminName = scannerAdminName; }
}
