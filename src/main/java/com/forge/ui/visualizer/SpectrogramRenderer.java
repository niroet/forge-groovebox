package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import com.forge.audio.engine.FftProcessor;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Scrolling spectrogram renderer.
 *
 * <p>Maintains an off-screen {@link WritableImage} that serves as the history buffer.
 * Each animation frame shifts the image one pixel to the left and paints the newest
 * FFT column on the rightmost edge, creating a continuous left-to-right waterfall.
 *
 * <p>Color mapping (magnitude 0 → 1):
 * <ul>
 *   <li>0.00 – black</li>
 *   <li>0.25 – dark red</li>
 *   <li>0.50 – orange</li>
 *   <li>0.75 – yellow</li>
 *   <li>1.00 – white</li>
 * </ul>
 */
public final class SpectrogramRenderer implements VisualizerRenderer {

    private WritableImage history;
    private int imgWidth;
    private int imgHeight;

    // Column pixel buffer: reused each frame to avoid allocation
    private int[] columnPixels;

    @Override
    public void init(double width, double height) {
        imgWidth  = (int) Math.max(1, width);
        imgHeight = (int) Math.max(1, height);
        history   = new WritableImage(imgWidth, imgHeight);
        columnPixels = new int[imgHeight];

        // Fill with black
        PixelWriter pw = history.getPixelWriter();
        for (int y = 0; y < imgHeight; y++) {
            for (int x = 0; x < imgWidth; x++) {
                pw.setArgb(x, y, 0xFF000000);
            }
        }
    }

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        int w = (int) Math.max(1, width);
        int h = (int) Math.max(1, height);

        // Re-initialise if canvas was resized
        if (history == null || imgWidth != w || imgHeight != h) {
            init(width, height);
        }

        float[] mags = bus.getFftMagnitudes();
        if (mags == null || mags.length == 0) {
            gc.drawImage(history, 0, 0);
            return;
        }

        // Build the new rightmost column
        int bins = Math.min(FftProcessor.BINS, mags.length);
        for (int y = 0; y < imgHeight; y++) {
            // Map pixel row to a bin index: y=0 → high freq, y=imgHeight-1 → low freq
            int bin = (int) ((1.0 - (double) y / imgHeight) * (bins - 1));
            bin = Math.max(0, Math.min(bins - 1, bin));
            float mag = Math.max(0f, Math.min(1f, mags[bin] * 3.0f));  // scale up for visibility
            columnPixels[y] = magnitudeToArgb(mag);
        }

        // Read all existing columns from the WritableImage, shift left by 1, write new column
        // Strategy: re-read pixel reader, shift on a row-by-row basis using JavaFX pixel API.
        // For performance we copy raw ARGB into a temporary int[] and write it back.
        javafx.scene.image.PixelReader pr = history.getPixelReader();
        PixelWriter pw = history.getPixelWriter();

        // Read each row, shift left by 1
        int[] rowBuf = new int[imgWidth];
        for (int y = 0; y < imgHeight; y++) {
            pr.getPixels(0, y, imgWidth, 1,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                rowBuf, 0, imgWidth);
            // Shift left by 1
            System.arraycopy(rowBuf, 1, rowBuf, 0, imgWidth - 1);
            // Write new column value at the right edge
            rowBuf[imgWidth - 1] = columnPixels[y];
            pw.setPixels(0, y, imgWidth, 1,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                rowBuf, 0, imgWidth);
        }

        // Draw the history image onto the canvas
        gc.drawImage(history, 0, 0, width, height);
    }

    @Override
    public void dispose() {
        history = null;
    }

    // -----------------------------------------------------------------------

    /**
     * Map a normalised magnitude [0, 1] to an ARGB int.
     * 0=black, 0.25=dark red, 0.5=orange, 0.75=yellow, 1=white.
     */
    private static int magnitudeToArgb(float t) {
        // 4-segment gradient
        int r, g, b;
        if (t < 0.25f) {
            float s = t / 0.25f;
            r = (int) (s * 0x66);
            g = 0;
            b = 0;
        } else if (t < 0.5f) {
            float s = (t - 0.25f) / 0.25f;
            r = (int) (0x66 + s * (0xff - 0x66));
            g = (int) (s * 0x44);
            b = 0;
        } else if (t < 0.75f) {
            float s = (t - 0.5f) / 0.25f;
            r = 0xff;
            g = (int) (0x44 + s * (0xcc - 0x44));
            b = 0;
        } else {
            float s = (t - 0.75f) / 0.25f;
            r = 0xff;
            g = (int) (0xcc + s * (0xff - 0xcc));
            b = (int) (s * 0xff);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
