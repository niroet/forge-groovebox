package com.forge.audio.sequencer;

import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.FillType;
import com.forge.model.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FillGeneratorTest {

    private Pattern emptyPattern;

    @BeforeEach
    void setUp() {
        // A default Pattern has all steps inactive — gives us a clean slate
        // to verify only the fill-specific steps are activated.
        emptyPattern = new Pattern();
    }

    // -------------------------------------------------------------------------
    // simpleFillAddsDensity
    // -------------------------------------------------------------------------

    /**
     * A SIMPLE fill must activate kick and snare on steps 12–15.
     */
    @Test
    void simpleFillAddsDensity() {
        DrumStep[][] fill = FillGenerator.generateFill(emptyPattern, FillType.SIMPLE);

        for (int s = 12; s < 16; s++) {
            assertTrue(fill[DrumTrack.KICK.ordinal()][s].active,
                    "KICK should be active on step " + s + " for SIMPLE fill");
            assertTrue(fill[DrumTrack.SNARE.ordinal()][s].active,
                    "SNARE should be active on step " + s + " for SIMPLE fill");
        }

        // Sanity: steps 0–11 should still be inactive on the empty source
        for (int s = 0; s < 12; s++) {
            assertFalse(fill[DrumTrack.KICK.ordinal()][s].active,
                    "KICK step " + s + " should remain inactive");
        }
    }

    // -------------------------------------------------------------------------
    // rollFillHasSnareRoll
    // -------------------------------------------------------------------------

    /**
     * A ROLL fill must have snare active on steps 12–15 with a crescendo velocity.
     */
    @Test
    void rollFillHasSnareRoll() {
        DrumStep[][] fill = FillGenerator.generateFill(emptyPattern, FillType.ROLL);

        for (int s = 12; s < 16; s++) {
            assertTrue(fill[DrumTrack.SNARE.ordinal()][s].active,
                    "SNARE should be active on step " + s + " for ROLL fill");

            double expectedVelocity = 0.6 + (s - 12) * 0.1;
            assertEquals(expectedVelocity, fill[DrumTrack.SNARE.ordinal()][s].velocity, 1e-9,
                    "SNARE velocity should crescendo at step " + s);
        }

        // Kick should not be forced on for a pure roll
        for (int s = 12; s < 16; s++) {
            assertFalse(fill[DrumTrack.KICK.ordinal()][s].active,
                    "KICK should NOT be activated by ROLL fill at step " + s);
        }
    }

    // -------------------------------------------------------------------------
    // breakdownStripsPattern
    // -------------------------------------------------------------------------

    /**
     * A BREAKDOWN fill must clear most steps and leave only kick on 0,8 and a
     * full explosion (all 4 tracks accented) on step 15.
     */
    @Test
    void breakdownStripsPattern() {
        // Start with a fully active source to verify clearing works
        Pattern fullPattern = new Pattern();
        for (int t = 0; t < 4; t++) {
            for (int s = 0; s < 16; s++) {
                fullPattern.drumSteps[t][s].active = true;
            }
        }

        DrumStep[][] fill = FillGenerator.generateFill(fullPattern, FillType.BREAKDOWN);

        // Count active steps per track — only specific ones should survive
        for (int t = 0; t < 4; t++) {
            for (int s = 0; s < 16; s++) {
                boolean shouldBeActive;
                if (s == 15) {
                    // All tracks explode on step 15
                    shouldBeActive = true;
                } else if (t == DrumTrack.KICK.ordinal() && (s == 0 || s == 8)) {
                    shouldBeActive = true;
                } else {
                    shouldBeActive = false;
                }
                assertEquals(shouldBeActive, fill[t][s].active,
                        "Track " + t + " step " + s + " active mismatch for BREAKDOWN fill");
            }
        }

        // Verify explosion step has accent and full velocity
        for (int t = 0; t < 4; t++) {
            assertEquals(1.0, fill[t][15].velocity, 1e-9,
                    "Explosion step 15 velocity should be 1.0 on track " + t);
            assertTrue(fill[t][15].accent,
                    "Explosion step 15 should be accented on track " + t);
        }

        // Verify sparse kick velocities
        assertEquals(0.6, fill[DrumTrack.KICK.ordinal()][0].velocity, 1e-9,
                "Kick step 0 velocity should be 0.6");
        assertEquals(0.7, fill[DrumTrack.KICK.ordinal()][8].velocity, 1e-9,
                "Kick step 8 velocity should be 0.7");
    }

    // -------------------------------------------------------------------------
    // buildupFillCoversSteps8to15
    // -------------------------------------------------------------------------

    /**
     * A BUILDUP fill must activate kick and hat from step 8, and snare from step 12.
     */
    @Test
    void buildupFillCoversSteps8to15() {
        DrumStep[][] fill = FillGenerator.generateFill(emptyPattern, FillType.BUILDUP);

        for (int s = 8; s < 16; s++) {
            assertTrue(fill[DrumTrack.KICK.ordinal()][s].active,
                    "KICK should be active on step " + s + " for BUILDUP fill");
            assertTrue(fill[DrumTrack.HAT.ordinal()][s].active,
                    "HAT should be active on step " + s + " for BUILDUP fill");
        }

        for (int s = 12; s < 16; s++) {
            assertTrue(fill[DrumTrack.SNARE.ordinal()][s].active,
                    "SNARE should be active on step " + s + " for BUILDUP fill");
        }

        // Snare should NOT be forced before step 12
        for (int s = 8; s < 12; s++) {
            assertFalse(fill[DrumTrack.SNARE.ordinal()][s].active,
                    "SNARE should NOT be active on step " + s + " for BUILDUP fill");
        }
    }

    // -------------------------------------------------------------------------
    // sourcePatternIsNotMutated
    // -------------------------------------------------------------------------

    /**
     * generateFill must not modify the source pattern.
     */
    @Test
    void sourcePatternIsNotMutated() {
        // All steps inactive in emptyPattern
        FillGenerator.generateFill(emptyPattern, FillType.SIMPLE);

        for (int t = 0; t < 4; t++) {
            for (int s = 0; s < 16; s++) {
                assertFalse(emptyPattern.drumSteps[t][s].active,
                        "Source pattern must not be mutated (track " + t + " step " + s + ")");
            }
        }
    }
}
