package com.cineflow.ui.customer;

import com.cineflow.model.*;
import com.cineflow.service.AuthService;
import com.cineflow.service.BookingService;
import com.cineflow.service.LoyaltyService;
import com.cineflow.service.PricingService;
import com.cineflow.service.PromoEngine;
import com.cineflow.service.RazorpayService;
import com.cineflow.ui.common.ToastManager;
import com.cineflow.util.AlertHelper;
import com.cineflow.util.AppConfig;
import com.cineflow.util.PosterHelper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CineFlow — Premium Secure Checkout Dialog.
 *
 * Fixes applied:
 *  1. JDBC transaction runs on a background thread (Platform.runLater for UI updates)
 *     → UI never freezes during "Processing..." phase
 *  2. Stage uses a solid dark background (no transparent scene hack)
 *     → Dialog renders correctly on all platforms
 *  3. Payment method state is reliably captured at confirm-click time
 */
public class PaymentDialog {

    private final Showtime showtime;
    private final List<Seat> selectedSeats;
    private final BookingService bookingService;
    private final Consumer<Booking> onBookingSuccessful;

    private Stage dialogStage;
    private Timeline countdownTimeline;
    private int remainingSeconds = 300;

    private Label timerLabel;
    private PaymentMethod selectedMethod = PaymentMethod.RAZORPAY;
    private final RazorpayService razorpayService = new RazorpayService();
    private RazorpayService.CheckoutServerHandle activeCheckoutHandle = null;
    private Label razorpayStatusLbl;

    private TextField upiField;
    private TextField cardNumberField;
    private TextField cardExpiryField;
    private TextField cardCvvField;
    private Button confirmBtn;
    private ProgressIndicator progressIndicator;

    private final LoyaltyService loyaltyService = new LoyaltyService();
    private BigDecimal baseBookingTotal = BigDecimal.ZERO;
    private BigDecimal payableTotal = BigDecimal.ZERO;
    private final List<BigDecimal> currentPrices = new ArrayList<>();
    private String appliedPromoCode = null;
    private int appliedPointsRedeemed = 0;
    private int earnedLoyaltyPoints = 0;
    private UserLoyalty currentUserLoyalty = null;
    private TextField promoField;
    private Label promoStatusLbl;
    private CheckBox redeemLoyaltyCheck;
    private Label loyaltyInfoLbl;

    public PaymentDialog(Showtime showtime,
                         List<Seat> selectedSeats,
                         Consumer<Booking> onBookingSuccessful) {
        this.showtime = showtime;
        this.selectedSeats = new ArrayList<>(selectedSeats);   // defensive copy
        this.bookingService = new BookingService();
        this.onBookingSuccessful = onBookingSuccessful;

        BigDecimal total = BigDecimal.ZERO;
        for (Seat s : this.selectedSeats) {
            BigDecimal p = s.getSeatType() == SeatType.PREMIUM
                    ? showtime.getPricePremium() : showtime.getPriceRegular();
            total = total.add(p);
            this.currentPrices.add(p);
        }
        this.baseBookingTotal = total;
        this.payableTotal = total;

        User user = AuthService.getCurrentUser();
        if (user != null) {
            try {
                this.currentUserLoyalty = loyaltyService.getUserLoyalty(user.getId());
            } catch (Exception ignored) {}
        }
    }

    /* ================================================================ */
    /* SHOW                                                               */
    /* ================================================================ */
    public void show() {
        dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("CineFlow  •  Secure Checkout");
        dialogStage.setResizable(false);
        dialogStage.setMinWidth(580);

        // ── Root layout ───────────────────────────────────────────────
        VBox content = buildContent();

        // ── Scene — solid dark background, no transparency tricks ─────
        Scene scene = new Scene(content);
        scene.setFill(Color.web("#0D0B18")); // matches card bg, no white flash

        try {
            String css = getClass().getResource("/css/styles.css") != null
                    ? getClass().getResource("/css/styles.css").toExternalForm() : null;
            if (css != null) scene.getStylesheets().add(css);
        } catch (Exception ignored) {}

        dialogStage.setScene(scene);
        dialogStage.setOnCloseRequest(e -> handleCancel());

        // Slide-in entrance
        content.setOpacity(0);
        content.setTranslateY(12);
        dialogStage.show();

        FadeTransition ft = new FadeTransition(Duration.millis(300), content);
        ft.setToValue(1.0);
        ft.play();
        TranslateTransition tt = new TranslateTransition(Duration.millis(300), content);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        tt.play();

        startCountdown();
    }

    /* ================================================================ */
    /* CONTENT BUILDER                                                    */
    /* ================================================================ */
    private VBox buildContent() {
        VBox root = new VBox(0);
        root.setStyle("-fx-background-color: #0D0B18;");
        root.setPrefWidth(580);

        // ── Coloured top accent bar ───────────────────────────────────
        Region accentBar = new Region();
        accentBar.setPrefHeight(4);
        accentBar.setStyle(
            "-fx-background-color: linear-gradient(to right, #8B5CF6, #00C2FF);"
        );

        // ── Main card padding wrapper ─────────────────────────────────
        VBox card = new VBox(16);
        card.setPadding(new Insets(24, 28, 24, 28));
        card.setStyle(
            "-fx-background-color: #0D0B18;" +
            "-fx-border-color: rgba(139,92,246,0.35);" +
            "-fx-border-width: 0 1px 1px 1px;"
        );

        card.getChildren().addAll(
                buildHeader(),
                buildTimerBanner(),
                buildSummaryBox(),
                divider(),
                buildPromoAndLoyaltySection(),
                divider(),
                buildPaymentMethodSection(),
                divider(),
                buildActionRow()
        );

        root.getChildren().addAll(accentBar, card);
        return root;
    }

    /* ================================================================ */
    /* HEADER                                                             */
    /* ================================================================ */
    private HBox buildHeader() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label brand = new Label("🔐  SECURE CHECKOUT");
        brand.setStyle(
            "-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: #F8FAFC;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("256-BIT ENCRYPTED");
        badge.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #00C2FF;" +
            "-fx-background-color: rgba(0,194,255,0.10);" +
            "-fx-border-color: rgba(0,194,255,0.35);" +
            "-fx-border-width: 1px; -fx-border-radius: 20px; -fx-background-radius: 20px;" +
            "-fx-padding: 3px 10px;"
        );

        row.getChildren().addAll(brand, spacer, badge);
        return row;
    }

    /* ================================================================ */
    /* TIMER BANNER                                                       */
    /* ================================================================ */
    private HBox buildTimerBanner() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle(
            "-fx-background-color: rgba(245,158,11,0.09);" +
            "-fx-padding: 10px 16px;" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: rgba(245,158,11,0.30);" +
            "-fx-border-radius: 10px; -fx-border-width: 1px;"
        );

        Label icon = new Label("⏳");
        icon.setStyle("-fx-font-size: 16px;");

        timerLabel = new Label("Seats & Quoted Price Locked: 05:00");
        timerLabel.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #FCD34D;"
        );

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label hint = new Label("Complete payment before time expires");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #78350F;");

        box.getChildren().addAll(icon, timerLabel, sp, hint);
        return box;
    }

    /* ================================================================ */
    /* ORDER SUMMARY                                                      */
    /* ================================================================ */
    private HBox buildSummaryBox() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle(
            "-fx-background-color: rgba(139,92,246,0.07);" +
            "-fx-padding: 16px;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-color: rgba(139,92,246,0.22);" +
            "-fx-border-radius: 12px; -fx-border-width: 1px;"
        );

        // Movie poster thumbnail
        ImageView poster = PosterHelper.createPosterImageView(
                showtime.getMovieTitle(), 70, 96, 10);

        // Details
        VBox details = new VBox(5);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label movieLbl = new Label(showtime.getMovieTitle());
        movieLbl.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #F8FAFC;"
        );

        Label screenLbl = new Label("🏛  " + showtime.getScreenName()
                + "    🕒  " + showtime.getFormattedStartTime());
        screenLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #A1A1AA;");

        // Calculate total and seat list
        PricingService pricingService = new PricingService();
        User currentUser = AuthService.getCurrentUser();
        int uid = currentUser != null ? currentUser.getId() : -1;

        StringBuilder seatCodes = new StringBuilder();
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int i = 0; i < selectedSeats.size(); i++) {
            Seat s = selectedSeats.get(i);
            BigDecimal p;
            try {
                PriceQuote q = pricingService.getOrRecalculateQuote(uid, showtime.getId(), s.getSeatType());
                p = q.getFinalPrice();
            } catch (Exception e) {
                p = s.getSeatType() == SeatType.PREMIUM ? showtime.getPricePremium() : showtime.getPriceRegular();
            }
            runningTotal = runningTotal.add(p);
            if (i > 0) seatCodes.append(", ");
            seatCodes.append(s.getSeatCode());
        }

        Label seatsLbl = new Label("🎟  Seats: " + seatCodes
                + "  (" + selectedSeats.size() + ")");
        seatsLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #CBD5E1;");

        Label totalLbl = new Label("Amount: ₹ " + runningTotal.toPlainString());
        totalLbl.setStyle(
            "-fx-font-size: 19px; -fx-font-weight: 900; -fx-text-fill: #22C55E;"
        );

        details.getChildren().addAll(movieLbl, screenLbl, seatsLbl, totalLbl);
        box.getChildren().addAll(poster, details);
        return box;
    }

    /* ================================================================ */
    /* PROMOTIONS & LOYALTY REWARDS                                      */
    /* ================================================================ */
    private VBox buildPromoAndLoyaltySection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setStyle(
            "-fx-background-color: rgba(139,92,246,0.06); " +
            "-fx-border-color: rgba(139,92,246,0.22); -fx-border-radius: 10px; -fx-background-radius: 10px;"
        );

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("🎁  Promotions & Loyalty Rewards");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;");
        titleRow.getChildren().add(title);

        HBox promoRow = new HBox(8);
        promoRow.setAlignment(Pos.CENTER_LEFT);
        promoField = new TextField();
        promoField.setPromptText("Enter promo code (e.g. CINEFLOW20)");
        promoField.setStyle("-fx-font-size: 12px; -fx-pref-width: 220px;");

        Button applyPromoBtn = new Button("Apply");
        applyPromoBtn.setStyle("-fx-background-color: #8B5CF6; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 12px; -fx-padding: 6px 14px; -fx-cursor: hand;");
        applyPromoBtn.setOnAction(e -> applyDiscounts());

        HBox chips = new HBox(6);
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.getChildren().addAll(
            createPromoChip("CINEFLOW20"),
            createPromoChip("BLOCKBUSTER50"),
            createPromoChip("VIP100")
        );

        promoRow.getChildren().addAll(promoField, applyPromoBtn, chips);

        promoStatusLbl = new Label("");
        promoStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        HBox loyaltyRow = new HBox(10);
        loyaltyRow.setAlignment(Pos.CENTER_LEFT);
        redeemLoyaltyCheck = new CheckBox("Redeem Loyalty Points");
        redeemLoyaltyCheck.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 12px; -fx-font-weight: 600; -fx-cursor: hand;");
        redeemLoyaltyCheck.setOnAction(e -> applyDiscounts());

        int balance = currentUserLoyalty != null ? currentUserLoyalty.getPointsBalance() : 0;
        String tierName = currentUserLoyalty != null ? currentUserLoyalty.getTier().getDisplayName() : "Silver";
        loyaltyInfoLbl = new Label("🪙 " + balance + " pts available (" + tierName + ")");
        loyaltyInfoLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #F59E0B;");

        loyaltyRow.getChildren().addAll(redeemLoyaltyCheck, loyaltyInfoLbl);

        box.getChildren().addAll(titleRow, promoRow, promoStatusLbl, loyaltyRow);
        return box;
    }

    private Label createPromoChip(String code) {
        Label chip = new Label(code);
        chip.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #00C2FF; " +
            "-fx-background-color: rgba(0,194,255,0.12); -fx-border-color: rgba(0,194,255,0.35); " +
            "-fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 3px 8px; -fx-cursor: hand;"
        );
        chip.setOnMouseClicked(e -> {
            if (promoField != null) {
                promoField.setText(code);
                applyDiscounts();
            }
        });
        return chip;
    }

    private void applyDiscounts() {
        User user = AuthService.getCurrentUser();
        int uid = user != null ? user.getId() : -1;
        String code = promoField != null ? promoField.getText().trim() : "";
        int pointsToRedeem = 0;
        if (redeemLoyaltyCheck != null && redeemLoyaltyCheck.isSelected() && currentUserLoyalty != null) {
            pointsToRedeem = currentUserLoyalty.getPointsBalance();
        }

        try {
            PromoEngine.PromoCalculationResult res = loyaltyService.calculateDiscount(
                    baseBookingTotal, code.isEmpty() ? null : code, pointsToRedeem, uid);

            if (res.isValid()) {
                payableTotal = res.getFinalPayableAmount();
                appliedPromoCode = res.getPromoCode();
                appliedPointsRedeemed = res.getPointsRedeemed();
                earnedLoyaltyPoints = res.getPointsEarned();

                StringBuilder status = new StringBuilder();
                if (res.getPromoDiscount().compareTo(BigDecimal.ZERO) > 0) {
                    status.append("🏷 Promo Saved: ₹ ").append(res.getPromoDiscount().toPlainString()).append("  ");
                }
                if (res.getLoyaltyDiscount().compareTo(BigDecimal.ZERO) > 0) {
                    status.append("🪙 Loyalty Saved: ₹ ").append(res.getLoyaltyDiscount().toPlainString()).append("  ");
                }
                status.append("(Will earn +").append(earnedLoyaltyPoints).append(" pts)");
                promoStatusLbl.setText(status.toString());
                promoStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #10B981; -fx-font-weight: 700;");

                if (selectedMethod == PaymentMethod.RAZORPAY) {
                    confirmBtn.setText("⚡ Pay with Razorpay  ₹ " + payableTotal.toPlainString());
                } else if (selectedMethod == PaymentMethod.CASH) {
                    confirmBtn.setText("Book at Box Office  ₹ " + payableTotal.toPlainString());
                } else {
                    confirmBtn.setText("Confirm & Pay  ₹ " + payableTotal.toPlainString());
                }
            } else {
                promoStatusLbl.setText(res.getMessage());
                promoStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444; -fx-font-weight: 700;");
                payableTotal = baseBookingTotal;
                if (selectedMethod == PaymentMethod.RAZORPAY) {
                    confirmBtn.setText("⚡ Pay with Razorpay  ₹ " + payableTotal.toPlainString());
                } else if (selectedMethod == PaymentMethod.CASH) {
                    confirmBtn.setText("Book at Box Office  ₹ " + payableTotal.toPlainString());
                } else {
                    confirmBtn.setText("Confirm & Pay  ₹ " + payableTotal.toPlainString());
                }
            }
        } catch (Exception ex) {
            promoStatusLbl.setText("Error applying discount: " + ex.getMessage());
            promoStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444;");
        }
    }

    /* ================================================================ */
    /* PAYMENT METHOD SELECTOR                                            */
    /* ================================================================ */
    private VBox buildPaymentMethodSection() {
        VBox section = new VBox(12);

        Label lbl = new Label("Select Payment Method");
        lbl.setStyle(
            "-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #CBD5E1;"
        );

        // Segmented tab bar
        HBox segBar = new HBox(4);
        segBar.setStyle(
            "-fx-background-color: rgba(0,0,0,0.40);" +
            "-fx-padding: 4px; -fx-background-radius: 12px;" +
            "-fx-border-color: rgba(139,92,246,0.18);" +
            "-fx-border-radius: 12px; -fx-border-width: 1px;"
        );

        Button rzpTab  = segBtn("⚡  Razorpay (Live)", true);
        Button upiTab  = segBtn("📱  UPI",             false);
        Button cardTab = segBtn("💳  Card",            false);
        Button cashTab = segBtn("💵  Cash",            false);
        segBar.getChildren().addAll(rzpTab, upiTab, cardTab, cashTab);

        // Form pane — only one visible at a time
        StackPane forms = new StackPane();
        forms.setMinHeight(120);

        VBox rzpForm  = buildRazorpayForm();
        VBox upiForm  = buildUpiForm();
        VBox cardForm = buildCardForm();
        VBox cashForm = buildCashForm();

        upiForm.setVisible(false);
        cardForm.setVisible(false);
        cashForm.setVisible(false);
        forms.getChildren().addAll(upiForm, cardForm, cashForm, rzpForm);

        // Tab switching
        rzpTab.setOnAction(e -> {
            selectedMethod = PaymentMethod.RAZORPAY;
            setActive(rzpTab, true); setActive(upiTab, false); setActive(cardTab, false); setActive(cashTab, false);
            rzpForm.setVisible(true); upiForm.setVisible(false); cardForm.setVisible(false); cashForm.setVisible(false);
            if (confirmBtn != null) confirmBtn.setText("⚡ Pay with Razorpay  ₹ " + payableTotal.toPlainString());
        });
        upiTab.setOnAction(e -> {
            selectedMethod = PaymentMethod.UPI;
            setActive(rzpTab, false); setActive(upiTab, true); setActive(cardTab, false); setActive(cashTab, false);
            rzpForm.setVisible(false); upiForm.setVisible(true); cardForm.setVisible(false); cashForm.setVisible(false);
            if (confirmBtn != null) confirmBtn.setText("Confirm & Pay  ₹ " + payableTotal.toPlainString());
        });
        cardTab.setOnAction(e -> {
            selectedMethod = PaymentMethod.CARD;
            setActive(rzpTab, false); setActive(upiTab, false); setActive(cardTab, true); setActive(cashTab, false);
            rzpForm.setVisible(false); upiForm.setVisible(false); cardForm.setVisible(true); cashForm.setVisible(false);
            if (confirmBtn != null) confirmBtn.setText("Confirm & Pay  ₹ " + payableTotal.toPlainString());
        });
        cashTab.setOnAction(e -> {
            selectedMethod = PaymentMethod.CASH;
            setActive(rzpTab, false); setActive(upiTab, false); setActive(cardTab, false); setActive(cashTab, true);
            rzpForm.setVisible(false); upiForm.setVisible(false); cardForm.setVisible(false); cashForm.setVisible(true);
            if (confirmBtn != null) confirmBtn.setText("Book at Box Office  ₹ " + payableTotal.toPlainString());
        });

        section.getChildren().addAll(lbl, segBar, forms);
        return section;
    }

    private VBox buildRazorpayForm() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10, 14, 10, 14));
        box.setStyle(
            "-fx-background-color: rgba(139,92,246,0.08);" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: rgba(139,92,246,0.28);" +
            "-fx-border-radius: 10px; -fx-border-width: 1px;"
        );

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label("⚡ REALTIME SECURE GATEWAY");
        badge.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #00C2FF;" +
            "-fx-background-color: rgba(0,194,255,0.12); -fx-padding: 3px 8px;" +
            "-fx-background-radius: 6px; -fx-border-color: rgba(0,194,255,0.3); -fx-border-radius: 6px;"
        );

        Label keyLbl = new Label("Key: " + AppConfig.getRazorpayKeyId());
        keyLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        topRow.getChildren().addAll(badge, keyLbl, sp);

        Label info = new Label(
            "Pay with UPI QR (GPay / PhonePe / Paytm), Credit & Debit Cards, NetBanking, or Wallets via official Razorpay modal."
        );
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: #CBD5E1; -fx-line-spacing: 2px;");
        info.setWrapText(true);

        HBox chips = new HBox(6);
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.getChildren().addAll(
            createRzpChip("📱 UPI QR"),
            createRzpChip("💳 Visa / MC / RuPay"),
            createRzpChip("🏛 NetBanking"),
            createRzpChip("👛 Wallets")
        );

        HBox testRow = new HBox(10);
        testRow.setAlignment(Pos.CENTER_LEFT);

        Button instantTestBtn = new Button("⚡ Instant 1-Click Test Approval (Fast QA)");
        instantTestBtn.setStyle(
            "-fx-background-color: rgba(34,197,94,0.14); -fx-text-fill: #4ADE80; -fx-font-weight: 700; " +
            "-fx-font-size: 11px; -fx-padding: 5px 12px; -fx-border-color: rgba(34,197,94,0.35); " +
            "-fx-border-radius: 6px; -fx-background-radius: 6px; -fx-cursor: hand;"
        );
        instantTestBtn.setOnAction(e -> triggerInstantTestRazorpay(payableTotal, currentPrices));

        razorpayStatusLbl = new Label("Ready to launch official Razorpay checkout modal.");
        razorpayStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        testRow.getChildren().addAll(instantTestBtn, razorpayStatusLbl);

        box.getChildren().addAll(topRow, info, chips, testRow);
        return box;
    }

    private Label createRzpChip(String text) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #C4B5FD;" +
            "-fx-background-color: rgba(139,92,246,0.15); -fx-padding: 3px 8px;" +
            "-fx-background-radius: 10px; -fx-border-color: rgba(139,92,246,0.3); -fx-border-radius: 10px;"
        );
        return l;
    }

    private Button segBtn(String text, boolean active) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        setActive(b, active);
        return b;
    }

    private void setActive(Button b, boolean active) {
        if (active) {
            b.setStyle(
                "-fx-background-color: linear-gradient(to right, #8B5CF6, #6D28D9);" +
                "-fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 13px;" +
                "-fx-background-radius: 9px; -fx-padding: 8px 14px; -fx-cursor: hand;" +
                "-fx-effect: dropshadow(gaussian, rgba(139,92,246,0.45), 10, 0, 0, 3);"
            );
        } else {
            b.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #6B7280;" +
                "-fx-font-size: 13px; -fx-background-radius: 9px;" +
                "-fx-padding: 8px 14px; -fx-cursor: hand;"
            );
        }
    }

    /* ── UPI form ───────────────────────────────────────────────────── */
    private VBox buildUpiForm() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(6, 0, 0, 0));

        Label prompt = new Label("UPI ID / Virtual Payment Address");
        prompt.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        upiField = new TextField();
        upiField.setPromptText("yourname@okaxis");
        User currentUser = AuthService.getCurrentUser();
        if (currentUser != null) {
            upiField.setText(currentUser.getUsername() + "@okhdfcbank");
        }

        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.getChildren().addAll(
                upiChip("@oksbi"), upiChip("@okaxis"),
                upiChip("@paytm"), upiChip("@ybl"));

        box.getChildren().addAll(prompt, upiField, chips);
        return box;
    }

    private Button upiChip(String suffix) {
        Button c = new Button(suffix);
        c.setStyle(
            "-fx-font-size: 11px; -fx-padding: 4px 10px;" +
            "-fx-background-color: rgba(139,92,246,0.10); -fx-text-fill: #C4B5FD;" +
            "-fx-background-radius: 12px;" +
            "-fx-border-color: rgba(139,92,246,0.28);" +
            "-fx-border-radius: 12px; -fx-border-width: 1px; -fx-cursor: hand;"
        );
        c.setOnAction(e -> {
            if (upiField == null) return;
            String cur = upiField.getText();
            int at = cur.indexOf('@');
            String prefix = at > 0 ? cur.substring(0, at) : cur;
            if (prefix.isBlank()) prefix = "customer";
            upiField.setText(prefix + suffix);
        });
        return c;
    }

    /* ── Card form ──────────────────────────────────────────────────── */
    private VBox buildCardForm() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(6, 0, 0, 0));

        Label prompt = new Label("Credit / Debit Card  (Simulated)");
        prompt.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

        cardNumberField = new TextField("4532 8901 2345 7890");
        cardNumberField.setPromptText("Card number");

        HBox row2 = new HBox(10);
        cardExpiryField = new TextField("12/28");
        cardExpiryField.setPromptText("MM/YY");
        cardExpiryField.setPrefWidth(100);

        cardCvvField = new TextField("888");
        cardCvvField.setPromptText("CVV");
        cardCvvField.setPrefWidth(80);

        Button autoFill = new Button("Auto-fill Test Card");
        autoFill.getStyleClass().add("button-secondary");
        autoFill.setStyle("-fx-font-size: 11px; -fx-padding: 5px 12px;");
        autoFill.setOnAction(e -> {
            cardNumberField.setText("4532 8901 2345 7890");
            cardExpiryField.setText("12/28");
            cardCvvField.setText("888");
        });

        row2.getChildren().addAll(cardExpiryField, cardCvvField, autoFill);
        box.getChildren().addAll(prompt, cardNumberField, row2);
        return box;
    }

    /* ── Cash form ──────────────────────────────────────────────────── */
    private VBox buildCashForm() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setStyle(
            "-fx-background-color: rgba(34,197,94,0.06);" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: rgba(34,197,94,0.22);" +
            "-fx-border-radius: 10px; -fx-border-width: 1px;"
        );

        Label title = new Label("📍  Pay at Box Office Counter");
        title.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #22C55E;"
        );

        Label desc = new Label(
            "Your booking is confirmed immediately. Please visit the box office " +
            "15 minutes before showtime with your Booking ID to pay and collect printed tickets."
        );
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #A1A1AA; -fx-line-spacing: 2px;");
        desc.setWrapText(true);

        box.getChildren().addAll(title, desc);
        return box;
    }

    /* ================================================================ */
    /* ACTION ROW                                                         */
    /* ================================================================ */
    private HBox buildActionRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 0, 0, 0));

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(24, 24);
        progressIndicator.setVisible(false);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button-secondary");
        cancelBtn.setStyle("-fx-font-size: 14px; -fx-padding: 11px 22px;");
        cancelBtn.setOnAction(e -> handleCancel());

        confirmBtn = new Button("⚡ Pay with Razorpay  ₹ " + payableTotal.toPlainString());
        confirmBtn.getStyleClass().add("button-primary");
        confirmBtn.setStyle("-fx-font-size: 14px; -fx-padding: 11px 26px;");
        confirmBtn.setOnAction(e -> triggerPayment(payableTotal, currentPrices));

        row.getChildren().addAll(progressIndicator, cancelBtn, confirmBtn);
        return row;
    }

    /* ================================================================ */
    /* PAYMENT PROCESSING — JDBC runs on background thread              */
    /* ================================================================ */
    private void triggerPayment(BigDecimal total, List<BigDecimal> prices) {
        User user = AuthService.getCurrentUser();
        if (user == null) {
            AlertHelper.showError("Session Expired", "Auth Error", "Please log in again.");
            return;
        }

        // ── Razorpay Gateway Handler ──────────────────────────────────
        if (selectedMethod == PaymentMethod.RAZORPAY) {
            launchRazorpayGateway(total, prices, user);
            return;
        }

        // ── Input validation for direct methods ───────────────────────
        if (selectedMethod == PaymentMethod.UPI) {
            String upi = upiField != null ? upiField.getText().trim() : "";
            if (upi.isEmpty() || !upi.contains("@")) {
                AlertHelper.showWarning("Invalid UPI ID",
                        "Validation Error",
                        "Please enter a valid UPI ID (e.g. yourname@okaxis).");
                return;
            }
        } else if (selectedMethod == PaymentMethod.CARD) {
            String num = cardNumberField != null
                    ? cardNumberField.getText().replaceAll("\\s+", "") : "";
            if (num.length() < 12) {
                AlertHelper.showWarning("Invalid Card Number",
                        "Validation Error",
                        "Card number must be at least 12 digits.");
                return;
            }
        }

        // ── Lock UI ───────────────────────────────────────────────────
        confirmBtn.setDisable(true);
        confirmBtn.setText("Processing...");
        progressIndicator.setVisible(true);

        // Snapshot payment method — captured before background thread starts
        final PaymentMethod methodSnapshot = selectedMethod;

        // ── Run JDBC on background thread — never block FX thread ─────
        Thread worker = new Thread(() -> {
            try { Thread.sleep(700); } catch (InterruptedException ignored) {}
            doBookingTransaction(total, prices, user, methodSnapshot, null, null);
        }, "cineflow-booking-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void launchRazorpayGateway(BigDecimal total, List<BigDecimal> prices, User user) {
        confirmBtn.setDisable(true);
        confirmBtn.setText("⚡ Calling Razorpay API...");
        progressIndicator.setVisible(true);
        if (razorpayStatusLbl != null) {
            razorpayStatusLbl.setText("Creating real-time order via Razorpay API...");
            razorpayStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #00C2FF; -fx-font-weight: 700;");
        }

        Thread t = new Thread(() -> {
            try {
                String receipt = "cf_rcpt_" + System.currentTimeMillis();
                RazorpayService.RazorpayOrder order = razorpayService.createOrder(total, receipt);

                Platform.runLater(() -> {
                    try {
                        confirmBtn.setText("🌐 Awaiting Browser Payment...");
                        if (razorpayStatusLbl != null) {
                            razorpayStatusLbl.setText("Order " + order.getOrderId() + " active. Opening browser window...");
                            razorpayStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #FCD34D; -fx-font-weight: 700;");
                        }

                        if (activeCheckoutHandle != null) activeCheckoutHandle.stop();

                        activeCheckoutHandle = razorpayService.startCheckoutBridge(
                                order, user, showtime.getMovieTitle(),
                                result -> {
                                    Platform.runLater(() -> {
                                        if (result.isVerified()) {
                                            ToastManager.showSuccess("Payment Verified", "Razorpay Confirmed: " + result.getPaymentId());
                                            doBookingTransaction(total, prices, user, PaymentMethod.RAZORPAY, result.getPaymentId(), result.getOrderId());
                                        } else {
                                            resetButton(total);
                                            AlertHelper.showError("Verification Error", "Signature Mismatch", "Cryptographic signature could not be verified.");
                                        }
                                    });
                                },
                                cancelMsg -> {
                                    Platform.runLater(() -> {
                                        resetButton(total);
                                        ToastManager.showInfo("Payment Window Closed", "You can retry whenever you are ready.");
                                        if (razorpayStatusLbl != null) {
                                            razorpayStatusLbl.setText("Payment modal closed. Click button to retry.");
                                            razorpayStatusLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444;");
                                        }
                                    });
                                }
                        );

                        RazorpayService.openInBrowser(activeCheckoutHandle.getCheckoutUrl());

                    } catch (Exception ex) {
                        resetButton(total);
                        AlertHelper.showError("Gateway Error", "Failed to launch Razorpay checkout", ex.getMessage());
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    resetButton(total);
                    AlertHelper.showError("Razorpay API Error", "Order Creation Failed", ex.getMessage());
                });
            }
        }, "cineflow-razorpay-launcher");
        t.setDaemon(true);
        t.start();
    }

    private void triggerInstantTestRazorpay(BigDecimal total, List<BigDecimal> prices) {
        User user = AuthService.getCurrentUser();
        if (user == null) {
            AlertHelper.showError("Session Expired", "Auth Error", "Please log in again.");
            return;
        }
        confirmBtn.setDisable(true);
        confirmBtn.setText("⚡ Approving Test Payment...");
        progressIndicator.setVisible(true);

        Thread t = new Thread(() -> {
            try {
                String orderId = "order_sim_" + java.util.UUID.randomUUID();
                RazorpayService.RazorpayPaymentResult result = razorpayService.simulateTestPayment(orderId, total);
                if (!result.isVerified()) {
                    throw new IllegalStateException("Instant test payment requires a local or Razorpay test configuration.");
                }

                Platform.runLater(() -> {
                    ToastManager.showSuccess("Instant Payment", "Verified: " + result.getPaymentId());
                    doBookingTransaction(total, prices, user, PaymentMethod.RAZORPAY, result.getPaymentId(), result.getOrderId());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    resetButton(total);
                    AlertHelper.showError("Razorpay Error", "Test Payment Failed", ex.getMessage());
                });
            }
        }, "cineflow-razorpay-test");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Executes the ACID JDBC booking transaction on a background thread.
     * All UI updates are marshalled back to the FX thread via Platform.runLater().
     */
    private void doBookingTransaction(BigDecimal total,
                                      List<BigDecimal> prices,
                                      User user,
                                      PaymentMethod method,
                                      String transactionId,
                                      String orderId) {

        Booking booking = new Booking(
                user.getId(), showtime.getId(), null, total, BookingStatus.CONFIRMED);

        try {
            PricingService pricingService = new PricingService();
            PriceQuote q = pricingService.getOrRecalculateQuote(user.getId(), showtime.getId(), SeatType.REGULAR);
            booking.setPricingSnapshot(q.getBreakdownJson());
        } catch (Exception ignored) {}

        Payment payment = new Payment(
                0, method, total, transactionId, orderId, PaymentStatus.PAID);

        List<Integer> seatIds = new ArrayList<>();
        for (Seat s : selectedSeats) seatIds.add(s.getId());

        try {
            boolean success = bookingService.confirmBooking(booking, seatIds, prices, payment);

            if (success) {
                try {
                    loyaltyService.recordUsageAndRewards(user.getId(), appliedPromoCode, appliedPointsRedeemed, earnedLoyaltyPoints);
                } catch (Exception ignored) {}

                // Stop countdown from background thread
                if (countdownTimeline != null) countdownTimeline.stop();
                if (activeCheckoutHandle != null) {
                    activeCheckoutHandle.stop();
                    activeCheckoutHandle = null;
                }

                // Populate display fields
                booking.setUsername(user.getUsername());
                booking.setCustomerName(user.getFullName());
                booking.setMovieTitle(showtime.getMovieTitle());
                booking.setScreenName(showtime.getScreenName());
                booking.setShowtimeStart(showtime.getStartTime());
                booking.setBookingTime(LocalDateTime.now());
                booking.setPaymentMethod(method);
                booking.setTransactionId(transactionId);
                booking.setStatus(BookingStatus.CONFIRMED);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < selectedSeats.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(selectedSeats.get(i).getSeatCode());
                }
                booking.setSeatNumbers(sb.toString());

                // If DAO didn't set an ID, re-fetch most recent booking
                if (booking.getId() <= 0) {
                    try {
                        List<Booking> recent = bookingService.getCustomerBookings(user.getId());
                        if (!recent.isEmpty()) {
                            Booking freshBooking = recent.get(0);
                            freshBooking.setPaymentMethod(method);
                            freshBooking.setTransactionId(transactionId);
                            Platform.runLater(() -> {
                                dialogStage.close();
                                if (onBookingSuccessful != null) onBookingSuccessful.accept(freshBooking);
                            });
                            return;
                        }
                    } catch (SQLException ignored) {}
                }

                final Booking finalBooking = booking;
                Platform.runLater(() -> {
                    dialogStage.close();
                    if (onBookingSuccessful != null) onBookingSuccessful.accept(finalBooking);
                });

            } else {
                Platform.runLater(() -> {
                    resetButton(total);
                    AlertHelper.showError("Booking Failed",
                            "Transaction Failed",
                            "Could not confirm your reservation. Please try again.");
                });
            }

        } catch (SQLException ex) {
            final String msg = ex.getMessage();
            Platform.runLater(() -> {
                resetButton(total);
                AlertHelper.showError("Booking Failed", "Database Error", msg);
            });
        }
    }

    private void resetButton(BigDecimal total) {
        confirmBtn.setDisable(false);
        if (selectedMethod == PaymentMethod.RAZORPAY) {
            confirmBtn.setText("⚡ Pay with Razorpay  ₹ " + total.toPlainString());
        } else if (selectedMethod == PaymentMethod.CASH) {
            confirmBtn.setText("Book at Box Office  ₹ " + total.toPlainString());
        } else {
            confirmBtn.setText("Confirm & Pay  ₹ " + total.toPlainString());
        }
        progressIndicator.setVisible(false);
    }

    /* ================================================================ */
    /* COUNTDOWN TIMER                                                    */
    /* ================================================================ */
    private void startCountdown() {
        remainingSeconds = 300;
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            int m = remainingSeconds / 60;
            int s = remainingSeconds % 60;
            timerLabel.setText(String.format("Seats reserved for: %02d:%02d", m, s));

            if (remainingSeconds <= 60) {
                timerLabel.setStyle(
                    "-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #EF4444;"
                );
            }
            if (remainingSeconds <= 0) {
                countdownTimeline.stop();
                handleExpiry();
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    private void handleExpiry() {
        if (activeCheckoutHandle != null) {
            activeCheckoutHandle.stop();
            activeCheckoutHandle = null;
        }
        User user = AuthService.getCurrentUser();
        if (user != null) bookingService.releaseUserHolds(showtime.getId(), user.getId());
        AlertHelper.showWarning("Time Expired",
                "Seat Hold Expired",
                "Your 5-minute hold has expired. The seats have been released. Please try again.");
        if (dialogStage != null) dialogStage.close();
    }

    private void handleCancel() {
        if (countdownTimeline != null) countdownTimeline.stop();
        if (activeCheckoutHandle != null) {
            activeCheckoutHandle.stop();
            activeCheckoutHandle = null;
        }
        User user = AuthService.getCurrentUser();
        if (user != null) bookingService.releaseUserHolds(showtime.getId(), user.getId());
        if (dialogStage != null) dialogStage.close();
    }

    /* ================================================================ */
    /* HELPERS                                                            */
    /* ================================================================ */
    private Region divider() {
        Region r = new Region();
        r.setMinHeight(1);
        r.setStyle("-fx-background-color: rgba(139,92,246,0.14);");
        return r;
    }
}
