package com.forge.audio.synth;

import com.forge.model.SynthPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;

/**
 * A single polyphonic voice capable of producing audio given a MIDI note and velocity.
 *
 * <p>Implementations are expected to manage their own JSyn signal chain internally.
 * The caller is responsible for connecting the voice's {@link #getOutput()} port to a
 * downstream mixer or bus.
 */
public interface SynthVoice {

    /**
     * Initialise the voice and add all internal JSyn unit generators to the given synthesizer.
     * Must be called exactly once, before any other method.
     *
     * @param synth the JSyn {@link Synthesizer} that owns this voice's unit generators
     */
    void init(Synthesizer synth);

    /**
     * Begin playing a note.
     *
     * @param midiNote MIDI note number (0–127)
     * @param velocity note velocity (0.0–1.0)
     */
    void noteOn(int midiNote, double velocity);

    /** Release the current note, entering the envelope release phase. */
    void noteOff();

    /**
     * Apply a {@link SynthPatch} to this voice, updating all synthesis parameters immediately.
     *
     * @param patch the patch to apply; must not be {@code null}
     */
    void applyPatch(SynthPatch patch);

    /**
     * Returns {@code true} while the voice is producing audio (attack through end of release).
     * Returns {@code false} only when the amp envelope is fully idle.
     */
    boolean isActive();

    /**
     * @return the final audio output port of this voice's signal chain
     */
    UnitOutputPort getOutput();
}
