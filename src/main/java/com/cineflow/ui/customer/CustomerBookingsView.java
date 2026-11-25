package com.cineflow.ui.customer;

import com.cineflow.model.Booking;
import com.cineflow.model.User;
import com.cineflow.model.WaitlistEntry;
import com.cineflow.model.WaitlistStatus;
import com.cineflow.service.AuthService;
import com.cineflow.service.BookingService;
import com.cineflow.service.RescheduleService;
import com.cineflow.service.WaitlistService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * CineFlow v2 — Customer Bookings & Waitlist Queue Management.
 */
public class CustomerBookingsView {

    private final BookingService bookingService;
    private final Consumer<Booking> onViewTicket;

    private VBox cachedRoot;
    private StackPane contentStack;
    private TableView<Booking> tableView;
    private ObservableList<Booking> bookingData;

    private TableView<WaitlistEntry> waitlistTableView;
    private ObservableList<WaitlistEntry> waitlistData;

    private Button bookingsTabBtn;
    private Button waitlistTabBtn;
    private HBox bookingsToolbar;
    private HBox waitlistToolbar;

    public CustomerBookingsView(Consumer<Booking> onViewTicket) {
        this.bookingService = new BookingService();
        this.onViewTicket = onViewTicket;
    }

    public Parent getView() {
        if (cachedRoot == null) {
            cachedRoot = buildView();
        }
        refreshBookings();
        refreshWaitlist();
        return cachedRoot;
    }

    private VBox buildView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("app-container");

        root.getChildren().add(createSectionHeader());

        // Segmented Tab Bar
        HBox tabRow = new HBox(12);
        tabRow.setPadding(new Insets(14, 24, 8, 24));
        tabRow.setAlignment(Pos.CENTER_LEFT);

        bookingsTabBtn = new Button("🎟  My Bookings");
        waitlistTabBtn = new Button("⏳  My Waitlist Queue");

        setTabActive(bookingsTabBtn, waitlistTabBtn);

        bookingsTabBtn.setOnAction(e -> {
            setTabActive(bookingsTabBtn, waitlistTabBtn);
            showBookingsSection();
        });

        waitlistTabBtn.setOnAction(e -> {
            setTabActive(waitlistTabBtn, bookingsTabBtn);
            showWaitlistSection();
        });

        tabRow.getChildren().addAll(bookingsTabBtn, waitlistTabBtn);
        root.getChildren().add(tabRow);

        // Toolbars
        bookingsToolbar = createBookingsToolbar();
        waitlistToolbar = createWaitlistToolbar();
        waitlistToolbar.setVisible(false);
        waitlistToolbar.setManaged(false);

        VBox toolbarsBox = new VBox(bookingsToolbar, waitlistToolbar);
        root.getChildren().add(toolbarsBox);

        // Stacked Tables
        contentStack = new StackPane();
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        tableView = createTableView();
        VBox bookingsTableWrapper = new VBox(tableView);
        bookingsTableWrapper.setPadding(new Insets(0, 24, 24, 24));
        VBox.setVgrow(bookingsTableWrapper, Priority.ALWAYS);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        waitlistTableView = createWaitlistTableView();
        VBox waitlistTableWrapper = new VBox(waitlistTableView);
        waitlistTableWrapper.setPadding(new Insets(0, 24, 24, 24));
        VBox.setVgrow(waitlistTableWrapper, Priority.ALWAYS);
        VBox.setVgrow(waitlistTableView, Priority.ALWAYS);
        waitlistTableWrapper.setVisible(false);

        contentStack.getChildren().addAll(bookingsTableWrapper, waitlistTableWrapper);
        root.getChildren().add(contentStack);

        return root;
    }

    private void showBookingsSection() {
        bookingsToolbar.setVisible(true);
        bookingsToolbar.setManaged(true);
        waitlistToolbar.setVisible(false);
        waitlistToolbar.setManaged(false);

        contentStack.getChildren().get(0).setVisible(true);
        contentStack.getChildren().get(1).setVisible(false);
        refreshBookings();
    }

    private void showWaitlistSection() {
        bookingsToolbar.setVisible(false);
        bookingsToolbar.setManaged(false);
        waitlistToolbar.setVisible(true);
        waitlistToolbar.setManaged(true);

        contentStack.getChildren().get(0).setVisible(false);
        contentStack.getChildren().get(1).setVisible(true);
        refreshWaitlist();
    }

    private void setTabActive(Button active, Button inactive) {
        active.setStyle(
            "-fx-background-color: linear-gradient(to right, #8B5CF6, #6D28D9);" +
            "-fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 13px;" +
            "-fx-background-radius: 8px; -fx-padding: 7px 18px; -fx-cursor: hand;"
        );
        inactive.setStyle(
            "-fx-background-color: rgba(139,92,246,0.08);" +
            "-fx-text-fill: #A1A1B5; -fx-font-weight: 600; -fx-font-size: 13px;" +
            "-fx-border-color: rgba(139,92,246,0.22);" +
            "-fx-border-width: 1px; -fx-background-radius: 8px; -fx-border-radius: 8px;" +
            "-fx-padding: 7px 18px; -fx-cursor: hand;"
        );
    }

    private HBox createSectionHeader() {
        HBox header = new HBox(14);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 28, 14, 28));
        header.setStyle(
            "-fx-background-color: linear-gradient(to right, rgba(139,92,246,0.10), transparent);" +
            "-fx-border-color: rgba(139,92,246,0.12);" +
            "-fx-border-width: 0 0 1px 0;"
        );

        Label title = new Label("My Activity");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");

        Label dotSep = new Label("✦");
        dotSep.setStyle("-fx-text-fill: #8B5CF6;");

        Label subtitle = new Label("Active bookings and live waitlist queue status");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1AA;");

        header.getChildren().addAll(title, dotSep, subtitle);
        return header;
    }

    private HBox createBookingsToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 24, 14, 24));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button viewTicketBtn = new Button("🎟  View Ticket & QR");
        viewTicketBtn.getStyleClass().add("button-primary");
        viewTicketBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px;");
        viewTicketBtn.setOnAction(e -> {
            Booking selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && onViewTicket != null) {
                onViewTicket.accept(selected);
            } else {
                ToastManager.show(ToastManager.ToastType.WARNING, "Select Booking", "Please select a booking to view its digital ticket.");
            }
        });

        Button snacksBtn = new Button("🍿  Snacks & F&B");
        snacksBtn.getStyleClass().add("button-secondary");
        snacksBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 16px; -fx-text-fill: #F59E0B;");
        snacksBtn.setOnAction(e -> handleOrderConcessions());

        Button reviewBtn = new Button("⭐  Review Movie");
        reviewBtn.getStyleClass().add("button-secondary");
        reviewBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 16px; -fx-text-fill: #38BDF8;");
        reviewBtn.setOnAction(e -> handleReviewMovie());

        Button rescheduleBtn = new Button("🔄  Reschedule Ticket");
        rescheduleBtn.getStyleClass().add("button-secondary");
        rescheduleBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px;");
        rescheduleBtn.setOnAction(e -> handleReschedule());

        Button cancelBtn = new Button("✕  Cancel Booking");
        cancelBtn.getStyleClass().add("button-danger");
        cancelBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 18px;");
        cancelBtn.setOnAction(e -> handleCancellation());

        Button refreshBtn = new Button("↻  Refresh");
        refreshBtn.getStyleClass().add("button-secondary");
        refreshBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 14px;");
        refreshBtn.setOnAction(e -> refreshBookings());

        bar.getChildren().addAll(spacer, viewTicketBtn, snacksBtn, reviewBtn, rescheduleBtn, cancelBtn, refreshBtn);
        return bar;
    }

    private void handleOrderConcessions() {
        Booking selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Booking", "Please select a booking to order snacks for.");
            return;
        }
        if (selected.getStatus() != com.cineflow.model.BookingStatus.CONFIRMED) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Invalid Booking", "You can only order snacks for confirmed bookings.");
            return;
        }
        ConcessionSelectionDialog dialog = new ConcessionSelectionDialog(selected, this::refreshBookings);
        dialog.show();
    }

    private void handleReviewMovie() {
        Booking selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Booking", "Please select a booking to review.");
            return;
        }
        if (selected.getStatus() != com.cineflow.model.BookingStatus.CONFIRMED) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Invalid Booking", "You can only review movies for confirmed bookings.");
            return;
        }
        MovieReviewDialog dialog = new MovieReviewDialog(selected, this::refreshBookings);
        dialog.show();
    }

    private void handleReschedule() {
        Booking selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Booking", "Please select an active booking to reschedule.");
            return;
        }

        RescheduleService rescheduleService = new RescheduleService();
        try {
            rescheduleService.validateEligibility(selected);
        } catch (Exception ex) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Cannot Reschedule", ex.getMessage());
            return;
        }

        User user = AuthService.getCurrentUser();
        if (user == null) return;

        TicketRescheduleDialog dialog = new TicketRescheduleDialog(selected, user, this::refreshBookings);
        dialog.showAndWait();
    }

    private HBox createWaitlistToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 24, 14, 24));

        Label infoLbl = new Label("Live automated queue. Cancelled tickets are immediately allocated to #1 in line.");
        infoLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #38BDF8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button leaveBtn = new Button("✕  Leave Waitlist");
        leaveBtn.getStyleClass().add("button-danger");
        leaveBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 16px;");
        leaveBtn.setOnAction(e -> handleLeaveWaitlist());

        Button refreshBtn = new Button("↻  Refresh Queue");
        refreshBtn.getStyleClass().add("button-secondary");
        refreshBtn.setStyle("-fx-font-size: 13px; -fx-padding: 8px 14px;");
        refreshBtn.setOnAction(e -> refreshWaitlist());

        bar.getChildren().addAll(infoLbl, spacer, leaveBtn, refreshBtn);
        return bar;
    }

    private TableView<Booking> createTableView() {
        TableView<Booking> table = new TableView<>();
        bookingData = FXCollections.observableArrayList();
        table.setItems(bookingData);
        table.setStyle("-fx-background-color: transparent;");

        TableColumn<Booking, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(70);

        TableColumn<Booking, String> movieCol = new TableColumn<>("Movie");
        movieCol.setCellValueFactory(new PropertyValueFactory<>("movieTitle"));
        movieCol.setPrefWidth(190);

        TableColumn<Booking, String> screenCol = new TableColumn<>("Auditorium");
        screenCol.setCellValueFactory(new PropertyValueFactory<>("screenName"));
        screenCol.setPrefWidth(130);

        TableColumn<Booking, String> timeCol = new TableColumn<>("Showtime");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedShowtimeStart()));
        timeCol.setPrefWidth(150);

        TableColumn<Booking, String> seatsCol = new TableColumn<>("Seats");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("seatNumbers"));
        seatsCol.setPrefWidth(120);

        TableColumn<Booking, String> totalCol = new TableColumn<>("Total Paid");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty("₹ " + c.getValue().getTotalAmount().toPlainString()));
        totalCol.setPrefWidth(110);

        TableColumn<Booking, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        statusCol.setPrefWidth(105);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("CONFIRMED".equals(item) ? "badge-success" : "badge-danger");
                badge.setStyle("-fx-font-size: 11px;");
                setGraphic(badge);
                setText(null);
            }
        });

        TableColumn<Booking, String> refundCol = new TableColumn<>("Refund");
        refundCol.setPrefWidth(100);
        refundCol.setCellValueFactory(c -> {
            var status = c.getValue().getRefundStatus();
            return new SimpleStringProperty(status == null ? "" : switch (status) {
                case REFUND_PENDING -> "Pending";
                case REFUNDED -> "Refunded";
                case REFUND_FAILED -> "Failed";
                default -> "";
            });
        });

        TableColumn<Booking, String> bookedCol = new TableColumn<>("Booked On");
        bookedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFormattedBookingTime()));
        bookedCol.setPrefWidth(150);

        table.getColumns().addAll(idCol, movieCol, screenCol, timeCol, seatsCol, totalCol, statusCol, refundCol, bookedCol);

        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Booking selected = table.getSelectionModel().getSelectedItem();
                if (selected != null && onViewTicket != null) {
                    onViewTicket.accept(selected);
                }
            }
        });

        return table;
    }

    private TableView<WaitlistEntry> createWaitlistTableView() {
        TableView<WaitlistEntry> table = new TableView<>();
        waitlistData = FXCollections.observableArrayList();
        table.setItems(waitlistData);
        table.setStyle("-fx-background-color: transparent;");

        TableColumn<WaitlistEntry, Integer> idCol = new TableColumn<>("Entry ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(80);

        TableColumn<WaitlistEntry, String> movieCol = new TableColumn<>("Movie");
        movieCol.setCellValueFactory(new PropertyValueFactory<>("movieTitle"));
        movieCol.setPrefWidth(190);

        TableColumn<WaitlistEntry, String> screenCol = new TableColumn<>("Screen");
        screenCol.setCellValueFactory(new PropertyValueFactory<>("screenName"));
        screenCol.setPrefWidth(120);

        TableColumn<WaitlistEntry, String> showtimeCol = new TableColumn<>("Showtime");
        showtimeCol.setCellValueFactory(c -> {
            var st = c.getValue().getShowtimeStartTime();
            return new SimpleStringProperty(st != null ? st.format(DateTimeFormatter.ofPattern("MMM dd, yyyy  hh:mm a")) : "—");
        });
        showtimeCol.setPrefWidth(160);

        TableColumn<WaitlistEntry, Integer> seatsCol = new TableColumn<>("Seats");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("seatsWanted"));
        seatsCol.setPrefWidth(70);

        TableColumn<WaitlistEntry, String> prefCol = new TableColumn<>("Preference");
        prefCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeatPreference().name()));
        prefCol.setPrefWidth(120);

        TableColumn<WaitlistEntry, String> queueCol = new TableColumn<>("Queue Status");
        queueCol.setCellValueFactory(c -> {
            WaitlistEntry e = c.getValue();
            if (e.getStatus() == WaitlistStatus.WAITING) {
                return new SimpleStringProperty("#" + e.getQueuePosition() + " in line");
            } else if (e.getStatus() == WaitlistStatus.OFFERED) {
                return new SimpleStringProperty("🎉 SEATS OFFERED!");
            } else {
                return new SimpleStringProperty("—");
            }
        });
        queueCol.setPrefWidth(140);
        queueCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                if (item.contains("OFFERED")) {
                    badge.getStyleClass().add("badge-warning");
                    badge.setStyle("-fx-font-weight: 800; -fx-font-size: 11px;");
                } else {
                    badge.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: 700; -fx-font-size: 11px;");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        TableColumn<WaitlistEntry, String> statusCol = new TableColumn<>("State");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(idCol, movieCol, screenCol, showtimeCol, seatsCol, prefCol, queueCol, statusCol);
        return table;
    }

    public void refreshBookings() {
        User user = AuthService.getCurrentUser();
        if (user == null || bookingData == null) return;

        AsyncUi.run(() -> {
            try {
                return bookingService.getCustomerBookings(user.getId());
            } catch (SQLException e) {
                return List.<Booking>of();
            }
        }, list -> bookingData.setAll(list), err -> {});
    }

    public void refreshWaitlist() {
        User user = AuthService.getCurrentUser();
        if (user == null || waitlistData == null) return;

        AsyncUi.run(() -> {
            try {
                return WaitlistService.getInstance().getCustomerWaitlist(user.getId());
            } catch (SQLException e) {
                return List.<WaitlistEntry>of();
            }
        }, list -> waitlistData.setAll(list), err -> {});
    }

    private void handleCancellation() {
        if (tableView == null) return;
        Booking selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Booking", "Please click on a booking row first.");
            return;
        }

        if (!selected.canCancel()) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Cannot Cancel", "This screening has already started or was cancelled.");
            return;
        }

        boolean confirm = AlertHelper.showConfirmation(
                "Cancel Booking",
                "Cancel Reservation #" + selected.getId() + "?",
                "Cancel your tickets for \"" + selected.getMovieTitle() + "\"?\n" +
                "Your seats will be released and any Razorpay refund will be requested automatically."
        );

        if (confirm) {
            User user = AuthService.getCurrentUser();
            AsyncUi.run(() -> {
                try {
                    return bookingService.cancelBooking(selected.getId(), user.getId());
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }, cancelled -> {
                if (cancelled) {
                    ToastManager.show(ToastManager.ToastType.SUCCESS, "Booking Cancelled", "Seats released. Check the Refund column for updates.");
                    refreshBookings();
                } else {
                    ToastManager.show(ToastManager.ToastType.ERROR, "Cancellation Failed", "Unable to cancel booking.");
                }
            }, err -> ToastManager.show(ToastManager.ToastType.ERROR, "Error", err.getMessage()));
        }
    }

    private void handleLeaveWaitlist() {
        WaitlistEntry selected = waitlistTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Entry", "Please select a waitlist queue entry first.");
            return;
        }

        if (selected.getStatus() != WaitlistStatus.WAITING && selected.getStatus() != WaitlistStatus.OFFERED) {
            ToastManager.show(ToastManager.ToastType.INFO, "Notice", "Entry is already " + selected.getStatus().name());
            return;
        }

        boolean confirm = AlertHelper.showConfirmation(
                "Leave Waitlist",
                "Leave Queue for " + selected.getMovieTitle() + "?",
                "Are you sure you want to surrender your queue position?"
        );

        if (confirm) {
            User user = AuthService.getCurrentUser();
            AsyncUi.run(() -> {
                try {
                    return WaitlistService.getInstance().leaveWaitlist(selected.getId(), user.getId());
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }, ok -> {
                if (ok) {
                    ToastManager.show(ToastManager.ToastType.INFO, "Left Queue", "You have left the waitlist.");
                    refreshWaitlist();
                }
            }, err -> ToastManager.show(ToastManager.ToastType.ERROR, "Error", err.getMessage()));
        }
    }
}
