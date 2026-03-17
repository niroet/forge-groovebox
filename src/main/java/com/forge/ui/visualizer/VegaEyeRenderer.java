package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.effect.DropShadow;

import java.util.ArrayList;
import java.util.List;

/**
 * VEGA-EYE visualizer.
 *
 * <p>Renders an animated "eye" metaphor for the VEGA AI system:
 * <ul>
 *   <li>Central iris drawn with a radial gradient; color shifts with spectral centroid.</li>
 *   <li>Pupil (inner dark circle) size scales with RMS energy (louder = bigger pupil).</li>
 *   <li>Concentric ring pulses spawn on beat and expand outward, fading as they grow.</li>
 *   <li>Subtle rotation animation for the iris texture.</li>
 * </ul>
 */
public final class VegaEyeRenderer implements VisualizerRenderer {

    private static final Color BG_COLOR         = Color.web("#020408");
    private static final Color IRIS_LOW_FREQ    = Color.web("#0044aa");   // deep blue
    private static final Color IRIS_HIGH_FREQ   = Color.web("#88ddff");   // bright cyan
    private static final Color PUPIL_COLOR      = Color.web("#000000");
    private static final Color RING_COLOR       = Color.web("#44bbff");

    private static final double BASE_IRIS_RATIO = 0.38;  // iris radius as fraction of min(w,h)
    private static final double MIN_PUPIL_RATIO = 0.06;  // pupil as fraction of min(w,h) when quiet
    private static final double MAX_PUPIL_RATIO = 0.22;  // pupil at full RMS

    private final DropShadow glow;
    private final List<Ring> rings = new ArrayList<>();

    private boolean prevBeat = false;
    private double  irisAngle = 0;

    public VegaEyeRenderer() {
        glow = new DropShadow();
        glow.setColor(Color.web("#44aaff"));
        glow.setRadius(20);
        glow.setSpread(0.15);
    }

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, width, height);

        double cx = width  / 2.0;
        double cy = height / 2.0;
        double minDim = Math.min(width, height);

        float rms      = bus.getRmsEnergy();
        float centroid = bus.getSpectralCentroid();
        boolean beat   = bus.isBeatDetected();

        // --- Spawn ring on beat ---
        if (beat && !prevBeat) {
            rings.add(new Ring(minDim * BASE_IRIS_RATIO * 0.9, 1.0));
        }
        prevBeat = beat;

        // --- Update and draw rings ---
        List<Ring> dead = new ArrayList<>();
        for (Ring ring : rings) {
            ring.radius  += minDim * 0.012;
            ring.opacity -= 0.022;
            if (ring.opacity <= 0) { dead.add(ring); continue; }

            double alpha = Math.max(0, ring.opacity);
            gc.setStroke(RING_COLOR.deriveColor(0, 1, 1, alpha));
            gc.setLineWidth(1.5);
            gc.strokeOval(cx - ring.radius, cy - ring.radius, ring.radius * 2, ring.radius * 2);
        }
        rings.removeAll(dead);

        // --- Iris ---
        double irisR = minDim * BASE_IRIS_RATIO;

        // Iris color: interpolate between low-freq (deep blue) and high-freq (bright cyan)
        Color irisInner = IRIS_LOW_FREQ.interpolate(IRIS_HIGH_FREQ, centroid);
        Color irisOuter = irisInner.darker().darker();

        // Slow rotation animation
        irisAngle += 0.3;

        // Outer glow ring
        gc.setEffect(glow);
        gc.setStroke(irisInner.deriveColor(0, 1, 1, 0.6 + rms * 0.4));
        gc.setLineWidth(2.5);
        gc.strokeOval(cx - irisR, cy - irisR, irisR * 2, irisR * 2);
        gc.setEffect(null);

        // Iris radial gradient fill
        RadialGradient irisFill = new RadialGradient(
            irisAngle, 0.3,
            cx, cy, irisR,
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, irisInner.brighter()),
            new Stop(0.5, irisInner),
            new Stop(0.8, irisOuter),
            new Stop(1.0, Color.BLACK)
        );
        gc.setFill(irisFill);
        gc.fillOval(cx - irisR, cy - irisR, irisR * 2, irisR * 2);

        // Iris texture lines (simulated stria)
        int striaCount = 24;
        gc.setStroke(irisInner.brighter().deriveColor(0, 1, 1, 0.2));
        gc.setLineWidth(0.5);
        for (int i = 0; i < striaCount; i++) {
            double angle = (2.0 * Math.PI * i / striaCount) + Math.toRadians(irisAngle);
            double x1 = cx + Math.cos(angle) * irisR * 0.25;
            double y1 = cy + Math.sin(angle) * irisR * 0.25;
            double x2 = cx + Math.cos(angle) * irisR * 0.92;
            double y2 = cy + Math.sin(angle) * irisR * 0.92;
            gc.strokeLine(x1, y1, x2, y2);
        }

        // --- Pupil ---
        double pupilR = minDim * (MIN_PUPIL_RATIO + rms * (MAX_PUPIL_RATIO - MIN_PUPIL_RATIO));
        pupilR = Math.min(pupilR, irisR * 0.75);

        gc.setFill(PUPIL_COLOR);
        gc.fillOval(cx - pupilR, cy - pupilR, pupilR * 2, pupilR * 2);

        // Pupil highlight
        double hiR = pupilR * 0.35;
        double hiX = cx + pupilR * 0.25;
        double hiY = cy - pupilR * 0.25;
        RadialGradient hiGrad = new RadialGradient(
            0, 0, hiX, hiY, hiR,
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#ffffff", 0.5)),
            new Stop(1.0, Color.web("#ffffff", 0.0))
        );
        gc.setFill(hiGrad);
        gc.fillOval(hiX - hiR, hiY - hiR, hiR * 2, hiR * 2);
    }

    // -----------------------------------------------------------------------

    private static final class Ring {
        double radius;
        double opacity;
        Ring(double r, double o) { this.radius = r; this.opacity = o; }
    }
}
