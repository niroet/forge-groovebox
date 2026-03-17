package com.forge.ui.panels;

import com.forge.ui.theme.ForgeColors;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * VEGA circular eye widget with mode toggle buttons.
 *
 * <p>Draws a stylised iris on Canvas with radial gradient,
 * outer glow ring, and inner dark pupil. Below the eye:
 * status text and [ASSIST] / [DIVINE] mode toggle buttons.
 */
public class VegaAvatar extends VBox {

    private static final double EYE_SIZE = 60.0;  // canvas width/height

    // Blue palette (ASSIST mode)
    private static final Color IRIS_CENTER_BLUE  = Color.web("#88ddff");
    private static final Color IRIS_EDGE_BLUE    = Color.web("#0066aa");
    private static final Color RING_BLUE         = ForgeColors.VEGA_BLUE;   // #44bbff

    // Gold palette (DIVINE mode)
    private static final Color IRIS_CENTER_GOLD  = Color.web("#ffee88");
    private static final Color IRIS_EDGE_GOLD    = Color.web("#aa6600");
    private static final Color RING_GOLD         = ForgeColors.DIVINE_GOLD; // #ffdd44

    private final BooleanProperty divineMode = new SimpleBooleanProperty(false);

    private final Canvas eyeCanvas;

    // -------------------------------------------------------------------------

    public VegaAvatar() {
        super(4);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(6, 4, 6, 4));

        // Eye canvas
        eyeCanvas = new Canvas(EYE_SIZE, EYE_SIZE);
        drawEye(false);

        // Status text
        Text statusText = new Text("STATUS: OPERATIONAL");
        statusText.setFont(Font.font("Monospace", FontWeight.NORMAL, 9));
        statusText.setFill(Color.web("#2a5570"));

        // Mode buttons
        HBox modeBar = buildModeBar();

        getChildren().addAll(eyeCanvas, statusText, modeBar);

        // Redraw when mode changes
        divineMode.addListener((obs, oldVal, newVal) -> drawEye(newVal));
    }

    // -------------------------------------------------------------------------
    // Eye rendering
    // -------------------------------------------------------------------------

    private void drawEye(boolean divine) {
        GraphicsContext gc = eyeCanvas.getGraphicsContext2D();
        double w = eyeCanvas.getWidth();
        double h = eyeCanvas.getHeight();

        // Background
        gc.setFill(Color.web("#050508"));
        gc.fillRect(0, 0, w, h);

        double cx = w / 2.0;
        double cy = h / 2.0;
        double irisR = w * 0.42;   // ~25px for 60px canvas
        double pupilR = irisR * 0.38;

        Color irisCenter = divine ? IRIS_CENTER_GOLD : IRIS_CENTER_BLUE;
        Color irisEdge   = divine ? IRIS_EDGE_GOLD   : IRIS_EDGE_BLUE;
        Color ringColor  = divine ? RING_GOLD         : RING_BLUE;

        // Outer glow ring
        DropShadow glow = new DropShadow();
        glow.setColor(ringColor.deriveColor(0, 1, 1, 0.8));
        glow.setRadius(8);
        glow.setSpread(0.3);
        gc.setEffect(glow);
        gc.setStroke(ringColor);
        gc.setLineWidth(1.8);
        gc.strokeOval(cx - irisR, cy - irisR, irisR * 2, irisR * 2);
        gc.setEffect(null);

        // Iris fill with radial gradient
        RadialGradient irisFill = new RadialGradient(
            0, 0,
            cx, cy, irisR,
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, irisCenter),
            new Stop(0.65, irisEdge),
            new Stop(1.0, Color.BLACK)
        );
        gc.setFill(irisFill);
        gc.fillOval(cx - irisR, cy - irisR, irisR * 2, irisR * 2);

        // Subtle iris stria lines
        int striaCount = 18;
        gc.setStroke(irisCenter.deriveColor(0, 1, 1, 0.18));
        gc.setLineWidth(0.5);
        for (int i = 0; i < striaCount; i++) {
            double angle = (2.0 * Math.PI * i / striaCount);
            double x1 = cx + Math.cos(angle) * irisR * 0.22;
            double y1 = cy + Math.sin(angle) * irisR * 0.22;
            double x2 = cx + Math.cos(angle) * irisR * 0.90;
            double y2 = cy + Math.sin(angle) * irisR * 0.90;
            gc.strokeLine(x1, y1, x2, y2);
        }

        // Dark pupil
        gc.setFill(Color.web("#000000"));
        gc.fillOval(cx - pupilR, cy - pupilR, pupilR * 2, pupilR * 2);

        // Pupil specular highlight
        double hiR = pupilR * 0.30;
        double hiX = cx + pupilR * 0.22;
        double hiY = cy - pupilR * 0.22;
        RadialGradient hiGrad = new RadialGradient(
            0, 0, hiX, hiY, hiR,
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#ffffff", 0.45)),
            new Stop(1.0, Color.TRANSPARENT)
        );
        gc.setFill(hiGrad);
        gc.fillOval(hiX - hiR, hiY - hiR, hiR * 2, hiR * 2);
    }

    // -------------------------------------------------------------------------
    // Mode buttons
    // -------------------------------------------------------------------------

    private HBox buildModeBar() {
        HBox bar = new HBox(4);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(3, 0, 0, 0));

        Button assistBtn = makeToggleButton("ASSIST");
        Button divineBtn = makeToggleButton("DIVINE");

        // Style both based on current mode
        Runnable refresh = () -> {
            boolean d = divineMode.get();
            applyButtonStyle(assistBtn, !d, false);
            applyButtonStyle(divineBtn, d, true);
        };

        assistBtn.setOnAction(e -> { divineMode.set(false); refresh.run(); });
        divineBtn.setOnAction(e -> { divineMode.set(true);  refresh.run(); });

        // Initial state
        applyButtonStyle(assistBtn, true, false);
        applyButtonStyle(divineBtn, false, true);

        bar.getChildren().addAll(assistBtn, divineBtn);
        return bar;
    }

    private Button makeToggleButton(String label) {
        Button btn = new Button(label);
        btn.setFont(Font.font("Monospace", FontWeight.BOLD, 8));
        btn.setPadding(new Insets(2, 6, 2, 6));
        btn.setStyle("-fx-cursor: hand;");
        return btn;
    }

    private void applyButtonStyle(Button btn, boolean active, boolean divine) {
        String bg, textCol, border;
        if (active) {
            if (divine) {
                bg      = "#3a2800";
                textCol = "#ffdd44";
                border  = "#ffdd44";
            } else {
                bg      = "#002233";
                textCol = "#44bbff";
                border  = "#44bbff";
            }
        } else {
            bg      = "#111111";
            textCol = "#444444";
            border  = "#333333";
        }
        btn.setStyle(String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 1;" +
            "-fx-background-radius: 1;" +
            "-fx-cursor: hand;" +
            "-fx-font-family: Monospace;" +
            "-fx-font-size: 8px;" +
            "-fx-font-weight: bold;",
            bg, textCol, border
        ));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns the property that tracks whether DIVINE mode is active. */
    public BooleanProperty divineModeProperty() {
        return divineMode;
    }

    /** Returns true when DIVINE mode is active. */
    public boolean isDivineMode() {
        return divineMode.get();
    }
}
