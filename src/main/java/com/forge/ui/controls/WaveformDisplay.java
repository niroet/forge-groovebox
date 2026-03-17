package com.forge.ui.controls;

import com.forge.model.WaveShape;
import com.forge.ui.theme.ForgeColors;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Mini oscilloscope canvas showing one cycle of a waveform.
 *
 * Supported shapes: SINE, SAW, SQUARE, PULSE, TRIANGLE
 * Default size: 80 × 30
 */
public class WaveformDisplay extends Canvas {

    private WaveShape shape = WaveShape.SINE;

    // -----------------------------------------------------------------------

    public WaveformDisplay() {
        super(80, 30);
        widthProperty() .addListener((obs, o, n) -> draw());
        heightProperty().addListener((obs, o, n) -> draw());
        draw();
    }

    public WaveformDisplay(double width, double height) {
        super(width, height);
        widthProperty() .addListener((obs, o, n) -> draw());
        heightProperty().addListener((obs, o, n) -> draw());
        draw();
    }

    // ---- Public API --------------------------------------------------------

    public void setWaveShape(WaveShape s) {
        this.shape = s;
        draw();
    }

    public WaveShape getWaveShape() {
        return shape;
    }

    // ---- Drawing -----------------------------------------------------------

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // Background
        gc.setFill(Color.web("#050505"));
        gc.fillRect(0, 0, w, h);

        // Center line (dim)
        gc.setStroke(Color.web("#1a1a1a"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, h / 2.0, w, h / 2.0);

        // Waveform
        Color waveColor = ForgeColors.ARGENT_AMBER;
        gc.setStroke(waveColor);
        gc.setLineWidth(1.5);
        gc.beginPath();

        int samples = (int) w;
        for (int i = 0; i < samples; i++) {
            double t  = (double) i / samples;  // 0.0 – 1.0 (one cycle)
            double y  = computeSample(t);       // -1.0 – +1.0
            double px = i;
            double py = (1.0 - y) / 2.0 * h;   // flip: +1 = top

            if (i == 0) {
                gc.moveTo(px, py);
            } else {
                gc.lineTo(px, py);
            }
        }
        gc.stroke();
    }

    /** Returns sample in [-1.0, +1.0] for phase t in [0.0, 1.0). */
    private double computeSample(double t) {
        return switch (shape) {
            case SINE     -> Math.sin(2.0 * Math.PI * t);
            case SAW      -> 2.0 * t - 1.0;          // rises from -1 to +1
            case SQUARE   -> t < 0.5 ? 1.0 : -1.0;
            case PULSE    -> t < 0.25 ? 1.0 : -1.0;  // 25% pulse width
            case TRIANGLE -> t < 0.25  ? 4.0 * t :
                             t < 0.75  ? 1.0 - 4.0 * (t - 0.25) :
                                         -1.0 + 4.0 * (t - 0.75);
        };
    }
}
