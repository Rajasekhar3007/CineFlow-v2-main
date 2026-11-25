package com.cineflow.ui.customer;

import com.cineflow.model.SeatPreference;
import com.cineflow.model.Showtime;
import com.cineflow.model.User;
import com.cineflow.model.WaitlistEntry;
import com.cineflow.service.AuthService;
import com.cineflow.service.WaitlistService;
import com.cineflow.ui.common.AnimationUtil;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * CineFlow v2 — Waitlist Join Dialog.
 * Enables guests to enqueue into the automated seat-reallocation queue for sold-out showtimes.
 */
public class WaitlistDialog {

    public static void show(Showtime showtime, Runnable onJoined) {
        User user = AuthService.getCurrentUser();
        if (user == null) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox card = new VBox(18);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(28));
        card.setPrefWidth(440);
        card.setStyle(
            "-fx-background-color: #12101F; " +
            "-fx-border-color: rgba(139, 92, 246, 0.4); " +
            "-fx-border-width: 1.5px; " +
            "-fx-border-radius: 16px; " +
            "-fx-background-radius: 16px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 8);"
        );

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label bellIcon = new Label("⏳");
        bellIcon.setStyle("-fx-font-size: 24px;");

        VBox titleBox = new VBox(3);
        Label titleLbl = new Label("Join Show Waitlist");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");
        Label subLbl = new Label(showtime.getMovieTitle() + " • " + showtime.getFormattedStartTime());
        subLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1B5;");
        titleBox.getChildren().addAll(titleLbl, subLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B7280; -fx-font-size: 14px; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        header.getChildren().addAll(bellIcon, titleBox, spacer, closeBtn);

        // Explanatory banner
        Label infoBanner = new Label("When booked tickets are cancelled, CineFlow automatically allocates the best available seats to the next person in line and reserves them for 10 minutes.");
        infoBanner.setWrapText(true);
        infoBanner.setStyle(
            "-fx-background-color: rgba(139, 92, 246, 0.1); " +
            "-fx-text-fill: #C4B5FD; " +
            "-fx-padding: 10px 14px; " +
            "-fx-background-radius: 8px; " +
            "-fx-font-size: 12px;"
        );

        // Stepper: Seats wanted
        HBox stepperRow = new HBox(14);
        stepperRow.setAlignment(Pos.CENTER_LEFT);
        Label seatsLbl = new Label("Seats Wanted:");
        seatsLbl.setStyle("-fx-text-fill: #E2E8F0; -fx-font-weight: 600; -fx-font-size: 13px;");

        Button minusBtn = new Button("−");
        minusBtn.getStyleClass().add("button-secondary");
        Label countLbl = new Label("2");
        countLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #00C2FF; -fx-min-width: 28px; -fx-alignment: center;");
        Button plusBtn = new Button("+");
        plusBtn.getStyleClass().add("button-secondary");

        final int[] seatsCount = {2};
        minusBtn.setOnAction(e -> {
            if (seatsCount[0] > 1) {
                seatsCount[0]--;
                countLbl.setText(String.valueOf(seatsCount[0]));
            }
        });
        plusBtn.setOnAction(e -> {
            if (seatsCount[0] < 8) {
                seatsCount[0]++;
                countLbl.setText(String.valueOf(seatsCount[0]));
            }
        });

        stepperRow.getChildren().addAll(seatsLbl, minusBtn, countLbl, plusBtn);

        // Seating Preference Selector
        VBox prefBox = new VBox(8);
        Label prefLbl = new Label("Seat Preference:");
        prefLbl.setStyle("-fx-text-fill: #E2E8F0; -fx-font-weight: 600; -fx-font-size: 13px;");

        ComboBox<SeatPreference> prefCombo = new ComboBox<>();
        prefCombo.getItems().addAll(SeatPreference.values());
        prefCombo.setValue(SeatPreference.BEST_OVERALL);
        prefCombo.setMaxWidth(Double.MAX_VALUE);
        prefBox.getChildren().addAll(prefLbl, prefCombo);

        // Status indicator
        Label statusLbl = new Label();
        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #EF4444;");

        // CTA Button
        Button joinBtn = new Button("Join Waitlist Queue  →");
        joinBtn.getStyleClass().add("button-primary");
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setStyle("-fx-font-size: 14px; -fx-padding: 12px;");

        joinBtn.setOnAction(e -> {
            joinBtn.setDisable(true);
            statusLbl.setText("Adding to queue...");
            statusLbl.setStyle("-fx-text-fill: #38BDF8;");

            AsyncUi.run(() -> {
                try {
                    return WaitlistService.getInstance().joinWaitlist(
                            user.getId(),
                            showtime.getId(),
                            seatsCount[0],
                            prefCombo.getValue()
                    );
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }, entry -> {
                dialog.close();
                ToastManager.show(
                        ToastManager.ToastType.SUCCESS,
                        "Waitlist Joined!",
                        "You are in line (" + entry.getSeatsWanted() + " seats). You will receive an offer the moment seats open!"
                );
                if (onJoined != null) onJoined.run();
            }, err -> {
                joinBtn.setDisable(false);
                statusLbl.setText(err.getMessage() != null ? err.getMessage() : "Failed to join waitlist.");
                statusLbl.setStyle("-fx-text-fill: #EF4444;");
                AnimationUtil.shake(card);
            });
        });

        card.getChildren().addAll(header, infoBanner, stepperRow, prefBox, statusLbl, joinBtn);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.65);");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(WaitlistDialog.class.getResource("/css/styles.css").toExternalForm());

        dialog.setScene(scene);
        AnimationUtil.fadeInUp(card, 0);
        dialog.show();
    }
}
