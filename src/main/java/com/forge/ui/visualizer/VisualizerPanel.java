package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import com.forge.model.VisualizerMode;
import com.forge.ui.theme.ForgeColors;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Container that hosts the active visualizer renderer.
 *
 * <p>Layout:
 * <pre>
 *   VBox (VisualizerPanel)
 *   +-- HBox  (mode tab bar: F1…F6 labels)
 *   +-- Canvas (fills remaining vertical space, resizes automatically)
 * </pre>
 *
 * <p>An {@link AnimationTimer} running at 60 fps calls
 * {@link VisualizerRenderer#render render(gc, w, h, analysisBus)} on the active renderer.
 * Switching modes via {@link #setMode} disposes the old renderer and initialises the new one.
 */
public final class VisualizerPanel extends VBox {

    // ---- Mode tab metadata --------------------------------------------------
    private static final VisualizerMode[] MODES = VisualizerMode.values();
    private static final String[] MODE_LABELS = {
        "F1:SPECTRUM", "F2:SCOPE", "F3:SPECTRO", "F4:TERRAIN", "F5:PARTICLES", "F6:VEGA EYE"
    };

    // ---- Frame throttle for heavier modes (30fps cap) -------------------------
    /** Minimum nanoseconds between frames for heavy modes (particles, terrain). */
    private static final long HEAVY_FRAME_NS = 33_333_333L; // ~30fps
    private long lastRenderNs = 0;

    // ---- State --------------------------------------------------------------
    private final Canvas            canvas;
    private       VisualizerRenderer activeRenderer;
    private       AnalysisBus        analysisBus;
    private       AnimationTimer     timer;
    private       VisualizerMode     currentMode = VisualizerMode.SPECTRUM;

    // Tab labels (kept for highlight updates)
    private final Label[] tabLabels = new Label[MODES.length];

    // =========================================================================

    public VisualizerPanel() {
        setStyle(
            "-fx-background-color: " + ForgeColors.hex(ForgeColors.BG_VOID) + ";" +
            "-fx-border-color: transparent transparent #1a1a1a transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // Build the mode tab bar
        HBox tabBar = buildTabBar();

        // Canvas — manually sized in layoutChildren() to avoid binding feedback loops.
        // Canvas is NOT a resizable JavaFX node, so binding its size to the parent
        // creates a layout invalidation cycle (canvas resize → parent relayout → canvas resize...).
        canvas = new Canvas(400, 200); // initial size, will be updated in layoutChildren()

        getChildren().addAll(tabBar, canvas);

        // Default renderer
        activeRenderer = createRenderer(currentMode);

        // Animation timer with frame-rate throttling for heavier modes
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (analysisBus == null) return;
                // Throttle heavy modes (terrain, particles) to ~30fps for integrated GPUs
                if (isHeavyMode() && (now - lastRenderNs) < HEAVY_FRAME_NS) return;
                lastRenderNs = now;
                double w = canvas.getWidth();
                double h = canvas.getHeight();
                if (w < 1 || h < 1) return;
                activeRenderer.render(canvas.getGraphicsContext2D(), w, h, analysisBus);
            }
        };
    }

    // =========================================================================
    // Layout — manually size the canvas to avoid binding feedback loops
    // =========================================================================

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        double w = getWidth();
        // Subtract tab bar height from total
        double tabH = getChildren().get(0).prefHeight(w);
        double canvasH = getHeight() - tabH;
        if (canvasH < 0) canvasH = 0;
        if (w != canvas.getWidth() || canvasH != canvas.getHeight()) {
            canvas.setWidth(w);
            canvas.setHeight(canvasH);
            canvas.setLayoutY(tabH);
            onCanvasResize();
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Switch to the given visualizer mode. Disposes the current renderer. */
    public void setMode(VisualizerMode mode) {
        if (mode == currentMode) return;
        activeRenderer.dispose();
        currentMode    = mode;
        activeRenderer = createRenderer(mode);
        activeRenderer.init(canvas.getWidth(), canvas.getHeight());
        updateTabHighlight();
    }

    /** Wire in the {@link AnalysisBus} provided by the audio subsystem. */
    public void setAnalysisBus(AnalysisBus bus) {
        this.analysisBus = bus;
    }

    /** Start the 60-fps animation timer. */
    public void start() {
        timer.start();
    }

    /** Stop the animation timer (e.g. when the window is minimised). */
    public void stop() {
        timer.stop();
    }

    /** @return the currently active visualizer mode */
    public VisualizerMode getCurrentMode() {
        return currentMode;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private HBox buildTabBar() {
        HBox bar = new HBox(0);
        bar.setStyle(
            "-fx-background-color: #0d0d0d;" +
            "-fx-border-color: transparent transparent #1a1a1a transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 0;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefHeight(22);
        bar.setMinHeight(22);
        bar.setMaxHeight(22);

        for (int i = 0; i < MODES.length; i++) {
            final int idx = i;
            Label lbl = new Label(MODE_LABELS[i]);
            lbl.setFont(Font.font("Monospace", FontWeight.NORMAL, 9));
            lbl.setPadding(new Insets(3, 8, 3, 8));
            lbl.setStyle("-fx-cursor: hand;");
            tabLabels[i] = lbl;

            final VisualizerMode target = MODES[i];
            lbl.setOnMouseClicked(e -> setMode(target));
            lbl.setOnMouseEntered(e -> {
                if (target != currentMode) {
                    lbl.setTextFill(Color.web("#ff8800"));
                    lbl.setStyle("-fx-cursor: hand; -fx-background-color: #1a0800;");
                }
            });
            lbl.setOnMouseExited(e -> {
                if (target != currentMode) applyInactiveTabStyle(lbl);
            });

            // Add a thin separator between tabs
            if (i > 0) {
                Region sep = new Region();
                sep.setPrefWidth(1);
                sep.setMinWidth(1);
                sep.setMaxWidth(1);
                sep.setPrefHeight(22);
                sep.setStyle("-fx-background-color: #1a1a1a;");
                bar.getChildren().add(sep);
            }

            bar.getChildren().add(lbl);
        }

        updateTabHighlight();
        return bar;
    }

    private void applyInactiveTabStyle(Label lbl) {
        lbl.setTextFill(Color.web("#555555"));
        lbl.setStyle("-fx-cursor: hand; -fx-background-color: transparent;");
    }

    private void updateTabHighlight() {
        for (int i = 0; i < MODES.length; i++) {
            Label lbl = tabLabels[i];
            if (lbl == null) continue;
            if (MODES[i] == currentMode) {
                lbl.setTextFill(Color.web("#ff6600"));
                lbl.setStyle("-fx-cursor: hand; -fx-background-color: #1a0500;");
            } else {
                applyInactiveTabStyle(lbl);
            }
        }
    }

    private void onCanvasResize() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w > 0 && h > 0 && activeRenderer != null) {
            activeRenderer.init(w, h);
        }
    }

    /** Whether the current mode is GPU-heavy and should be throttled to ~30fps. */
    private boolean isHeavyMode() {
        return currentMode == VisualizerMode.TERRAIN
            || currentMode == VisualizerMode.PARTICLES;
    }

    private static VisualizerRenderer createRenderer(VisualizerMode mode) {
        return switch (mode) {
            case SPECTRUM      -> new SpectrumRenderer();
            case OSCILLOSCOPE  -> new OscilloscopeRenderer();
            case SPECTROGRAM   -> new SpectrogramRenderer();
            case TERRAIN       -> new TerrainRenderer();
            case PARTICLES     -> new ParticleRenderer();
            case VEGA_EYE      -> new VegaEyeRenderer();
        };
    }
}
