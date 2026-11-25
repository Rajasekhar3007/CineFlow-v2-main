package com.cineflow.ui.common;

import com.cineflow.util.AppConfig;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CineFlow v2 Central Motion and Animation Engine.
 * Provides high-performance, cancellable animations with full reduced-motion support.
 */
public class AnimationUtil {

    private static final Interpolator SPLINE_OUT = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    public static boolean isReducedMotion() {
        return AppConfig.isReducedMotion();
    }

    /**
     * Staggered fade and slide entrance for a single node.
     */
    public static void fadeInUp(Node node, double delayMs) {
        if (node == null) return;
        if (AppConfig.isReducedMotion()) {
            node.setOpacity(1.0);
            node.setTranslateY(0);
            return;
        }

        node.setOpacity(0.0);
        node.setTranslateY(14.0);

        FadeTransition ft = new FadeTransition(Duration.millis(320), node);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(320), node);
        tt.setToY(0.0);
        tt.setInterpolator(SPLINE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setDelay(Duration.millis(delayMs));
        pt.play();
    }

    /**
     * Cascading entrance for a collection of nodes.
     */
    public static void stagger(List<? extends Node> nodes, double stepMs) {
        if (nodes == null || nodes.isEmpty()) return;
        double currentDelay = 0;
        double cap = 600.0;
        for (Node node : nodes) {
            fadeInUp(node, Math.min(currentDelay, cap));
            currentDelay += stepMs;
        }
    }

    /**
     * Micro-interaction: Subtle shrink on mouse press.
     */
    public static void pressScale(Node node) {
        if (node == null || AppConfig.isReducedMotion()) return;
        node.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), node);
            st.setToX(0.96);
            st.setToY(0.96);
            st.play();
        });
        node.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    /**
     * Micro-interaction: Elevation and smooth lift on hover.
     */
    public static void hoverLift(Node node) {
        if (node == null || AppConfig.isReducedMotion()) return;
        DropShadow baseShadow = new DropShadow(18, Color.color(0.54, 0.36, 0.96, 0.18));
        node.setEffect(baseShadow);

        node.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
            st.setToX(1.025);
            st.setToY(1.025);
            st.setInterpolator(SPLINE_OUT);
            st.play();

            Timeline tl = new Timeline(
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(baseShadow.radiusProperty(), 28),
                            new KeyValue(baseShadow.colorProperty(), Color.color(0.54, 0.36, 0.96, 0.35)))
            );
            tl.play();
        });

        node.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(SPLINE_OUT);
            st.play();

            Timeline tl = new Timeline(
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(baseShadow.radiusProperty(), 18),
                            new KeyValue(baseShadow.colorProperty(), Color.color(0.54, 0.36, 0.96, 0.18)))
            );
            tl.play();
        });
    }

    /**
     * Attention getter: Soft pulsating glow around a node.
     */
    public static Timeline pulseGlow(Node node) {
        if (node == null) return null;
        DropShadow glow = new DropShadow(12, Color.web("#8B5CF6"));
        node.setEffect(glow);

        if (AppConfig.isReducedMotion()) return null;

        Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), 8),
                        new KeyValue(glow.colorProperty(), Color.color(0.54, 0.36, 0.96, 0.3))),
                new KeyFrame(Duration.millis(700),
                        new KeyValue(glow.radiusProperty(), 24),
                        new KeyValue(glow.colorProperty(), Color.color(0.0, 0.76, 1.0, 0.85))),
                new KeyFrame(Duration.millis(1400),
                        new KeyValue(glow.radiusProperty(), 8),
                        new KeyValue(glow.colorProperty(), Color.color(0.54, 0.36, 0.96, 0.3)))
        );
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
        return pulse;
    }

    /**
     * Horizontal 6-step error shake animation.
     */
    public static void shake(Node node) {
        if (node == null) return;
        if (AppConfig.isReducedMotion()) return;

        double origX = node.getTranslateX();
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(0), new KeyValue(node.translateXProperty(), origX)),
                new KeyFrame(Duration.millis(60), new KeyValue(node.translateXProperty(), origX - 9)),
                new KeyFrame(Duration.millis(120), new KeyValue(node.translateXProperty(), origX + 9)),
                new KeyFrame(Duration.millis(180), new KeyValue(node.translateXProperty(), origX - 7)),
                new KeyFrame(Duration.millis(240), new KeyValue(node.translateXProperty(), origX + 7)),
                new KeyFrame(Duration.millis(300), new KeyValue(node.translateXProperty(), origX - 4)),
                new KeyFrame(Duration.millis(360), new KeyValue(node.translateXProperty(), origX))
        );
        timeline.play();
    }

    /**
     * Animated counter for monetary and integer values.
     */
    public static void countUp(Label label, BigDecimal from, BigDecimal to) {
        if (label == null || from == null || to == null) return;
        if (AppConfig.isReducedMotion()) {
            label.setText("₹" + to.setScale(2, RoundingMode.HALF_UP));
            return;
        }

        DoubleProperty countProp = new SimpleDoubleProperty(from.doubleValue());
        countProp.addListener((obs, oldVal, newVal) -> {
            BigDecimal current = BigDecimal.valueOf(newVal.doubleValue()).setScale(2, RoundingMode.HALF_UP);
            label.setText("₹" + current);
        });

        Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(countProp, from.doubleValue())),
                new KeyFrame(Duration.millis(500), new KeyValue(countProp, to.doubleValue(), SPLINE_OUT))
        );
        tl.play();
    }

    /**
     * Smooth page transition crossfade & subtle slide.
     */
    public static void crossfadeSlide(Parent oldRoot, Parent newRoot, Runnable onComplete) {
        if (newRoot == null) return;
        if (AppConfig.isReducedMotion() || oldRoot == null) {
            newRoot.setOpacity(1.0);
            newRoot.setTranslateX(0);
            if (onComplete != null) onComplete.run();
            return;
        }

        newRoot.setOpacity(0.0);
        newRoot.setTranslateX(18.0);

        FadeTransition ft = new FadeTransition(Duration.millis(220), newRoot);
        ft.setToValue(1.0);

        TranslateTransition tt = new TranslateTransition(Duration.millis(220), newRoot);
        tt.setToX(0);
        tt.setInterpolator(SPLINE_OUT);

        ParallelTransition pt = new ParallelTransition(ft, tt);
        pt.setOnFinished(e -> {
            if (onComplete != null) onComplete.run();
        });
        pt.play();
    }

    /**
     * Shimmer placeholder state for skeleton loading.
     */
    public static Timeline skeleton(Node node) {
        if (node == null) return null;
        if (AppConfig.isReducedMotion()) {
            node.setOpacity(0.6);
            return null;
        }

        Timeline shimmer = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(node.opacityProperty(), 0.35)),
                new KeyFrame(Duration.millis(600), new KeyValue(node.opacityProperty(), 0.75)),
                new KeyFrame(Duration.millis(1200), new KeyValue(node.opacityProperty(), 0.35))
        );
        shimmer.setCycleCount(Animation.INDEFINITE);
        shimmer.play();
        return shimmer;
    }

    /**
     * Animated draw checkmark using stroke dash properties.
     */
    public static void drawCheck(Path path) {
        if (path == null) return;
        if (AppConfig.isReducedMotion()) {
            path.setOpacity(1.0);
            return;
        }
        path.setOpacity(0);
        path.setScaleX(0.5);
        path.setScaleY(0.5);

        ParallelTransition pt = new ParallelTransition(
                new FadeTransition(Duration.millis(250), path),
                new ScaleTransition(Duration.millis(350), path)
        );
        ((FadeTransition) pt.getChildren().get(0)).setToValue(1.0);
        ScaleTransition st = (ScaleTransition) pt.getChildren().get(1);
        st.setToX(1.0);
        st.setToY(1.0);
        st.setInterpolator(Interpolator.SPLINE(0.175, 0.885, 0.32, 1.275)); // Overshoot pop
        pt.play();
    }

    /**
     * Lightweight confetti particle burst on successful booking checkout.
     */
    public static void confetti(Pane container) {
        if (container == null || AppConfig.isReducedMotion()) return;

        Random rnd = new Random();
        Color[] colors = {
                Color.web("#8B5CF6"), Color.web("#00C2FF"), Color.web("#22C55E"),
                Color.web("#F59E0B"), Color.web("#EC4899"), Color.web("#F8FAFC")
        };

        List<Particle> particles = new ArrayList<>();
        double startX = container.getWidth() > 0 ? container.getWidth() / 2.0 : 300.0;
        double startY = container.getHeight() > 0 ? container.getHeight() / 2.0 : 250.0;

        for (int i = 0; i < 45; i++) {
            Circle c = new Circle(3 + rnd.nextInt(4), colors[rnd.nextInt(colors.length)]);
            c.setManaged(false);
            c.setLayoutX(startX);
            c.setLayoutY(startY);
            container.getChildren().add(c);

            double angle = rnd.nextDouble() * 2 * Math.PI;
            double speed = 3.0 + rnd.nextDouble() * 8.0;
            particles.add(new Particle(c, Math.cos(angle) * speed, Math.sin(angle) * speed - 4.0));
        }

        AnimationTimer timer = new AnimationTimer() {
            int frames = 0;
            @Override
            public void handle(long now) {
                frames++;
                for (Particle p : particles) {
                    p.node.setLayoutX(p.node.getLayoutX() + p.vx);
                    p.node.setLayoutY(p.node.getLayoutY() + p.vy);
                    p.vy += 0.22; // Gravity
                    p.vx *= 0.98; // Drag
                    p.node.setOpacity(Math.max(0.0, 1.0 - (frames / 90.0)));
                }

                if (frames > 90) {
                    this.stop();
                    for (Particle p : particles) {
                        container.getChildren().remove(p.node);
                    }
                }
            }
        };
        timer.start();
    }

    private static class Particle {
        final Node node;
        double vx;
        double vy;
        Particle(Node node, double vx, double vy) {
            this.node = node;
            this.vx = vx;
            this.vy = vy;
        }
    }
}
