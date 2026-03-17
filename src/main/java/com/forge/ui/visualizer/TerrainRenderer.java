package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import com.forge.audio.engine.FftProcessor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Pseudo-3D wireframe terrain visualizer.
 *
 * <p>Renders a "mountain range" effect using the 2D Canvas. Horizontal polylines
 * are drawn at different Y positions; each line's vertex heights are displaced by
 * FFT magnitudes. Lines closer to the bottom (front) are brighter; lines near the
 * top (back) are faded (depth fog). The result is a shifting argent-energy landscape
 * that responds dynamically to audio.
 */
public final class TerrainRenderer implements VisualizerRenderer {

    private static final int   LINES       = 24;   // number of depth slices
    private static final int   SEGMENTS    = 64;   // vertices per line
    private static final Color BG_COLOR    = Color.web("#020202");
    private static final Color LINE_NEAR   = Color.web("#ff6600");
    private static final Color LINE_FAR    = Color.web("#220800");

    // Smoothed heights per line per segment: [line][segment]
    private final float[][] heights    = new float[LINES][SEGMENTS];
    private final float[][] prevHeights = new float[LINES][SEGMENTS];

    // Each line keeps a phase offset so terrain "moves"
    private final double[] phaseOffset = new double[LINES];
    private long frameCount = 0;

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        float[] mags = bus.getFftMagnitudes();
        frameCount++;

        // Each line occupies a band of the vertical space.
        // y=0 is the "horizon" (back), y=height is the bottom (front).
        double yTop    = height * 0.15;
        double yBottom = height * 0.92;
        double ySpan   = yBottom - yTop;

        double[] px = new double[SEGMENTS + 1];
        double[] py = new double[SEGMENTS + 1];

        for (int line = 0; line < LINES; line++) {
            double t      = (double) line / (LINES - 1);          // 0 = back, 1 = front
            double baseY  = yTop + t * ySpan;
            double depthScale = 0.3 + t * 0.7;                   // front lines taller
            double alpha  = 0.05 + t * 0.95;                     // front lines brighter

            // Interpolate line color between far and near
            Color lineColor = LINE_FAR.interpolate(LINE_NEAR, t).deriveColor(0, 1, 1, alpha);
            gc.setStroke(lineColor);
            gc.setLineWidth(0.8 + t * 1.2);

            // Map FFT bins to segments for this line
            // Each line uses a different frequency band (higher lines → higher freqs)
            int binStart = (int) (line * (FftProcessor.BINS / (double) LINES));
            int binEnd   = Math.min(FftProcessor.BINS - 1, binStart + FftProcessor.BINS / LINES);

            // Animate: advance phase offset each frame
            phaseOffset[line] += 0.015 * (1.0 + t * 2.0);

            for (int seg = 0; seg <= SEGMENTS; seg++) {
                double segT    = (double) seg / SEGMENTS;
                double xPos    = segT * width;

                // Sample magnitude from the bin range for this line
                int bin = binStart + (int) (segT * (binEnd - binStart));
                bin = Math.max(0, Math.min(FftProcessor.BINS - 1, bin));
                float mag = (mags != null && mags.length > bin) ? mags[bin] : 0f;

                // Smooth toward new magnitude
                float prevH = prevHeights[line][Math.min(seg, SEGMENTS - 1)];
                float targetH = mag;
                float smoothH = prevH + (targetH - prevH) * 0.25f;
                if (seg < SEGMENTS) {
                    heights[line][seg] = smoothH;
                    prevHeights[line][seg] = smoothH;
                }

                // Displacement: magnitude * depth scale, plus a subtle wave animation
                double wave = Math.sin(segT * Math.PI * 4 + phaseOffset[line]) * 0.02;
                double displacement = (smoothH * 0.6 + wave) * height * depthScale;

                px[seg] = xPos;
                py[seg] = baseY - displacement;
            }

            gc.strokePolyline(px, py, SEGMENTS + 1);
        }
    }
}
