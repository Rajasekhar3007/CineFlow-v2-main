package com.cineflow.ui.common;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * CineFlow v2 Toast Notification Manager.
 * Displays non-blocking, glassmorphic toast notifications stacked at the top-right of the window.
 */
public class ToastManager {

    public enum ToastType {
        SUCCESS("#22C55E", "✓"),
        INFO("#00C2FF", "ℹ"),
        WARNING("#F59E0B", "⚠"),
        ERROR("#EF4444", "✕");

        final String colorHex;
        final String icon;

        ToastType(String colorHex, String icon) {
            this.colorHex = colorHex;
            this.icon = icon;
        }
    }

    private static final int MAX_TOASTS = 3;
    private static final VBox toastContainer = new VBox(10);
    private static Popup toastPopup;

    static {
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPadding(new Insets(16, 20, 16, 20));
        toastContainer.setPickOnBounds(false);
    }

    public static void showSuccess(String title, String message) {
        show(ToastType.SUCCESS, title, message);
    }

    public static void showInfo(String title, String message) {
        show(ToastType.INFO, title, message);
    }

    public static void showWarning(String title, String message) {
        show(ToastType.WARNING, title, message);
    }

    public static void showError(String title, String message) {
        show(ToastType.ERROR, title, message);
    }

    public static void show(ToastType type, String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message));
            return;
        }

        Stage stage = NavigationManager.getPrimaryStage();
        if (stage == null || !stage.isShowing()) return;

        if (toastPopup == null) {
            toastPopup = new Popup();
            toastPopup.setAutoFix(false);
            toastPopup.getContent().add(toastContainer);
        }

        if (!toastPopup.isShowing()) {
            toastPopup.show(stage);
        }

        updatePopupPosition(stage);

        // Cap at MAX_TOASTS
        if (toastContainer.getChildren().size() >= MAX_TOASTS) {
            toastContainer.getChildren().remove(0);
        }

        HBox toast = createToastNode(type, title, message);
        toastContainer.getChildren().add(toast);

        // Slide in from right
        toast.setTranslateX(260);
        toast.setOpacity(0.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(260), toast);
        tt.setToX(0);
        tt.setInterpolator(Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0));

        FadeTransition ft = new FadeTransition(Duration.millis(260), toast);
        ft.setToValue(1.0);

        ParallelTransition enter = new ParallelTransition(tt, ft);
        enter.play();

        // Auto dismiss after 4 seconds
        PauseTransition pause = new PauseTransition(Duration.millis(4200));
        pause.setOnFinished(e -> dismissToast(toast));
        pause.play();

        toast.setOnMouseClicked(e -> {
            pause.stop();
            dismissToast(toast);
        });
    }

    private static void updatePopupPosition(Stage stage) {
        if (toastPopup != null && stage != null) {
            toastPopup.setX(stage.getX() + stage.getWidth() - 380);
            toastPopup.setY(stage.getY() + 45);
        }
    }

    private static HBox createToastNode(ToastType type, String title, String message) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setMinWidth(320);
        card.setMaxWidth(360);
        card.setStyle(
                "-fx-background-color: rgba(21, 19, 35, 0.95); " +
                "-fx-background-radius: 12px; " +
                "-fx-border-color: " + type.colorHex + "; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 12px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 16, 0, 0, 4); " +
                "-fx-cursor: hand;"
        );

        // Badge icon
        StackPane iconBadge = new StackPane();
        Circle circle = new Circle(14, Color.web(type.colorHex, 0.22));
        Label iconLbl = new Label(type.icon);
        iconLbl.setStyle("-fx-text-fill: " + type.colorHex + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        iconBadge.getChildren().addAll(circle, iconLbl);

        VBox textCol = new VBox(2);
        HBox.setHgrow(textCol, Priority.ALWAYS);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-font-size: 13px;");

        Label msgLbl = new Label(message);
        msgLbl.setWrapText(true);
        msgLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");

        textCol.getChildren().addAll(titleLbl, msgLbl);
        card.getChildren().addAll(iconBadge, textCol);

        return card;
    }

    private static void dismissToast(HBox toast) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(200), toast);
        tt.setToX(260);

        FadeTransition ft = new FadeTransition(Duration.millis(200), toast);
        ft.setToValue(0.0);

        ParallelTransition exit = new ParallelTransition(tt, ft);
        exit.setOnFinished(e -> {
            toastContainer.getChildren().remove(toast);
            if (toastContainer.getChildren().isEmpty() && toastPopup != null) {
                toastPopup.hide();
            }
        });
        exit.play();
    }
}
