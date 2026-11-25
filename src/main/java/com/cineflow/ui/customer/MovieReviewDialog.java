package com.cineflow.ui.customer;

import com.cineflow.model.Booking;
import com.cineflow.model.MovieReview;
import com.cineflow.model.ReviewSentiment;
import com.cineflow.service.AuthService;
import com.cineflow.service.MovieReviewService;
import com.cineflow.service.SentimentAnalyzer;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.AlertHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * CineFlow v3 — Verified Customer Movie Review & AI Sentiment Dialog.
 * Interactive 5-star rating with real-time explainable lexical sentiment detection.
 */
public class MovieReviewDialog {

    private final Booking booking;
    private final MovieReviewService reviewService;
    private final Runnable onReviewSubmitted;

    private Stage dialogStage;
    private int selectedRating = 5;
    private final Label[] starLabels = new Label[5];
    private TextArea reviewTextArea;
    private Label sentimentBadgeLbl;
    private Label sentimentScoreLbl;
    private Label sentimentKeywordsLbl;
    private Button submitBtn;

    public MovieReviewDialog(Booking booking, Runnable onReviewSubmitted) {
        this.booking = booking;
        this.reviewService = new MovieReviewService();
        this.onReviewSubmitted = onReviewSubmitted;
    }

    public void show() {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("CineFlow  •  Verified Movie Review");
        dialogStage.setResizable(false);
        dialogStage.setMinWidth(560);

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #0D0B18;");

        // Top Accent Bar
        Region accentBar = new Region();
        accentBar.setPrefHeight(4);
        accentBar.setStyle("-fx-background-color: linear-gradient(to right, #8B5CF6, #00C2FF);");
        content.getChildren().add(accentBar);

        VBox mainBox = new VBox(16);
        mainBox.setPadding(new Insets(22, 28, 22, 28));
        mainBox.setStyle("-fx-background-color: #0D0B18; -fx-border-color: rgba(139,92,246,0.3); -fx-border-width: 0 1px 1px 1px;");

        // Header
        mainBox.getChildren().add(createHeader());

        // Star Rating Selector
        mainBox.getChildren().add(createRatingRow());

        // Review Text Area
        mainBox.getChildren().add(createReviewTextInput());

        // Live Sentiment Analysis Card
        mainBox.getChildren().add(createSentimentPreviewCard());

        // Action Buttons
        mainBox.getChildren().add(createActionRow());

        content.getChildren().add(mainBox);

        Scene scene = new Scene(content);
        scene.setFill(Color.web("#0D0B18"));
        try {
            String css = getClass().getResource("/css/styles.css") != null
                    ? getClass().getResource("/css/styles.css").toExternalForm() : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        dialogStage.setScene(scene);

        // Pre-fill if review already exists
        int userId = AuthService.getCurrentUser() != null ? AuthService.getCurrentUser().getId() : -1;
        if (userId > 0 && booking.getMovieId() > 0) {
            AsyncUi.run(
                    () -> reviewService.getUserReviewForMovie(userId, booking.getMovieId()),
                    existing -> {
                        if (existing != null) {
                            setRating(existing.getRating());
                            reviewTextArea.setText(existing.getReviewText());
                            updateLiveSentiment();
                            submitBtn.setText("Update Review");
                        }
                    },
                    err -> {}
            );
        }

        updateLiveSentiment();
        dialogStage.show();
    }

    private HBox createHeader() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);

        VBox meta = new VBox(3);
        Label title = new Label("⭐  Verified Audience Review");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label sub = new Label(booking.getMovieTitle() + "  •  Booking #" + booking.getId());
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
        meta.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("VERIFIED TICKET HOLDER");
        badge.setStyle(
                "-fx-background-color: rgba(16,185,129,0.12); -fx-text-fill: #10B981; " +
                "-fx-border-color: rgba(16,185,129,0.35); -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                "-fx-padding: 3px 9px; -fx-font-size: 10px; -fx-font-weight: 700;"
        );

        box.getChildren().addAll(meta, spacer, badge);
        return box;
    }

    private VBox createRatingRow() {
        VBox box = new VBox(6);
        Label lbl = new Label("Your Rating:");
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");

        HBox stars = new HBox(8);
        stars.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < 5; i++) {
            final int starIndex = i + 1;
            Label star = new Label("★");
            star.setStyle("-fx-font-size: 28px; -fx-text-fill: #F59E0B; -fx-cursor: hand;");
            star.setOnMouseClicked(e -> {
                setRating(starIndex);
                updateLiveSentiment();
            });
            starLabels[i] = star;
            stars.getChildren().add(star);
        }

        box.getChildren().addAll(lbl, stars);
        return box;
    }

    private void setRating(int r) {
        selectedRating = r;
        for (int i = 0; i < 5; i++) {
            if (i < r) {
                starLabels[i].setStyle("-fx-font-size: 28px; -fx-text-fill: #F59E0B; -fx-cursor: hand;");
            } else {
                starLabels[i].setStyle("-fx-font-size: 28px; -fx-text-fill: #4B5563; -fx-cursor: hand;");
            }
        }
    }

    private VBox createReviewTextInput() {
        VBox box = new VBox(6);
        Label lbl = new Label("Write your experience & feedback:");
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");

        reviewTextArea = new TextArea();
        reviewTextArea.setPromptText("Share your honest review of the movie, pacing, performances, visuals...");
        reviewTextArea.setPrefRowCount(4);
        reviewTextArea.setWrapText(true);
        reviewTextArea.setStyle("-fx-control-inner-background: #131021; -fx-text-fill: #F8FAFC; -fx-border-color: rgba(139,92,246,0.3); -fx-border-radius: 8px;");

        reviewTextArea.textProperty().addListener((obs, oldVal, newVal) -> updateLiveSentiment());

        box.getChildren().addAll(lbl, reviewTextArea);
        return box;
    }

    private VBox createSentimentPreviewCard() {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(
                "-fx-background-color: rgba(139,92,246,0.06); " +
                "-fx-border-color: rgba(139,92,246,0.22); -fx-border-radius: 10px; -fx-background-radius: 10px;"
        );

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("AI Sentiment Analysis (Lexical):");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #94A3B8;");

        sentimentBadgeLbl = new Label("POSITIVE 🟢");
        sentimentBadgeLbl.setStyle(
                "-fx-background-color: rgba(16,185,129,0.18); -fx-text-fill: #10B981; " +
                "-fx-border-color: rgba(16,185,129,0.35); -fx-border-radius: 10px; -fx-background-radius: 10px; " +
                "-fx-padding: 2px 8px; -fx-font-size: 10px; -fx-font-weight: 800;"
        );

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        sentimentScoreLbl = new Label("Score: +0.60");
        sentimentScoreLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #C4B5FD;");

        top.getChildren().addAll(title, sentimentBadgeLbl, sp, sentimentScoreLbl);

        sentimentKeywordsLbl = new Label("Key terms detected: None");
        sentimentKeywordsLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        card.getChildren().addAll(top, sentimentKeywordsLbl);
        return card;
    }

    private HBox createActionRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 0, 0, 0));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button-secondary");
        cancelBtn.setOnAction(e -> dialogStage.close());

        submitBtn = new Button("Submit Verified Review");
        submitBtn.getStyleClass().add("button-primary");
        submitBtn.setOnAction(e -> submitReview());

        row.getChildren().addAll(cancelBtn, submitBtn);
        return row;
    }

    private void updateLiveSentiment() {
        String text = reviewTextArea != null ? reviewTextArea.getText() : "";
        SentimentAnalyzer.SentimentAnalysisResult result = reviewService.previewSentiment(text, selectedRating);

        ReviewSentiment s = result.getSentiment();
        sentimentBadgeLbl.setText(s.getLabel().toUpperCase() + " " + s.getBadge());
        String hex = s.getColorHex();
        sentimentBadgeLbl.setStyle(
                "-fx-background-color: " + hex + "22; -fx-text-fill: " + hex + "; " +
                "-fx-border-color: " + hex + "55; -fx-border-radius: 10px; -fx-background-radius: 10px; " +
                "-fx-padding: 2px 8px; -fx-font-size: 10px; -fx-font-weight: 800;"
        );

        sentimentScoreLbl.setText(String.format("Score: %+.2f", result.getCompositeScore()));
        if (!result.getExtractedKeywords().isEmpty()) {
            sentimentKeywordsLbl.setText("Key terms detected: " + String.join(", ", result.getExtractedKeywords()));
        } else {
            sentimentKeywordsLbl.setText("Key terms detected: (Neutral rating-weighted)");
        }
    }

    private void submitReview() {
        int userId = AuthService.getCurrentUser() != null ? AuthService.getCurrentUser().getId() : -1;
        if (userId <= 0) {
            AlertHelper.showError("Auth Error", "Session Expired", "Please log in again.");
            return;
        }

        String text = reviewTextArea.getText().trim();
        if (text.isEmpty()) {
            AlertHelper.showWarning("Review Required", "Empty Review", "Please provide a few words sharing your feedback.");
            return;
        }

        submitBtn.setDisable(true);
        submitBtn.setText("Submitting...");

        AsyncUi.run(
                () -> reviewService.submitReview(userId, booking.getMovieId(), selectedRating, text),
                rev -> {
                    ToastManager.showSuccess("Review Submitted", "Your review was posted! Sentiment: " + rev.getSentiment().getLabel());
                    dialogStage.close();
                    if (onReviewSubmitted != null) onReviewSubmitted.run();
                },
                err -> {
                    submitBtn.setDisable(false);
                    submitBtn.setText("Submit Verified Review");
                    AlertHelper.showError("Submission Failed", "Could not submit review", err.getMessage());
                }
        );
    }
}
