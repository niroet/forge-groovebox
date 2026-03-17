package com.forge.audio.drums;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;

/**
 * Manages all four drum voices and routes their output to the master mixer.
 *
 * <p>Voices are indexed by {@link DrumTrack#ordinal()}: KICK=0, SNARE=1, HAT=2, PERC=3.
 * Each voice is connected to a dedicated master mixer channel (channels 4–7, leaving
 * channels 0–3 available for synth voices).
 */
public class DrumEngine {

    /** First mixer channel used by drum voices — avoids collision with synth voices. */
    private static final int MIXER_CHANNEL_OFFSET = 4;

    private final DrumVoice[] voices = new DrumVoice[4];

    /**
     * Construct the DrumEngine, creating and initialising all four drum voices.
     *
     * @param engine the {@link AudioEngine} whose synth and master mixer will host the voices
     */
    public DrumEngine(AudioEngine engine) {
        voices[DrumTrack.KICK.ordinal()]  = new KickVoice();
        voices[DrumTrack.SNARE.ordinal()] = new SnareVoice();
        voices[DrumTrack.HAT.ordinal()]   = new HatVoice();
        voices[DrumTrack.PERC.ordinal()]  = new PercVoice();

        for (DrumVoice voice : voices) {
            voice.init(engine.getSynth());
        }

        // Connect each voice's output to a dedicated master mixer channel
        for (int i = 0; i < voices.length; i++) {
            voices[i].getOutput().connect(
                0,
                engine.getMasterMixer().input,
                MIXER_CHANNEL_OFFSET + i
            );
        }
    }

    /**
     * Trigger a drum voice with the given velocity.
     *
     * @param track    the {@link DrumTrack} to trigger
     * @param velocity note velocity (0.0–1.0)
     */
    public void trigger(DrumTrack track, double velocity) {
        voices[track.ordinal()].trigger(velocity);
    }

    /**
     * Apply a patch to a specific drum voice.
     *
     * @param track the {@link DrumTrack} whose voice should receive the patch
     * @param patch the {@link DrumPatch} to apply
     */
    public void applyPatch(DrumTrack track, DrumPatch patch) {
        voices[track.ordinal()].applyPatch(patch);
    }
}
