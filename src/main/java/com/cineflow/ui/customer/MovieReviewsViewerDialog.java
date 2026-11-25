package com.cineflow.ui.customer;

import com.cineflow.model.Movie;
import com.cineflow.model.MovieReview;
import com.cineflow.service.MovieReviewService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.util.AlertHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * CineFlow v3 — Modal Dialog to view verified audience reviews and sentiment analytics for a movie.
 */
public class MovieReviewsViewerDialog {

    private final Movie movie;
    private final MovieReviewService reviewService;
    private Stage dialogStage;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public MovieReviewsViewerDialog(Movie movie) {
        this.movie = movie;
        this.reviewService = new MovieReviewService();
    }

    public void show() {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("CineFlow  •  Verified Audience Sentiment & Reviews");
        dialogStage.setResizable(false);
        dialogStage.setMinWidth(600);
        dialogStage.setMinHeight(520);

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #0D0B18;");

        Region accentBar = new Region();
        accentBar.setPrefHeight(4);
        accentBar.setStyle("-fx-background-color: linear-gradient(to right, #10B981, #00C2FF);");
        content.getChildren().add(accentBar);

        VBox mainBox = new VBox(16);
        mainBox.setPadding(new Insets(20, 24, 20, 24));
        mainBox.setStyle("-fx-background-color: #0D0B18; -fx-border-color: rgba(139,92,246,0.3); -fx-border-width: 0 1px 1px 1px;");

        // Header with stats
        VBox headerBox = createHeader();
        mainBox.getChildren().add(headerBox);

        // Reviews list scroll pane
        VBox reviewsListBox = new VBox(10);
        ScrollPane sp = new ScrollPane(reviewsListBox);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        AsyncUi.run(
                () -> reviewService.getReviewsForMovie(movie.getId()),
                reviews -> {
                    reviewsListBox.getChildren().clear();
                    if (reviews.isEmpty()) {
                        Label empty = new Label("No verified audience reviews submitted yet. Be the first ticket holder to review!");
                        empty.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-padding: 30px;");
                        reviewsListBox.getChildren().add(empty);
                    } else {
                        for (MovieReview rev : reviews) {
                            reviewsListBox.getChildren().add(createReviewCard(rev));
                        }
                    }
                },
                err -> AlertHelper.showError("Error", "Could not load reviews", err.getMessage())
        );

        mainBox.getChildren().add(sp);

        // Close button
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-secondary");
        closeBtn.setOnAction(e -> dialogStage.close());
        bottom.getChildren().add(closeBtn);
        mainBox.getChildren().add(bottom);

        content.getChildren().add(mainBox);

        Scene scene = new Scene(content);
        scene.setFill(Color.web("#0D0B18"));
        try {
            String css = getClass().getResource("/css/styles.css") != null
                    ? getClass().getResource("/css/styles.css").toExternalForm() : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        dialogStage.setScene(scene);
        dialogStage.show();
    }

    private VBox createHeader() {
        VBox box = new VBox(6);
        Label title = new Label("💬  Audience Sentiment: " + movie.getTitle());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        HBox statsRow = new HBox(12);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        Label statsLbl = new Label("Loading sentiment stats...");
        statsLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
        statsRow.getChildren().add(statsLbl);

        AsyncUi.run(
                () -> reviewService.getMovieReviewStats(movie.getId()),
                stats -> {
                    int total = (int) stats.getOrDefault("totalReviews", 0);
                    double avg = (double) stats.getOrDefault("avgRating", 0.0);
                    double pct = (double) stats.getOrDefault("positivePct", 100.0);

                    statsLbl.setText(String.format("⭐ %.1f / 5.0 Rating  •  %d Verified Reviews  •  %.0f%% Positive Audience Sentiment",
                            avg, total, pct));
                    statsLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #10B981;");
                },
                err -> {}
        );

        box.getChildren().addAll(title, statsRow);
        return box;
    }

    private VBox createReviewCard(MovieReview review) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setStyle(
                "-fx-background-color: rgba(255,255,255,0.03); " +
                "-fx-border-color: rgba(139,92,246,0.18); -fx-border-width: 1px; " +
                "-fx-background-radius: 10px; -fx-border-radius: 10px;"
        );

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label author = new Label(review.getAuthorName() != null ? review.getAuthorName() : "Verified Guest");
        author.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #F1F5F9;");

        Label verifiedPill = new Label("✓ VERIFIED TICKET");
        verifiedPill.setStyle(
                "-fx-background-color: rgba(16,185,129,0.12); -fx-text-fill: #10B981; " +
                "-fx-font-size: 9px; -fx-font-weight: 800; -fx-padding: 2px 6px; -fx-background-radius: 8px;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Star rating
        Label stars = new Label("★".repeat(review.getRating()) + "☆".repeat(Math.max(0, 5 - review.getRating())));
        stars.setStyle("-fx-font-size: 14px; -fx-text-fill: #F59E0B;");

        // Sentiment badge
        Label sentimentPill = new Label(review.getSentiment().getLabel() + " " + review.getSentiment().getBadge());
        String hex = review.getSentiment().getColorHex();
        sentimentPill.setStyle(
                "-fx-background-color: " + hex + "22; -fx-text-fill: " + hex + "; " +
                "-fx-border-color: " + hex + "55; -fx-border-radius: 10px; -fx-background-radius: 10px; " +
                "-fx-padding: 2px 8px; -fx-font-size: 10px; -fx-font-weight: 700;"
        );

        top.getChildren().addAll(author, verifiedPill, spacer, stars, sentimentPill);

        Label body = new Label(review.getReviewText());
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2px;");

        Label dateLbl = new Label(review.getCreatedAt() != null ? review.getCreatedAt().format(DATE_FMT) : "");
        dateLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");

        card.getChildren().addAll(top, body, dateLbl);
        return card;
    }
}
