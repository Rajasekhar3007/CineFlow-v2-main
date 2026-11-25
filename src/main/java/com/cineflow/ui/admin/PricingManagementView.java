package com.cineflow.ui.admin;

import com.cineflow.dao.PricingRuleDAO;
import com.cineflow.model.*;
import com.cineflow.service.PricingEngine;
import com.cineflow.ui.common.AnimationUtil;
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
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CineFlow v2 — Dynamic Pricing Rules Manager & Live Interactive Simulator.
 * Allows multiplex managers to view/toggle pricing rules and simulate price outcomes in real time.
 */
public class PricingManagementView {

    private final PricingRuleDAO ruleDAO = new PricingRuleDAO();
    private final PricingEngine engine = new PricingEngine();

    private TableView<PricingRule> rulesTable;
    private ObservableList<PricingRule> rulesData;

    // Simulator controls
    private Slider occupancySlider;
    private Label occupancyValLbl;
    private Slider hoursSlider;
    private Label hoursValLbl;
    private ComboBox<DayOfWeek> dayCombo;
    private Slider hourOfDaySlider;
    private Label hourOfDayValLbl;

    // Simulation outputs
    private Label simBasePriceLbl;
    private Label simFinalPriceLbl;
    private Label simNetAdjustmentLbl;
    private VBox simBreakdownList;

    public Parent getView() {
        VBox root = new VBox(0);
        root.getStyleClass().add("app-container");

        HBox split = new HBox(20);
        split.setPadding(new Insets(20, 24, 24, 24));
        VBox.setVgrow(split, Priority.ALWAYS);

        // Left Panel: Rules Table (60% width)
        VBox leftPanel = createRulesPanel();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);

        // Right Panel: Live Pricing Simulator (40% width)
        VBox rightPanel = createSimulatorPanel();
        rightPanel.setPrefWidth(420);
        rightPanel.setMinWidth(380);

        split.getChildren().addAll(leftPanel, rightPanel);
        root.getChildren().add(split);

        refreshRules();
        recalculateSimulation();

        return root;
    }

    private VBox createRulesPanel() {
        VBox panel = new VBox(12);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Active Pricing Rules");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button toggleBtn = new Button("⚡ Toggle Active");
        toggleBtn.getStyleClass().add("button-primary");
        toggleBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 14px;");
        toggleBtn.setOnAction(e -> handleToggleRule());

        Button refreshBtn = new Button("↻ Refresh");
        refreshBtn.getStyleClass().add("button-secondary");
        refreshBtn.setStyle("-fx-font-size: 12px; -fx-padding: 6px 12px;");
        refreshBtn.setOnAction(e -> refreshRules());

        header.getChildren().addAll(title, sp, toggleBtn, refreshBtn);

        // Table
        rulesTable = new TableView<>();
        rulesData = FXCollections.observableArrayList();
        rulesTable.setItems(rulesData);
        rulesTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(rulesTable, Priority.ALWAYS);

        TableColumn<PricingRule, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(45);

        TableColumn<PricingRule, String> nameCol = new TableColumn<>("Rule Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(160);

        TableColumn<PricingRule, String> typeCol = new TableColumn<>("Factor Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRuleType().name()));
        typeCol.setPrefWidth(110);

        TableColumn<PricingRule, String> adjCol = new TableColumn<>("Adjustment");
        adjCol.setCellValueFactory(c -> {
            BigDecimal adj = c.getValue().getAdjustmentPct();
            String prefix = adj.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            return new SimpleStringProperty(prefix + adj.toPlainString() + "%");
        });
        adjCol.setPrefWidth(90);
        adjCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.setStyle(item.startsWith("+") ?
                        "-fx-text-fill: #F59E0B; -fx-font-weight: 800;" :
                        "-fx-text-fill: #22C55E; -fx-font-weight: 800;");
                setGraphic(badge);
                setText(null);
            }
        });

        TableColumn<PricingRule, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "ACTIVE" : "DISABLED"));
        statusCol.setPrefWidth(85);
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add("ACTIVE".equals(item) ? "badge-success" : "badge-danger");
                badge.setStyle("-fx-font-size: 10px;");
                setGraphic(badge);
                setText(null);
            }
        });

        TableColumn<PricingRule, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(170);

        rulesTable.getColumns().addAll(idCol, nameCol, typeCol, adjCol, statusCol, descCol);

        panel.getChildren().addAll(header, rulesTable);
        return panel;
    }

    private VBox createSimulatorPanel() {
        VBox card = new VBox(14);
        card.setPadding(new Insets(20));
        card.setStyle(
            "-fx-background-color: #12101F; " +
            "-fx-border-color: rgba(139, 92, 246, 0.35); " +
            "-fx-border-width: 1.5px; -fx-border-radius: 14px; -fx-background-radius: 14px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 20, 0, 0, 6);"
        );

        Label title = new Label("⚡ Dynamic Pricing Simulator");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");

        Label desc = new Label("Drag conditions in real-time to observe additive percentage factor stacking, bounding floor/cap limits, and nearest ₹5 rounding.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #A1A1B5;");

        // 1. Occupancy Slider
        HBox occHeader = new HBox(8);
        occHeader.setAlignment(Pos.CENTER_LEFT);
        Label occLbl = new Label("Screen Occupancy:");
        occLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        Region occSp = new Region();
        HBox.setHgrow(occSp, Priority.ALWAYS);
        occupancyValLbl = new Label("85%");
        occupancyValLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;");
        occHeader.getChildren().addAll(occLbl, occSp, occupancyValLbl);

        occupancySlider = new Slider(0, 100, 85);
        occupancySlider.valueProperty().addListener((obs, o, n) -> {
            occupancyValLbl.setText(String.format("%.0f%%", n.doubleValue()));
            recalculateSimulation();
        });

        // 2. Time to Show Slider
        HBox hoursHeader = new HBox(8);
        hoursHeader.setAlignment(Pos.CENTER_LEFT);
        Label hoursLbl = new Label("Time Until Showtime:");
        hoursLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        Region hrSp = new Region();
        HBox.setHgrow(hrSp, Priority.ALWAYS);
        hoursValLbl = new Label("2.5 hrs");
        hoursValLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;");
        hoursHeader.getChildren().addAll(hoursLbl, hrSp, hoursValLbl);

        hoursSlider = new Slider(0, 48, 2.5);
        hoursSlider.valueProperty().addListener((obs, o, n) -> {
            hoursValLbl.setText(String.format("%.1f hrs", n.doubleValue()));
            recalculateSimulation();
        });

        // 3. Day of Week
        VBox dayBox = new VBox(4);
        Label dayLbl = new Label("Day Type:");
        dayLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        dayCombo = new ComboBox<>(FXCollections.observableArrayList(DayOfWeek.values()));
        dayCombo.setValue(DayOfWeek.SATURDAY);
        dayCombo.setMaxWidth(Double.MAX_VALUE);
        dayCombo.valueProperty().addListener((obs, o, n) -> recalculateSimulation());
        dayBox.getChildren().addAll(dayLbl, dayCombo);

        // 4. Hour of Day Slider (Screening Time)
        HBox timeHeader = new HBox(8);
        timeHeader.setAlignment(Pos.CENTER_LEFT);
        Label timeLbl = new Label("Screening Hour (24h):");
        timeLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #E2E8F0;");
        Region tSp = new Region();
        HBox.setHgrow(tSp, Priority.ALWAYS);
        hourOfDayValLbl = new Label("19:00 (Prime Evening)");
        hourOfDayValLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;");
        timeHeader.getChildren().addAll(timeLbl, tSp, hourOfDayValLbl);

        hourOfDaySlider = new Slider(8, 23, 19);
        hourOfDaySlider.valueProperty().addListener((obs, o, n) -> {
            int h = n.intValue();
            String tag = h >= 18 && h <= 21 ? " (Prime Evening)" : h < 12 ? " (Morning Saver)" : " (Matinee)";
            hourOfDayValLbl.setText(String.format("%02d:00%s", h, tag));
            recalculateSimulation();
        });

        // Result Meter Box
        VBox meterBox = new VBox(10);
        meterBox.setPadding(new Insets(14));
        meterBox.setStyle(
            "-fx-background-color: rgba(139, 92, 246, 0.08); " +
            "-fx-border-color: rgba(139, 92, 246, 0.3); " +
            "-fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px;"
        );

        HBox priceRow = new HBox(12);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        VBox baseBox = new VBox(2);
        Label bTitle = new Label("Base Regular");
        bTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #A1A1B5;");
        simBasePriceLbl = new Label("₹ 250.00");
        simBasePriceLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");
        baseBox.getChildren().addAll(bTitle, simBasePriceLbl);

        Label arrow = new Label("→");
        arrow.setStyle("-fx-font-size: 18px; -fx-text-fill: #8B5CF6;");

        VBox finalBox = new VBox(2);
        Label fTitle = new Label("Final Dynamic Price");
        fTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #A1A1B5;");
        simFinalPriceLbl = new Label("₹ 320.00");
        simFinalPriceLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900; -fx-text-fill: #22C55E;");
        finalBox.getChildren().addAll(fTitle, simFinalPriceLbl);

        Region pSp = new Region();
        HBox.setHgrow(pSp, Priority.ALWAYS);

        simNetAdjustmentLbl = new Label("+28%");
        simNetAdjustmentLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #F59E0B;");

        priceRow.getChildren().addAll(baseBox, arrow, finalBox, pSp, simNetAdjustmentLbl);

        // Breakdown List
        simBreakdownList = new VBox(4);
        simBreakdownList.setStyle("-fx-padding: 4px 0 0 0;");

        meterBox.getChildren().addAll(priceRow, simBreakdownList);

        card.getChildren().addAll(
                title, desc,
                occHeader, occupancySlider,
                hoursHeader, hoursSlider,
                dayBox,
                timeHeader, hourOfDaySlider,
                meterBox
        );

        return card;
    }

    private void recalculateSimulation() {
        if (rulesData == null || simFinalPriceLbl == null) return;

        BigDecimal basePrice = new BigDecimal("250.00");
        double occupancy = occupancySlider.getValue() / 100.0;
        double hoursLeft = hoursSlider.getValue();
        DayOfWeek dow = dayCombo.getValue();
        int hour = (int) hourOfDaySlider.getValue();

        LocalDateTime mockNow = LocalDateTime.now();
        LocalDateTime mockShowtime = mockNow.plusHours((long) Math.max(1, hoursLeft))
                .withHour(hour).withMinute(0);

        // Calculate quote using pure PricingEngine
        PriceQuote quote = PricingEngine.quote(
                basePrice,
                SeatType.REGULAR,
                mockShowtime,
                mockNow,
                100,
                (int) (occupancy * 100),
                new java.util.ArrayList<>(rulesData),
                new BigDecimal("-0.20"),
                new BigDecimal("0.50")
        );

        simFinalPriceLbl.setText("₹ " + quote.getFinalPrice().toPlainString());

        BigDecimal diff = quote.getFinalPrice().subtract(basePrice);
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            simNetAdjustmentLbl.setText("▲ Surge");
            simNetAdjustmentLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #F59E0B;");
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            simNetAdjustmentLbl.setText("▼ Saver");
            simNetAdjustmentLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #22C55E;");
        } else {
            simNetAdjustmentLbl.setText("Standard");
            simNetAdjustmentLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #CBD5E1;");
        }

        simBreakdownList.getChildren().clear();
        for (PriceBreakdownLine line : quote.getBreakdownLines()) {
            Label lineLbl = new Label(" • " + line.toString());
            lineLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #A1A1B5;");
            simBreakdownList.getChildren().add(lineLbl);
        }
    }

    private void refreshRules() {
        AsyncUi.run(() -> {
            try {
                return ruleDAO.getAllRules();
            } catch (SQLException e) {
                return List.<PricingRule>of();
            }
        }, list -> {
            rulesData.setAll(list);
            recalculateSimulation();
        }, err -> {});
    }

    private void handleToggleRule() {
        PricingRule selected = rulesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ToastManager.show(ToastManager.ToastType.WARNING, "Select Rule", "Please select a pricing rule first.");
            return;
        }

        boolean newState = !selected.isActive();
        AsyncUi.run(() -> {
            try {
                return ruleDAO.toggleActive(selected.getId(), newState);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, ok -> {
            if (ok) {
                selected.setActive(newState);
                rulesTable.refresh();
                recalculateSimulation();
                ToastManager.show(
                        ToastManager.ToastType.SUCCESS,
                        "Rule Updated",
                        selected.getName() + " is now " + (newState ? "ACTIVE" : "DISABLED")
                );
            }
        }, err -> ToastManager.show(ToastManager.ToastType.ERROR, "Error", err.getMessage()));
    }
}
