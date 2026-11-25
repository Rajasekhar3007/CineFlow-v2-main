package com.cineflow.ui.customer;

import com.cineflow.model.Movie;
import com.cineflow.model.MovieRecommendation;
import com.cineflow.model.User;
import com.cineflow.service.AuthService;
import com.cineflow.service.MovieService;
import com.cineflow.service.RecommendationService;
import com.cineflow.ui.common.AnimationUtil;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.util.AlertHelper;
import com.cineflow.util.PosterHelper;
import javafx.animation.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * CineFlow v2 — Premium Movie Discovery View.
 * Includes Personalised "For You" Carousel with match percentage,
 * explainability tags, Taste Profile onboarding picker, and animated search.
 */
public class MovieCatalogView {

    private final MovieService movieService;
    private final RecommendationService recommendationService;
    private final Consumer<Movie> onMovieSelected;

    private HBox forYouCardsBox;
    private FlowPane movieGrid;
    private TextField searchField;
    private ComboBox<String> genreFilter;
    private ComboBox<String> langFilter;
    private DatePicker screeningDateFilter;
    private Label countLabel;

    public MovieCatalogView(Consumer<Movie> onMovieSelected) {
        this.movieService = new MovieService();
        this.recommendationService = new RecommendationService();
        this.onMovieSelected = onMovieSelected;
    }

    public Parent getView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("app-container");

        // Top Hero header
        VBox heroBar = createHeroBar();
        root.getChildren().add(heroBar);

        // Main scrollable page body
        VBox pageBody = new VBox(16);
        pageBody.setPadding(new Insets(18, 28, 28, 28));

        // 1. Personalised "For You" Section
        VBox forYouSection = createForYouSection();
        pageBody.getChildren().add(forYouSection);

        // Divider
        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setStyle("-fx-background-color: rgba(139,92,246,0.18); -fx-margin: 8px 0;");
        pageBody.getChildren().add(divider);

        // 2. Section Header: All Movies + Filter toolbar
        VBox catalogHeader = createCatalogHeader();
        pageBody.getChildren().add(catalogHeader);

        // 3. Movie Grid (instantiated before filterBar so search listeners can populate it)
        movieGrid = new FlowPane(20, 20);
        movieGrid.setPadding(new Insets(12, 0, 16, 0));
        movieGrid.setAlignment(Pos.TOP_LEFT);

        HBox filterBar = createFilterBar();
        pageBody.getChildren().addAll(filterBar, movieGrid);

        ScrollPane scroll = new ScrollPane(pageBody);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().add(scroll);

        // Load data
        loadRecommendations();
        applySearch();

        return root;
    }

    /* ---------------------------------------------------------------- */
    /* HERO BAR                                                           */
    /* ---------------------------------------------------------------- */
    private VBox createHeroBar() {
        VBox hero = new VBox(6);
        hero.setPadding(new Insets(24, 28, 16, 28));
        hero.setStyle(
            "-fx-background-color: linear-gradient(to right, rgba(139,92,246,0.12), rgba(0,194,255,0.06), transparent);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        HBox titleRow = new HBox(14);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("CineFlow Multiplex");
        sectionTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");

        Label dotSep = new Label("✦");
        dotSep.setStyle("-fx-font-size: 14px; -fx-text-fill: #8B5CF6;");

        Label subtitleLbl = new Label("Personalised Discovery & Live Seat Allocation");
        subtitleLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1AA;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button tasteBtn = new Button("✦  Edit Movie Taste");
        tasteBtn.getStyleClass().add("button-primary");
        tasteBtn.setStyle("-fx-font-size: 12px; -fx-padding: 7px 14px;");
        tasteBtn.setOnAction(e -> TastePickerDialog.show(this::loadRecommendations));

        titleRow.getChildren().addAll(sectionTitle, dotSep, subtitleLbl, spacer, tasteBtn);
        hero.getChildren().add(titleRow);
        return hero;
    }

    /* ---------------------------------------------------------------- */
    /* PERSONALISED "FOR YOU" SECTION                                      */
    /* ---------------------------------------------------------------- */
    private VBox createForYouSection() {
        VBox section = new VBox(12);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("✨");
        icon.setStyle("-fx-font-size: 18px;");

        Label title = new Label("Recommended For You");
        title.setStyle("-fx-font-size: 19px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label subtitle = new Label("Tailored based on your taste preferences & viewing history");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1B5;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button prefBtn = new Button("Personalise Taste ⚙");
        prefBtn.setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #00C2FF; -fx-font-size: 12px; " +
            "-fx-border-color: rgba(0,194,255,0.4); -fx-border-radius: 8px; -fx-padding: 4px 10px; -fx-cursor: hand;"
        );
        prefBtn.setOnAction(e -> TastePickerDialog.show(this::loadRecommendations));

        header.getChildren().addAll(icon, title, subtitle, sp, prefBtn);

        // Horizontal cards shelf
        forYouCardsBox = new HBox(16);
        forYouCardsBox.setAlignment(Pos.CENTER_LEFT);
        forYouCardsBox.setPadding(new Insets(4, 4, 8, 4));

        ScrollPane shelfScroll = new ScrollPane(forYouCardsBox);
        shelfScroll.setFitToHeight(true);
        shelfScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        shelfScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shelfScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");

        section.getChildren().addAll(header, shelfScroll);
        return section;
    }

    private void loadRecommendations() {
        User user = AuthService.getCurrentUser();
        if (user == null || forYouCardsBox == null) return;

        forYouCardsBox.getChildren().clear();
        Label loadingLbl = new Label("Calibrating your recommendations...");
        loadingLbl.setStyle("-fx-text-fill: #6B7280; -fx-padding: 20px;");
        forYouCardsBox.getChildren().add(loadingLbl);

        AsyncUi.run(() -> {
            try {
                return recommendationService.getRecommendationsForUser(user.getId(), 5);
            } catch (SQLException e) {
                return List.<MovieRecommendation>of();
            }
        }, recs -> {
            forYouCardsBox.getChildren().clear();
            if (recs.isEmpty()) {
                Label empty = new Label("Add your taste profile to see recommendations!");
                empty.setStyle("-fx-text-fill: #6B7280; -fx-padding: 15px;");
                forYouCardsBox.getChildren().add(empty);
            } else {
                for (int i = 0; i < recs.size(); i++) {
                    MovieRecommendation rec = recs.get(i);
                    VBox card = createRecommendationCard(rec);
                    forYouCardsBox.getChildren().add(card);
                    AnimationUtil.fadeInUp(card, i * 70);
                }
            }
        }, err -> {
            forYouCardsBox.getChildren().clear();
        });
    }

    private VBox createRecommendationCard(MovieRecommendation rec) {
        Movie m = rec.getMovie();
        VBox card = new VBox(8);
        card.setPrefWidth(260);
        card.setMinWidth(260);
        card.setMaxWidth(260);
        card.setPadding(new Insets(12));
        card.setStyle(
            "-fx-background-color: rgba(21, 19, 35, 0.90); " +
            "-fx-border-color: rgba(139, 92, 246, 0.35); " +
            "-fx-border-width: 1px; -fx-border-radius: 14px; -fx-background-radius: 14px; " +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.18), 16, 0, 0, 4); -fx-cursor: hand;"
        );

        // Poster + Match Badge overlay
        StackPane posterBox = new StackPane();
        ImageView poster = PosterHelper.createPosterImageView(m.getTitle(), 236, 170, 10);

        // Match percentage badge
        Label matchBadge = new Label(rec.getMatchPercentage() + "% MATCH");
        matchBadge.setStyle(
            "-fx-background-color: linear-gradient(to right, #00C2FF, #8B5CF6); " +
            "-fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: 11px; " +
            "-fx-padding: 3px 8px; -fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,194,255,0.5), 8, 0, 0, 1);"
        );
        StackPane.setAlignment(matchBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(matchBadge, new Insets(8));

        posterBox.getChildren().addAll(poster, matchBadge);

        // Explanation Pill
        Label explanationLbl = new Label("✦ " + rec.getExplanation());
        explanationLbl.setWrapText(true);
        explanationLbl.setStyle(
            "-fx-background-color: rgba(139,92,246,0.12); -fx-text-fill: #C4B5FD; " +
            "-fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 4px 8px; -fx-background-radius: 6px;"
        );

        // Title & metadata
        Label titleLbl = new Label(m.getTitle());
        titleLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label gLbl = new Label(m.getGenre());
        gLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #38BDF8;");
        Label rLbl = new Label("★ " + (m.getRating() > 0 ? m.getRating() : "8.5"));
        rLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #FCD34D;");
        metaRow.getChildren().addAll(gLbl, new Label("•"), rLbl);

        // Book CTA
        Button bookBtn = new Button("Book Tickets  →");
        bookBtn.getStyleClass().add("button-primary");
        bookBtn.setMaxWidth(Double.MAX_VALUE);
        bookBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8px;");
        bookBtn.setOnAction(e -> {
            if (onMovieSelected != null) onMovieSelected.accept(m);
        });

        card.getChildren().addAll(posterBox, explanationLbl, titleLbl, metaRow, bookBtn);

        AnimationUtil.hoverLift(card);
        return card;
    }

    private VBox createCatalogHeader() {
        VBox box = new VBox(4);
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("All Now Showing Films");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        countLabel = new Label();
        countLabel.getStyleClass().add("badge-purple");
        countLabel.setStyle("-fx-font-size: 12px;");

        row.getChildren().addAll(title, sp, countLabel);
        box.getChildren().add(row);
        return box;
    }

    /* ---------------------------------------------------------------- */
    /* FILTER BAR                                                         */
    /* ---------------------------------------------------------------- */
    private HBox createFilterBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 16, 12, 16));
        bar.setStyle(
            "-fx-background-color: rgba(21,19,35,0.60);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 12px;" +
            "-fx-background-radius: 12px;"
        );

        Label searchIcon = new Label("🔍");
        searchIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: #6B7280;");

        searchField = new TextField();
        searchField.setPromptText("Search movies by title...");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, old, val) -> applySearch());

        Region sep1 = filterSep();

        Label genreIcon = new Label("🏷");
        genreIcon.setStyle("-fx-font-size: 13px;");
        genreFilter = new ComboBox<>();
        genreFilter.setPromptText("All Genres");
        genreFilter.setPrefWidth(140);
        genreFilter.valueProperty().addListener((obs, old, val) -> applySearch());

        Region sep2 = filterSep();

        Label langIcon = new Label("🌐");
        langIcon.setStyle("-fx-font-size: 13px;");
        langFilter = new ComboBox<>();
        langFilter.setPromptText("All Languages");
        langFilter.setPrefWidth(140);
        langFilter.valueProperty().addListener((obs, old, val) -> applySearch());

        screeningDateFilter = new DatePicker();
        screeningDateFilter.setPromptText("Showtime date");
        screeningDateFilter.setAccessibleText("Filter by showtime date");
        screeningDateFilter.setEditable(false);
        screeningDateFilter.setPrefWidth(150);
        screeningDateFilter.valueProperty().addListener((obs, old, val) -> applySearch());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("✕  Clear");
        clearBtn.getStyleClass().add("button-secondary");
        clearBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px;");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            genreFilter.setValue("All Genres");
            langFilter.setValue("All Languages");
            screeningDateFilter.setValue(null);
            applySearch();
        });

        bar.getChildren().addAll(
                searchIcon, searchField,
                sep1,
                genreIcon, genreFilter,
                sep2,
                langIcon, langFilter,
                filterSep(), screeningDateFilter,
                spacer,
                clearBtn
        );

        loadFilterOptions();
        return bar;
    }

    private Region filterSep() {
        Region r = new Region();
        r.setStyle("-fx-border-color: rgba(139,92,246,0.18); -fx-border-width: 0 1px 0 0;");
        r.setMinWidth(1);
        r.setMinHeight(22);
        return r;
    }

    private void loadFilterOptions() {
        try {
            List<String> genres = movieService.getAllGenres();
            genres.add(0, "All Genres");
            genreFilter.setItems(FXCollections.observableArrayList(genres));
            genreFilter.setValue("All Genres");

            List<String> langs = movieService.getAllLanguages();
            langs.add(0, "All Languages");
            langFilter.setItems(FXCollections.observableArrayList(langs));
            langFilter.setValue("All Languages");
        } catch (SQLException ignored) {}
    }

    private void applySearch() {
        if (movieGrid == null) return;
        String keyword = searchField != null ? searchField.getText() : "";
        String genre   = genreFilter != null ? genreFilter.getValue() : null;
        String lang    = langFilter  != null ? langFilter.getValue()  : null;

        if ("All Genres".equals(genre))       genre = null;
        if ("All Languages".equals(lang))     lang = null;

        try {
            List<Movie> movies = movieService.searchMovies(keyword, genre, lang, screeningDateFilter.getValue());
            movieGrid.getChildren().clear();

            if (countLabel != null) {
                countLabel.setText(movies.size() + " films");
            }

            if (movies.isEmpty()) {
                VBox empty = createEmptyState();
                movieGrid.getChildren().add(empty);
                return;
            }

            for (int i = 0; i < movies.size(); i++) {
                VBox card = createMovieCard(movies.get(i));
                card.setOpacity(0);
                movieGrid.getChildren().add(card);
                AnimationUtil.fadeInUp(card, i * 40);
            }
        } catch (SQLException e) {
            AlertHelper.showError("Catalog Error", "Failed to load movies", e.getMessage());
        }
    }

    private VBox createEmptyState() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(60));

        Label icon = new Label("🎬");
        icon.setStyle("-fx-font-size: 48px;");

        Label msg = new Label("No movies match your search criteria.");
        msg.setStyle("-fx-font-size: 16px; -fx-text-fill: #E2E8F0;");

        Label hint = new Label("Try clearing filters or searching for a different title.");
        hint.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B7280;");

        box.getChildren().addAll(icon, msg, hint);
        return box;
    }

    private VBox createMovieCard(Movie movie) {
        VBox card = new VBox(0);
        card.setPrefWidth(235);
        card.setMaxWidth(235);
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(
            "-fx-background-color: rgba(21,19,35,0.88);" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: rgba(139,92,246,0.28);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 16px;" +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.15), 16, 0, 0, 5);" +
            "-fx-cursor: hand;"
        );

        StackPane posterPane = new StackPane();
        ImageView poster = PosterHelper.createPosterImageView(movie.getTitle(), 235, 310, 0);
        poster.setStyle("-fx-background-radius: 16px 16px 0 0;");

        Rectangle posterClip = new Rectangle(235, 310);
        posterClip.setArcWidth(32);
        posterClip.setArcHeight(32);
        poster.setClip(posterClip);

        Pane gradientOverlay = new Pane();
        gradientOverlay.setPrefSize(235, 80);
        gradientOverlay.setStyle("-fx-background-color: linear-gradient(to bottom, transparent, rgba(9,9,15,0.92));");
        gradientOverlay.setTranslateY(230);

        Label ratingBadge = new Label("★  " + movie.getRating());
        ratingBadge.setStyle(
            "-fx-background-color: rgba(9,9,15,0.78); -fx-text-fill: #FCD34D;" +
            "-fx-font-size: 11px; -fx-font-weight: 800; -fx-background-radius: 8px;" +
            "-fx-padding: 4px 8px; -fx-border-color: rgba(245,158,11,0.35); -fx-border-width: 1px; -fx-border-radius: 8px;"
        );
        StackPane.setAlignment(ratingBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(ratingBadge, new Insets(10, 10, 0, 0));

        posterPane.getChildren().addAll(poster, gradientOverlay, ratingBadge);

        VBox body = new VBox(7);
        body.setPadding(new Insets(12, 14, 14, 14));
        body.setAlignment(Pos.TOP_LEFT);

        Label title = new Label(movie.getTitle());
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");
        title.setWrapText(false);

        HBox badges = new HBox(6);
        badges.setAlignment(Pos.CENTER_LEFT);

        Label genreBadge = new Label(movie.getGenre() != null ? movie.getGenre() : "Drama");
        genreBadge.getStyleClass().add("badge-info");
        genreBadge.setStyle("-fx-font-size: 10px;");

        Label langBadge = new Label(movie.getLanguage() != null ? movie.getLanguage() : "English");
        langBadge.getStyleClass().add("badge-warning");
        langBadge.setStyle("-fx-font-size: 10px;");

        Label durBadge = new Label(movie.getDurationMinutes() + "m");
        durBadge.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");

        badges.getChildren().addAll(genreBadge, langBadge, durBadge);

        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button reviewsBtn = new Button("💬 Reviews");
        reviewsBtn.setStyle("-fx-background-color: rgba(255,255,255,0.07); -fx-text-fill: #CBD5E1; -fx-font-size: 11px; -fx-padding: 8px 10px; -fx-cursor: hand; -fx-background-radius: 8px; -fx-border-color: rgba(139,92,246,0.25); -fx-border-radius: 8px;");
        reviewsBtn.setOnAction(e -> new MovieReviewsViewerDialog(movie).show());

        Button bookBtn = new Button("Book Tickets  →");
        bookBtn.getStyleClass().add("button-primary");
        bookBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bookBtn, Priority.ALWAYS);
        bookBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8px;");
        bookBtn.setOnAction(e -> {
            if (onMovieSelected != null) onMovieSelected.accept(movie);
        });

        actionsRow.getChildren().addAll(reviewsBtn, bookBtn);

        body.getChildren().addAll(title, badges, actionsRow);
        card.getChildren().addAll(posterPane, body);

        AnimationUtil.hoverLift(card);
        return card;
    }
}
