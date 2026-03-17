package com.forge.audio.drums;

import com.forge.model.DrumPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.SineOscillator;
import com.jsyn.unitgen.WhiteNoise;

/**
 * Snare drum voice.
 *
 * <p>Signal chain:
 * <pre>
 *  [SineOsc] ──────────────────────────────────────────────────┐
 *                                                               ├─ [2ch Mixer] ─→ [Multiply/ampEnv] ─→ output
 *  [WhiteNoise] ─→ [FilterStateVariable (band-pass)] ──────────┘
 * </pre>
 *
 * <p>The sine body and filtered noise are mixed according to {@link DrumPatch#toneNoise}
 * (1.0 = all tone, 0.0 = all noise). The "snap" parameter controls attack sharpness:
 * higher snap = shorter attack.
 *
 * <p>Params read from {@link DrumPatch}: {@code pitch}, {@code toneNoise},
 * {@code snap}, {@code decay}.
 */
public class SnareVoice implements DrumVoice {

    // ---- Signal chain units -------------------------------------------------

    /** Sine body oscillator. */
    private SineOscillator bodyOsc;

    /** White noise source. */
    private WhiteNoise noise;

    /** Band-pass filter on the noise to give snare character. */
    private FilterStateVariable noiseFilter;

    /** Mixes body (ch0) and noise (ch1). */
    private MixerMono bodyMixer;

    /** Amp envelope — controls overall output level and decay. */
    private EnvelopeDAHDSR ampEnv;

    /** Final output multiplier: bodyMixer × ampEnv. */
    private Multiply ampMul;

    // ---- Patch state --------------------------------------------------------

    private double currentPitch     = 200.0; // Hz
    private double currentToneNoise = 1.0;   // 1 = all tone
    private double currentSnap      = 0.5;   // 0–1
    private double currentDecay     = 0.15;  // seconds

    /** Band-pass filter centre frequency for snare noise character. */
    private static final double NOISE_FILTER_FREQ = 2000.0;

    /** Maximum attack time (seconds) — for snap=0 (softly attacked). */
    private static final double MAX_ATTACK = 0.015;

    // =========================================================================
    // DrumVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        bodyOsc     = new SineOscillator();
        noise       = new WhiteNoise();
        noiseFilter = new FilterStateVariable();
        bodyMixer   = new MixerMono(2);
        ampEnv      = new EnvelopeDAHDSR();
        ampMul      = new Multiply();

        synth.add(bodyOsc);
        synth.add(noise);
        synth.add(noiseFilter);
        synth.add(bodyMixer);
        synth.add(ampEnv);
        synth.add(ampMul);

        // Noise → band-pass filter → mixer ch1
        noise.output.connect(noiseFilter.input);
        noiseFilter.frequency.set(NOISE_FILTER_FREQ);
        noiseFilter.resonance.set(1.5);
        noiseFilter.bandPass.connect(0, bodyMixer.input, 1);

        // Body sine → mixer ch0
        bodyOsc.output.connect(0, bodyMixer.input, 0);

        // Amp stage: bodyMixer × ampEnv
        bodyMixer.output.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        ampEnv.setupAutoDisable(ampEnv);

        // Sensible envelope defaults
        ampEnv.hold.set(0.0);
        ampEnv.sustain.set(0.0);
        ampEnv.release.set(0.02);

        bodyOsc.amplitude.set(1.0);
        noise.amplitude.set(1.0);

        applyPatch(new DrumPatch());
    }

    @Override
    public void trigger(double velocity) {
        ampEnv.amplitude.set(velocity);
        ampEnv.input.on();
    }

    @Override
    public void applyPatch(DrumPatch patch) {
        currentPitch     = patch.pitch;
        currentToneNoise = patch.toneNoise;
        currentSnap      = patch.snap;
        currentDecay     = patch.decay;

        bodyOsc.frequency.set(currentPitch);

        // snap: higher snap → shorter attack (more transient)
        // snap=1 → ~0.001s, snap=0 → MAX_ATTACK
        double attack = MAX_ATTACK * (1.0 - currentSnap) + 0.001;
        ampEnv.attack.set(attack);
        ampEnv.decay.set(currentDecay);

        // toneNoise: 1.0 = full tone, 0.0 = full noise
        bodyMixer.gain.set(0, currentToneNoise);
        bodyMixer.gain.set(1, 1.0 - currentToneNoise);
    }

    @Override
    public UnitOutputPort getOutput() {
        return ampMul.output;
    }
}
