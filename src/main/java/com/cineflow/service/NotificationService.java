package com.cineflow.service;

import com.cineflow.dao.NotificationDAO;
import com.cineflow.model.Notification;
import com.cineflow.model.NotificationType;

import java.sql.SQLException;
import java.util.List;

/**
 * Service for delivering and managing in-app notifications.
 */
public class NotificationService {

    private static final NotificationService INSTANCE = new NotificationService();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    private NotificationService() {}

    public static NotificationService getInstance() {
        return INSTANCE;
    }

    public List<Notification> getRecentNotifications(int userId, int limit) throws SQLException {
        return notificationDAO.findByUserId(userId, limit);
    }

    public int getUnreadCount(int userId) {
        try {
            return notificationDAO.countUnreadByUserId(userId);
        } catch (SQLException e) {
            System.err.println("Failed to fetch unread notification count: " + e.getMessage());
            return 0;
        }
    }

    public boolean markAsRead(int notificationId) throws SQLException {
        return notificationDAO.markAsRead(notificationId);
    }

    public boolean markAllAsRead(int userId) throws SQLException {
        return notificationDAO.markAllAsRead(userId);
    }

    public int sendNotification(int userId, NotificationType type, String title, String message, String refType, Integer refId) {
        try {
            Notification n = new Notification(userId, type, title, message, refType, refId);
            return notificationDAO.createNotification(n);
        } catch (SQLException e) {
            System.err.println("Failed to persist notification: " + e.getMessage());
            return -1;
        }
    }
}
