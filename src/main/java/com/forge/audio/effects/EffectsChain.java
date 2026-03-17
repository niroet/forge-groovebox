package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;

/**
 * Serial effects chain for the FORGE Groovebox.
 *
 * <p>Connects all six effects in order:
 * <pre>
 *   input ─→ [Distortion] ─→ [Delay] ─→ [Reverb] ─→ [Chorus] ─→ [Compressor] ─→ [EQ] ─→ output
 * </pre>
 *
 * <p>Each effect is indexed by {@link EffectType#ordinal()} inside an internal array.
 * All effects are initialised with the supplied synthesizer.  By default every effect
 * starts with {@code enabled = true}; callers can bypass individual effects via
 * {@link Effect#setEnabled(boolean)}.
 *
 * <p>Usage:
 * <pre>
 *   EffectsChain chain = new EffectsChain(synth);
 *   someSource.output.connect(chain.getInput());
 *   chain.getOutput().connect(masterMixer.input, 0);
 * </pre>
 */
public class EffectsChain {

    private final Effect[] chain;

    /**
     * Construct, initialise, and wire all effects.
     *
     * @param synth the JSyn synthesizer to add all units to
     */
    public EffectsChain(Synthesizer synth) {
        // Build array in EffectType ordinal order
        chain = new Effect[EffectType.values().length];
        chain[EffectType.DISTORTION.ordinal()] = new Distortion();
        chain[EffectType.DELAY.ordinal()]       = new StereoDelay();
        chain[EffectType.REVERB.ordinal()]      = new FreeverbReverb();
        chain[EffectType.CHORUS.ordinal()]      = new Chorus();
        chain[EffectType.COMPRESSOR.ordinal()]  = new Compressor();
        chain[EffectType.EQ.ordinal()]          = new ParametricEQ();

        // Initialise each effect with the synthesizer
        for (Effect effect : chain) {
            effect.init(synth);
        }

        // Wire effects in series: output[i] → input[i+1]
        for (int i = 0; i < chain.length - 1; i++) {
            chain[i].getOutput().connect(0, chain[i + 1].getInput(), 0);
        }
    }

    // =========================================================================
    // Signal-chain entry/exit
    // =========================================================================

    /**
     * @return the input port of the first effect (Distortion); connect your audio source here
     */
    public UnitInputPort getInput() {
        return chain[0].getInput();
    }

    /**
     * @return the output port of the last effect (EQ); connect this to your mixer
     */
    public UnitOutputPort getOutput() {
        return chain[chain.length - 1].getOutput();
    }

    // =========================================================================
    // Effect access
    // =========================================================================

    /**
     * Get the effect of a given type from the chain.
     *
     * @param type the effect to retrieve
     * @return the corresponding {@link Effect} instance
     */
    public Effect getEffect(EffectType type) {
        return chain[type.ordinal()];
    }

    /**
     * @return the total number of effects in the chain (always {@code EffectType.values().length})
     */
    public int size() {
        return chain.length;
    }
}
