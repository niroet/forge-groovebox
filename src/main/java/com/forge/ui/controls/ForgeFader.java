package com.forge.ui.controls;

import com.forge.ui.theme.ForgeColors;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;

/**
 * Vertical fader control.
 *
 * - Drag to change value 0.0 (bottom) – 1.0 (top)
 * - LED-style colored fill from bottom
 * - Typical size: 16 × 80
 */
public class ForgeFader extends Region {

    private static final double HANDLE_HEIGHT = 8.0;

    private final DoubleProperty value = new SimpleDoubleProperty(0.5);
    private final Canvas canvas;
    private double dragStartY;
    private double dragStartValue;

    // -----------------------------------------------------------------------

    public ForgeFader() {
        // Fixed-size canvas avoids resize feedback loops
        canvas = new Canvas(16, 80);
        getChildren().add(canvas);
        setPrefSize(16, 80);
        setMinSize(16, 80);
        setMaxSize(16, 80);
        value.addListener((obs, o, n) -> draw());

        canvas.setOnMousePressed(e -> {
            dragStartY     = e.getSceneY();
            dragStartValue = value.get();
            canvas.requestFocus();
        });

        canvas.setOnMouseDragged(e -> {
            double h     = canvas.getHeight();
            double delta = (dragStartY - e.getSceneY()) / (h - HANDLE_HEIGHT);
            value.set(clamp(dragStartValue + delta, 0.0, 1.0));
        });

        draw();
    }

    // ---- Public API --------------------------------------------------------

    public DoubleProperty valueProperty() { return value; }
    public double getValue()              { return value.get(); }
    public void setValue(double v)        { value.set(clamp(v, 0.0, 1.0)); }

    // ---- Drawing -----------------------------------------------------------

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double trackX = w / 2.0 - 2;
        double trackW = 4;

        // Track background
        gc.setFill(Color.web("#0a0a0a"));
        gc.fillRoundRect(trackX, 0, trackW, h, 2, 2);
        gc.setStroke(Color.web("#2a2a2a"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(trackX, 0, trackW, h, 2, 2);

        // LED fill from bottom up to current value
        double fillH   = (h - HANDLE_HEIGHT) * value.get();
        double fillY   = h - HANDLE_HEIGHT / 2.0 - fillH;

        if (fillH > 0) {
            LinearGradient ledGrad = new LinearGradient(
                0, fillY + fillH, 0, fillY,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, ForgeColors.ARGENT_ORANGE.deriveColor(0, 1, 0.5, 1)),
                new Stop(0.6, ForgeColors.ARGENT_ORANGE),
                new Stop(1.0, ForgeColors.ARGENT_YELLOW)
            );
            gc.setFill(ledGrad);
            gc.fillRoundRect(trackX, fillY, trackW, fillH, 1, 1);
        }

        // Handle
        double handleY = h - HANDLE_HEIGHT / 2.0 - (h - HANDLE_HEIGHT) * value.get() - HANDLE_HEIGHT / 2.0;
        gc.setFill(Color.web("#3a3a3a"));
        gc.fillRect(1, handleY, w - 2, HANDLE_HEIGHT);
        gc.setStroke(Color.web("#666666"));
        gc.setLineWidth(1.0);
        gc.strokeRect(1, handleY, w - 2, HANDLE_HEIGHT);
        // Center line on handle
        gc.setStroke(Color.web("#999999"));
        gc.setLineWidth(1.0);
        double midY = handleY + HANDLE_HEIGHT / 2.0;
        gc.strokeLine(3, midY, w - 3, midY);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
