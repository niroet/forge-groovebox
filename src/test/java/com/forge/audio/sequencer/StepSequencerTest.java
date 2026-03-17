package com.forge.audio.sequencer;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.engine.AudioEngine;
import com.forge.audio.synth.SubtractiveSynthVoice;
import com.forge.audio.synth.SynthVoice;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.model.SynthStep;
import com.forge.model.TrigCondition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepSequencerTest {

    // -------------------------------------------------------------------------
    // A DrumEngine subclass that counts triggers per track for test assertions
    // -------------------------------------------------------------------------

    static class CountingDrumEngine extends DrumEngine {
        final int[] triggerCounts = new int[4];

        CountingDrumEngine(AudioEngine engine) {
            super(engine);
        }

        @Override
        public void trigger(DrumTrack track, double velocity) {
            triggerCounts[track.ordinal()]++;
            super.trigger(track, velocity);
        }
    }

    // -------------------------------------------------------------------------
    // Test setup
    // -------------------------------------------------------------------------

    private AudioEngine engine;
    private CountingDrumEngine drumEngine;
    private VoiceAllocator synthVoices;
    private StepSequencer sequencer;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();

        drumEngine = new CountingDrumEngine(engine);

        // Build a small synth voice pool
        SynthVoice[] voices = new SubtractiveSynthVoice[4];
        for (int i = 0; i < voices.length; i++) {
            SubtractiveSynthVoice v = new SubtractiveSynthVoice();
            v.init(engine.getSynth());
            v.getOutput().connect(0, engine.getMasterMixer().input, i);
            voices[i] = v;
        }
        synthVoices = new VoiceAllocator(voices);

        sequencer = new StepSequencer(drumEngine, synthVoices);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Walk the sequencer through stepCount steps starting at 0. */
    private void walkSteps(int stepCount) {
        for (int i = 0; i < stepCount; i++) {
            sequencer.onStep(i);
        }
    }

    /** Create a pattern with kick on the given steps (all 16 steps defined, only listed active). */
    private Pattern kickPattern(int... activeSteps) {
        Pattern p = new Pattern();
        // Deactivate all drum steps by default (Pattern() creates them inactive already)
        for (int s : activeSteps) {
            p.drumSteps[DrumTrack.KICK.ordinal()][s].active = true;
        }
        return p;
    }

    // -------------------------------------------------------------------------
    // triggersActiveSteps
    // -------------------------------------------------------------------------

    /**
     * Kick on steps 0, 4, 8, 12 → exactly 4 KICK triggers over 16 steps.
     */
    @Test
    void triggersActiveSteps() {
        Pattern p = kickPattern(0, 4, 8, 12);
        sequencer.setPattern(p);

        walkSteps(16);

        assertEquals(4, drumEngine.triggerCounts[DrumTrack.KICK.ordinal()],
            "KICK should fire exactly 4 times for steps 0,4,8,12");
    }

    // -------------------------------------------------------------------------
    // respectsMuteState
    // -------------------------------------------------------------------------

    /**
     * Muting the kick track (index 0) must suppress all kick triggers.
     */
    @Test
    void respectsMuteState() {
        Pattern p = kickPattern(0, 4, 8, 12);
        sequencer.setPattern(p);
        sequencer.setTrackMuted(0, true); // mute KICK

        walkSteps(16);

        assertEquals(0, drumEngine.triggerCounts[DrumTrack.KICK.ordinal()],
            "KICK should not fire when track is muted");
    }

    // -------------------------------------------------------------------------
    // fillOnlyStepsRespectFillFlag
    // -------------------------------------------------------------------------

    /**
     * A step with FILL_ONLY condition should only trigger when fillActive is true.
     */
    @Test
    void fillOnlyStepsRespectFillFlag() {
        Pattern p = new Pattern();
        // Step 15 of kick with FILL_ONLY
        DrumStep step15 = p.drumSteps[DrumTrack.KICK.ordinal()][15];
        step15.active = true;
        step15.trigCondition = TrigCondition.FILL_ONLY;

        sequencer.setPattern(p);

        // First pass: no fill
        sequencer.setFillActive(false);
        sequencer.onStep(15);
        assertEquals(0, drumEngine.triggerCounts[DrumTrack.KICK.ordinal()],
            "FILL_ONLY step should NOT fire when fillActive=false");

        // Second pass: fill active
        sequencer.setFillActive(true);
        sequencer.onStep(15);
        assertEquals(1, drumEngine.triggerCounts[DrumTrack.KICK.ordinal()],
            "FILL_ONLY step should fire when fillActive=true");
    }

    // -------------------------------------------------------------------------
    // conditionalTriggersWorkStatistically
    // -------------------------------------------------------------------------

    /**
     * A FIFTY_PERCENT step should fire roughly 50% of the time over 1000 iterations.
     * Acceptable range: 400–600.
     */
    @Test
    void conditionalTriggersWorkStatistically() {
        Pattern p = new Pattern();
        DrumStep kickStep = p.drumSteps[DrumTrack.KICK.ordinal()][0];
        kickStep.active = true;
        kickStep.trigCondition = TrigCondition.FIFTY_PERCENT;

        sequencer.setPattern(p);

        // Walk step 0 one thousand times
        for (int i = 0; i < 1000; i++) {
            sequencer.onStep(0);
        }

        int count = drumEngine.triggerCounts[DrumTrack.KICK.ordinal()];
        assertTrue(count >= 400 && count <= 600,
            "FIFTY_PERCENT step should fire ~500 times out of 1000; got " + count);
    }
}
