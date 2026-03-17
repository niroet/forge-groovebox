package com.forge.ui.controls;

import com.forge.ui.theme.ForgeColors;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Rotary knob control.
 *
 * - 270° sweep: 7 o'clock (−135°) to 5 o'clock (+135°)
 * - Drag up = increase, drag down = decrease
 * - Shift+drag = fine control (1/10th speed)
 * - valueProperty for binding
 * - Configurable accent color and label
 */
public class ForgeKnob extends VBox {

    // ---- Geometry ----------------------------------------------------------
    private static final double KNOB_DIAMETER = 36.0;
    private static final double CANVAS_SIZE   = 42.0; // canvas bigger than knob for glow
    private static final double TOTAL_SWEEP_DEG = 270.0;
    // Start angle: 7 o'clock = 225° from 3 o'clock (JavaFX: 0° = 3 o'clock, CW positive)
    // In JavaFX canvas, 0° = east (3 o'clock), angles increase clockwise
    // 7 o'clock = 225° clockwise from 3 o'clock (i.e. start at 135° past 12 o'clock going CCW from top)
    // Easier: start = 90 + 135 = 225° (measuring from right, clockwise)
    private static final double START_ANGLE_DEG = 225.0; // 7 o'clock from east, clockwise

    // ---- Properties --------------------------------------------------------
    private final DoubleProperty value     = new SimpleDoubleProperty(0.5);
    private final DoubleProperty minValue  = new SimpleDoubleProperty(0.0);
    private final DoubleProperty maxValue  = new SimpleDoubleProperty(1.0);
    private final StringProperty labelText = new SimpleStringProperty("");

    private Color accentColor = ForgeColors.ARGENT_AMBER;

    // ---- Internal state ----------------------------------------------------
    private final Canvas canvas;
    private final Text   label;
    private double       dragStartY;
    private double       dragStartValue;

    // -----------------------------------------------------------------------

    public ForgeKnob() {
        this("", 0.0, 1.0, 0.5);
    }

    public ForgeKnob(String label, double min, double max, double initial) {
        super(2);
        setAlignment(Pos.CENTER);

        this.labelText.set(label);
        this.minValue.set(min);
        this.maxValue.set(max);
        this.value.set(clamp(initial, min, max));

        canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        canvas.setStyle("-fx-cursor: default;");

        this.label = new Text(label);
        this.label.setFont(Font.font("Monospace", FontWeight.NORMAL, 9));
        this.label.setFill(ForgeColors.TEXT_LABEL);

        getChildren().addAll(canvas, this.label);
        setPrefSize(CANVAS_SIZE, CANVAS_SIZE + 14);

        wireEvents();
        draw();

        // Redraw on value or property change
        value.addListener((obs, o, n) -> draw());
    }

    // ---- Public API --------------------------------------------------------

    public DoubleProperty valueProperty()    { return value; }
    public double getValue()                 { return value.get(); }
    public void setValue(double v)           { value.set(clamp(v, minValue.get(), maxValue.get())); }

    public void setAccentColor(Color c)      { this.accentColor = c; draw(); }
    public void setLabelText(String text)    { this.label.setText(text); }

    // ---- Mouse events ------------------------------------------------------

    private void wireEvents() {
        canvas.setOnMousePressed(e -> {
            dragStartY     = e.getSceneY();
            dragStartValue = getNormalized();
            canvas.requestFocus();
        });

        canvas.setOnMouseDragged(e -> {
            double delta  = dragStartY - e.getSceneY();   // up = positive
            double speed  = e.isShiftDown() ? 0.002 : 0.02;
            double newNorm = clamp(dragStartValue + delta * speed, 0.0, 1.0);
            value.set(minValue.get() + newNorm * (maxValue.get() - minValue.get()));
        });

        // Prominent glow on hover
        canvas.setOnMouseEntered(e -> {
            DropShadow hoverGlow = new DropShadow();
            hoverGlow.setColor(Color.color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0.8));
            hoverGlow.setRadius(14);
            hoverGlow.setSpread(0.3);
            canvas.setEffect(hoverGlow);
        });

        canvas.setOnMouseExited(e -> draw());
    }

    // ---- Drawing -----------------------------------------------------------

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w  = canvas.getWidth();
        double h  = canvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double r  = KNOB_DIAMETER / 2.0;

        gc.clearRect(0, 0, w, h);

        // --- Glow shadow
        DropShadow glow = new DropShadow();
        glow.setColor(Color.color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0.5));
        glow.setRadius(6);
        canvas.setEffect(glow);

        // --- Body: dark circle
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillOval(cx - r, cy - r, KNOB_DIAMETER, KNOB_DIAMETER);

        // --- Accent border arc (2px, accent color)
        gc.setStroke(accentColor);
        gc.setLineWidth(2.0);
        gc.strokeOval(cx - r, cy - r, KNOB_DIAMETER, KNOB_DIAMETER);

        // --- Inner ring subtle
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - r + 4, cy - r + 4, KNOB_DIAMETER - 8, KNOB_DIAMETER - 8);

        // --- Indicator line
        double normalized  = getNormalized();
        // Map normalized [0,1] to angle: startAngle + normalized * TOTAL_SWEEP
        // JavaFX canvas: atan2 angle where 0° = east, increasing CW
        // We use radians for Math.cos/sin
        double angleDeg    = START_ANGLE_DEG + normalized * TOTAL_SWEEP_DEG;
        double angleRad    = Math.toRadians(angleDeg);
        double lineR       = r - 4;
        double x2          = cx + lineR * Math.cos(angleRad);
        double y2          = cy + lineR * Math.sin(angleRad);

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeLine(cx, cy, x2, y2);

        // --- Center dot
        gc.setFill(Color.web("#333333"));
        gc.fillOval(cx - 2, cy - 2, 4, 4);
    }

    // ---- Helpers -----------------------------------------------------------

    private double getNormalized() {
        double range = maxValue.get() - minValue.get();
        if (range == 0) return 0;
        return clamp((value.get() - minValue.get()) / range, 0.0, 1.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
