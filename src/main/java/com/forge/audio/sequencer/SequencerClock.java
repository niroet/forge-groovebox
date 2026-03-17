package com.forge.audio.sequencer;

/**
 * Sample-accurate sequencer clock at 96 PPQN.
 *
 * <p>Call {@link #tick()} once per audio sample. The clock fires {@link SequencerListener}
 * callbacks when step and bar boundaries are crossed.
 *
 * <p><b>Swing model:</b> steps are processed in pairs (even + odd).  The total tick budget
 * for a pair is always {@code 2 * TICKS_PER_STEP = 48}.  Swing redistributes that budget:
 * <ul>
 *   <li>swing = 50.0 → even step gets 24 ticks, odd step gets 24 ticks (straight)</li>
 *   <li>swing = 75.0 → even step gets 36 ticks, odd step gets 12 ticks (max triplet feel)</li>
 * </ul>
 */
public class SequencerClock {

    private static final int PPQN = 96;
    /** Ticks per 16th-note step (96 / 4). */
    public static final int TICKS_PER_STEP = PPQN / 4; // 24

    private int sampleRate = 44_100;
    private double bpm = 128.0;
    /** swing 50 = straight, 75 = max swing. Clamped to [50, 75]. */
    private double swing = 50.0;

    private boolean playing = false;

    /** Fractional sample accumulator — carries sub-sample remainder across calls. */
    private double sampleAccumulator = 0.0;
    /** How many ticks have elapsed within the current step. */
    private int ticksIntoCurrentStep = 0;
    private int currentStep = 0;
    private int currentBar = 0;
    private int stepsPerBar = 16;

    private SequencerListener listener;

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setListener(SequencerListener listener) { this.listener = listener; }
    public void setSampleRate(int rate) { this.sampleRate = rate; }
    public void setBpm(double bpm) { this.bpm = bpm; }
    public void setSwing(double swing) { this.swing = Math.max(50.0, Math.min(75.0, swing)); }
    public void setStepsPerBar(int steps) { this.stepsPerBar = steps; }

    // -------------------------------------------------------------------------
    // Transport
    // -------------------------------------------------------------------------

    /**
     * Start (or restart) playback from step 0.
     * Fires {@code onStep(0)} immediately so the listener hears step 0 without delay.
     */
    public void play() {
        playing = true;
        sampleAccumulator = 0.0;
        ticksIntoCurrentStep = 0;
        currentStep = 0;
        currentBar = 0;
        if (listener != null) listener.onStep(0);
    }

    /** Stop playback. Subsequent {@link #tick()} calls are no-ops. */
    public void stop() {
        playing = false;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isPlaying() { return playing; }
    public int getCurrentStep() { return currentStep; }
    public int getCurrentBar() { return currentBar; }
    public double getBpm() { return bpm; }

    // -------------------------------------------------------------------------
    // Clock driver
    // -------------------------------------------------------------------------

    /**
     * Advance the clock by one sample.  Must be called exactly once per audio sample from
     * the audio processing loop (or test harness).
     */
    public void tick() {
        if (!playing) return;

        // How many samples does one PPQN tick take at the current tempo?
        double samplesPerTick = (sampleRate * 60.0) / (bpm * PPQN);

        sampleAccumulator += 1.0;
        while (sampleAccumulator >= samplesPerTick) {
            sampleAccumulator -= samplesPerTick;
            // Advance one tick and check for step boundary
            ticksIntoCurrentStep++;
            int threshold = ticksForStep(currentStep);
            if (ticksIntoCurrentStep >= threshold) {
                ticksIntoCurrentStep = 0;
                currentStep++;

                if (currentStep >= stepsPerBar) {
                    currentStep = 0;
                    if (listener != null) listener.onBarEnd(currentBar);
                    currentBar++;
                }
                if (listener != null) listener.onStep(currentStep);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Return the number of ticks allotted to {@code step}.
     *
     * <p>Steps are processed in pairs.  The pair budget is always
     * {@code 2 * TICKS_PER_STEP}.  Even steps receive
     * {@code round(swing/50 * TICKS_PER_STEP)} ticks; odd steps receive the
     * remainder so the pair invariant holds.
     */
    int ticksForStep(int step) {
        int evenTicks = (int) Math.round((swing / 50.0) * TICKS_PER_STEP);
        // Clamp so both halves are at least 1 tick
        evenTicks = Math.max(1, Math.min(2 * TICKS_PER_STEP - 1, evenTicks));
        int oddTicks = 2 * TICKS_PER_STEP - evenTicks;
        return (step % 2 == 0) ? evenTicks : oddTicks;
    }
}
