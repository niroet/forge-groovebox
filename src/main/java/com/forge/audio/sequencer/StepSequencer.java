package com.forge.audio.sequencer;

import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.model.SynthStep;
import com.forge.model.TrigCondition;
import com.forge.audio.drums.DrumEngine;
import com.forge.audio.synth.VoiceAllocator;

/**
 * Listens to the {@link SequencerClock} and triggers drum/synth voices for every active step
 * in the current {@link Pattern}.
 *
 * <p>Features:
 * <ul>
 *   <li>Polymetric drum tracks — each track can have a different step count.</li>
 *   <li>Conditional triggers (ALWAYS, FIFTY_PERCENT, FIRST, FILL_ONLY, …)</li>
 *   <li>Per-track mute</li>
 *   <li>Accent — velocity boosted by 30% (capped at 1.0)</li>
 *   <li>Fill flag for FILL_ONLY conditions</li>
 * </ul>
 */
public class StepSequencer implements SequencerListener {

    private Pattern currentPattern;
    private final DrumEngine drumEngine;
    private final VoiceAllocator synthVoices;

    private boolean fillActive = false;
    /** How many full pattern loops have completed (used for FIRST/SECOND/… conditions). */
    private int loopCount = 0;
    /** Per-track mute: indices 0-3 = drum tracks, index 4 = synth track. */
    private final boolean[] trackMuted = new boolean[5];

    // -------------------------------------------------------------------------

    public StepSequencer(DrumEngine drumEngine, VoiceAllocator synthVoices) {
        this.drumEngine  = drumEngine;
        this.synthVoices = synthVoices;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    public void setPattern(Pattern pattern) { this.currentPattern = pattern; }
    public void setFillActive(boolean active) { this.fillActive = active; }
    public void setTrackMuted(int track, boolean muted) { trackMuted[track] = muted; }
    public boolean isSynthMuted() { return trackMuted[4]; }
    public int getLoopCount() { return loopCount; }

    // -------------------------------------------------------------------------
    // SequencerListener
    // -------------------------------------------------------------------------

    @Override
    public void onStep(int stepIndex) {
        if (currentPattern == null) return;

        // --- Drum tracks (0-3) ---
        for (int t = 0; t < 4; t++) {
            if (trackMuted[t]) continue;

            DrumStep[] track = currentPattern.drumSteps[t];
            int trackLen = currentPattern.drumStepCounts[t];
            int step = stepIndex % trackLen;
            DrumStep ds = track[step];

            if (!ds.active) continue;
            if (!evaluateCondition(ds.trigCondition)) continue;

            double vel = ds.velocity;
            if (ds.accent) vel = Math.min(1.0, vel * 1.3);

            drumEngine.trigger(DrumTrack.values()[t], vel);
        }

        // --- Synth track (index 4) ---
        if (!trackMuted[4]) {
            int synthStep = stepIndex % currentPattern.synthStepCount;
            SynthStep ss = currentPattern.synthSteps[synthStep];
            if (ss.active && evaluateCondition(ss.trigCondition)) {
                synthVoices.allocate(ss.midiNote, ss.velocity);
            }
        }
    }

    @Override
    public void onBarEnd(int barNumber) {
        loopCount++;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean evaluateCondition(TrigCondition condition) {
        return switch (condition) {
            case ALWAYS            -> true;
            case FIFTY_PERCENT     -> Math.random() < 0.5;
            case TWENTY_FIVE_PERCENT -> Math.random() < 0.25;
            case FIRST             -> loopCount == 0;
            case SECOND            -> loopCount == 1;
            case THIRD             -> loopCount == 2;
            case FOURTH            -> loopCount == 3;
            case FILL_ONLY         -> fillActive;
        };
    }
}
