package com.forge.audio.sequencer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SequencerClockTest {

    // -------------------------------------------------------------------------
    // samplesPerStepCalculation
    // -------------------------------------------------------------------------

    /**
     * At 120 BPM, 44100 Hz: one 16th note = 44100 * 60 / 120 / 4 = 5512.5 samples.
     * {@code play()} fires step 0 immediately (at "sample 0").
     * We count how many times {@code tick()} must be called before step 1 fires.
     * That count should be approximately 5512.
     */
    @Test
    void samplesPerStepCalculation() {
        SequencerClock clock = new SequencerClock();
        clock.setSampleRate(44_100);
        clock.setBpm(120.0);
        clock.setSwing(50.0); // straight — no swing

        long[] step1TickCount = {-1};
        long[] counter = {0};

        clock.setListener(new SequencerListener() {
            @Override
            public void onStep(int stepIndex) {
                if (stepIndex == 1 && step1TickCount[0] < 0) {
                    step1TickCount[0] = counter[0];
                }
            }
            @Override
            public void onBarEnd(int barNumber) {}
        });

        clock.play(); // fires step 0; counter is still 0 here

        int maxSamples = 20_000;
        for (int i = 1; i <= maxSamples; i++) {
            counter[0] = i;
            clock.tick();
            if (step1TickCount[0] >= 0) break;
        }

        assertNotEquals(-1, step1TickCount[0], "step 1 should have fired within 20000 ticks");

        // Expected: ~5512 samples. Allow ±50 samples for rounding.
        long expected = 5512;
        long actual = step1TickCount[0];
        assertTrue(Math.abs(actual - expected) <= 50,
            "step 1 should fire at ~5512 ticks from play(); got " + actual);
    }

    // -------------------------------------------------------------------------
    // swingOffsetsEvenSteps
    // -------------------------------------------------------------------------

    /**
     * With swing = 66.7%:
     *   evenTicks = round(66.7 / 50.0 * 24) = round(32.016) = 32
     *   oddTicks  = 48 - 32 = 16
     * The step-0 → step-1 gap should be ~twice the step-1 → step-2 gap.
     */
    @Test
    void swingOffsetsEvenSteps() {
        SequencerClock clock = new SequencerClock();
        clock.setSampleRate(44_100);
        clock.setBpm(120.0);
        clock.setSwing(66.7);

        long[] step0At = {-1};
        long[] step1At = {-1};
        long[] step2At = {-1};
        long[] counter = {0};

        clock.setListener(new SequencerListener() {
            @Override
            public void onStep(int stepIndex) {
                long t = counter[0];
                if (stepIndex == 0 && step0At[0] < 0) step0At[0] = t;
                if (stepIndex == 1 && step1At[0] < 0) step1At[0] = t;
                if (stepIndex == 2 && step2At[0] < 0) step2At[0] = t;
            }
            @Override
            public void onBarEnd(int barNumber) {}
        });

        clock.play(); // step 0 fires; counter = 0

        int maxSamples = 50_000;
        for (int i = 1; i <= maxSamples; i++) {
            counter[0] = i;
            clock.tick();
            if (step2At[0] >= 0) break;
        }

        assertNotEquals(-1, step1At[0], "step 1 should have fired");
        assertNotEquals(-1, step2At[0], "step 2 should have fired");

        long gap01 = step1At[0] - step0At[0]; // samples from step 0 to step 1
        long gap12 = step2At[0] - step1At[0]; // samples from step 1 to step 2

        // Expect ratio ≈ 2 (evenTicks=32, oddTicks=16)
        double ratio = (double) gap01 / gap12;
        assertTrue(ratio > 1.7 && ratio < 2.3,
            "gap01 should be ~2× gap12 with swing=66.7%; ratio=" + ratio
                + " (gap01=" + gap01 + ", gap12=" + gap12 + ")");
    }

    // -------------------------------------------------------------------------
    // stopPreventsStepFiring
    // -------------------------------------------------------------------------

    /**
     * After {@code stop()}, no further step callbacks should fire regardless of
     * how many {@code tick()} calls are made.
     */
    @Test
    void stopPreventsStepFiring() {
        SequencerClock clock = new SequencerClock();
        clock.setSampleRate(44_100);
        clock.setBpm(120.0);

        int[] stepsFired = {0};
        clock.setListener(new SequencerListener() {
            @Override
            public void onStep(int stepIndex) { stepsFired[0]++; }
            @Override
            public void onBarEnd(int barNumber) {}
        });

        clock.play();  // step 0 fires → stepsFired becomes 1
        clock.stop();

        for (int i = 0; i < 10_000; i++) {
            clock.tick();
        }

        // Only the initial step 0 on play() should have fired
        assertEquals(1, stepsFired[0],
            "no steps should fire after stop(); got " + stepsFired[0]);
    }
}
