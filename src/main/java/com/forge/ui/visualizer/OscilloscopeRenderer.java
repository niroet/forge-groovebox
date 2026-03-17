package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

/**
 * CRT-style oscilloscope waveform renderer.
 *
 * <p>Draws the 256-sample waveform as a polyline with a phosphor glow effect.
 * A faint CRT-style grid is rendered at 25% intervals. The zero-crossing centre
 * line is marked with a slightly brighter rule.
 */
public final class OscilloscopeRenderer implements VisualizerRenderer {

    private static final Color BG_COLOR    = Color.web("#000800");  // very dark green-black
    private static final Color GRID_COLOR  = Color.web("#00220000").deriveColor(0, 1, 1, 0.18);
    private static final Color ZERO_COLOR  = Color.web("#ff2200").deriveColor(0, 1, 1, 0.25);
    private static final Color LINE_COLOR  = Color.web("#ff2200");

    private final DropShadow glow;

    public OscilloscopeRenderer() {
        glow = new DropShadow();
        glow.setColor(Color.web("#ff4400"));
        glow.setRadius(6);
        glow.setSpread(0.2);
    }

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        // CRT grid — 25% intervals horizontal and vertical
        gc.setStroke(Color.web("#ff2200", 0.12));
        gc.setLineWidth(0.5);
        for (int i = 1; i < 4; i++) {
            double x = width * i / 4.0;
            double y = height * i / 4.0;
            gc.strokeLine(x, 0, x, height);
            gc.strokeLine(0, y, width, y);
        }

        // Zero-crossing centre line
        double cy = height / 2.0;
        gc.setStroke(Color.web("#ff2200", 0.3));
        gc.setLineWidth(0.8);
        gc.strokeLine(0, cy, width, cy);

        // Waveform polyline
        float[] samples = bus.getWaveformSamples();
        if (samples == null || samples.length == 0) return;

        int len = samples.length;
        double[] px = new double[len];
        double[] py = new double[len];

        double xStep = width / (len - 1.0);
        for (int i = 0; i < len; i++) {
            px[i] = i * xStep;
            // samples in [-1, 1]: map -1 -> height*0.9, 1 -> height*0.1
            py[i] = cy - samples[i] * (height * 0.42);
        }

        gc.setEffect(glow);
        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.strokePolyline(px, py, len);
        gc.setEffect(null);
    }
}
