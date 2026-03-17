package com.forge.audio.sequencer;

import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.FillType;
import com.forge.model.Pattern;

/**
 * Generates 1-bar (16-step) drum fill patterns derived from a source pattern.
 *
 * <p>The source pattern is deep-copied so the original is never mutated.
 * The returned {@code DrumStep[][]} array is a standalone 4 × 16 grid that
 * can be swapped into a {@link Pattern} or applied directly to a sequencer.
 */
public class FillGenerator {

    // No mutable state — all methods are effectively static.

    /**
     * Generate a fill pattern for one bar.
     *
     * @param source the pattern to base the fill on
     * @param type   the fill style
     * @return a 4 × 16 {@link DrumStep} array with the fill applied
     */
    public static DrumStep[][] generateFill(Pattern source, FillType type) {
        DrumStep[][] fill = deepCopy(source.drumSteps);

        switch (type) {
            case SIMPLE -> {
                // Increase density on last 4 steps: add kick and snare hits
                for (int s = 12; s < 16; s++) {
                    fill[DrumTrack.KICK.ordinal()][s].active   = true;
                    fill[DrumTrack.KICK.ordinal()][s].velocity = 0.9;
                    fill[DrumTrack.SNARE.ordinal()][s].active   = true;
                    fill[DrumTrack.SNARE.ordinal()][s].velocity = 0.7;
                }
            }

            case ROLL -> {
                // Snare roll on last 4 steps (16th notes, all active, crescendo)
                for (int s = 12; s < 16; s++) {
                    fill[DrumTrack.SNARE.ordinal()][s].active   = true;
                    fill[DrumTrack.SNARE.ordinal()][s].velocity = 0.6 + (s - 12) * 0.1;
                }
            }

            case BUILDUP -> {
                // Density increase + hat opens + kick roll from step 8
                for (int s = 8; s < 16; s++) {
                    fill[DrumTrack.KICK.ordinal()][s].active   = true;
                    fill[DrumTrack.KICK.ordinal()][s].velocity = 0.5 + (s - 8) * 0.06;
                    fill[DrumTrack.HAT.ordinal()][s].active    = true;
                    fill[DrumTrack.HAT.ordinal()][s].velocity  = 0.8;
                }
                // Snare crescendo on last 4
                for (int s = 12; s < 16; s++) {
                    fill[DrumTrack.SNARE.ordinal()][s].active   = true;
                    fill[DrumTrack.SNARE.ordinal()][s].velocity = 0.7 + (s - 12) * 0.08;
                }
            }

            case BREAKDOWN -> {
                // Strip everything except the explosion at the end
                for (int t = 0; t < 4; t++) {
                    for (int s = 0; s < 16; s++) {
                        fill[t][s].active = false;
                    }
                }
                // Sparse kick on beats 1 and 3
                fill[DrumTrack.KICK.ordinal()][0].active   = true;
                fill[DrumTrack.KICK.ordinal()][0].velocity = 0.6;
                fill[DrumTrack.KICK.ordinal()][8].active   = true;
                fill[DrumTrack.KICK.ordinal()][8].velocity = 0.7;
                // Full explosion on last step
                for (int t = 0; t < 4; t++) {
                    fill[t][15].active   = true;
                    fill[t][15].velocity = 1.0;
                    fill[t][15].accent   = true;
                }
            }
        }

        return fill;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Deep-copy a 4 × N {@link DrumStep} grid.
     * Only the first 16 steps per track are copied (one bar).
     */
    private static DrumStep[][] deepCopy(DrumStep[][] source) {
        int tracks = source.length;
        DrumStep[][] copy = new DrumStep[tracks][];
        for (int t = 0; t < tracks; t++) {
            int len = Math.min(source[t].length, 16);
            copy[t] = new DrumStep[len];
            for (int s = 0; s < len; s++) {
                copy[t][s] = copyStep(source[t][s]);
            }
        }
        return copy;
    }

    private static DrumStep copyStep(DrumStep src) {
        DrumStep dst = new DrumStep();
        dst.active       = src.active;
        dst.velocity     = src.velocity;
        dst.accent       = src.accent;
        dst.flam         = src.flam;
        dst.trigCondition = src.trigCondition;
        // pLocks is not needed for fill generation
        return dst;
    }
}
