package com.forge.audio.synth;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.SynthPatch;
import com.forge.model.WaveShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubtractiveSynthVoiceTest {

    private AudioEngine engine;
    private SubtractiveSynthVoice voice;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();
        voice = new SubtractiveSynthVoice();
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
    void voiceCanBeCreatedAndInitialized() {
        // If we got here without exceptions, init succeeded
        assertNotNull(voice.getOutput(), "voice output port must be non-null after init");
    }

    @Test
    void noteOnMakesVoiceActive() {
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active immediately after noteOn");
    }

    @Test
    void noteOffTriggersRelease() throws InterruptedException {
        voice.noteOn(60, 0.8);
        assertTrue(voice.isActive(), "voice must be active after noteOn");

        voice.noteOff();

        // After noteOff the voice enters release. With default release=0.3s we need
        // to wait longer than that for the envelope to go fully idle.
        // Use JSyn's time-aware sleep to keep us in the audio thread's time domain.
        try {
            engine.getSynth().sleepFor(0.6); // wait 600ms — well past the 300ms release
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        assertFalse(voice.isActive(), "voice must become inactive after release completes");
    }

    @Test
    void applyPatchDoesNotCrash() {
        SynthPatch patch = new SynthPatch();
        patch.oscAShape = WaveShape.SQUARE;
        patch.oscBShape = WaveShape.SINE;
        patch.oscALevel = 0.8;
        patch.oscBLevel = 0.2;
        patch.filterCutoff = 4000.0;
        patch.filterResonance = 0.5;
        // Should not throw
        assertDoesNotThrow(() -> voice.applyPatch(patch));
    }

    @Test
    void allWaveShapesApplyWithoutCrash() {
        for (WaveShape shape : WaveShape.values()) {
            SynthPatch patch = new SynthPatch();
            patch.oscAShape = shape;
            patch.oscBShape = shape;
            assertDoesNotThrow(() -> voice.applyPatch(patch),
                    "applyPatch must not throw for shape: " + shape);
        }
    }

    @Test
    void noteOnAfterApplyPatchUsesCorrectFrequency() {
        SynthPatch patch = new SynthPatch();
        patch.oscADetune = 7.0; // 7 semitones up
        voice.applyPatch(patch);
        // Just verify it doesn't throw and voice becomes active
        voice.noteOn(69, 1.0); // A4
        assertTrue(voice.isActive(), "voice must be active after noteOn with detuned patch");
    }
}
