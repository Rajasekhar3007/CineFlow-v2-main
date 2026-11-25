package com.cineflow.ui.admin;

import com.cineflow.model.EntryScan;
import com.cineflow.model.ScanResult;
import com.cineflow.model.Ticket;
import com.cineflow.service.AuthService;
import com.cineflow.service.TicketService;
import com.cineflow.ui.common.AnimationUtil;
import com.cineflow.ui.common.AsyncUi;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.QrCodeGenerator;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CineFlow v2 Admin Entry Gate & Admission Scanner.
 * Rapid hardware barcode scanner listener, image upload decoder, and atomic entry validator.
 */
public class EntryGateView {

    private final TicketService ticketService;
    private TextField scanField;
    private VBox resultOverlayBox;
    private VBox recentScansBox;

    private int totalScannedCount = 0;
    private int admittedCount = 0;
    private int rejectedCount = 0;

    private Label totalScannedLbl;
    private Label admittedLbl;
    private Label rejectedLbl;

    public EntryGateView() {
        this.ticketService = new TicketService();
    }

    public Parent getView() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: transparent;");

        // Top Header + KPI Stat Chips
        VBox topHeader = createHeader();
        root.setTop(topHeader);

        // Center split: Left Scanner Console + Right Audit Feed
        HBox centerSplit = new HBox(28);
        centerSplit.setAlignment(Pos.TOP_LEFT);
        centerSplit.setPadding(new Insets(20, 0, 0, 0));

        VBox scannerConsole = createScannerConsole();
        scannerConsole.setPrefWidth(520);
        HBox.setHgrow(scannerConsole, Priority.ALWAYS);

        VBox auditFeed = createAuditFeed();
        auditFeed.setPrefWidth(420);

        centerSplit.getChildren().addAll(scannerConsole, auditFeed);

        ScrollPane scroll = new ScrollPane(centerSplit);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        root.setCenter(scroll);

        loadRecentScans();
        return root;
    }

    private VBox createHeader() {
        VBox box = new VBox(14);

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🎟  Cinema Entry Gate");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        // KPI chips
        HBox kpiRow = new HBox(12);
        kpiRow.setAlignment(Pos.CENTER_RIGHT);

        totalScannedLbl = makeKpiChip("Total Scans", "0", "#8B5CF6");
        admittedLbl = makeKpiChip("Admitted", "0", "#22C55E");
        rejectedLbl = makeKpiChip("Rejected", "0", "#EF4444");

        kpiRow.getChildren().addAll(totalScannedLbl.getParent(), admittedLbl.getParent(), rejectedLbl.getParent());
        titleRow.getChildren().addAll(title, sp, kpiRow);

        box.getChildren().add(titleRow);
        return box;
    }

    private Label makeKpiChip(String label, String initialVal, String colorHex) {
        HBox chip = new HBox(8);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle(
            "-fx-background-color: rgba(21,19,35,0.85);" +
            "-fx-border-color: " + colorHex + ";" +
            "-fx-border-width: 1px; -fx-border-radius: 10px; -fx-background-radius: 10px;" +
            "-fx-padding: 6px 14px;"
        );

        Label textLbl = new Label(label + ":");
        textLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-font-weight: 700;");

        Label valLbl = new Label(initialVal);
        valLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 900; -fx-text-fill: " + colorHex + ";");

        chip.getChildren().addAll(textLbl, valLbl);
        return valLbl;
    }

    private VBox createScannerConsole() {
        VBox console = new VBox(16);
        console.getStyleClass().add("card");
        console.setPadding(new Insets(22));

        Label sectionLbl = new Label("Scanner Console");
        sectionLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        Label instruction = new Label("Supports USB barcode/QR handheld scanners, keyboard input, or image file decoding.");
        instruction.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");

        // Input Field
        scanField = new TextField();
        scanField.setPromptText("Scan QR or paste token (e.g. CF1.142.nonce.sig) and press Enter...");
        scanField.setStyle("-fx-font-size: 14px; -fx-padding: 12px 16px; -fx-background-radius: 10px;");
        scanField.setOnAction(e -> handleScanSubmit());

        // Actions row
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button submitBtn = new Button("⚡ Validate & Admit");
        submitBtn.getStyleClass().add("button-primary");
        submitBtn.setOnAction(e -> handleScanSubmit());

        Button imageBtn = new Button("📷 Scan QR from Image...");
        imageBtn.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #00C2FF; -fx-border-color: rgba(0,194,255,0.4); -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px 14px;");
        imageBtn.setOnAction(e -> handleImageUpload());

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8;");
        clearBtn.setOnAction(e -> {
            scanField.clear();
            hideOverlay();
        });

        actions.getChildren().addAll(submitBtn, imageBtn, clearBtn);

        // Result visual overlay placeholder
        resultOverlayBox = new VBox(10);
        resultOverlayBox.setAlignment(Pos.CENTER);
        resultOverlayBox.setPadding(new Insets(18));
        resultOverlayBox.setVisible(false);
        resultOverlayBox.setManaged(false);

        console.getChildren().addAll(sectionLbl, instruction, scanField, actions, resultOverlayBox);
        return console;
    }

    private void handleScanSubmit() {
        String token = scanField.getText() != null ? scanField.getText().trim() : "";
        if (token.isEmpty()) return;

        int adminId = AuthService.getCurrentUser() != null ? AuthService.getCurrentUser().getId() : 1;

        AsyncUi.run(() -> ticketService.validateAndAdmit(token, adminId),
                result -> {
                    totalScannedCount++;
                    totalScannedLbl.setText(String.valueOf(totalScannedCount));

                    if (result.result() == ScanResult.ADMITTED) {
                        admittedCount++;
                        admittedLbl.setText(String.valueOf(admittedCount));
                    } else {
                        rejectedCount++;
                        rejectedLbl.setText(String.valueOf(rejectedCount));
                    }

                    showResultOverlay(result);
                    scanField.clear();
                    loadRecentScans();
                },
                err -> ToastManager.showError("Scan Error", err.getMessage())
        );
    }

    private void handleImageUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select QR Code Ticket Image");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(scanField.getScene().getWindow());
        if (file != null) {
            try {
                String decodedToken = QrCodeGenerator.decodeQrFromFile(file);
                scanField.setText(decodedToken);
                handleScanSubmit();
            } catch (Exception e) {
                ToastManager.showError("QR Decode Failed", "Could not locate or decode QR code in image: " + e.getMessage());
            }
        }
    }

    private void showResultOverlay(TicketService.EntryValidationResult res) {
        resultOverlayBox.getChildren().clear();
        resultOverlayBox.setVisible(true);
        resultOverlayBox.setManaged(true);

        ScanResult sr = res.result();
        Ticket t = res.ticket();

        String bgWash;
        String borderColor;
        String iconText;
        String titleColor;

        if (sr == ScanResult.ADMITTED) {
            bgWash = "rgba(34, 197, 94, 0.16)";
            borderColor = "#22C55E";
            iconText = "✓";
            titleColor = "#22C55E";
        } else if (sr == ScanResult.ALREADY_USED) {
            bgWash = "rgba(245, 158, 11, 0.16)";
            borderColor = "#F59E0B";
            iconText = "⚠";
            titleColor = "#F59E0B";
        } else {
            bgWash = "rgba(239, 68, 68, 0.16)";
            borderColor = "#EF4444";
            iconText = "✕";
            titleColor = "#EF4444";
        }

        resultOverlayBox.setStyle(
            "-fx-background-color: " + bgWash + ";" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 1.5px; -fx-border-radius: 12px; -fx-background-radius: 12px;"
        );

        Label icon = new Label(iconText);
        icon.setStyle("-fx-font-size: 32px; -fx-font-weight: 900; -fx-text-fill: " + titleColor + ";");

        Label title = new Label(sr.getTitle().toUpperCase());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: " + titleColor + ";");

        Label desc = new Label(res.message());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #F8FAFC; -fx-alignment: center;");

        resultOverlayBox.getChildren().addAll(icon, title, desc);

        if (t != null) {
            HBox meta = new HBox(14);
            meta.setAlignment(Pos.CENTER);
            Label seatLbl = new Label("Seat: " + (t.getSeatCode() != null ? t.getSeatCode() : "—"));
            seatLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;");
            Label movieLbl = new Label("Movie: " + (t.getMovieTitle() != null ? t.getMovieTitle() : "—"));
            movieLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #CBD5E1;");
            meta.getChildren().addAll(seatLbl, movieLbl);
            resultOverlayBox.getChildren().add(meta);
        }

        if (sr != ScanResult.ADMITTED) {
            AnimationUtil.shake(resultOverlayBox);
        } else {
            ScaleTransition st = new ScaleTransition(Duration.millis(250), resultOverlayBox);
            st.setFromX(0.95);
            st.setToX(1.0);
            st.setFromY(0.95);
            st.setToY(1.0);
            st.play();
        }

        // Auto dismiss after 4 seconds
        PauseTransition pt = new PauseTransition(Duration.millis(4000));
        pt.setOnFinished(e -> hideOverlay());
        pt.play();
    }

    private void hideOverlay() {
        FadeTransition ft = new FadeTransition(Duration.millis(200), resultOverlayBox);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> {
            resultOverlayBox.setVisible(false);
            resultOverlayBox.setManaged(false);
            resultOverlayBox.setOpacity(1.0);
        });
        ft.play();
    }

    private VBox createAuditFeed() {
        VBox feed = new VBox(14);
        feed.getStyleClass().add("card");
        feed.setPadding(new Insets(20));

        Label feedTitle = new Label("Live Entry Audit Feed");
        feedTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;");

        recentScansBox = new VBox(8);
        feed.getChildren().addAll(feedTitle, recentScansBox);
        return feed;
    }

    public void loadRecentScans() {
        AsyncUi.run(() -> {
            try {
                return ticketService.getRecentScans(10);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, scans -> {
            recentScansBox.getChildren().clear();
            if (scans.isEmpty()) {
                Label noScans = new Label("No recent scan activity recorded.");
                noScans.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
                recentScansBox.getChildren().add(noScans);
                return;
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
            for (EntryScan s : scans) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 10, 6, 10));
                row.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 8px;");

                String color = s.getResult() == ScanResult.ADMITTED ? "#22C55E" : (s.getResult() == ScanResult.ALREADY_USED ? "#F59E0B" : "#EF4444");

                Label badge = new Label(s.getResult().name());
                badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: " + color + "; -fx-min-width: 90px;");

                Label info = new Label((s.getSeatCode() != null ? s.getSeatCode() : "—") + " • " + (s.getMovieTitle() != null ? s.getMovieTitle() : "—"));
                info.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
                HBox.setHgrow(info, Priority.ALWAYS);

                Label time = new Label(s.getScannedAt() != null ? s.getScannedAt().format(dtf) : "");
                time.setStyle("-fx-font-size: 10px; -fx-text-fill: #94A3B8;");

                row.getChildren().addAll(badge, info, time);
                recentScansBox.getChildren().add(row);
            }
        }, null);
    }
}
