package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import com.forge.audio.engine.FftProcessor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;

/**
 * Spectrum bar visualizer.
 *
 * <p>Draws one vertical bar per FFT bin. Bars use a gradient from red (bottom) through
 * orange (middle) to yellow (top). A DropShadow glow effect is applied for the argent-energy
 * aesthetic. Bar heights decay smoothly via linear interpolation toward the current magnitude,
 * preventing jitter on transient peaks.
 */
public final class SpectrumRenderer implements VisualizerRenderer {

    // Gradient stops: bottom = #ff2200, middle = #ff8800, top = #ffcc00
    private static final Color COLOR_BOTTOM = Color.web("#ff2200");
    private static final Color COLOR_MID    = Color.web("#ff8800");
    private static final Color COLOR_TOP    = Color.web("#ffcc00");

    private static final Color BG_COLOR   = Color.web("#050505");
    private static final double DECAY     = 0.18;  // lerp factor toward new magnitude per frame

    // Display only the lower portion of bins (most musical content is here)
    private static final int DISPLAY_BINS = 96;

    private final float[] smoothed = new float[FftProcessor.BINS];
    private final DropShadow glow;

    public SpectrumRenderer() {
        glow = new DropShadow();
        glow.setColor(Color.web("#ff4400"));
        glow.setRadius(8);
        glow.setSpread(0.3);
    }

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        // Clear background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        float[] mags = bus.getFftMagnitudes();
        if (mags == null || mags.length == 0) return;

        int bins = Math.min(DISPLAY_BINS, mags.length);
        double barW = width / bins;

        // Enable glow
        gc.setEffect(glow);

        for (int i = 0; i < bins; i++) {
            // Smooth: lerp previous smoothed value toward current magnitude
            smoothed[i] = smoothed[i] + (float) ((mags[i] - smoothed[i]) * DECAY);
            float mag = Math.max(0f, smoothed[i]);

            double barH = Math.min(height * 0.95, mag * height * 2.5);
            double x    = i * barW;
            double y    = height - barH;

            if (barH < 1.0) continue;

            // Gradient: bottom to top = red -> orange -> yellow
            LinearGradient grad = new LinearGradient(
                x, height, x, y,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, COLOR_BOTTOM),
                new Stop(0.5, COLOR_MID),
                new Stop(1.0, COLOR_TOP)
            );

            gc.setFill(grad);
            double gap = Math.max(0.5, barW * 0.1);
            gc.fillRect(x + gap / 2, y, barW - gap, barH);
        }

        gc.setEffect(null);
    }
}
