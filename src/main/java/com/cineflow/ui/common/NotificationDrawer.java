package com.cineflow.ui.common;

import com.cineflow.model.Notification;
import com.cineflow.model.NotificationType;
import com.cineflow.model.User;
import com.cineflow.service.AuthService;
import com.cineflow.service.NotificationService;
import com.cineflow.service.WaitlistService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * CineFlow v2 — Slide-in Glass Notification Drawer.
 * Displays transactional alerts and actionable waitlist seat offers.
 */
public class NotificationDrawer {

    public static void show(javafx.scene.Node anchorNode, Consumer<Integer> onClaimOffer) {
        User user = AuthService.getCurrentUser();
        if (user == null) return;

        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox drawer = new VBox(12);
        drawer.setPrefWidth(380);
        drawer.setMaxHeight(520);
        drawer.setPadding(new Insets(18));
        drawer.setStyle(
            "-fx-background-color: #12101F; " +
            "-fx-border-color: rgba(139, 92, 246, 0.35); " +
            "-fx-border-width: 1.5px; " +
            "-fx-border-radius: 14px; " +
            "-fx-background-radius: 14px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 8);"
        );

        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Notifications");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button markAllBtn = new Button("Mark all read");
        markAllBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #8B5CF6; -fx-font-size: 12px; -fx-cursor: hand;");
        markAllBtn.setOnAction(e -> {
            AsyncUi.run(() -> {
                try {
                    NotificationService.getInstance().markAllAsRead(user.getId());
                } catch (Exception ignored) {}
                return null;
            }, res -> {
                drawer.getChildren().clear();
                show(anchorNode, onClaimOffer);
            }, err -> {});
        });

        header.getChildren().addAll(title, spacer, markAllBtn);

        // Content Area
        VBox feed = new VBox(10);
        ScrollPane sp = new ScrollPane(feed);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sp.setPrefHeight(420);

        Label loading = new Label("Loading notifications...");
        loading.setStyle("-fx-text-fill: #6B7280; -fx-padding: 20px;");
        feed.getChildren().add(loading);

        drawer.getChildren().addAll(header, sp);
        popup.getContent().add(drawer);

        // Fetch notifications
        AsyncUi.run(() -> {
            try {
                return NotificationService.getInstance().getRecentNotifications(user.getId(), 25);
            } catch (Exception e) {
                return List.<Notification>of();
            }
        }, list -> {
            feed.getChildren().clear();
            if (list.isEmpty()) {
                VBox emptyBox = new VBox(8);
                emptyBox.setAlignment(Pos.CENTER);
                emptyBox.setPadding(new Insets(30, 10, 30, 10));
                Label emptyIcon = new Label("🔔");
                emptyIcon.setStyle("-fx-font-size: 28px;");
                Label emptyLbl = new Label("No new notifications");
                emptyLbl.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 13px;");
                emptyBox.getChildren().addAll(emptyIcon, emptyLbl);
                feed.getChildren().add(emptyBox);
            } else {
                for (Notification n : list) {
                    feed.getChildren().add(buildNotificationCard(n, user.getId(), popup, onClaimOffer));
                }
            }
        }, err -> {
            feed.getChildren().clear();
            feed.getChildren().add(new Label("Failed to load notifications."));
        });

        javafx.geometry.Bounds bounds = anchorNode.localToScreen(anchorNode.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchorNode, bounds.getMaxX() - 380, bounds.getMaxY() + 8);
        } else {
            popup.show(anchorNode.getScene().getWindow());
        }
    }

    private static VBox buildNotificationCard(Notification n, int userId, Popup popup, Consumer<Integer> onClaimOffer) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: " + (n.isRead() ? "rgba(255,255,255,0.02)" : "rgba(139,92,246,0.08)") + "; " +
            "-fx-border-color: " + (n.getType() == NotificationType.WAITLIST_OFFER ? "rgba(245,158,11,0.5)" : "rgba(139,92,246,0.18)") + "; " +
            "-fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;"
        );

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(n.getType() == NotificationType.WAITLIST_OFFER ? "🎟" :
                               n.getType() == NotificationType.BOOKING_CONFIRMED ? "✅" : "🔔");
        icon.setStyle("-fx-font-size: 14px;");

        Label title = new Label(n.getTitle());
        title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: " +
                       (n.getType() == NotificationType.WAITLIST_OFFER ? "#FCD34D" : "#E2E8F0") + ";");
        HBox.setHgrow(title, Priority.ALWAYS);

        Label timeLbl = new Label(formatRelativeTime(n.getCreatedAt()));
        timeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        topRow.getChildren().addAll(icon, title, timeLbl);

        Label msg = new Label(n.getMessage());
        msg.setWrapText(true);
        msg.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1B5;");

        card.getChildren().addAll(topRow, msg);

        // Action buttons for WAITLIST_OFFER
        if (n.getType() == NotificationType.WAITLIST_OFFER && n.getRefId() != null) {
            HBox actionRow = new HBox(8);
            actionRow.setAlignment(Pos.CENTER_RIGHT);
            actionRow.setPadding(new Insets(6, 0, 0, 0));

            Button declineBtn = new Button("Decline");
            declineBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #EF4444; -fx-font-size: 11px; " +
                "-fx-border-color: rgba(239,68,68,0.4); -fx-border-radius: 6px; -fx-padding: 4px 8px; -fx-cursor: hand;"
            );
            declineBtn.setOnAction(e -> {
                declineBtn.setDisable(true);
                AsyncUi.run(() -> {
                    try {
                        WaitlistService.getInstance().declineOffer(n.getRefId(), userId);
                        NotificationService.getInstance().markAsRead(n.getId());
                    } catch (Exception ignored) {}
                    return null;
                }, res -> {
                    ToastManager.show(ToastManager.ToastType.INFO, "Offer Declined", "Seats offered to next person in queue.");
                    popup.hide();
                }, err -> {});
            });

            Button claimBtn = new Button("Claim Seats  →");
            claimBtn.getStyleClass().add("button-primary");
            claimBtn.setStyle("-fx-font-size: 11px; -fx-padding: 5px 12px; -fx-font-weight: 700;");
            claimBtn.setOnAction(e -> {
                claimBtn.setDisable(true);
                popup.hide();
                if (onClaimOffer != null) {
                    onClaimOffer.accept(n.getRefId());
                }
            });

            actionRow.getChildren().addAll(declineBtn, claimBtn);
            card.getChildren().add(actionRow);
        }

        return card;
    }

    private static String formatRelativeTime(LocalDateTime ldt) {
        if (ldt == null) return "";
        Duration d = Duration.between(ldt, LocalDateTime.now());
        long mins = d.toMinutes();
        if (mins < 1) return "just now";
        if (mins < 60) return mins + "m ago";
        long hrs = d.toHours();
        if (hrs < 24) return hrs + "h ago";
        return (hrs / 24) + "d ago";
    }
}
