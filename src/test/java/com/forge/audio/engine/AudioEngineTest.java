package com.forge.audio.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioEngineTest {

    private AudioEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void engineStartsAndStops() {
        engine = new AudioEngine();
        assertFalse(engine.isRunning(), "engine must not be running before start()");

        engine.start();
        assertTrue(engine.isRunning(), "engine must be running after start()");

        engine.stop();
        assertFalse(engine.isRunning(), "engine must not be running after stop()");
    }

    @Test
    void analysisBusExists() {
        engine = new AudioEngine();
        assertNotNull(engine.getAnalysisBus(), "getAnalysisBus() must return a non-null AnalysisBus");
    }
}
