package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;

/**
 * Common interface for all audio effects in the FORGE effects chain.
 *
 * <p>Each effect wraps one or more JSyn unit generators. The effect is responsible for
 * initialising and wiring its internal units in {@link #init(Synthesizer)}, and for
 * providing the first input port and last output port so that the {@link EffectsChain}
 * can wire effects together in series.
 *
 * <p>When disabled, the effect must pass its input signal to its output unchanged (bypass).
 */
public interface Effect {

    /**
     * Create and register all internal JSyn unit generators with the synthesizer,
     * wire them together, and set default parameter values.
     *
     * @param synth the JSyn synthesizer to add units to
     */
    void init(Synthesizer synth);

    /** @return the input port that feeds audio into this effect */
    UnitInputPort getInput();

    /** @return the output port that carries processed audio out of this effect */
    UnitOutputPort getOutput();

    /**
     * Enable or disable the effect.
     *
     * <p>When disabled the effect must behave as a transparent bypass: output equals input.
     *
     * @param enabled {@code true} to enable processing, {@code false} to bypass
     */
    void setEnabled(boolean enabled);

    /** @return {@code true} if the effect is currently active (not bypassed) */
    boolean isEnabled();

    /**
     * Set a named parameter on this effect.
     *
     * @param name  parameter name (effect-specific)
     * @param value new value
     * @throws IllegalArgumentException if {@code name} is not a recognised parameter
     */
    void setParam(String name, double value);

    /**
     * Get the current value of a named parameter.
     *
     * @param name parameter name (effect-specific)
     * @return current value
     * @throws IllegalArgumentException if {@code name} is not a recognised parameter
     */
    double getParam(String name);
}
