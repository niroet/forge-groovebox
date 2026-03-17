package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import javafx.scene.canvas.GraphicsContext;

/**
 * Contract for a single visualizer rendering mode.
 *
 * <p>Implementations are called once per animation frame (targeting 60 fps) on the
 * JavaFX application thread. Each call must fully repaint the canvas area — it is
 * the renderer's responsibility to clear the background before drawing.
 */
public interface VisualizerRenderer {

    /**
     * Render one frame onto the provided {@link GraphicsContext}.
     *
     * @param gc     the canvas graphics context (canvas is {@code width} x {@code height})
     * @param width  current canvas width in pixels
     * @param height current canvas height in pixels
     * @param bus    shared analysis data from the audio thread
     */
    void render(GraphicsContext gc, double width, double height, AnalysisBus bus);

    /**
     * Called once when the renderer becomes active (or when the canvas is resized).
     * Implementors may use this to pre-allocate buffers that depend on canvas dimensions.
     *
     * <p>Default implementation does nothing.
     *
     * @param width  initial canvas width
     * @param height initial canvas height
     */
    default void init(double width, double height) {}

    /**
     * Called when the renderer is being replaced or the panel is shutting down.
     * Implementors should release any heavyweight resources (e.g. {@link javafx.scene.image.WritableImage}).
     *
     * <p>Default implementation does nothing.
     */
    default void dispose() {}
}
