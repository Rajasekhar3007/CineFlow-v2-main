package com.cineflow.model;

import java.time.LocalDateTime;

/**
 * In-app Notification model for transactional alerts, waitlist offers, and system updates.
 */
public class Notification {
    private int id;
    private int userId;
    private NotificationType type;
    private String title;
    private String message;
    private String refType;
    private Integer refId;
    private boolean read;
    private LocalDateTime createdAt;

    public Notification() {}

    public Notification(int userId, NotificationType type, String title, String message, String refType, Integer refId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.refType = refType;
        this.refId = refId;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }

    public Integer getRefId() { return refId; }
    public void setRefId(Integer refId) { this.refId = refId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
