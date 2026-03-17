package com.forge.audio.drums;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrumEngineTest {

    private AudioEngine engine;
    private DrumEngine drumEngine;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();
        drumEngine = new DrumEngine(engine);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void canCreateDrumEngine() {
        // If we got here without exceptions, construction succeeded
        assertNotNull(drumEngine, "DrumEngine must be created successfully");
    }

    @Test
    void triggerKickDoesNotCrash() {
        assertDoesNotThrow(
            () -> drumEngine.trigger(DrumTrack.KICK, 0.8),
            "Triggering KICK must not throw"
        );
    }

    @Test
    void triggerAllVoices() {
        for (DrumTrack track : DrumTrack.values()) {
            assertDoesNotThrow(
                () -> drumEngine.trigger(track, 0.8),
                "Triggering " + track + " must not throw"
            );
        }
    }

    @Test
    void applyPatchDoesNotCrash() {
        DrumPatch patch = new DrumPatch();
        patch.pitch      = 80.0;
        patch.decay      = 0.4;
        patch.toneNoise  = 0.7;
        patch.drive      = 0.2;
        patch.snap       = 0.5;
        patch.clickLevel = 0.3;
        patch.open       = false;

        for (DrumTrack track : DrumTrack.values()) {
            assertDoesNotThrow(
                () -> drumEngine.applyPatch(track, patch),
                "applyPatch must not throw for " + track
            );
        }
    }

    @Test
    void triggerAfterApplyPatchDoesNotCrash() {
        // Apply a non-default patch first, then trigger
        DrumPatch kickPatch = new DrumPatch();
        kickPatch.pitch      = 50.0;
        kickPatch.decay      = 0.5;
        kickPatch.clickLevel = 0.8;
        kickPatch.drive      = 0.5;

        assertDoesNotThrow(() -> {
            drumEngine.applyPatch(DrumTrack.KICK, kickPatch);
            drumEngine.trigger(DrumTrack.KICK, 1.0);
        }, "trigger after applyPatch must not throw for KICK");
    }

    @Test
    void openHatVsClosedHat() {
        DrumPatch openPatch = new DrumPatch();
        openPatch.open = true;
        openPatch.decay = 0.4;

        DrumPatch closedPatch = new DrumPatch();
        closedPatch.open = false;
        closedPatch.decay = 0.05;

        assertDoesNotThrow(() -> {
            drumEngine.applyPatch(DrumTrack.HAT, openPatch);
            drumEngine.trigger(DrumTrack.HAT, 0.9);
            drumEngine.applyPatch(DrumTrack.HAT, closedPatch);
            drumEngine.trigger(DrumTrack.HAT, 0.9);
        }, "Open/closed hat switching must not throw");
    }
}
