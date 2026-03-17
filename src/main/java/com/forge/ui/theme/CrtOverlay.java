package com.forge.ui.theme;

import javafx.scene.CacheHint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * A full-scene CRT overlay drawn on a Canvas child.
 * Renders horizontal scanlines and a radial vignette on every resize.
 * Mouse-transparent — does not consume input events.
 * Toggle visibility with setEnabled(boolean).
 */
public class CrtOverlay extends Pane {

    private final Canvas canvas;
    private boolean enabled = true;

    public CrtOverlay() {
        canvas = new Canvas();
        setMouseTransparent(true);
        setPickOnBounds(false);

        // Cache the overlay — it only redraws on resize, not per-frame
        setCache(true);
        setCacheHint(CacheHint.SPEED);

        getChildren().add(canvas);

        // Bind canvas size to this pane
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        // Redraw whenever size changes
        canvas.widthProperty().addListener((obs, o, n) -> redraw());
        canvas.heightProperty().addListener((obs, o, n) -> redraw());
    }

    /** Enable or disable the CRT overlay rendering. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        } else {
            redraw();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void redraw() {
        if (!enabled) return;

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        drawScanlines(gc, w, h);
        drawVignette(gc, w, h);
    }

    /** Horizontal semi-transparent lines every 2px — classic CRT scanline look.
     *  Uses fillRect instead of strokeLine for better GPU batching. */
    private void drawScanlines(GraphicsContext gc, double w, double h) {
        gc.setFill(Color.rgb(0, 0, 0, 0.18));
        for (double y = 0; y < h; y += 2) {
            gc.fillRect(0, y, w, 1.0);
        }
    }

    /** Radial gradient darkening the edges — classic CRT vignette. */
    private void drawVignette(GraphicsContext gc, double w, double h) {
        RadialGradient vignette = new RadialGradient(
            0, 0,           // focus angle, focus distance
            0.5, 0.5,       // center X/Y (proportional)
            0.75,           // radius (proportional)
            true,           // proportional
            CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(0, 0, 0, 0.0)),   // transparent center
            new Stop(0.7, Color.rgb(0, 0, 0, 0.0)),
            new Stop(1.0, Color.rgb(0, 0, 0, 0.55))   // dark edges
        );

        gc.setFill(vignette);
        gc.fillRect(0, 0, w, h);
    }
}
