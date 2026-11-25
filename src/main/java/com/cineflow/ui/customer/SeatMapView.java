package com.cineflow.ui.customer;

import com.cineflow.model.*;
import com.cineflow.service.*;
import com.cineflow.ui.common.AnimationUtil;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.AlertHelper;
import javafx.animation.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

/**
 * CineFlow — Premium Interactive Seat Map.
 * Cinema screen arc, animated seat selection, real-time price calculation,
 * 5-minute countdown, and color-coded seat states.
 */
public class SeatMapView {

    private final Showtime showtime;
    private final BookingService bookingService;
    private final ScreenService screenService;
    private final PricingService pricingService;
    private final SeatAllocationService allocationService;
    private final Consumer<List<Seat>> onProceedToPayment;
    private final Runnable onBack;

    private final List<Seat> selectedSeats = new ArrayList<>();
    private final Map<Integer, Button> seatButtonMap = new HashMap<>();
    private final SimpleObjectProperty<BigDecimal> totalPriceProperty = new SimpleObjectProperty<>(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
    private BigDecimal dynamicRegularPrice;
    private BigDecimal dynamicPremiumPrice;
    private PriceQuote regularQuote;
    private PriceQuote premiumQuote;
    private Label totalLabel;
    private Label selectionCountLabel;
    private Button proceedBtn;
    private GridPane seatGrid;

    private int smartPickCount = 2;
    private SeatPreference smartPickPreference = SeatPreference.BEST_OVERALL;
    private VBox smartPickResultsBox;

    public SeatMapView(Showtime showtime, Consumer<List<Seat>> onProceedToPayment, Runnable onBack) {
        this.showtime = showtime;
        this.bookingService = new BookingService();
        this.screenService = new ScreenService();
        this.pricingService = new PricingService();
        this.allocationService = new SeatAllocationService();
        this.dynamicRegularPrice = showtime.getPriceRegular();
        this.dynamicPremiumPrice = showtime.getPricePremium();
        this.onProceedToPayment = onProceedToPayment;
        this.onBack = onBack;
    }

    public Parent getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-container");

        // Top breadcrumb + info bar
        VBox topBar = createTopBar();
        root.setTop(topBar);

        // Center split layout: Left Map + Right Smart Pick Panel
        HBox mainContent = new HBox(28);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(20, 24, 20, 24));

        VBox leftMapBox = new VBox(18);
        leftMapBox.setAlignment(Pos.TOP_CENTER);

        // Cinema screen graphic
        VBox screenGraphic = createCinemaScreenGraphic();
        leftMapBox.getChildren().add(screenGraphic);

        // Seat grid
        seatGrid = new GridPane();
        seatGrid.setHgap(6);
        seatGrid.setVgap(6);
        seatGrid.setAlignment(Pos.CENTER);
        leftMapBox.getChildren().add(seatGrid);

        // Legend
        HBox legend = createLegend();
        leftMapBox.getChildren().add(legend);

        // Smart Pick Panel
        VBox smartPickPanel = createSmartPickPanel();
        mainContent.getChildren().addAll(leftMapBox, smartPickPanel);

        ScrollPane scroll = new ScrollPane(mainContent);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        root.setCenter(scroll);

        // Bottom checkout bar
        HBox bottomBar = createCheckoutBar();
        root.setBottom(bottomBar);

        loadSeatMap();
        return root;
    }

    /* ---------------------------------------------------------------- */
    /* TOP BAR                                                            */
    /* ---------------------------------------------------------------- */
    private VBox createTopBar() {
        VBox wrapper = new VBox(0);

        // Breadcrumb row
        HBox breadcrumb = new HBox(12);
        breadcrumb.setAlignment(Pos.CENTER_LEFT);
        breadcrumb.setPadding(new Insets(12, 28, 12, 28));
        breadcrumb.setStyle(
            "-fx-background-color: rgba(21,19,35,0.78);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        Button backBtn = new Button("←  Showtimes");
        backBtn.getStyleClass().add("button-secondary");
        backBtn.setStyle("-fx-font-size: 13px; -fx-padding: 7px 16px;");
        backBtn.setOnAction(e -> onBack.run());

        Label sep = new Label("/");
        sep.setStyle("-fx-text-fill: #4B5563;");

        Label movLbl = new Label(showtime.getMovieTitle());
        movLbl.setStyle("-fx-text-fill: #A1A1AA; -fx-font-size: 13px;");

        Label sep2 = new Label("/");
        sep2.setStyle("-fx-text-fill: #4B5563;");

        Label curLbl = new Label("Select Seats");
        curLbl.setStyle("-fx-text-fill: #8B5CF6; -fx-font-size: 13px; -fx-font-weight: 700;");

        breadcrumb.getChildren().addAll(backBtn, sep, movLbl, sep2, curLbl);

        // Info row
        HBox infoRow = new HBox(24);
        infoRow.setAlignment(Pos.CENTER);
        infoRow.setPadding(new Insets(12, 28, 12, 28));
        infoRow.setStyle(
            "-fx-background-color: rgba(139,92,246,0.06);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        Label movieLbl = buildInfoChip("🎬", showtime.getMovieTitle(), "#F8FAFC");
        Label screenLbl = buildInfoChip("🏛", showtime.getScreenName(), "#C4B5FD");
        Label timeLbl = buildInfoChip("🕒", showtime.getFormattedStartTime(), "#00C2FF");
        Label regLbl = buildInfoChip("◉", "Regular: ₹ " + showtime.getPriceRegular().toPlainString(), "#86EFAC");
        Label premLbl = buildInfoChip("◈", "Premium: ₹ " + showtime.getPricePremium().toPlainString(), "#FCD34D");

        infoRow.getChildren().addAll(movieLbl, screenLbl, timeLbl, regLbl, premLbl);
        wrapper.getChildren().addAll(breadcrumb, infoRow);
        return wrapper;
    }

    private Label buildInfoChip(String icon, String text, String color) {
        Label lbl = new Label(icon + "  " + text);
        lbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: " + color + ";");
        return lbl;
    }

    /* ---------------------------------------------------------------- */
    /* CINEMA SCREEN GRAPHIC                                              */
    /* ---------------------------------------------------------------- */
    private VBox createCinemaScreenGraphic() {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(800);

        Region screenBar = new Region();
        screenBar.getStyleClass().add("cinema-screen-bar");
        screenBar.setPrefWidth(680);
        screenBar.setPrefHeight(20);

        Label screenLabel = new Label("S  C  R  E  E  N");
        screenLabel.setStyle(
            "-fx-font-size: 11px;" +
            "-fx-font-weight: 900;" +
            "-fx-text-fill: rgba(0,194,255,0.55);" +
            "-fx-letter-spacing: 4px;"
        );

        box.getChildren().addAll(screenBar, screenLabel);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    /* ---------------------------------------------------------------- */
    /* LEGEND                                                             */
    /* ---------------------------------------------------------------- */
    private HBox createLegend() {
        HBox box = new HBox(18);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(14, 0, 6, 0));

        box.getChildren().addAll(
                makeLegendItem("Available",   "#22C55E", "rgba(34,197,94,0.18)",  "rgba(34,197,94,0.55)"),
                makeLegendItem("Selected",    "#FFFFFF",  "linear-gradient(to bottom right, #8B5CF6, #00C2FF)", "rgba(139,92,246,0.8)"),
                makeLegendItem("Held",        "#FCD34D", "rgba(245,158,11,0.20)", "rgba(245,158,11,0.55)"),
                makeLegendItem("Booked",      "#71717A", "rgba(239,68,68,0.12)",  "rgba(239,68,68,0.20)"),
                makeLegendItemPremium()
        );
        return box;
    }

    private HBox makeLegendItem(String label, String textColor, String bg, String border) {
        HBox item = new HBox(7);
        item.setAlignment(Pos.CENTER);

        Label swatch = new Label();
        swatch.setMinSize(22, 22);
        swatch.setMaxSize(22, 22);
        swatch.setStyle(
            "-fx-background-color: " + bg + ";" +
            "-fx-background-radius: 6px;" +
            "-fx-border-color: " + border + ";" +
            "-fx-border-radius: 6px; -fx-border-width: 1.5px;"
        );

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1AA;");
        item.getChildren().addAll(swatch, lbl);
        return item;
    }

    private HBox makeLegendItemPremium() {
        HBox item = new HBox(7);
        item.setAlignment(Pos.CENTER);

        Label swatch = new Label();
        swatch.setMinSize(22, 22);
        swatch.setMaxSize(22, 22);
        swatch.setStyle(
            "-fx-background-color: rgba(34,197,94,0.18);" +
            "-fx-background-radius: 6px;" +
            "-fx-border-color: #F59E0B;" +
            "-fx-border-radius: 6px; -fx-border-width: 2.5px;"
        );

        Label lbl = new Label("Premium Tier");
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #FCD34D;");
        item.getChildren().addAll(swatch, lbl);
        return item;
    }

    /* ---------------------------------------------------------------- */
    /* CHECKOUT BAR                                                       */
    /* ---------------------------------------------------------------- */
    private HBox createCheckoutBar() {
        HBox bar = new HBox(20);
        bar.setPadding(new Insets(16, 32, 16, 32));
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setStyle(
            "-fx-background-color: rgba(14,11,25,0.95);" +
            "-fx-border-color: rgba(139,92,246,0.22);" +
            "-fx-border-width: 1px 0 0 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.20), 16, 0, 0, -4);"
        );

        selectionCountLabel = new Label("Select seats to continue");
        selectionCountLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B7280;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        totalLabel = new Label("₹  0.00");
        totalLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #22C55E;");

        totalPriceProperty.addListener((obs, oldVal, newVal) -> {
            AnimationUtil.countUp(totalLabel, oldVal != null ? oldVal : BigDecimal.ZERO, newVal != null ? newVal : BigDecimal.ZERO);
            int count = selectedSeats.size();
            if (count == 0) {
                selectionCountLabel.setText("Select seats to continue");
                selectionCountLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B7280;");
            } else {
                selectionCountLabel.setText(count + " seat" + (count > 1 ? "s" : "") + " selected");
                selectionCountLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #C4B5FD;");
            }
            proceedBtn.setDisable(selectedSeats.isEmpty());
        });

        proceedBtn = new Button("Proceed to Payment  →");
        proceedBtn.getStyleClass().add("button-primary");
        proceedBtn.setStyle("-fx-font-size: 15px; -fx-padding: 12px 28px;");
        proceedBtn.setDisable(true);

        proceedBtn.setOnAction(e -> handleProceedToPayment());

        bar.getChildren().addAll(selectionCountLabel, spacer, totalLabel, proceedBtn);
        return bar;
    }

    private void handleProceedToPayment() {
        if (selectedSeats.isEmpty()) {
            AlertHelper.showWarning("No Seats Selected", "Choose Your Seats", "Please select at least one seat to continue.");
            return;
        }

        User user = AuthService.getCurrentUser();
        if (user == null) {
            AlertHelper.showError("Session Expired", "Auth Error", "Please log in again.");
            return;
        }

        List<Integer> seatIds = new ArrayList<>();
        for (Seat s : selectedSeats) seatIds.add(s.getId());

        try {
            bookingService.holdSeats(showtime.getId(), seatIds, user.getId());
            if (onProceedToPayment != null) {
                onProceedToPayment.accept(new ArrayList<>(selectedSeats));
            }
        } catch (ValidationException ve) {
            AlertHelper.showWarning("Seat Unavailable", "Could Not Hold Seats", ve.getMessage());
            loadSeatMap();
        } catch (SQLException ex) {
            AlertHelper.showError("Database Error", "Failed to Reserve Seats", ex.getMessage());
            loadSeatMap();
        }
    }

    /* ---------------------------------------------------------------- */
    /* SEAT MAP BUILDER                                                   */
    /* ---------------------------------------------------------------- */
    public void loadSeatMap() {
        seatGrid.getChildren().clear();
        selectedSeats.clear();
        seatButtonMap.clear();
        totalPriceProperty.set(BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));

        try {
            int currentUserId = AuthService.getCurrentUser() != null
                    ? AuthService.getCurrentUser().getId() : -1;

            try {
                regularQuote = pricingService.getPreviewQuote(showtime, SeatType.REGULAR);
                premiumQuote = pricingService.getPreviewQuote(showtime, SeatType.PREMIUM);
                dynamicRegularPrice = regularQuote != null ? regularQuote.getFinalPrice() : showtime.getPriceRegular();
                dynamicPremiumPrice = premiumQuote != null ? premiumQuote.getFinalPrice() : showtime.getPricePremium();
            } catch (Exception ignored) {
                dynamicRegularPrice = showtime.getPriceRegular();
                dynamicPremiumPrice = showtime.getPricePremium();
            }

            List<Seat> seats = screenService.getSeatsForScreen(showtime.getScreenId());
            List<Integer> bookedSeatIds = bookingService.getBookedSeats(showtime.getId());
            List<Integer> heldSeatIds = bookingService.getHeldSeatsByOthers(showtime.getId(), currentUserId);

            Map<String, List<Seat>> rows = new LinkedHashMap<>();
            for (Seat seat : seats) {
                rows.computeIfAbsent(seat.getRowLabel(), k -> new ArrayList<>()).add(seat);
            }

            int rowIndex = 0;
            for (Map.Entry<String, List<Seat>> entry : rows.entrySet()) {
                String rowLabel = entry.getKey();
                List<Seat> rowSeats = entry.getValue();

                Label rowLbl = new Label(rowLabel);
                rowLbl.setStyle(
                    "-fx-font-size: 11px; -fx-font-weight: 800;" +
                    "-fx-text-fill: #6B7280; -fx-min-width: 22px;"
                );
                rowLbl.setAlignment(Pos.CENTER);
                seatGrid.add(rowLbl, 0, rowIndex);

                for (int colIndex = 0; colIndex < rowSeats.size(); colIndex++) {
                    // Aisle gap after column 4 for wide screens
                    int colOffset = colIndex + 1;
                    if (colIndex >= 4) colOffset += 1;  // 1-column aisle gap

                    Button seatBtn = createSeatButton(rowSeats.get(colIndex), bookedSeatIds, heldSeatIds);
                    seatGrid.add(seatBtn, colOffset, rowIndex);
                }
                rowIndex++;
            }

        } catch (SQLException e) {
            AlertHelper.showError("Database Error", "Failed to Load Seats", e.getMessage());
        }
    }

    private Button createSeatButton(Seat seat, List<Integer> bookedIds, List<Integer> heldIds) {
        Button btn = new Button(seat.getSeatCode());
        btn.getStyleClass().add("seat-button");

        boolean isBooked  = bookedIds.contains(seat.getId());
        boolean isHeld    = heldIds.contains(seat.getId());
        boolean isPremium = seat.getSeatType() == SeatType.PREMIUM;

        BigDecimal price = isPremium ? dynamicPremiumPrice : dynamicRegularPrice;

        if (isPremium) btn.getStyleClass().add("seat-premium");

        if (isBooked) {
            btn.getStyleClass().add("seat-booked");
            btn.setDisable(true);
            btn.setTooltip(new Tooltip("Seat " + seat.getSeatCode() + "  •  Booked"));
        } else if (isHeld) {
            btn.getStyleClass().add("seat-held");
            btn.setDisable(true);
            btn.setTooltip(new Tooltip("Seat " + seat.getSeatCode() + "  •  Held by another customer"));
        } else {
            btn.getStyleClass().add("seat-available");
            String tipText = seat.getSeatCode() + "  •  " + seat.getSeatType() + "  •  ₹ " + price.toPlainString();
            Tooltip tip = new Tooltip(tipText);
            tip.setShowDelay(Duration.millis(200));
            btn.setTooltip(tip);

            btn.setOnAction(e -> toggleSeatSelection(btn, seat, price, isPremium));
        }

        seatButtonMap.put(seat.getId(), btn);
        return btn;
    }

    private void toggleSeatSelection(Button btn, Seat seat, BigDecimal price, boolean isPremium) {
        if (selectedSeats.contains(seat)) {
            selectedSeats.remove(seat);
            btn.getStyleClass().remove("seat-selected");
            btn.getStyleClass().add("seat-available");
            if (isPremium) {
                btn.getStyleClass().remove("seat-premium");
                btn.getStyleClass().add("seat-premium");
            }
            totalPriceProperty.set(totalPriceProperty.get().subtract(price).setScale(2, java.math.RoundingMode.HALF_UP));

            // Deselect animation
            ScaleTransition st = new ScaleTransition(Duration.millis(120), btn);
            st.setFromX(1.1);
            st.setToX(1.0);
            st.setFromY(1.1);
            st.setToY(1.0);
            st.play();

        } else {
            selectedSeats.add(seat);
            btn.getStyleClass().remove("seat-available");
            btn.getStyleClass().add("seat-selected");
            totalPriceProperty.set(totalPriceProperty.get().add(price).setScale(2, java.math.RoundingMode.HALF_UP));

            // Select animation
            ScaleTransition st = new ScaleTransition(Duration.millis(150), btn);
            st.setFromX(1.0);
            st.setToX(1.12);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
        }
    }

    /* ---------------------------------------------------------------- */
    /* SMART PICK PANEL (FEATURE 2)                                      */
    /* ---------------------------------------------------------------- */
    private VBox createSmartPickPanel() {
        VBox panel = new VBox(14);
        panel.setPrefWidth(330);
        panel.setMaxWidth(340);
        panel.getStyleClass().add("smart-pick-panel");

        Label title = new Label("✨ Smart Pick");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");

        Label subtitle = new Label("Auto-allocate optimal seats via acoustic sweet-spots");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        // Stepper: [-] [ X Seats ] [+]
        HBox stepperBox = new HBox(10);
        stepperBox.setAlignment(Pos.CENTER_LEFT);

        Label stepLbl = new Label("Seats:");
        stepLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");

        Button minusBtn = new Button("−");
        minusBtn.setStyle("-fx-font-size: 14px; -fx-padding: 4px 12px; -fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: white; -fx-background-radius: 6px;");

        Label countLbl = new Label(smartPickCount + " Seats");
        countLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #00C2FF; -fx-min-width: 60px; -fx-alignment: center;");

        Button plusBtn = new Button("+");
        plusBtn.setStyle("-fx-font-size: 14px; -fx-padding: 4px 12px; -fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: white; -fx-background-radius: 6px;");

        minusBtn.setOnAction(e -> {
            if (smartPickCount > 1) {
                smartPickCount--;
                countLbl.setText(smartPickCount + " Seats");
            }
        });

        plusBtn.setOnAction(e -> {
            if (smartPickCount < 10) {
                smartPickCount++;
                countLbl.setText(smartPickCount + " Seats");
            }
        });

        stepperBox.getChildren().addAll(stepLbl, minusBtn, countLbl, plusBtn);

        // Preference segmented control
        VBox prefBox = new VBox(6);
        Label prefLbl = new Label("Preference:");
        prefLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");

        FlowPane chipsPane = new FlowPane(6, 6);
        for (SeatPreference pref : SeatPreference.values()) {
            Button chip = new Button(pref.getDisplayName());
            chip.setStyle(pref == smartPickPreference
                    ? "-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-padding: 4px 10px;"
                    : "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-padding: 4px 10px;");

            chip.setOnAction(e -> {
                smartPickPreference = pref;
                chipsPane.getChildren().forEach(n -> {
                    Button b = (Button) n;
                    b.setStyle("-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-padding: 4px 10px;");
                });
                chip.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-padding: 4px 10px;");
            });
            chipsPane.getChildren().add(chip);
        }
        prefBox.getChildren().addAll(prefLbl, chipsPane);

        // Find Best Seats CTA
        Button findBtn = new Button("✨  Find Best Seats");
        findBtn.getStyleClass().add("button-primary");
        findBtn.setMaxWidth(Double.MAX_VALUE);
        findBtn.setStyle("-fx-font-size: 13px; -fx-padding: 10px;");

        smartPickResultsBox = new VBox(10);
        smartPickResultsBox.setPadding(new Insets(6, 0, 0, 0));

        findBtn.setOnAction(e -> triggerSmartPick());

        panel.getChildren().addAll(title, subtitle, stepperBox, prefBox, findBtn, smartPickResultsBox);
        return panel;
    }

    private void triggerSmartPick() {
        smartPickResultsBox.getChildren().clear();
        Label loadingLbl = new Label("Analyzing screen acoustics and sightlines...");
        loadingLbl.setStyle("-fx-text-fill: #A1A1AA; -fx-font-size: 12px;");
        smartPickResultsBox.getChildren().add(loadingLbl);
        AnimationUtil.skeleton(loadingLbl);

        int currentUserId = AuthService.getCurrentUser() != null ? AuthService.getCurrentUser().getId() : -1;

        AsyncUi.run(() -> {
            try {
                return allocationService.findBestSeats(showtime.getId(), smartPickCount, smartPickPreference, currentUserId);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }, suggestions -> {
            smartPickResultsBox.getChildren().clear();
            if (suggestions.isEmpty() || suggestions.get(0).isNotEnoughSeats()) {
                String msg = !suggestions.isEmpty() ? suggestions.get(0).getReason() : "No matching seat group available.";
                VBox errCard = new VBox(8);
                errCard.setStyle("-fx-background-color: rgba(245,158,11,0.12); -fx-padding: 12px; -fx-background-radius: 10px; -fx-border-color: rgba(245,158,11,0.4); -fx-border-radius: 10px;");
                Label l = new Label(msg);
                l.setWrapText(true);
                l.setStyle("-fx-text-fill: #FCD34D; -fx-font-size: 12px;");
                
                Button waitlistBtn = new Button("Join Waitlist Queue ⏳");
                waitlistBtn.getStyleClass().add("button-primary");
                waitlistBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8px 12px;");
                waitlistBtn.setOnAction(ev -> WaitlistDialog.show(showtime, null));

                errCard.getChildren().addAll(l, waitlistBtn);
                smartPickResultsBox.getChildren().add(errCard);
            } else {
                for (int i = 0; i < suggestions.size(); i++) {
                    SeatSuggestion s = suggestions.get(i);
                    VBox card = createSuggestionCard(s, i + 1);
                    smartPickResultsBox.getChildren().add(card);
                    AnimationUtil.fadeInUp(card, i * 70);
                }
            }
        }, err -> {
            smartPickResultsBox.getChildren().clear();
            smartPickResultsBox.getChildren().add(new Label("Error: " + err.getMessage()));
        });
    }

    private VBox createSuggestionCard(SeatSuggestion s, int rank) {
        VBox card = new VBox(6);
        card.setStyle(
            "-fx-background-color: rgba(139,92,246,0.08);" +
            "-fx-padding: 10px 12px;" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: rgba(139,92,246,0.30);" +
            "-fx-border-radius: 10px;" +
            "-fx-border-width: 1px;" +
            "-fx-cursor: hand;"
        );

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label rankLbl = new Label("#" + rank + (rank == 1 ? " Recommended" : " Alternative"));
        rankLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label scoreLbl = new Label(String.format("%.0f%% Match", s.getScore()));
        scoreLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #22C55E;");
        top.getChildren().addAll(rankLbl, sp, scoreLbl);

        Label seatsLbl = new Label("🎟 " + s.getSeatCodesSummary());
        seatsLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label reasonLbl = new Label(s.getReason());
        reasonLbl.setWrapText(true);
        reasonLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        Button applyBtn = new Button("Apply Selection");
        applyBtn.setStyle("-fx-background-color: linear-gradient(to right, #8B5CF6, #00C2FF); -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: 700; -fx-background-radius: 6px; -fx-padding: 5px 12px;");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setOnAction(e -> applySuggestion(s));

        // Hover effect: pulse-glow the candidate seats on the map
        card.setOnMouseEntered(e -> {
            for (Seat seat : s.getSeats()) {
                Button b = seatButtonMap.get(seat.getId());
                if (b != null && !selectedSeats.contains(seat)) {
                    AnimationUtil.pulseGlow(b);
                }
            }
        });

        card.getChildren().addAll(top, seatsLbl, reasonLbl, applyBtn);
        return card;
    }

    private void applySuggestion(SeatSuggestion s) {
        // Clear current selections
        List<Seat> toDeselect = new ArrayList<>(selectedSeats);
        for (Seat oldSeat : toDeselect) {
            Button b = seatButtonMap.get(oldSeat.getId());
            if (b != null) {
                BigDecimal price = oldSeat.getSeatType() == SeatType.PREMIUM ? dynamicPremiumPrice : dynamicRegularPrice;
                toggleSeatSelection(b, oldSeat, price, oldSeat.getSeatType() == SeatType.PREMIUM);
            }
        }

        // Apply new seats with stagger
        List<Button> buttonsToAnimate = new ArrayList<>();
        for (Seat seat : s.getSeats()) {
            Button b = seatButtonMap.get(seat.getId());
            if (b != null) {
                BigDecimal price = seat.getSeatType() == SeatType.PREMIUM ? dynamicPremiumPrice : dynamicRegularPrice;
                toggleSeatSelection(b, seat, price, seat.getSeatType() == SeatType.PREMIUM);
                buttonsToAnimate.add(b);
            }
        }
        AnimationUtil.stagger(buttonsToAnimate, 60);
        ToastManager.showSuccess("Smart Pick Applied", "Selected seats: " + s.getSeatCodesSummary());
    }
}
