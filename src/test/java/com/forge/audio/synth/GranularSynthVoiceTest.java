package com.forge.audio.synth;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.SynthPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GranularSynthVoiceTest {

    private AudioEngine engine;
    private GranularSynthVoice voice;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();
        voice = new GranularSynthVoice();
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
    void freezeCapturesBuffer() throws InterruptedException {
        // Start a note so the oscillator generates content into the source buffer
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active after noteOn");

        // Let the engine render a few blocks to fill the source buffer
        try {
            engine.getSynth().sleepFor(0.05); // 50 ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        // Freeze — the voice should continue to be active (grains still playing)
        assertFalse(voice.isFrozen(), "voice must not be frozen before freeze() is called");
        voice.freeze();
        assertTrue(voice.isFrozen(), "voice must report frozen after freeze()");

        // The voice must still be active (amp envelope is still open)
        assertTrue(voice.isActive(), "voice must remain active while frozen and note is held");

        // Unfreeze and verify the flag clears
        voice.unfreeze();
        assertFalse(voice.isFrozen(), "voice must not be frozen after unfreeze()");
    }

    @Test
    void applyPatchDoesNotCrash() {
        SynthPatch patch = new SynthPatch();
        patch.granularSize    = 80.0;
        patch.granularDensity = 30.0;
        patch.granularScatter = 0.2;
        patch.filterCutoff    = 4000.0;
        patch.filterResonance = 0.4;
        patch.ampAttack       = 0.02;
        patch.ampDecay        = 0.15;
        patch.ampSustain      = 0.6;
        patch.ampRelease      = 0.25;
        assertDoesNotThrow(() -> voice.applyPatch(patch),
                "applyPatch must not throw for valid granular parameters");

        // Applying a patch and triggering a note must still produce an active voice
        voice.applyPatch(patch);
        voice.noteOn(69, 1.0);
        assertTrue(voice.isActive(), "voice must be active after applyPatch + noteOn");
    }
}
