package com.cineflow.ui.admin;

import com.cineflow.dao.WaitlistDAO;
import com.cineflow.model.WaitlistEntry;
import com.cineflow.model.WaitlistStatus;
import com.cineflow.service.WaitlistService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CineFlow v2 — Admin Waitlist Monitor & Queue Sweeper View.
 */
public class AdminWaitlistView {

    private final WaitlistDAO waitlistDAO = new WaitlistDAO();
    private final WaitlistService waitlistService = WaitlistService.getInstance();

    private TableView<WaitlistEntry> tableView;
    private ObservableList<WaitlistEntry> waitlistData;
    private ComboBox<String> statusFilter;
    private Label queueSummaryLabel;

    public Parent getView() {
        VBox root = new VBox(14);
        root.setPadding(new Insets(20, 24, 24, 24));
        root.getStyleClass().add("app-container");

        // Toolbar
        HBox toolbar = createToolbar();
        root.getChildren().add(toolbar);

        // Table
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        refreshTable();
        return root;
    }

    private HBox createToolbar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Waitlist Queue Monitor");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);

        queueSummaryLabel = new Label();
        queueSummaryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #38BDF8; -fx-font-weight: 700;");

        Label filterLbl = new Label("Filter:");
        filterLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1B5;");

        statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "ALL", "WAITING", "OFFERED", "CLAIMED", "EXPIRED", "CANCELLED", "DECLINED"
        ));
        statusFilter.setValue("ALL");
        statusFilter.valueProperty().addListener((obs, o, n) -> refreshTable());

        Button sweepBtn = new Button("⚡ Run Queue Sweep");
        sweepBtn.getStyleClass().add("button-primary");
        sweepBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px;");
        sweepBtn.setOnAction(e -> {
            AsyncUi.run(() -> {
                waitlistService.processAllWaitingQueues();
                return null;
            }, res -> {
                ToastManager.show(ToastManager.ToastType.SUCCESS, "Queue Swept", "Evaluated active waiting lists against free seats.");
                refreshTable();
            }, err -> {});
        });

        Button expireBtn = new Button("⌛ Expire Overdue");
        expireBtn.getStyleClass().add("button-secondary");
        expireBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");
        expireBtn.setOnAction(e -> {
            AsyncUi.run(() -> {
                waitlistService.expireOffers();
                return null;
            }, res -> {
                ToastManager.show(ToastManager.ToastType.INFO, "Swept Offers", "Cleaned expired offers and reassigned to next in line.");
                refreshTable();
            }, err -> {});
        });

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("button-secondary");
        refreshBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");
        refreshBtn.setOnAction(e -> refreshTable());

        bar.getChildren().addAll(title, queueSummaryLabel, sp1, filterLbl, statusFilter, sweepBtn, expireBtn, refreshBtn);
        return bar;
    }

    private TableView<WaitlistEntry> createTableView() {
        TableView<WaitlistEntry> table = new TableView<>();
        waitlistData = FXCollections.observableArrayList();
        table.setItems(waitlistData);
        table.setStyle("-fx-background-color: transparent;");

        TableColumn<WaitlistEntry, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(55);

        TableColumn<WaitlistEntry, String> userCol = new TableColumn<>("Guest User");
        userCol.setCellValueFactory(c -> new SimpleStringProperty("@" + (c.getValue().getUsername() != null ? c.getValue().getUsername() : "user_" + c.getValue().getUserId())));
        userCol.setPrefWidth(120);

        TableColumn<WaitlistEntry, String> movieCol = new TableColumn<>("Movie");
        movieCol.setCellValueFactory(new PropertyValueFactory<>("movieTitle"));
        movieCol.setPrefWidth(170);

        TableColumn<WaitlistEntry, String> screenCol = new TableColumn<>("Auditorium");
        screenCol.setCellValueFactory(new PropertyValueFactory<>("screenName"));
        screenCol.setPrefWidth(120);

        TableColumn<WaitlistEntry, String> timeCol = new TableColumn<>("Showtime");
        timeCol.setCellValueFactory(c -> {
            var st = c.getValue().getShowtimeStartTime();
            return new SimpleStringProperty(st != null ? st.format(DateTimeFormatter.ofPattern("MMM dd  hh:mm a")) : "—");
        });
        timeCol.setPrefWidth(140);

        TableColumn<WaitlistEntry, Integer> seatsCol = new TableColumn<>("Seats");
        seatsCol.setCellValueFactory(new PropertyValueFactory<>("seatsWanted"));
        seatsCol.setPrefWidth(65);

        TableColumn<WaitlistEntry, String> prefCol = new TableColumn<>("Pref");
        prefCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeatPreference().name()));
        prefCol.setPrefWidth(110);

        TableColumn<WaitlistEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                if ("CLAIMED".equals(item)) badge.getStyleClass().add("badge-success");
                else if ("OFFERED".equals(item)) badge.getStyleClass().add("badge-warning");
                else if ("WAITING".equals(item)) badge.getStyleClass().add("badge-info");
                else badge.getStyleClass().add("badge-danger");
                badge.setStyle("-fx-font-size: 10px;");
                setGraphic(badge);
                setText(null);
            }
        });

        TableColumn<WaitlistEntry, String> expiryCol = new TableColumn<>("Offer Expiry");
        expiryCol.setCellValueFactory(c -> {
            var exp = c.getValue().getOfferExpiresAt();
            return new SimpleStringProperty(exp != null ? exp.format(DateTimeFormatter.ofPattern("hh:mm:ss a")) : "—");
        });
        expiryCol.setPrefWidth(120);

        table.getColumns().addAll(idCol, userCol, movieCol, screenCol, timeCol, seatsCol, prefCol, statusCol, expiryCol);
        return table;
    }

    public void refreshTable() {
        String filter = statusFilter != null ? statusFilter.getValue() : "ALL";
        AsyncUi.run(() -> {
            try {
                return waitlistDAO.findAll(filter);
            } catch (SQLException e) {
                return List.<WaitlistEntry>of();
            }
        }, list -> {
            waitlistData.setAll(list);
            long waitingCount = list.stream().filter(e -> e.getStatus() == WaitlistStatus.WAITING).count();
            long offeredCount = list.stream().filter(e -> e.getStatus() == WaitlistStatus.OFFERED).count();
            queueSummaryLabel.setText(waitingCount + " waiting • " + offeredCount + " offered");
        }, err -> {});
    }
}
