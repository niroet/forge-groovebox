package com.forge.audio.drums;

import com.forge.model.DrumPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;

/**
 * Interface for a single drum synthesizer voice.
 *
 * <p>Each implementation produces audio via its own JSyn signal chain and exposes
 * a single output port to be connected to a downstream mixer.
 */
public interface DrumVoice {

    /**
     * Initialise the voice and register all internal JSyn unit generators with the
     * given synthesizer. Must be called exactly once, before any other method.
     *
     * @param synth the JSyn {@link Synthesizer} that will own this voice's unit generators
     */
    void init(Synthesizer synth);

    /**
     * Trigger the drum voice — fires the internal amp and pitch envelopes.
     *
     * @param velocity note velocity (0.0–1.0); scales the output amplitude
     */
    void trigger(double velocity);

    /**
     * Apply a {@link DrumPatch} to this voice, updating all synthesis parameters immediately.
     *
     * @param patch the patch to apply; must not be {@code null}
     */
    void applyPatch(DrumPatch patch);

    /**
     * @return the final audio output port of this voice's signal chain
     */
    UnitOutputPort getOutput();
}
