package com.cineflow.ui.common;

import com.cineflow.model.User;
import com.cineflow.service.AuthService;
import com.cineflow.service.NotificationService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CineFlow v2 — Notification Bell Button with live pulsing unread badge.
 */
public class NotificationBell extends StackPane {

    private final Label bellIcon;
    private final Label badge;
    private ScheduledFuture<?> pollJob;

    public NotificationBell(Consumer<Integer> onClaimOffer) {
        setAlignment(Pos.CENTER);
        setStyle("-fx-cursor: hand;");

        bellIcon = new Label("🔔");
        bellIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: #E2E8F0; -fx-padding: 6px;");

        badge = new Label("0");
        badge.setStyle(
            "-fx-background-color: #EF4444; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 9px; " +
            "-fx-font-weight: 800; " +
            "-fx-background-radius: 10px; " +
            "-fx-padding: 1px 5px; " +
            "-fx-min-width: 15px; -fx-alignment: center;"
        );
        badge.setVisible(false);

        StackPane.setAlignment(badge, Pos.TOP_RIGHT);
        getChildren().addAll(bellIcon, badge);

        setOnMouseClicked(e -> {
            NotificationDrawer.show(this, onClaimOffer);
            // Refresh count
            refreshUnreadCount();
        });

        startPolling();
    }

    public void refreshUnreadCount() {
        User user = AuthService.getCurrentUser();
        if (user == null) {
            badge.setVisible(false);
            return;
        }

        AsyncUi.run(() -> NotificationService.getInstance().getUnreadCount(user.getId()),
                count -> {
                    if (count > 0) {
                        badge.setText(count > 9 ? "9+" : String.valueOf(count));
                        badge.setVisible(true);
                        AnimationUtil.pulseGlow(badge);
                    } else {
                        badge.setVisible(false);
                    }
                },
                err -> badge.setVisible(false)
        );
    }

    private void startPolling() {
        refreshUnreadCount();
        pollJob = BackgroundJobs.scheduleAtFixedRate(
                () -> Platform.runLater(this::refreshUnreadCount),
                10,
                15,
                TimeUnit.SECONDS
        );
    }
}
