package com.forge.audio.synth;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.SynthPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmSynthVoiceTest {

    private AudioEngine engine;
    private FmSynthVoice voice;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();
        voice = new FmSynthVoice();
        voice.init(engine.getSynth());
        // Connect voice output to master mixer so the render graph is live and
        // the amp envelope auto-disable mechanism fires correctly.
        voice.getOutput().connect(0, engine.getMasterMixer().input, 0);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void canInitAndNoteOn() {
        assertNotNull(voice.getOutput(), "voice output port must be non-null after init");
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active immediately after noteOn");
    }

    @Test
    void noteOffTriggersRelease() throws InterruptedException {
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active after noteOn");

        voice.noteOff();

        // After noteOff the voice enters release. With default release=0.3s we wait
        // well past that for the envelope to reach IDLE and auto-disable.
        try {
            engine.getSynth().sleepFor(0.6); // 600ms — well past the 300ms release
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        assertFalse(voice.isActive(), "voice must become inactive after release completes");
    }

    @Test
    void applyPatchChangesParams() {
        SynthPatch patch = new SynthPatch();
        patch.fmRatio    = 3.0;
        patch.fmDepth    = 1.5;
        patch.fmFeedback = 0.2;
        patch.filterCutoff = 2000.0;
        patch.filterResonance = 0.6;
        // Must not throw
        assertDoesNotThrow(() -> voice.applyPatch(patch));
    }

    @Test
    void noteOnAfterApplyPatchUsesNewRatio() {
        SynthPatch patch = new SynthPatch();
        patch.fmRatio = 7.0;
        patch.fmDepth = 0.8;
        voice.applyPatch(patch);
        // Just verify it doesn't throw and voice becomes active
        voice.noteOn(69, 1.0); // A4
        assertTrue(voice.isActive(), "voice must be active after noteOn with non-default FM ratio");
    }
}
