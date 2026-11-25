package com.cineflow.ui.customer;

import com.cineflow.dao.BookingDAO;
import com.cineflow.dao.SeatDAO;
import com.cineflow.dao.SeatHoldDAO;
import com.cineflow.dao.ShowtimeDAO;
import com.cineflow.model.*;
import com.cineflow.service.RescheduleService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Smart Ticket Rescheduling Dialog.
 * Allows customers to switch showtimes and select replacement seats
 * without cancelling their booking, maintaining transaction safety.
 */
public class TicketRescheduleDialog extends Stage {

    private final Booking booking;
    private final User currentUser;
    private final Runnable onRescheduled;
    private final RescheduleService rescheduleService;
    private final ShowtimeDAO showtimeDAO;
    private final SeatDAO seatDAO;
    private final BookingDAO bookingDAO;
    private final SeatHoldDAO seatHoldDAO;

    private ComboBox<Showtime> showtimeCombo;
    private FlowPane seatsFlowPane;
    private Label fareDiffLabel;
    private Label seatCountLabel;
    private ComboBox<PaymentMethod> paymentCombo;
    private Button confirmBtn;

    private final List<Seat> selectedNewSeats = new ArrayList<>();
    private final List<BigDecimal> selectedNewPrices = new ArrayList<>();
    private int requiredSeatCount;
    private BigDecimal fareDifference = BigDecimal.ZERO;

    public TicketRescheduleDialog(Booking booking, User currentUser, Runnable onRescheduled) {
        this.booking = booking;
        this.currentUser = currentUser;
        this.onRescheduled = onRescheduled;
        this.rescheduleService = new RescheduleService();
        this.showtimeDAO = new ShowtimeDAO();
        this.seatDAO = new SeatDAO();
        this.bookingDAO = new BookingDAO();
        this.seatHoldDAO = new SeatHoldDAO();

        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.TRANSPARENT);

        // Compute original seat count from comma-separated string
        String seats = booking.getSeatNumbers();
        this.requiredSeatCount = (seats != null && !seats.trim().isEmpty()) ? seats.split(",").length : 1;

        Scene scene = new Scene(createContent());
        scene.setFill(Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignored) {}
        setScene(scene);
    }

    private Region createContent() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setPrefWidth(580);
        root.setStyle(
            "-fx-background-color: #12101F;" +
            "-fx-background-radius: 18px;" +
            "-fx-border-color: rgba(139,92,246,0.35);" +
            "-fx-border-width: 1.5px; -fx-border-radius: 18px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.70), 24, 0, 0, 8);"
        );

        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("🔄");
        icon.setStyle("-fx-font-size: 22px;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Smart Ticket Rescheduling");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");
        Label subtitle = new Label("Keep your reservation, switch showtime & choose replacement seats");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #A1A1AA;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("button-secondary");
        closeBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 10px;");
        closeBtn.setOnAction(e -> close());

        header.getChildren().addAll(icon, titleBox, spacer, closeBtn);
        root.getChildren().add(header);

        // Original Booking Summary Card
        VBox originalCard = new VBox(6);
        originalCard.setStyle(
            "-fx-background-color: rgba(139,92,246,0.08);" +
            "-fx-border-color: rgba(139,92,246,0.20);" +
            "-fx-border-width: 1px; -fx-background-radius: 12px; -fx-border-radius: 12px;" +
            "-fx-padding: 12px 16px;"
        );
        Label origHeader = new Label("ORIGINAL BOOKING #" + booking.getId());
        origHeader.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #8B5CF6; -fx-letter-spacing: 1px;");

        Label origMovie = new Label("🎬 " + booking.getMovieTitle());
        origMovie.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");

        Label origDetails = new Label(
            "Showtime: " + booking.getFormattedShowtimeStart() + " (" + booking.getScreenName() + ")  •  Seats: " +
            booking.getSeatNumbers() + "  •  Paid: ₹" + booking.getTotalAmount()
        );
        origDetails.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1;");
        originalCard.getChildren().addAll(origHeader, origMovie, origDetails);
        root.getChildren().add(originalCard);

        // Step 1: Select New Showtime
        VBox step1Box = new VBox(8);
        Label step1Lbl = new Label("1. Select New Showtime for " + booking.getMovieTitle());
        step1Lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");

        showtimeCombo = new ComboBox<>();
        showtimeCombo.setMaxWidth(Double.MAX_VALUE);
        showtimeCombo.setStyle("-fx-background-radius: 8px; -fx-padding: 6px;");
        showtimeCombo.setPromptText("Loading available screenings...");
        showtimeCombo.setOnAction(e -> loadSeatsForSelectedShowtime());

        step1Box.getChildren().addAll(step1Lbl, showtimeCombo);
        root.getChildren().add(step1Box);

        // Step 2: Seat Selection
        VBox step2Box = new VBox(8);
        HBox step2Header = new HBox(10);
        step2Header.setAlignment(Pos.CENTER_LEFT);

        Label step2Lbl = new Label("2. Pick Replacement Seats");
        step2Lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC;");

        seatCountLabel = new Label("Selected: 0 / " + requiredSeatCount);
        seatCountLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #F59E0B;");

        step2Header.getChildren().addAll(step2Lbl, seatCountLabel);

        seatsFlowPane = new FlowPane(6, 6);
        seatsFlowPane.setPadding(new Insets(10));
        seatsFlowPane.setStyle(
            "-fx-background-color: rgba(9,9,15,0.70);" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-width: 1px; -fx-background-radius: 10px; -fx-border-radius: 10px;"
        );
        seatsFlowPane.setMinHeight(120);
        seatsFlowPane.setPrefWrapLength(520);

        Label emptySeatsLbl = new Label("Select a showtime above to view available seats.");
        emptySeatsLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        seatsFlowPane.getChildren().add(emptySeatsLbl);

        step2Box.getChildren().addAll(step2Header, seatsFlowPane);
        root.getChildren().add(step2Box);

        // Step 3: Fare difference & Payment
        HBox bottomRow = new HBox(16);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.setPadding(new Insets(8, 0, 0, 0));

        VBox fareBox = new VBox(3);
        fareDiffLabel = new Label("Fare Difference: ₹0.00");
        fareDiffLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #22C55E;");
        Label farePolicyLbl = new Label("Policy: Higher fare requires difference; lower fare is credited.");
        farePolicyLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");
        fareBox.getChildren().addAll(fareDiffLabel, farePolicyLbl);

        Region spacerBottom = new Region();
        HBox.setHgrow(spacerBottom, Priority.ALWAYS);

        paymentCombo = new ComboBox<>(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentCombo.setValue(PaymentMethod.UPI);
        paymentCombo.setVisible(false);
        paymentCombo.setPrefWidth(100);

        confirmBtn = new Button("Confirm Reschedule  →");
        confirmBtn.getStyleClass().add("button-primary");
        confirmBtn.setStyle("-fx-font-size: 13px; -fx-padding: 9px 20px;");
        confirmBtn.setDisable(true);
        confirmBtn.setOnAction(e -> handleConfirmReschedule());

        bottomRow.getChildren().addAll(fareBox, spacerBottom, paymentCombo, confirmBtn);
        root.getChildren().add(bottomRow);

        // Async load alternative showtimes
        loadUpcomingShowtimesAsync();

        return root;
    }

    private void loadUpcomingShowtimesAsync() {
        AsyncUi.run(
            () -> {
                try {
                    Optional<Showtime> currentShow = showtimeDAO.findById(booking.getShowtimeId());
                    if (currentShow.isEmpty()) return Collections.<Showtime>emptyList();
                    int movieId = currentShow.get().getMovieId();
                    return rescheduleService.getAlternativeShowtimes(movieId, booking.getShowtimeId());
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            },
            alternatives -> {
                if (alternatives.isEmpty()) {
                    showtimeCombo.setPromptText("No other upcoming screenings for " + booking.getMovieTitle());
                    showtimeCombo.setDisable(true);
                } else {
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, dd MMM • HH:mm");
                    showtimeCombo.setCellFactory(lv -> new ListCell<>() {
                        @Override
                        protected void updateItem(Showtime s, boolean empty) {
                            super.updateItem(s, empty);
                            if (empty || s == null) {
                                setText(null);
                            } else {
                                setText(s.getStartTime().format(fmt) + " (" + s.getScreenName() + ") — Reg ₹" +
                                        s.getPriceRegular() + " / Prem ₹" + s.getPricePremium());
                            }
                        }
                    });
                    showtimeCombo.setButtonCell(new ListCell<>() {
                        @Override
                        protected void updateItem(Showtime s, boolean empty) {
                            super.updateItem(s, empty);
                            if (empty || s == null) {
                                setText(null);
                            } else {
                                setText(s.getStartTime().format(fmt) + " (" + s.getScreenName() + ")");
                            }
                        }
                    });
                    showtimeCombo.setItems(FXCollections.observableArrayList(alternatives));
                    showtimeCombo.getSelectionModel().selectFirst();
                }
            },
            err -> ToastManager.show(ToastManager.ToastType.ERROR, "Load Failed", err.getMessage())
        );
    }

    private void loadSeatsForSelectedShowtime() {
        Showtime selectedShow = showtimeCombo.getValue();
        if (selectedShow == null) return;

        selectedNewSeats.clear();
        selectedNewPrices.clear();
        updateFareCalculation();

        seatsFlowPane.getChildren().clear();
        Label loadingLbl = new Label("Loading auditorium seat map...");
        loadingLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #8B5CF6;");
        seatsFlowPane.getChildren().add(loadingLbl);

        AsyncUi.run(
            () -> {
                try {
                    List<Seat> allSeats = seatDAO.findByScreenId(selectedShow.getScreenId());
                    List<Integer> bookedIds = bookingDAO.getBookedSeatIdsForShowtime(selectedShow.getId());
                    List<Integer> heldIds = seatHoldDAO.getActiveHeldSeatIds(selectedShow.getId(), currentUser.getId());
                    return new Object[]{allSeats, new HashSet<>(bookedIds), new HashSet<>(heldIds)};
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            },
            data -> {
                seatsFlowPane.getChildren().clear();
                List<Seat> allSeats = (List<Seat>) data[0];
                Set<Integer> booked = (Set<Integer>) data[1];
                Set<Integer> held = (Set<Integer>) data[2];

                for (Seat seat : allSeats) {
                    Button sBtn = new Button(seat.getRowLabel() + seat.getSeatNumber());
                    sBtn.setPrefSize(42, 34);

                    boolean unavailable = booked.contains(seat.getId()) || held.contains(seat.getId());
                    if (unavailable) {
                        sBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #9CA3AF; -fx-background-radius: 6px; -fx-font-size: 10px;");
                        sBtn.setDisable(true);
                    } else {
                        sBtn.setStyle(
                            "-fx-background-color: rgba(34,197,94,0.18);" +
                            "-fx-border-color: rgba(34,197,94,0.50);" +
                            "-fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;" +
                            "-fx-text-fill: #4ADE80; -fx-font-size: 10px; -fx-cursor: hand;"
                        );
                        sBtn.setOnAction(e -> toggleSeatSelection(sBtn, seat, selectedShow));
                    }
                    seatsFlowPane.getChildren().add(sBtn);
                }
            },
            err -> ToastManager.show(ToastManager.ToastType.ERROR, "Seat Map Error", err.getMessage())
        );
    }

    private void toggleSeatSelection(Button btn, Seat seat, Showtime show) {
        if (selectedNewSeats.contains(seat)) {
            selectedNewSeats.remove(seat);
            btn.setStyle(
                "-fx-background-color: rgba(34,197,94,0.18);" +
                "-fx-border-color: rgba(34,197,94,0.50);" +
                "-fx-border-width: 1px; -fx-background-radius: 6px; -fx-border-radius: 6px;" +
                "-fx-text-fill: #4ADE80; -fx-font-size: 10px; -fx-cursor: hand;"
            );
        } else {
            if (selectedNewSeats.size() >= requiredSeatCount) {
                ToastManager.show(ToastManager.ToastType.WARNING, "Selection Limit",
                        "You can only select exactly " + requiredSeatCount + " replacement seats.");
                return;
            }
            selectedNewSeats.add(seat);
            btn.setStyle(
                "-fx-background-color: #8B5CF6;" +
                "-fx-text-fill: white; -fx-font-weight: 800; -fx-background-radius: 6px; -fx-font-size: 10px;" +
                "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.60), 8, 0, 0, 2);"
            );
        }
        updateFareCalculation();
    }

    private void updateFareCalculation() {
        seatCountLabel.setText("Selected: " + selectedNewSeats.size() + " / " + requiredSeatCount);
        Showtime show = showtimeCombo.getValue();
        if (show == null || selectedNewSeats.size() != requiredSeatCount) {
            fareDiffLabel.setText("Select " + requiredSeatCount + " seats to calculate fare.");
            fareDiffLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1AA;");
            confirmBtn.setDisable(true);
            paymentCombo.setVisible(false);
            return;
        }

        selectedNewPrices.clear();
        BigDecimal newTotal = BigDecimal.ZERO;
        for (Seat s : selectedNewSeats) {
            BigDecimal price = s.getSeatType() == SeatType.PREMIUM ? show.getPricePremium() : show.getPriceRegular();
            selectedNewPrices.add(price);
            newTotal = newTotal.add(price);
        }

        fareDifference = newTotal.subtract(booking.getTotalAmount());
        if (fareDifference.compareTo(BigDecimal.ZERO) > 0) {
            fareDiffLabel.setText("Additional Due: +₹" + fareDifference);
            fareDiffLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #F59E0B;");
            paymentCombo.setVisible(true);
        } else if (fareDifference.compareTo(BigDecimal.ZERO) < 0) {
            fareDiffLabel.setText("Refund/Credit Due: -₹" + fareDifference.abs());
            fareDiffLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #38BDF8;");
            paymentCombo.setVisible(false);
        } else {
            fareDiffLabel.setText("Fare Difference: ₹0.00 (Even Exchange)");
            fareDiffLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #22C55E;");
            paymentCombo.setVisible(false);
        }

        confirmBtn.setDisable(false);
    }

    private void handleConfirmReschedule() {
        Showtime show = showtimeCombo.getValue();
        if (show == null || selectedNewSeats.size() != requiredSeatCount) return;

        confirmBtn.setDisable(true);
        List<Integer> newSeatIds = new ArrayList<>();
        for (Seat s : selectedNewSeats) {
            newSeatIds.add(s.getId());
        }

        AsyncUi.run(
            () -> {
                try {
                    return rescheduleService.rescheduleBooking(
                            booking.getId(),
                            currentUser.getId(),
                            show.getId(),
                            newSeatIds,
                            selectedNewPrices,
                            fareDifference,
                            paymentCombo.getValue()
                    );
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            },
            success -> {
                ToastManager.show(ToastManager.ToastType.SUCCESS, "Reschedule Confirmed! 🎟",
                        "Your tickets have been moved to " + show.getFormattedStartTime() + ".");
                close();
                if (onRescheduled != null) {
                    onRescheduled.run();
                }
            },
            err -> {
                confirmBtn.setDisable(false);
                ToastManager.show(ToastManager.ToastType.ERROR, "Reschedule Failed", err.getMessage());
            }
        );
    }
}
