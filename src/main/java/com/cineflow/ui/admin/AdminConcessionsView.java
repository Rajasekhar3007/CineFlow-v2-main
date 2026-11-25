package com.cineflow.ui.admin;

import com.cineflow.model.BookingConcession;
import com.cineflow.model.ConcessionPickupStatus;
import com.cineflow.service.ConcessionService;
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
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * CineFlow v3 — Live F&B Concessions Kitchen & Order Management Console.
 * Allows staff to monitor snack/beverage/combo orders and advance pickup lifecycle.
 */
public class AdminConcessionsView {

    private final ConcessionService concessionService;
    private TableView<BookingConcession> tableView;
    private ObservableList<BookingConcession> orderData;
    private ComboBox<String> statusFilter;
    private Label totalOrdersLbl;
    private Label pendingOrdersLbl;
    private Label revenueLbl;

    public AdminConcessionsView() {
        this.concessionService = new ConcessionService();
        this.orderData = FXCollections.observableArrayList();
    }

    public Parent getView() {
        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("app-container");

        root.getChildren().addAll(
                createHeader(),
                createKpiSummaryRow(),
                createFilterAndActionToolbar(),
                createTableWrapper()
        );

        loadOrders();
        return root;
    }

    private VBox createHeader() {
        VBox box = new VBox(4);
        Label title = new Label("🍿  F&B Concessions & Kitchen Dispatch");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label subtitle = new Label("Live monitor for snack pre-orders, kitchen preparation, and customer pickup dispatch");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");

        box.getChildren().addAll(title, subtitle);
        return box;
    }

    private HBox createKpiSummaryRow() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        totalOrdersLbl = new Label("0");
        pendingOrdersLbl = new Label("0");
        revenueLbl = new Label("₹ 0");

        row.getChildren().addAll(
                createStatCard("Total F&B Orders", totalOrdersLbl, "#8B5CF6"),
                createStatCard("Pending in Kitchen", pendingOrdersLbl, "#F59E0B"),
                createStatCard("Total F&B Revenue", revenueLbl, "#10B981")
        );
        return row;
    }

    private VBox createStatCard(String label, Label valueLbl, String accentHex) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14, 20, 14, 20));
        card.setStyle(
                "-fx-background-color: rgba(19,16,33,0.85);" +
                "-fx-border-color: rgba(139,92,246,0.22);" +
                "-fx-border-width: 1px; -fx-border-radius: 12px; -fx-background-radius: 12px;" +
                "-fx-min-width: 190px;"
        );

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #94A3B8;");

        valueLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: " + accentHex + ";");

        card.getChildren().addAll(lbl, valueLbl);
        return card;
    }

    private HBox createFilterAndActionToolbar() {
        HBox toolbar = new HBox(14);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 0, 6, 0));

        Label filterLbl = new Label("Filter Status:");
        filterLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #E2E8F0; -fx-font-weight: 600;");

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "PENDING", "PREPARING", "READY", "COLLECTED");
        statusFilter.setValue("ALL");
        statusFilter.getStyleClass().add("combo-box");
        statusFilter.setOnAction(e -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button startPrepBtn = new Button("🍳 Start Preparing");
        startPrepBtn.setStyle(
                "-fx-background-color: rgba(59,130,246,0.18); -fx-text-fill: #60A5FA; " +
                "-fx-border-color: rgba(59,130,246,0.40); -fx-border-radius: 8px; -fx-padding: 7px 14px; -fx-cursor: hand;"
        );
        startPrepBtn.setOnAction(e -> updateSelectedStatus(ConcessionPickupStatus.PREPARING));

        Button markReadyBtn = new Button("🔔 Mark Ready for Pickup");
        markReadyBtn.setStyle(
                "-fx-background-color: rgba(16,185,129,0.18); -fx-text-fill: #34D399; " +
                "-fx-border-color: rgba(16,185,129,0.40); -fx-border-radius: 8px; -fx-padding: 7px 14px; -fx-cursor: hand;"
        );
        markReadyBtn.setOnAction(e -> updateSelectedStatus(ConcessionPickupStatus.READY));

        Button markCollectedBtn = new Button("✅ Mark Collected");
        markCollectedBtn.setStyle(
                "-fx-background-color: rgba(139,92,246,0.18); -fx-text-fill: #C4B5FD; " +
                "-fx-border-color: rgba(139,92,246,0.40); -fx-border-radius: 8px; -fx-padding: 7px 14px; -fx-cursor: hand;"
        );
        markCollectedBtn.setOnAction(e -> updateSelectedStatus(ConcessionPickupStatus.COLLECTED));

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("button-secondary");
        refreshBtn.setOnAction(e -> loadOrders());

        toolbar.getChildren().addAll(filterLbl, statusFilter, spacer, startPrepBtn, markReadyBtn, markCollectedBtn, refreshBtn);
        return toolbar;
    }

    private VBox createTableWrapper() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("table-view");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<BookingConcession, Integer> colId = new TableColumn<>("Order ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colId.setPrefWidth(75);

        TableColumn<BookingConcession, Integer> colBooking = new TableColumn<>("Booking #");
        colBooking.setCellValueFactory(new PropertyValueFactory<>("bookingId"));
        colBooking.setPrefWidth(90);

        TableColumn<BookingConcession, String> colCustomer = new TableColumn<>("Customer");
        colCustomer.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCustomerName() != null ? cell.getValue().getCustomerName() : "Guest"));
        colCustomer.setPrefWidth(140);

        TableColumn<BookingConcession, String> colMovie = new TableColumn<>("Movie & Screen");
        colMovie.setCellValueFactory(cell -> new SimpleStringProperty(
                (cell.getValue().getMovieTitle() != null ? cell.getValue().getMovieTitle() : "Cinema") +
                " (" + (cell.getValue().getScreenName() != null ? cell.getValue().getScreenName() : "Main") + ")"));
        colMovie.setPrefWidth(200);

        TableColumn<BookingConcession, String> colItem = new TableColumn<>("Concession Item");
        colItem.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCategory().getEmoji() + " " + cell.getValue().getConcessionName()));
        colItem.setPrefWidth(210);

        TableColumn<BookingConcession, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setPrefWidth(60);

        TableColumn<BookingConcession, String> colTotal = new TableColumn<>("Total");
        colTotal.setCellValueFactory(cell -> new SimpleStringProperty("₹ " + cell.getValue().getTotalPrice().toPlainString()));
        colTotal.setPrefWidth(100);

        TableColumn<BookingConcession, String> colStatus = new TableColumn<>("Pickup Status");
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPickupStatus().getLabel()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    BookingConcession bc = getTableView().getItems().get(getIndex());
                    Label pill = new Label(bc.getPickupStatus().getLabel());
                    String hex = bc.getPickupStatus().getColorHex();
                    pill.setStyle(
                            "-fx-background-color: " + hex + "22; -fx-text-fill: " + hex + "; " +
                            "-fx-border-color: " + hex + "55; -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                            "-fx-padding: 3px 10px; -fx-font-weight: 700; -fx-font-size: 11px;"
                    );
                    setGraphic(pill);
                    setText(null);
                }
            }
        });
        colStatus.setPrefWidth(170);

        tableView.getColumns().addAll(colId, colBooking, colCustomer, colMovie, colItem, colQty, colTotal, colStatus);
        tableView.setItems(orderData);

        VBox wrapper = new VBox(tableView);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    public void loadOrders() {
        AsyncUi.run(
                () -> concessionService.getAllConcessionOrders(),
                orders -> {
                    orderData.setAll(orders);
                    updateKpis(orders);
                    applyFilter();
                },
                error -> AlertHelper.showError("Concessions Error", "Could not load F&B orders", error.getMessage())
        );
    }

    private void updateKpis(List<BookingConcession> orders) {
        int total = orders.size();
        int pending = 0;
        BigDecimal totalRev = BigDecimal.ZERO;

        for (BookingConcession o : orders) {
            if (o.getPickupStatus() == ConcessionPickupStatus.PENDING || o.getPickupStatus() == ConcessionPickupStatus.PREPARING) {
                pending++;
            }
            if (o.getTotalPrice() != null) {
                totalRev = totalRev.add(o.getTotalPrice());
            }
        }

        totalOrdersLbl.setText(String.valueOf(total));
        pendingOrdersLbl.setText(String.valueOf(pending));
        revenueLbl.setText("₹ " + totalRev.toPlainString());
    }

    private void applyFilter() {
        String filter = statusFilter != null ? statusFilter.getValue() : "ALL";
        if ("ALL".equalsIgnoreCase(filter)) {
            tableView.setItems(orderData);
        } else {
            ObservableList<BookingConcession> filtered = FXCollections.observableArrayList();
            for (BookingConcession bc : orderData) {
                if (bc.getPickupStatus().name().equalsIgnoreCase(filter)) {
                    filtered.add(bc);
                }
            }
            tableView.setItems(filtered);
        }
    }

    private void updateSelectedStatus(ConcessionPickupStatus status) {
        BookingConcession selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showWarning("No Selection", "Order Selection", "Please select a concession order from the table.");
            return;
        }

        AsyncUi.run(
                () -> concessionService.updatePickupStatus(selected.getId(), status),
                success -> {
                    if (success) {
                        ToastManager.showInfo("Order Updated", "Order #" + selected.getId() + " updated to " + status.getLabel());
                        loadOrders();
                    }
                },
                error -> AlertHelper.showError("Update Failed", "Could not update status", error.getMessage())
        );
    }
}
