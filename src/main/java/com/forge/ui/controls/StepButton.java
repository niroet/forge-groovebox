package com.forge.ui.controls;

import com.forge.ui.theme.ForgeColors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * Toggle button for drum/synth step grids.
 *
 * States:
 *   OFF      — dark #111
 *   ON       — track color at velocity brightness
 *   PLAYING  — white flash overlay
 *   P_LOCKED — colored dot (parameter-lock indicator)
 *
 * Typical grid size: 20×20.
 */
public class StepButton extends Region {

    public enum ButtonState { OFF, ON, PLAYING, P_LOCKED }

    // ---- Properties --------------------------------------------------------
    private final BooleanProperty active   = new SimpleBooleanProperty(false);
    private final DoubleProperty  velocity = new SimpleDoubleProperty(0.75);
    private final ObjectProperty<Color> trackColor =
            new SimpleObjectProperty<>(ForgeColors.ARGENT_AMBER);
    private final ObjectProperty<ButtonState> state =
            new SimpleObjectProperty<>(ButtonState.OFF);

    private final Canvas canvas;
    private static final double ARC = 3.0; // corner radius

    // -----------------------------------------------------------------------

    public StepButton() {
        // Fixed-size canvas avoids resize feedback loops that cause layout thrashing
        canvas = new Canvas(26, 22);
        getChildren().add(canvas);
        setPrefSize(26, 22);
        setMinSize(26, 22);
        setMaxSize(26, 22);

        // Redraw on any relevant change
        active    .addListener((obs, o, n) -> { state.set(n ? ButtonState.ON : ButtonState.OFF); draw(); });
        velocity  .addListener((obs, o, n) -> draw());
        trackColor.addListener((obs, o, n) -> draw());
        state     .addListener((obs, o, n) -> draw());

        // Click toggles active
        setOnMouseClicked(e -> active.set(!active.get()));

        draw();
    }

    // ---- Public API --------------------------------------------------------

    public BooleanProperty activeProperty()              { return active; }
    public boolean isActive()                            { return active.get(); }
    public void setActive(boolean v)                     { active.set(v); }

    public DoubleProperty velocityProperty()             { return velocity; }
    public double getVelocity()                          { return velocity.get(); }
    public void setVelocity(double v)                    { velocity.set(v); }

    public ObjectProperty<Color> trackColorProperty()    { return trackColor; }
    public void setTrackColor(Color c)                   { trackColor.set(c); }

    public ObjectProperty<ButtonState> stateProperty()  { return state; }
    public ButtonState getState()                        { return state.get(); }
    public void setState(ButtonState s)                  { state.set(s); }

    // ---- Drawing -----------------------------------------------------------

    private void draw() {
        double w  = canvas.getWidth();
        double h  = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        ButtonState s = state.get();

        switch (s) {
            case OFF -> {
                gc.setFill(Color.web("#111111"));
                gc.fillRoundRect(0, 0, w, h, ARC, ARC);
                gc.setStroke(Color.web("#2a2a2a"));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, ARC, ARC);
            }
            case ON -> {
                // Blend track color at velocity brightness
                Color base  = trackColor.get();
                double vel  = velocity.get();
                Color fill  = base.deriveColor(0, 1.0, vel * 0.7 + 0.3, 1.0);
                gc.setFill(fill);
                gc.fillRoundRect(0, 0, w, h, ARC, ARC);
                gc.setStroke(base.brighter());
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, ARC, ARC);
            }
            case PLAYING -> {
                // White flash: draw ON color underneath, white overlay on top
                Color base = trackColor.get();
                Color fill = base.deriveColor(0, 1.0, velocity.get() * 0.7 + 0.3, 1.0);
                gc.setFill(fill);
                gc.fillRoundRect(0, 0, w, h, ARC, ARC);
                // White translucent overlay
                gc.setFill(Color.rgb(255, 255, 255, 0.7));
                gc.fillRoundRect(0, 0, w, h, ARC, ARC);
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, ARC, ARC);
            }
            case P_LOCKED -> {
                // Dark background with small colored dot (parameter-lock indicator)
                gc.setFill(Color.web("#111111"));
                gc.fillRoundRect(0, 0, w, h, ARC, ARC);
                gc.setStroke(Color.web("#2a2a2a"));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, ARC, ARC);
                // Dot in upper-right corner
                gc.setFill(trackColor.get());
                double dotR = 3.0;
                gc.fillOval(w - dotR * 2 - 2, 2, dotR * 2, dotR * 2);
            }
        }
    }
}
