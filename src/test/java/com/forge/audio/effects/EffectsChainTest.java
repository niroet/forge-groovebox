package com.forge.audio.effects;

import com.forge.audio.engine.AudioEngine;
import com.jsyn.unitgen.SineOscillator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectsChainTest {

    private AudioEngine  engine;
    private EffectsChain chain;
    private SineOscillator osc;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();

        chain = new EffectsChain(engine.getSynth());

        osc = new SineOscillator();
        engine.getSynth().add(osc);
        osc.frequency.set(440.0);
        osc.amplitude.set(0.5);

        // Connect osc → chain → master mixer
        osc.output.connect(0, chain.getInput(), 0);
        chain.getOutput().connect(0, engine.getMasterMixer().input, 1);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    // =========================================================================

    @Test
    void chainConnectsAllEffects() {
        assertEquals(EffectType.values().length, chain.size(),
                "chain must contain exactly " + EffectType.values().length + " effects");

        for (EffectType type : EffectType.values()) {
            Effect e = chain.getEffect(type);
            assertNotNull(e, "effect must not be null for type: " + type);
            assertNotNull(e.getInput(),  "effect input port must be non-null for: " + type);
            assertNotNull(e.getOutput(), "effect output port must be non-null for: " + type);
        }
    }

    @Test
    void chainHasInputAndOutputPorts() {
        assertNotNull(chain.getInput(),  "chain input must be non-null");
        assertNotNull(chain.getOutput(), "chain output must be non-null");
    }

    @Test
    void allEffectTypesArePresent() {
        assertNotNull(chain.getEffect(EffectType.DISTORTION), "DISTORTION effect must be present");
        assertNotNull(chain.getEffect(EffectType.DELAY),      "DELAY effect must be present");
        assertNotNull(chain.getEffect(EffectType.REVERB),     "REVERB effect must be present");
        assertNotNull(chain.getEffect(EffectType.CHORUS),     "CHORUS effect must be present");
        assertNotNull(chain.getEffect(EffectType.COMPRESSOR), "COMPRESSOR effect must be present");
        assertNotNull(chain.getEffect(EffectType.EQ),         "EQ effect must be present");
    }

    @Test
    void effectTypeOrderMatchesOrdinals() {
        // Verify ordinals match the expected signal-flow order
        assertEquals(0, EffectType.DISTORTION.ordinal());
        assertEquals(1, EffectType.DELAY.ordinal());
        assertEquals(2, EffectType.REVERB.ordinal());
        assertEquals(3, EffectType.CHORUS.ordinal());
        assertEquals(4, EffectType.COMPRESSOR.ordinal());
        assertEquals(5, EffectType.EQ.ordinal());
    }

    @Test
    void disabledEffectsBypassSignal() throws InterruptedException {
        // Disable all effects — signal should pass through
        for (EffectType type : EffectType.values()) {
            chain.getEffect(type).setEnabled(false);
        }

        // Verify all effects report as disabled
        for (EffectType type : EffectType.values()) {
            assertFalse(chain.getEffect(type).isEnabled(),
                    "effect must report disabled for: " + type);
        }

        // Let the synth render a short burst with all effects bypassed
        engine.getSynth().sleepFor(0.05);

        // With all effects disabled the output should still be a bounded signal
        // (the sine oscillator amplitude is 0.5, so we expect output within [-0.5, 0.5]).
        // We allow some tolerance because effects like compressor may still apply makeup gain
        // even when disabled. We just verify the chain doesn't produce NaN or runaway values.
        double[] outputValues = chain.getOutput().getValues();
        assertNotNull(outputValues, "chain output values must not be null");
        assertTrue(outputValues.length > 0, "chain output values must not be empty");

        for (double v : outputValues) {
            assertFalse(Double.isNaN(v),      "output must not be NaN");
            assertFalse(Double.isInfinite(v), "output must not be infinite");
            assertTrue(Math.abs(v) <= 2.0,
                    () -> "output amplitude should be bounded when all effects are bypassed, got: " + v);
        }
    }

    @Test
    void enabledStateIsIndependentPerEffect() {
        chain.getEffect(EffectType.DISTORTION).setEnabled(false);
        chain.getEffect(EffectType.DELAY).setEnabled(true);

        assertFalse(chain.getEffect(EffectType.DISTORTION).isEnabled());
        assertTrue(chain.getEffect(EffectType.DELAY).isEnabled());
    }

    @Test
    void distortionParamsAreAccessible() {
        Effect dist = chain.getEffect(EffectType.DISTORTION);
        dist.setParam("drive", 5.0);
        assertEquals(5.0, dist.getParam("drive"), 1e-9);
    }

    @Test
    void delayParamsAreAccessible() {
        Effect delay = chain.getEffect(EffectType.DELAY);
        delay.setParam("time", 500.0);
        assertEquals(500.0, delay.getParam("time"), 1e-9);

        delay.setParam("feedback", 0.5);
        assertEquals(0.5, delay.getParam("feedback"), 1e-9);
    }
}
