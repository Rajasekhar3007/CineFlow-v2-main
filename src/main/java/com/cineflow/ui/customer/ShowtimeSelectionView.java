package com.cineflow.ui.customer;

import com.cineflow.model.Movie;
import com.cineflow.model.PriceQuote;
import com.cineflow.model.SeatType;
import com.cineflow.model.Showtime;
import com.cineflow.service.PricingService;
import com.cineflow.service.ShowtimeService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.util.PosterHelper;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * CineFlow — Premium Showtime Selection View.
 * Movie showcase banner with poster art, and elegant showtime cards.
 */
public class ShowtimeSelectionView {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("h:mm a");

    private record Screening(Showtime showtime, PriceQuote regularQuote, PriceQuote premiumQuote) {}

    private final Movie movie;
    private final ShowtimeService showtimeService;
    private final Consumer<Showtime> onShowtimeSelected;
    private final Runnable onBack;

    public ShowtimeSelectionView(Movie movie, Consumer<Showtime> onShowtimeSelected, Runnable onBack) {
        this.movie = movie;
        this.showtimeService = new ShowtimeService();
        this.onShowtimeSelected = onShowtimeSelected;
        this.onBack = onBack;
    }

    public Parent getView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("app-container");

        // Breadcrumb nav bar
        HBox topBar = createTopBar();
        root.getChildren().add(topBar);

        // Movie showcase banner
        HBox movieBanner = createMovieBanner();
        root.getChildren().add(movieBanner);

        // Showtimes section
        VBox showtimesSection = createShowtimesSection();
        VBox.setVgrow(showtimesSection, Priority.ALWAYS);
        root.getChildren().add(showtimesSection);

        return root;
    }

    /* ---------------------------------------------------------------- */
    /* TOP BAR                                                            */
    /* ---------------------------------------------------------------- */
    private HBox createTopBar() {
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 28, 14, 28));
        bar.setStyle(
            "-fx-background-color: rgba(21,19,35,0.75);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        Button backBtn = new Button("←  All Movies");
        backBtn.getStyleClass().add("button-secondary");
        backBtn.setStyle("-fx-font-size: 13px; -fx-padding: 7px 16px;");
        backBtn.setOnAction(e -> onBack.run());

        Label sep = new Label("/");
        sep.setStyle("-fx-text-fill: #4B5563; -fx-font-size: 14px;");

        Label breadcrumb = new Label(movie.getTitle());
        breadcrumb.setStyle("-fx-text-fill: #A1A1AA; -fx-font-size: 14px;");

        Label sep2 = new Label("/");
        sep2.setStyle("-fx-text-fill: #4B5563; -fx-font-size: 14px;");

        Label currentPage = new Label("Select Showtime");
        currentPage.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 14px; -fx-font-weight: 700;");

        bar.getChildren().addAll(backBtn, sep, breadcrumb, sep2, currentPage);
        return bar;
    }

    /* ---------------------------------------------------------------- */
    /* MOVIE BANNER                                                       */
    /* ---------------------------------------------------------------- */
    private HBox createMovieBanner() {
        HBox card = new HBox(24);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(28, 36, 28, 36));
        card.setStyle(
            "-fx-background-color: linear-gradient(to right," +
            "  rgba(139,92,246,0.14) 0%," +
            "  rgba(0,194,255,0.06) 60%," +
            "  transparent 100%);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        // Poster with rounded corners
        ImageView poster = PosterHelper.createPosterImageView(movie.getTitle(), 130, 180, 14);

        // Details
        VBox details = new VBox(10);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label movieTitle = new Label(movie.getTitle());
        movieTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");
        movieTitle.setWrapText(true);

        HBox metaRow = new HBox(10);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label genreBadge = new Label(movie.getGenre() != null ? movie.getGenre() : "FEATURE");
        genreBadge.getStyleClass().add("badge-info");

        Label langBadge = new Label(movie.getLanguage() != null ? movie.getLanguage() : "ORIGINAL");
        langBadge.getStyleClass().add("badge-warning");

        Label ratingBadge = new Label("★  " + (movie.getRating() > 0 ? movie.getRating() : "8.5"));
        ratingBadge.getStyleClass().add("badge-success");

        Label durLbl = new Label("⏱  " + (movie.getDurationMinutes() > 0 ? movie.getDurationMinutes() : "148") + " min");
        durLbl.getStyleClass().add("text-muted");

        metaRow.getChildren().addAll(genreBadge, langBadge, ratingBadge, durLbl);

        Label desc = new Label(movie.getDescription() != null && !movie.getDescription().isBlank()
                ? movie.getDescription()
                : "Experience this cinematic masterpiece at CineFlow — premium sound, premium seating, premium experience.");
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1AA; -fx-line-spacing: 2px;");
        desc.setWrapText(true);
        desc.setMaxWidth(600);

        details.getChildren().addAll(movieTitle, metaRow, desc);
        card.getChildren().addAll(poster, details);
        return card;
    }

    /* ---------------------------------------------------------------- */
    /* SHOWTIMES SECTION                                                  */
    /* ---------------------------------------------------------------- */
    private VBox createShowtimesSection() {
        VBox section = new VBox(0);
        section.setPadding(new Insets(0));

        // Section header
        HBox sectionHeader = new HBox(12);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);
        sectionHeader.setPadding(new Insets(22, 28, 14, 28));

        Label sectionTitle = new Label("Available Screenings");
        sectionTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label dotSep = new Label("✦");
        dotSep.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 12px;");

        Label hint = new Label("Choose a date and time, then select seats");
        hint.getStyleClass().add("text-muted");

        sectionHeader.getChildren().addAll(sectionTitle, dotSep, hint);

        VBox schedule = new VBox(20);
        schedule.setPadding(new Insets(4, 28, 28, 28));
        Label status = new Label("Loading scheduled screenings…");
        status.getStyleClass().add("text-muted");
        schedule.getChildren().add(status);

        AsyncUi.run(() -> {
            PricingService pricingService = new PricingService();
            return showtimeService.getShowtimesForMovie(movie.getId()).stream()
                    .map(showtime -> new Screening(showtime,
                            previewPrice(pricingService, showtime, SeatType.REGULAR),
                            previewPrice(pricingService, showtime, SeatType.PREMIUM)))
                    .toList();
        }, screenings -> {
            if (screenings.isEmpty()) {
                status.setText("No upcoming screenings scheduled for " + movie.getTitle() + ".");
                return;
            }
            schedule.getChildren().clear();
            addScreeningsByDate(schedule, screenings);
        }, error -> status.setText("Could not load screenings. Go back and try again."));

        ScrollPane scroll = new ScrollPane(schedule);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        section.getChildren().addAll(sectionHeader, scroll);
        return section;
    }

    private void addScreeningsByDate(VBox schedule, List<Screening> screenings) {
        LocalDate currentDate = null;
        FlowPane times = null;
        for (Screening screening : screenings) {
            LocalDate date = screening.showtime().getStartTime().toLocalDate();
            if (!date.equals(currentDate)) {
                currentDate = date;
                Label heading = new Label(DATE_LABEL.format(date));
                heading.getStyleClass().add("text-subtitle");
                times = new FlowPane(16, 16);
                schedule.getChildren().add(new VBox(10, heading, times));
            }
            times.getChildren().add(createShowtimeCard(screening));
        }
    }

    private PriceQuote previewPrice(PricingService pricingService, Showtime showtime, SeatType seatType) {
        try {
            return pricingService.getPreviewQuote(showtime, seatType);
        } catch (Exception ignored) {
            return null;
        }
    }

    /* ---------------------------------------------------------------- */
    /* SHOWTIME CARD                                                      */
    /* ---------------------------------------------------------------- */
    private VBox createShowtimeCard(Screening screening) {
        Showtime st = screening.showtime();
        VBox card = new VBox(12);
        card.setPrefWidth(260);
        card.setPadding(new Insets(18));
        card.setStyle(
            "-fx-background-color: rgba(21,19,35,0.88);" +
            "-fx-background-radius: 14px;" +
            "-fx-border-color: rgba(139,92,246,0.28);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 14px;" +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.12), 14, 0, 0, 4);" +
            "-fx-cursor: hand;"
        );

        // Screen name
        HBox screenRow = new HBox(8);
        screenRow.setAlignment(Pos.CENTER_LEFT);
        Label screenIcon = new Label("🏛");
        screenIcon.setStyle("-fx-font-size: 14px;");
        Label screenLbl = new Label(st.getScreenName());
        screenLbl.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");
        screenRow.getChildren().addAll(screenIcon, screenLbl);

        // Time
        HBox timeRow = new HBox(8);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        Label clockIcon = new Label("🕒");
        clockIcon.setStyle("-fx-font-size: 13px;");
        Label timeLbl = new Label(TIME_LABEL.format(st.getStartTime()));
        timeLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #00C2FF;");
        timeRow.getChildren().addAll(clockIcon, timeLbl);

        // Price box with dynamic pricing calculation
        VBox priceBox = new VBox(6);
        priceBox.setStyle(
            "-fx-background-color: rgba(139,92,246,0.06);" +
            "-fx-padding: 10px 12px;" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: rgba(139,92,246,0.16);" +
            "-fx-border-width: 1px; -fx-border-radius: 10px;"
        );

        PriceQuote regQuote = screening.regularQuote();
        PriceQuote premQuote = screening.premiumQuote();

        java.math.BigDecimal regPrice = regQuote != null ? regQuote.getFinalPrice() : st.getPriceRegular();
        java.math.BigDecimal premPrice = premQuote != null ? premQuote.getFinalPrice() : st.getPricePremium();

        HBox regRow = new HBox(8);
        regRow.setAlignment(Pos.CENTER_LEFT);
        Label regDot = new Label("●");
        regDot.setStyle("-fx-font-size: 8px; -fx-text-fill: #22C55E;");
        Label regLbl = new Label("Regular  —  ₹ " + regPrice.toPlainString());
        regLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1AA;");
        regRow.getChildren().addAll(regDot, regLbl);

        if (regQuote != null && regQuote.getFinalPrice().compareTo(st.getPriceRegular()) != 0) {
            boolean isSurge = regQuote.getFinalPrice().compareTo(st.getPriceRegular()) > 0;
            Label chip = new Label(isSurge ? "▲ Surge" : "▼ Saver");
            chip.getStyleClass().add(isSurge ? "badge-surge" : "badge-discount");

            StringBuilder tip = new StringBuilder("Dynamic Pricing Factors:\n");
            for (var line : regQuote.getBreakdownLines()) {
                tip.append(" • ").append(line.toString()).append("\n");
            }
            Tooltip t = new Tooltip(tip.toString().trim());
            t.setShowDelay(Duration.millis(150));
            chip.setTooltip(t);
            regRow.getChildren().add(chip);
        }

        HBox premRow = new HBox(8);
        premRow.setAlignment(Pos.CENTER_LEFT);
        Label premDot = new Label("●");
        premDot.setStyle("-fx-font-size: 8px; -fx-text-fill: #F59E0B;");
        Label premLbl = new Label("Premium  —  ₹ " + premPrice.toPlainString());
        premLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #FCD34D;");
        premRow.getChildren().addAll(premDot, premLbl);

        priceBox.getChildren().addAll(regRow, premRow);

        // Action buttons (Select Seats / Join Waitlist)
        HBox btnRow = new HBox(8);
        btnRow.setAlignment(Pos.CENTER);

        Button selectBtn = new Button("Select Seats  →");
        selectBtn.getStyleClass().add("button-primary");
        selectBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(selectBtn, Priority.ALWAYS);
        selectBtn.setStyle("-fx-font-size: 13px; -fx-padding: 9px;");
        selectBtn.setOnAction(e -> {
            if (onShowtimeSelected != null) onShowtimeSelected.accept(st);
        });

        Button waitlistBtn = new Button("Waitlist ⏳");
        waitlistBtn.getStyleClass().add("button-secondary");
        waitlistBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8px 10px;");
        Tooltip wt = new Tooltip("Join waitlist queue for this screening");
        waitlistBtn.setTooltip(wt);
        waitlistBtn.setOnAction(e -> WaitlistDialog.show(st, null));

        btnRow.getChildren().addAll(selectBtn, waitlistBtn);
        card.getChildren().addAll(screenRow, timeRow, priceBox, btnRow);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: rgba(21,19,35,0.95);" +
            "-fx-background-radius: 14px;" +
            "-fx-border-color: rgba(139,92,246,0.60);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 14px;" +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.38), 22, 0, 0, 8);" +
            "-fx-cursor: hand;" +
            "-fx-translate-y: -3px;"
        ));

        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: rgba(21,19,35,0.88);" +
            "-fx-background-radius: 14px;" +
            "-fx-border-color: rgba(139,92,246,0.28);" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 14px;" +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.12), 14, 0, 0, 4);" +
            "-fx-cursor: hand;" +
            "-fx-translate-y: 0px;"
        ));

        return card;
    }
}
