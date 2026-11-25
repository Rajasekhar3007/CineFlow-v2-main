package com.cineflow.ui.customer;

import com.cineflow.model.Booking;
import com.cineflow.model.BookingConcession;
import com.cineflow.model.Concession;
import com.cineflow.model.ConcessionCategory;
import com.cineflow.service.ConcessionService;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.*;

/**
 * CineFlow v3 — Customer F&B Pre-Ordering Modal Dialog.
 * Beautiful snack, beverage, combo picker with instant quantity controls and order confirmation.
 */
public class ConcessionSelectionDialog {

    private final Booking booking;
    private final ConcessionService concessionService;
    private final Runnable onOrderPlaced;

    private Stage dialogStage;
    private final Map<Integer, Integer> cartQuantities = new HashMap<>();
    private final Map<Integer, Concession> concessionMap = new HashMap<>();
    private Label totalAmountLbl;
    private Label totalItemsLbl;
    private Button confirmOrderBtn;

    public ConcessionSelectionDialog(Booking booking, Runnable onOrderPlaced) {
        this.booking = booking;
        this.concessionService = new ConcessionService();
        this.onOrderPlaced = onOrderPlaced;
    }

    public void show() {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("CineFlow  •  Pre-Order Snacks & F&B");
        dialogStage.setResizable(false);
        dialogStage.setMinWidth(640);
        dialogStage.setMinHeight(560);

        VBox content = new VBox(0);
        content.setStyle("-fx-background-color: #0D0B18;");

        // Top Accent Bar
        Region accentBar = new Region();
        accentBar.setPrefHeight(4);
        accentBar.setStyle("-fx-background-color: linear-gradient(to right, #8B5CF6, #F59E0B);");
        content.getChildren().add(accentBar);

        VBox mainBox = new VBox(16);
        mainBox.setPadding(new Insets(20, 24, 20, 24));
        mainBox.setStyle("-fx-background-color: #0D0B18; -fx-border-color: rgba(139,92,246,0.3); -fx-border-width: 0 1px 1px 1px;");

        // Header
        mainBox.getChildren().add(createHeader());

        // Category Tab Pane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("card");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Fetch active items asynchronously
        AsyncUi.run(
                () -> concessionService.getActiveConcessions(),
                items -> {
                    for (Concession c : items) {
                        concessionMap.put(c.getId(), c);
                    }
                    populateCategoryTabs(tabPane, items);
                },
                err -> AlertHelper.showError("Error", "Could not load concessions", err.getMessage())
        );

        mainBox.getChildren().add(tabPane);

        // Bottom summary & order action
        mainBox.getChildren().add(createBottomBar());

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

    private HBox createHeader() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);

        VBox text = new VBox(3);
        Label title = new Label("🍿  Add Movie Snacks & Refreshments");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label sub = new Label("Booking #" + booking.getId() + "  •  " + booking.getMovieTitle() + " (" + booking.getScreenName() + ")");
        sub.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
        text.getChildren().addAll(title, sub);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("COLLECT AT COUNTER");
        badge.setStyle(
                "-fx-background-color: rgba(245,158,11,0.12); -fx-text-fill: #F59E0B; " +
                "-fx-border-color: rgba(245,158,11,0.35); -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                "-fx-padding: 3px 9px; -fx-font-size: 10px; -fx-font-weight: 700;"
        );

        box.getChildren().addAll(text, spacer, badge);
        return box;
    }

    private void populateCategoryTabs(TabPane tabPane, List<Concession> items) {
        for (ConcessionCategory cat : ConcessionCategory.values()) {
            VBox catBox = new VBox(10);
            catBox.setPadding(new Insets(12));

            ScrollPane sp = new ScrollPane(catBox);
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

            List<Concession> catItems = items.stream().filter(i -> i.getCategory() == cat).toList();
            if (catItems.isEmpty()) {
                Label empty = new Label("No items currently available in this category.");
                empty.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 13px; -fx-padding: 20px;");
                catBox.getChildren().add(empty);
            } else {
                for (Concession item : catItems) {
                    catBox.getChildren().add(createConcessionItemRow(item));
                }
            }

            Tab tab = new Tab(cat.getEmoji() + " " + cat.getDisplayName(), sp);
            tabPane.getTabs().add(tab);
        }
    }

    private HBox createConcessionItemRow(Concession item) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle(
                "-fx-background-color: rgba(255,255,255,0.03); " +
                "-fx-border-color: rgba(139,92,246,0.15); -fx-border-width: 1px; " +
                "-fx-background-radius: 10px; -fx-border-radius: 10px;"
        );

        VBox meta = new VBox(2);
        HBox.setHgrow(meta, Priority.ALWAYS);

        Label name = new Label(item.getName());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #F1F5F9;");

        Label desc = new Label(item.getDescription() != null ? item.getDescription() : "");
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        Label price = new Label("₹ " + item.getPrice().toPlainString());
        price.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #10B981;");

        meta.getChildren().addAll(name, desc, price);

        // Qty controls (- / count / +)
        HBox qtyBox = new HBox(8);
        qtyBox.setAlignment(Pos.CENTER);

        Label countLbl = new Label("0");
        countLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #F8FAFC; -fx-min-width: 24px; -fx-alignment: center;");

        Button minusBtn = new Button("-");
        minusBtn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: white; -fx-font-weight: 800; " +
                "-fx-background-radius: 6px; -fx-min-width: 28px; -fx-cursor: hand;"
        );

        Button plusBtn = new Button("+");
        plusBtn.setStyle(
                "-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-weight: 800; " +
                "-fx-background-radius: 6px; -fx-min-width: 28px; -fx-cursor: hand;"
        );

        minusBtn.setOnAction(e -> {
            int current = cartQuantities.getOrDefault(item.getId(), 0);
            if (current > 0) {
                current--;
                if (current == 0) {
                    cartQuantities.remove(item.getId());
                } else {
                    cartQuantities.put(item.getId(), current);
                }
                countLbl.setText(String.valueOf(current));
                recalculateCart();
            }
        });

        plusBtn.setOnAction(e -> {
            int current = cartQuantities.getOrDefault(item.getId(), 0);
            current++;
            cartQuantities.put(item.getId(), current);
            countLbl.setText(String.valueOf(current));
            recalculateCart();
        });

        qtyBox.getChildren().addAll(minusBtn, countLbl, plusBtn);
        row.getChildren().addAll(meta, qtyBox);
        return row;
    }

    private HBox createBottomBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 0, 0, 0));

        VBox summary = new VBox(2);
        totalItemsLbl = new Label("0 items selected");
        totalItemsLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        totalAmountLbl = new Label("Total: ₹ 0.00");
        totalAmountLbl.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #22C55E;");

        summary.getChildren().addAll(totalItemsLbl, totalAmountLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button cancelBtn = new Button("Close");
        cancelBtn.getStyleClass().add("button-secondary");
        cancelBtn.setOnAction(e -> dialogStage.close());

        confirmOrderBtn = new Button("Confirm Snack Order");
        confirmOrderBtn.getStyleClass().add("button-primary");
        confirmOrderBtn.setDisable(true);
        confirmOrderBtn.setOnAction(e -> submitOrder());

        bar.getChildren().addAll(summary, spacer, cancelBtn, confirmOrderBtn);
        return bar;
    }

    private void recalculateCart() {
        int totalItems = 0;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map.Entry<Integer, Integer> entry : cartQuantities.entrySet()) {
            int cid = entry.getKey();
            int qty = entry.getValue();
            Concession c = concessionMap.get(cid);
            if (c != null && qty > 0) {
                totalItems += qty;
                grandTotal = grandTotal.add(c.getPrice().multiply(new BigDecimal(qty)));
            }
        }

        totalItemsLbl.setText(totalItems + " items selected");
        totalAmountLbl.setText("Total: ₹ " + grandTotal.toPlainString());
        confirmOrderBtn.setDisable(totalItems == 0);
        confirmOrderBtn.setText(totalItems > 0 ? "Confirm Snack Order (₹ " + grandTotal.toPlainString() + ")" : "Confirm Snack Order");
    }

    private void submitOrder() {
        if (cartQuantities.isEmpty()) return;

        List<BookingConcession> orderItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : cartQuantities.entrySet()) {
            int cid = entry.getKey();
            int qty = entry.getValue();
            Concession c = concessionMap.get(cid);
            if (c != null && qty > 0) {
                BookingConcession bc = new BookingConcession();
                bc.setBookingId(booking.getId());
                bc.setConcessionId(cid);
                bc.setQuantity(qty);
                bc.setUnitPrice(c.getPrice());
                bc.setTotalPrice(c.getPrice().multiply(new BigDecimal(qty)));
                orderItems.add(bc);
            }
        }

        confirmOrderBtn.setDisable(true);
        confirmOrderBtn.setText("Placing Order...");

        AsyncUi.run(
                () -> {
                    concessionService.placeConcessionOrder(booking.getId(), orderItems);
                    return true;
                },
                success -> {
                    ToastManager.showSuccess("Order Confirmed", "Snacks ordered successfully! Collect at the counter.");
                    dialogStage.close();
                    if (onOrderPlaced != null) onOrderPlaced.run();
                },
                err -> {
                    confirmOrderBtn.setDisable(false);
                    confirmOrderBtn.setText("Confirm Snack Order");
                    AlertHelper.showError("Order Failed", "Could not submit concession order", err.getMessage());
                }
        );
    }
}
