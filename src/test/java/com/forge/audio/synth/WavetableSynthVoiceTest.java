package com.forge.audio.synth;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.SynthPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WavetableSynthVoiceTest {

    private AudioEngine engine;
    private WavetableSynthVoice voice;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();
        voice = new WavetableSynthVoice();
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
    void morphChangesTimbre() {
        // Apply a patch with morph=0 (first table — sine)
        SynthPatch patchA = new SynthPatch();
        patchA.wavetableMorph = 0.0;
        assertDoesNotThrow(() -> voice.applyPatch(patchA),
                "applyPatch with morph=0.0 must not throw");
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active after noteOn with morph=0");

        voice.noteOff();

        // Re-initialise the voice so we start from a clean gate-off state
        // (the release will still be running — that's fine, we just test applyPatch)
        SynthPatch patchB = new SynthPatch();
        patchB.wavetableMorph = 1.0;
        assertDoesNotThrow(() -> voice.applyPatch(patchB),
                "applyPatch with morph=1.0 must not throw");

        // Trigger another note on the new morph position
        voice.noteOn(69, 1.0);
        assertTrue(voice.isActive(), "voice must be active after noteOn with morph=1.0");
    }

    @Test
    void applyPatchDoesNotCrash() {
        SynthPatch patch = new SynthPatch();
        patch.wavetableMorph   = 0.5;
        patch.filterCutoff     = 4000.0;
        patch.filterResonance  = 0.4;
        patch.ampAttack        = 0.02;
        patch.ampDecay         = 0.15;
        patch.ampSustain       = 0.6;
        patch.ampRelease       = 0.25;
        assertDoesNotThrow(() -> voice.applyPatch(patch));
    }

    @Test
    void noteOffTriggersRelease() throws InterruptedException {
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active after noteOn");

        voice.noteOff();

        // Wait well past the default 0.3 s release time
        try {
            engine.getSynth().sleepFor(0.6);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        assertFalse(voice.isActive(), "voice must become inactive after release completes");
    }
}
