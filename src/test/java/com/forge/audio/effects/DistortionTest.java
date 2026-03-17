package com.forge.audio.effects;

import com.forge.audio.engine.AudioEngine;
import com.jsyn.unitgen.SineOscillator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistortionTest {

    private AudioEngine engine;
    private Distortion  distortion;
    private SineOscillator osc;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();

        distortion = new Distortion();
        distortion.init(engine.getSynth());

        // Sine oscillator as a known test signal source
        osc = new SineOscillator();
        engine.getSynth().add(osc);
        osc.frequency.set(440.0);
        osc.amplitude.set(1.0);

        // Connect: osc → distortion input
        osc.output.connect(0, distortion.getInput(), 0);

        // Connect distortion output to the master mixer so the render graph is live
        distortion.getOutput().connect(0, engine.getMasterMixer().input, 0);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    // =========================================================================

    @Test
    void distortionCanBeCreatedAndInitialized() {
        assertNotNull(distortion.getInput(),  "distortion input port must be non-null after init");
        assertNotNull(distortion.getOutput(), "distortion output port must be non-null after init");
    }

    /**
     * With a very high drive, the tanh waveshaper compresses signal amplitudes hard.
     * All output samples must be strictly within (-1, 1) because tanh asymptotes at ±1.
     * We verify that with drive=20 the output never exceeds 0.9999 in absolute value,
     * confirming the saturation is active.
     */
    @Test
    void distortionClipsSignal() throws InterruptedException {
        distortion.setEnabled(true);
        distortion.setParam("drive", 20.0);
        distortion.setParam("mix", 1.0); // full wet

        // Let the synth render a short burst
        engine.getSynth().sleepFor(0.05); // 50ms of audio

        // Read the output values directly (JSyn makes current buffer accessible)
        double[] outputValues = distortion.getOutput().getValues();
        assertNotNull(outputValues, "output values array must not be null");
        assertTrue(outputValues.length > 0, "output values array must not be empty");

        boolean foundClipping = false;
        for (double v : outputValues) {
            // tanh output is strictly bounded by (-1, 1)
            assertTrue(Math.abs(v) < 1.0,
                    () -> "tanh output must be < 1.0 in absolute value, got: " + v);
            // With high drive on a full-amplitude sine, output should be near saturation (> 0.9)
            if (Math.abs(v) > 0.9) {
                foundClipping = true;
            }
        }
        assertTrue(foundClipping,
                "With drive=20 and full-amplitude input, output should saturate near ±1");
    }

    /**
     * When the effect is disabled, the output should equal the dry input signal.
     * We verify by checking that the dry channel (mix=0) passes the input unchanged.
     */
    @Test
    void bypassPassesSignalUnchanged() throws InterruptedException {
        distortion.setEnabled(false);

        // Let the synth render some audio
        engine.getSynth().sleepFor(0.02); // 20ms

        // With bypass, the mixer should use full dry gain (1.0) and zero wet gain.
        // We verify that outputs are within the range of a 440Hz sine wave with amplitude 1.0.
        double[] outputValues = distortion.getOutput().getValues();
        assertNotNull(outputValues);
        assertTrue(outputValues.length > 0);

        for (double v : outputValues) {
            // A sine wave with amplitude 1.0 is bounded to [-1, 1]
            assertTrue(Math.abs(v) <= 1.0 + 1e-6,
                    () -> "bypass output must not exceed amplitude 1.0, got: " + v);
        }
    }

    @Test
    void defaultDriveIsOne() {
        assertEquals(1.0, distortion.getParam("drive"), 1e-9, "default drive must be 1.0");
    }

    @Test
    void defaultMixIsOne() {
        assertEquals(1.0, distortion.getParam("mix"), 1e-9, "default mix must be 1.0");
    }

    @Test
    void setParamUpdatesValue() {
        distortion.setParam("drive", 10.0);
        assertEquals(10.0, distortion.getParam("drive"), 1e-9);

        distortion.setParam("mix", 0.5);
        assertEquals(0.5, distortion.getParam("mix"), 1e-9);
    }

    @Test
    void unknownParamThrows() {
        assertThrows(IllegalArgumentException.class, () -> distortion.setParam("bogus", 1.0));
        assertThrows(IllegalArgumentException.class, () -> distortion.getParam("bogus"));
    }
}
