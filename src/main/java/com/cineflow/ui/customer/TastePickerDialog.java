package com.cineflow.ui.customer;

import com.cineflow.model.User;
import com.cineflow.model.UserPreference;
import com.cineflow.service.AuthService;
import com.cineflow.service.RecommendationService;
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

import java.sql.SQLException;
import java.util.*;

/**
 * CineFlow v2 — Interactive Movie Taste Profile Onboarding / Picker.
 * Features glassmorphic animated selectable chips for genres, languages, and min rating.
 */
public class TastePickerDialog {

    private static final String[] GENRES = {
        "Sci-Fi", "Action", "Drama", "Thriller", "Comedy", "Animation", "Horror", "Adventure"
    };

    private static final String[] LANGUAGES = {
        "English", "Hindi", "Telugu", "Tamil", "Malayalam", "Kannada"
    };

    public static void show(Runnable onSaved) {
        User user = AuthService.getCurrentUser();
        if (user == null) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox card = new VBox(20);
        card.setPadding(new Insets(28));
        card.setPrefWidth(540);
        card.setStyle(
            "-fx-background-color: #12101F; " +
            "-fx-border-color: rgba(139, 92, 246, 0.45); " +
            "-fx-border-width: 1.5px; " +
            "-fx-border-radius: 16px; " +
            "-fx-background-radius: 16px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 28, 0, 0, 10);"
        );

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("✨");
        icon.setStyle("-fx-font-size: 24px;");

        VBox titleBox = new VBox(3);
        Label title = new Label("Personalise Your CineFlow");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");
        Label subtitle = new Label("Select your favourite genres and languages for tailor-made recommendations");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1B5;");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6B7280; -fx-font-size: 14px; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        header.getChildren().addAll(icon, titleBox, sp, closeBtn);

        // Selected Sets
        Set<String> selectedGenres = new LinkedHashSet<>();
        Set<String> selectedLanguages = new LinkedHashSet<>();

        // Genre Section
        VBox genreBox = new VBox(8);
        Label genreLbl = new Label("Favourite Genres (tap to select):");
        genreLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        FlowPane genreChips = new FlowPane(8, 8);

        Map<String, Button> genreBtnMap = new HashMap<>();
        for (String g : GENRES) {
            Button chip = createChip(g, selectedGenres);
            genreBtnMap.put(g.toUpperCase(), chip);
            genreChips.getChildren().add(chip);
        }
        genreBox.getChildren().addAll(genreLbl, genreChips);

        // Language Section
        VBox langBox = new VBox(8);
        Label langLbl = new Label("Preferred Languages:");
        langLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        FlowPane langChips = new FlowPane(8, 8);

        Map<String, Button> langBtnMap = new HashMap<>();
        for (String l : LANGUAGES) {
            Button chip = createChip(l, selectedLanguages);
            langBtnMap.put(l.toUpperCase(), chip);
            langChips.getChildren().add(chip);
        }
        langBox.getChildren().addAll(langLbl, langChips);

        // Rating Slider
        VBox ratingBox = new VBox(6);
        HBox ratingHeader = new HBox(10);
        ratingHeader.setAlignment(Pos.CENTER_LEFT);
        Label ratingLbl = new Label("Minimum Rating Threshold:");
        ratingLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        Label ratingValLbl = new Label("★ 7.5+");
        ratingValLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #22C55E;");
        ratingHeader.getChildren().addAll(ratingLbl, ratingValLbl);

        Slider ratingSlider = new Slider(6.0, 9.0, 7.5);
        ratingSlider.setBlockIncrement(0.5);
        ratingSlider.valueProperty().addListener((obs, o, n) -> {
            ratingValLbl.setText(String.format("★ %.1f+", n.doubleValue()));
        });
        ratingBox.getChildren().addAll(ratingHeader, ratingSlider);

        // Load existing preferences if available
        RecommendationService recService = new RecommendationService();
        AsyncUi.run(() -> {
            try {
                return recService.getUserPreference(user.getId()).orElse(null);
            } catch (Exception e) {
                return null;
            }
        }, existing -> {
            if (existing != null) {
                if (existing.getGenres() != null) {
                    for (String g : existing.getGenres()) {
                        Button b = genreBtnMap.get(g.toUpperCase());
                        if (b != null) toggleChip(b, g, selectedGenres, true);
                    }
                }
                if (existing.getLanguages() != null) {
                    for (String l : existing.getLanguages()) {
                        Button b = langBtnMap.get(l.toUpperCase());
                        if (b != null) toggleChip(b, l, selectedLanguages, true);
                    }
                }
                if (existing.getMinRating() > 0) {
                    ratingSlider.setValue(existing.getMinRating());
                }
            }
        }, err -> {});

        // Save Button
        Button saveBtn = new Button("Save Movie Taste & Personalise  →");
        saveBtn.getStyleClass().add("button-primary");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setStyle("-fx-font-size: 14px; -fx-padding: 12px;");

        saveBtn.setOnAction(e -> {
            saveBtn.setDisable(true);
            UserPreference pref = new UserPreference(
                    user.getId(),
                    selectedGenres,
                    selectedLanguages,
                    ratingSlider.getValue()
            );

            AsyncUi.run(() -> {
                try {
                    return recService.saveUserPreference(pref);
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }, ok -> {
                dialog.close();
                ToastManager.show(
                        ToastManager.ToastType.SUCCESS,
                        "Taste Profile Saved!",
                        "Your personalised recommendations have been re-calibrated."
                );
                if (onSaved != null) onSaved.run();
            }, err -> {
                saveBtn.setDisable(false);
                ToastManager.show(ToastManager.ToastType.ERROR, "Error", "Failed to save preferences.");
            });
        });

        card.getChildren().addAll(header, genreBox, langBox, ratingBox, saveBtn);

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.65);");
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(TastePickerDialog.class.getResource("/css/styles.css").toExternalForm());

        dialog.setScene(scene);
        AnimationUtil.fadeInUp(card, 0);
        dialog.show();
    }

    private static Button createChip(String text, Set<String> selectionSet) {
        Button chip = new Button(text);
        chip.setStyle(
            "-fx-background-color: rgba(139,92,246,0.10); " +
            "-fx-text-fill: #CBD5E1; " +
            "-fx-font-size: 12px; -fx-font-weight: 600; " +
            "-fx-border-color: rgba(139,92,246,0.25); " +
            "-fx-border-radius: 20px; -fx-background-radius: 20px; " +
            "-fx-padding: 6px 14px; -fx-cursor: hand;"
        );

        chip.setOnAction(e -> {
            boolean isSelected = selectionSet.contains(text);
            toggleChip(chip, text, selectionSet, !isSelected);
        });

        return chip;
    }

    private static void toggleChip(Button chip, String text, Set<String> selectionSet, boolean select) {
        if (select) {
            selectionSet.add(text);
            chip.setStyle(
                "-fx-background-color: linear-gradient(to right, #8B5CF6, #6D28D9); " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 12px; -fx-font-weight: 700; " +
                "-fx-border-color: #A78BFA; " +
                "-fx-border-radius: 20px; -fx-background-radius: 20px; " +
                "-fx-padding: 6px 14px; -fx-cursor: hand; " +
                "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.45), 10, 0, 0, 2);"
            );
        } else {
            selectionSet.remove(text);
            chip.setStyle(
                "-fx-background-color: rgba(139,92,246,0.10); " +
                "-fx-text-fill: #CBD5E1; " +
                "-fx-font-size: 12px; -fx-font-weight: 600; " +
                "-fx-border-color: rgba(139,92,246,0.25); " +
                "-fx-border-radius: 20px; -fx-background-radius: 20px; " +
                "-fx-padding: 6px 14px; -fx-cursor: hand;"
            );
        }
    }
}
