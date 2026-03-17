package com.forge.ui.visualizer;

import com.forge.audio.engine.AnalysisBus;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Canvas-based particle system renderer.
 *
 * <p>Maintains a pool of up to {@value #MAX_PARTICLES} particles. When a beat
 * is detected (or the sequencer clock fires), a burst of particles is spawned
 * from the center. Particles are affected by gravity and fade out over their
 * lifetime.
 *
 * <p>No FXGL dependency: all rendering uses standard JavaFX {@link GraphicsContext}.
 */
public final class ParticleRenderer implements VisualizerRenderer {

    private static final int    MAX_PARTICLES   = 200;
    private static final double GRAVITY         = 0.06;
    private static final double DRAG            = 0.985;
    private static final double NANOS_PER_SEC   = 1_000_000_000.0;

    // Particle colors by "type"
    private static final Color COLOR_KICK   = Color.web("#ff2200");   // big red
    private static final Color COLOR_SNARE  = Color.web("#ffcc00");   // yellow sparks
    private static final Color COLOR_HAT    = Color.web("#ff8800");   // amber embers
    private static final Color BG_COLOR     = Color.web("#050505");

    private final List<Particle> active  = new ArrayList<>(MAX_PARTICLES);
    private final Random         rng     = new Random();
    private final DropShadow     glow;

    private boolean prevBeat    = false;
    private boolean prevClock   = false;
    private long    lastFrameNs = 0;

    public ParticleRenderer() {
        glow = new DropShadow();
        glow.setColor(Color.web("#ff4400"));
        glow.setRadius(10);
        glow.setSpread(0.4);
    }

    // -----------------------------------------------------------------------

    @Override
    public void render(GraphicsContext gc, double width, double height, AnalysisBus bus) {
        long nowNs = System.nanoTime();
        double dt  = (lastFrameNs == 0) ? 0.016 : (nowNs - lastFrameNs) / NANOS_PER_SEC;
        dt = Math.min(dt, 0.05);   // cap at 50ms to avoid spiral on long pauses
        lastFrameNs = nowNs;

        // Background (slightly transparent to create trails)
        gc.setFill(Color.web("#050505", 0.85));
        gc.fillRect(0, 0, width, height);

        double cx = width  / 2.0;
        double cy = height / 2.0;

        // --- Spawn on beat ---
        boolean beat  = bus.isBeatDetected();
        boolean clock = bus.isClockBeat();
        float   rms   = bus.getRmsEnergy();

        if (beat && !prevBeat) {
            spawnBurst(cx, cy, rms, COLOR_KICK, 40, 4.0);
        }
        if (clock && !prevClock) {
            spawnBurst(cx, cy, Math.min(rms, 0.5f), COLOR_SNARE, 20, 3.0);
        }
        // Continuous ambient embers proportional to RMS
        if (rms > 0.05f && active.size() < MAX_PARTICLES) {
            int count = (int) (rms * 5);
            for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
                spawnEmber(cx + (rng.nextDouble() - 0.5) * width * 0.3,
                           cy + (rng.nextDouble() - 0.5) * height * 0.3,
                           rms, COLOR_HAT);
            }
        }

        prevBeat  = beat;
        prevClock = clock;

        // --- Update and draw particles ---
        gc.setEffect(glow);
        List<Particle> dead = new ArrayList<>();

        for (Particle p : active) {
            p.update(dt);
            if (p.isDead()) {
                dead.add(p);
                continue;
            }

            double alpha = p.life / p.maxLife;
            Color c = p.color.deriveColor(0, 1, 1, alpha * alpha);
            double r = p.size * alpha;
            if (r < 0.5) continue;

            // Radial gradient for soft glow appearance
            RadialGradient rg = new RadialGradient(
                0, 0, p.x, p.y, r,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, c),
                new Stop(1.0, c.deriveColor(0, 1, 1, 0))
            );
            gc.setFill(rg);
            gc.fillOval(p.x - r, p.y - r, r * 2, r * 2);
        }

        gc.setEffect(null);
        active.removeAll(dead);
    }

    // -----------------------------------------------------------------------
    // Spawn helpers
    // -----------------------------------------------------------------------

    private void spawnBurst(double cx, double cy, float rms, Color color, int count, double speed) {
        for (int i = 0; i < count && active.size() < MAX_PARTICLES; i++) {
            double angle  = rng.nextDouble() * Math.PI * 2;
            double vel    = speed * (0.5 + rng.nextDouble()) * (0.5 + rms);
            Particle p    = new Particle();
            p.x      = cx + (rng.nextDouble() - 0.5) * 20;
            p.y      = cy + (rng.nextDouble() - 0.5) * 20;
            p.vx     = Math.cos(angle) * vel;
            p.vy     = Math.sin(angle) * vel - speed * 0.5;  // slight upward bias
            p.life   = 0.8 + rng.nextDouble() * 0.8;
            p.maxLife = p.life;
            p.size   = 4 + rng.nextDouble() * 6;
            p.color  = color;
            active.add(p);
        }
    }

    private void spawnEmber(double x, double y, float rms, Color color) {
        Particle p = new Particle();
        p.x      = x;
        p.y      = y;
        p.vx     = (rng.nextDouble() - 0.5) * 1.5;
        p.vy     = -(0.5 + rng.nextDouble() * 2.0) * rms;
        p.life   = 0.3 + rng.nextDouble() * 0.5;
        p.maxLife = p.life;
        p.size   = 2 + rng.nextDouble() * 3;
        p.color  = color;
        active.add(p);
    }

    // -----------------------------------------------------------------------
    // Particle data class
    // -----------------------------------------------------------------------

    private static final class Particle {
        double x, y;
        double vx, vy;
        double life, maxLife;
        double size;
        Color  color;

        void update(double dt) {
            vy   += GRAVITY;
            vx   *= DRAG;
            vy   *= DRAG;
            x    += vx;
            y    += vy;
            life -= dt;
        }

        boolean isDead() {
            return life <= 0;
        }
    }
}
