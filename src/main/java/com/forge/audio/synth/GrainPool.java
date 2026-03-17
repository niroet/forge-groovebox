package com.forge.audio.synth;

/**
 * Pre-allocated pool of 64 {@link Grain} objects for granular synthesis.
 *
 * <p>Avoids allocation on the audio thread by reusing grain instances. Callers
 * {@link #activate} a grain to start it and then process all active grains
 * together via {@link #processSample}.
 */
public class GrainPool {

    /** Maximum number of simultaneously active grains. */
    public static final int POOL_SIZE = 64;

    // -------------------------------------------------------------------------
    // Inner class: a single grain
    // -------------------------------------------------------------------------

    /**
     * One grain in the pool.
     *
     * <p>Fields are package-private so {@link GrainPool} can manipulate them directly
     * without accessor overhead on the audio thread.
     */
    static final class Grain {
        /** Position in source buffer (0.0–1.0). */
        float position;

        /** Playback rate / pitch factor (1.0 = original pitch). */
        float playbackRate;

        /** Peak amplitude of this grain. */
        float amplitude;

        /** Stereo pan (−1 to 1). Reserved; voice is currently mono. */
        float pan;

        /** Current sample index within the grain window. */
        int phase;

        /** Total length of this grain in samples. */
        int grainSamples;

        /** Whether this grain is currently playing. */
        boolean active;
    }

    // -------------------------------------------------------------------------
    // Pool storage
    // -------------------------------------------------------------------------

    private final Grain[] grains = new Grain[POOL_SIZE];

    public GrainPool() {
        for (int i = 0; i < POOL_SIZE; i++) {
            grains[i] = new Grain();
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Find an inactive grain, configure it, mark it active, and return it.
     *
     * @param position     normalised position in source buffer (0.0–1.0)
     * @param rate         playback rate factor
     * @param amplitude    peak amplitude
     * @param grainSamples total samples in the grain envelope
     * @return the activated grain, or {@code null} if the pool is exhausted
     */
    public Grain activate(float position, float rate, float amplitude, int grainSamples) {
        for (Grain g : grains) {
            if (!g.active) {
                g.position     = position;
                g.playbackRate = rate;
                g.amplitude    = amplitude;
                g.pan          = 0.0f;
                g.phase        = 0;
                g.grainSamples = Math.max(1, grainSamples);
                g.active       = true;
                return g;
            }
        }
        return null; // pool full — caller should reduce density
    }

    /**
     * Sum the contribution of all active grains for the current sample, then
     * advance every grain's phase by one step.
     *
     * <p>Grains whose phase reaches {@code grainSamples} are automatically deactivated.
     *
     * @param sourceBuffer the buffer that grains read from; must not be {@code null}
     * @return the mixed output sample for this frame
     */
    public float processSample(float[] sourceBuffer) {
        float out = 0.0f;
        final int bufLen = sourceBuffer.length;

        for (Grain g : grains) {
            if (!g.active) continue;

            // Hann window: 0.5 * (1 − cos(2π·phase / grainSamples))
            double windowPos = (double) g.phase / g.grainSamples;
            float  window    = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * windowPos)));

            // Map (position + phase-offset) → source buffer index
            double readPos  = g.position * bufLen + g.phase * g.playbackRate;
            // Wrap into buffer bounds
            int    readIdx  = (int) readPos % bufLen;
            if (readIdx < 0) readIdx += bufLen;

            out += sourceBuffer[readIdx] * window * g.amplitude;

            // Advance phase
            g.phase++;
            if (g.phase >= g.grainSamples) {
                g.active = false;
            }
        }

        return out;
    }
}
