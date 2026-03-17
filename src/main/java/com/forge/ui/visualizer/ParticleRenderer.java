package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Canvas-based particle system renderer with per-voice behaviors.
 *
 * <p>Maintains a pool of up to {@value #MAX_PARTICLES} particles. Different drum
 * voices spawn distinct particle types:
 * <ul>
 *   <li>Kick: expanding shockwave ring + central burst</li>
 *   <li>Snare: scattered sparks shooting outward with trails</li>
 *   <li>Hat: tiny upward-floating embers that fade slowly</li>
 *   <li>Perc: side-splash arcs</li>
 *   <li>Synth: horizontal flowing wave particles (VEGA_BLUE)</li>
 * </ul>
 *
 * <p>Features: color trails via semi-transparent background clear, glow halos,
 * size-over-lifetime, friction/drag, gravity, ambient center glow.
 */
public final class ParticleRenderer implements VisualizerRenderer {

    private static final int    MAX_PARTICLES   = 500;
    private static final double GRAVITY         = 0.04;
    private static final double DRAG            = 0.975;
    private static final double NANOS_PER_SEC   = 1_000_000_000.0;

    // Particle colors by voice type
    private static final Color COLOR_KICK   = Color.web("#ff2200");
    private static final Color COLOR_SNARE  = Color.web("#ffcc00");
    private static final Color COLOR_HAT    = Color.web("#ff8800");
    private static final Color COLOR_PERC   = Color.web("#ff6600");
    private static final Color COLOR_SYNTH  = Color.web("#44bbff");  // VEGA_BLUE

    private final List<Particle> active  = new ArrayList<>(MAX_PARTICLES);
    private final Random         rng     = new Random();

    private boolean prevBeat    = false;
    private boolean prevClock   = false;
    private long    lastFrameNs = 0;

    // Ambient glow pulse state
    private double glowPulse = 0.0;

    // Track recent centroid for synth detection
    private float prevCentroid = 0f;

    // -----------------------------------------------------------------------

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        long nowNs = System.nanoTime();
        double dt  = (lastFrameNs == 0) ? 0.016 : (nowNs - lastFrameNs) / NANOS_PER_SEC;
        dt = Math.min(dt, 0.05);
        lastFrameNs = nowNs;

        // Semi-transparent background for trail effect
        gc.setFill(Color.web("#050505", 0.75));
        gc.fillRect(0, 0, width, height);

        double cx = width  / 2.0;
        double cy = height / 2.0;

        // --- Read analysis data ---
        boolean beat  = bus.isBeatDetected();
        boolean clock = bus.isClockBeat();
        float   rms   = bus.getRmsEnergy();
        float   centroid = bus.getSpectralCentroid();
        int     step  = bus.getClockStep();

        // --- Ambient center glow (pulses with RMS energy) ---
        double targetGlow = rms * 2.0;
        glowPulse += (targetGlow - glowPulse) * 0.15;
        if (glowPulse > 0.01) {
            double glowR = Math.min(width, height) * 0.25 * (0.5 + glowPulse);
            RadialGradient ambientGlow = new RadialGradient(
                0, 0, cx, cy, glowR,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#ff4400", Math.min(0.12, glowPulse * 0.15))),
                new Stop(0.5, Color.web("#ff2200", Math.min(0.05, glowPulse * 0.06))),
                new Stop(1.0, Color.TRANSPARENT)
            );
            gc.setFill(ambientGlow);
            gc.fillRect(cx - glowR, cy - glowR, glowR * 2, glowR * 2);
        }

        // --- Spawn on beat (Kick: shockwave ring + burst) ---
        if (beat && !prevBeat) {
            spawnKickShockwave(cx, cy, rms);
            spawnKickBurst(cx, cy, rms);
        }

        // --- Spawn on clock tick (Snare: scattered sparks) ---
        if (clock && !prevClock) {
            // Snare on steps 4,12 (backbeat); hat on even steps; perc on odd steps
            if (step == 4 || step == 12) {
                spawnSnareSparks(cx, cy, rms);
            } else if (step % 2 == 0) {
                spawnHatEmbers(cx, cy, rms);
            } else {
                spawnPercSplash(cx, cy, rms);
            }
        }

        // --- Continuous ambient embers proportional to RMS ---
        if (rms > 0.05f && active.size() < MAX_PARTICLES) {
            int count = (int) (rms * 3);
            for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
                spawnHatEmber(
                    cx + (rng.nextDouble() - 0.5) * width * 0.3,
                    cy + (rng.nextDouble() - 0.5) * height * 0.3,
                    rms);
            }
        }

        // --- Synth response: horizontal wave particles when centroid is high ---
        if (centroid > 0.15f && rms > 0.03f) {
            int synthCount = (int) (centroid * rms * 8);
            for (int i = 0; i < synthCount && active.size() < MAX_PARTICLES; i++) {
                spawnSynthWave(width, height, centroid, rms);
            }
        }
        prevCentroid = centroid;

        prevBeat  = beat;
        prevClock = clock;

        // --- Update and draw particles ---
        Iterator<Particle> it = active.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(dt);
            if (p.isDead()) {
                it.remove();
                continue;
            }

            double lifeRatio = p.life / p.maxLife;
            double alpha = lifeRatio * lifeRatio; // quadratic fade
            double size = p.size * (0.3 + 0.7 * lifeRatio); // shrink as they age

            if (size < 0.3) continue;

            Color c = p.color.deriveColor(0, 1, 1, alpha);

            if (p.type == ParticleType.RING) {
                // Expanding ring
                double ringRadius = p.ringRadius;
                gc.setStroke(c);
                gc.setLineWidth(Math.max(1.0, 3.0 * lifeRatio));
                gc.setEffect(null);
                gc.strokeOval(p.x - ringRadius, p.y - ringRadius,
                              ringRadius * 2, ringRadius * 2);
            } else {
                // Glow halo: draw at 3x size with very low opacity
                double glowAlpha = alpha * 0.15;
                if (glowAlpha > 0.005) {
                    double glowSize = size * 3.0;
                    Color glowColor = p.color.deriveColor(0, 1, 1, glowAlpha);
                    gc.setFill(glowColor);
                    gc.fillOval(p.x - glowSize, p.y - glowSize,
                                glowSize * 2, glowSize * 2);
                }

                // Main particle
                gc.setFill(c);
                gc.fillOval(p.x - size, p.y - size, size * 2, size * 2);
            }
        }

        gc.setEffect(null);
    }

    // -----------------------------------------------------------------------
    // Spawn helpers — per drum voice
    // -----------------------------------------------------------------------

    /** Kick: expanding ring shockwave */
    private void spawnKickShockwave(double cx, double cy, float rms) {
        if (active.size() >= MAX_PARTICLES) return;
        Particle p = new Particle();
        p.type   = ParticleType.RING;
        p.x      = cx;
        p.y      = cy;
        p.vx     = 0;
        p.vy     = 0;
        p.life   = 0.6 + rms * 0.4;
        p.maxLife = p.life;
        p.size   = 2;
        p.ringRadius = 5;
        p.ringGrowth = 120 + rms * 200;
        p.color  = COLOR_KICK;
        active.add(p);
    }

    /** Kick: central burst of large particles */
    private void spawnKickBurst(double cx, double cy, float rms) {
        int count = 20 + (int)(rms * 25);
        for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double vel   = (3.0 + rng.nextDouble() * 4.0) * (0.5 + rms);
            Particle p   = new Particle();
            p.type   = ParticleType.NORMAL;
            p.x      = cx + (rng.nextDouble() - 0.5) * 10;
            p.y      = cy + (rng.nextDouble() - 0.5) * 10;
            p.vx     = Math.cos(angle) * vel;
            p.vy     = Math.sin(angle) * vel;
            p.life   = 0.5 + rng.nextDouble() * 0.6;
            p.maxLife = p.life;
            p.size   = 5 + rng.nextDouble() * 8;
            p.color  = COLOR_KICK.interpolate(Color.web("#ff6600"), rng.nextDouble() * 0.3);
            active.add(p);
        }
    }

    /** Snare: scattered sparks shooting outward with trails */
    private void spawnSnareSparks(double cx, double cy, float rms) {
        int count = 30 + (int)(rms * 20);
        for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double vel   = (5.0 + rng.nextDouble() * 6.0) * (0.4 + rms);
            Particle p   = new Particle();
            p.type   = ParticleType.NORMAL;
            p.x      = cx + (rng.nextDouble() - 0.5) * 20;
            p.y      = cy + (rng.nextDouble() - 0.5) * 20;
            p.vx     = Math.cos(angle) * vel;
            p.vy     = Math.sin(angle) * vel;
            p.life   = 0.3 + rng.nextDouble() * 0.5;
            p.maxLife = p.life;
            p.size   = 2 + rng.nextDouble() * 3;
            p.color  = COLOR_SNARE.interpolate(Color.WHITE, rng.nextDouble() * 0.4);
            active.add(p);
        }
    }

    /** Hat: tiny upward-floating embers */
    private void spawnHatEmbers(double cx, double cy, float rms) {
        int count = 8 + (int)(rms * 10);
        for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
            spawnHatEmber(
                cx + (rng.nextDouble() - 0.5) * 60,
                cy + (rng.nextDouble() - 0.5) * 30,
                rms);
        }
    }

    /** Single hat ember */
    private void spawnHatEmber(double x, double y, float rms) {
        Particle p = new Particle();
        p.type   = ParticleType.NORMAL;
        p.x      = x;
        p.y      = y;
        p.vx     = (rng.nextDouble() - 0.5) * 1.0;
        p.vy     = -(0.8 + rng.nextDouble() * 2.5) * (0.5 + rms);
        p.life   = 0.8 + rng.nextDouble() * 1.0;
        p.maxLife = p.life;
        p.size   = 1.5 + rng.nextDouble() * 2.0;
        p.color  = COLOR_HAT.interpolate(COLOR_SNARE, rng.nextDouble() * 0.3);
        p.gravityMult = -0.3; // float upward
        active.add(p);
    }

    /** Perc: side-splash arcs */
    private void spawnPercSplash(double cx, double cy, float rms) {
        int count = 15 + (int)(rms * 10);
        boolean goLeft = rng.nextBoolean();
        for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
            // Splash to one side with arc
            double baseAngle = goLeft ? Math.PI * 0.7 : Math.PI * 0.3;
            double spread = (rng.nextDouble() - 0.5) * Math.PI * 0.5;
            double angle = baseAngle + spread;
            double vel   = (3.0 + rng.nextDouble() * 3.0) * (0.4 + rms);
            Particle p   = new Particle();
            p.type   = ParticleType.NORMAL;
            p.x      = cx;
            p.y      = cy;
            p.vx     = Math.cos(angle) * vel;
            p.vy     = Math.sin(angle) * vel - 2.0; // arc upward
            p.life   = 0.4 + rng.nextDouble() * 0.4;
            p.maxLife = p.life;
            p.size   = 2 + rng.nextDouble() * 3;
            p.color  = COLOR_PERC;
            active.add(p);
        }
    }

    /** Synth: horizontal flowing wave particles */
    private void spawnSynthWave(double width, double height, float centroid, float rms) {
        Particle p = new Particle();
        p.type   = ParticleType.NORMAL;
        // Spawn from left or right edge
        boolean fromLeft = rng.nextBoolean();
        p.x      = fromLeft ? -5 : width + 5;
        p.y      = height * (0.3 + rng.nextDouble() * 0.4);
        p.vx     = (fromLeft ? 1 : -1) * (1.5 + rng.nextDouble() * 3.0);
        p.vy     = (rng.nextDouble() - 0.5) * 0.5;
        p.life   = 1.5 + rng.nextDouble() * 2.0;
        p.maxLife = p.life;
        p.size   = 2 + rng.nextDouble() * 3;
        p.color  = COLOR_SYNTH.interpolate(Color.web("#88ccee"), rng.nextDouble() * 0.5);
        p.gravityMult = 0.0; // no gravity for synth particles
        p.dragMult = 0.998;  // very low drag — drift smoothly
        active.add(p);
    }

    // -----------------------------------------------------------------------
    // Particle types
    // -----------------------------------------------------------------------

    private enum ParticleType { NORMAL, RING }

    // -----------------------------------------------------------------------
    // Particle data class
    // -----------------------------------------------------------------------

    private static final class Particle {
        ParticleType type = ParticleType.NORMAL;
        double x, y;
        double vx, vy;
        double life, maxLife;
        double size;
        Color  color;
        double gravityMult = 1.0;   // 1.0 = normal gravity, 0 = none, negative = float up
        double dragMult = DRAG;     // per-particle drag override
        // Ring-specific
        double ringRadius;
        double ringGrowth;          // pixels per second

        void update(double dt) {
            if (type == ParticleType.RING) {
                ringRadius += ringGrowth * dt;
            } else {
                vy   += GRAVITY * gravityMult;
                vx   *= dragMult;
                vy   *= dragMult;
                x    += vx;
                y    += vy;
            }
            life -= dt;
        }

        boolean isDead() {
            return life <= 0;
        }
    }
}
