package com.forge.audio.drums;

import com.forge.model.DrumPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.SineOscillator;
import com.jsyn.unitgen.WhiteNoise;

/**
 * General-purpose percussion voice.
 *
 * <p>Signal chain:
 * <pre>
 *  pitchEnv ─→ Add(pitchBase + sweep) ─→ SineOsc.frequency
 *
 *  [SineOsc] ──────────────────────────────────────────┐
 *                                                       ├─ [2ch Mixer] ─→ [Multiply/ampEnv] ─→ output
 *  [WhiteNoise] ─→ [Multiply/noiseGate] ───────────────┘
 * </pre>
 *
 * <p>A sine oscillator with a configurable pitch sweep models toms, rimshots, and
 * similar percussion. White noise is mixed in for texture.
 *
 * <p>Configurable for:
 * <ul>
 *   <li>Tom: low pitch (~80Hz), long decay (~0.4s), low noise (toneNoise ~0.9)</li>
 *   <li>Rimshot: high pitch (~400Hz), short decay (~0.05s), higher noise (toneNoise ~0.5)</li>
 *   <li>Claves: high pitch (~800Hz), very short decay (~0.02s), no noise (toneNoise ~1.0)</li>
 * </ul>
 *
 * <p>Params read from {@link DrumPatch}: {@code pitch}, {@code snap} (sweep amount 0–1),
 * {@code decay}, {@code toneNoise}.
 */
public class PercVoice implements DrumVoice {

    // ---- Signal chain units -------------------------------------------------

    /** Main body oscillator — sine wave. */
    private SineOscillator bodyOsc;

    /** Pitch sweep envelope — decays from sweepRange to 0. */
    private EnvelopeDAHDSR pitchEnv;

    /**
     * Sums constant base pitch (inputA) and pitch sweep envelope (inputB).
     * Output connected to bodyOsc.frequency.
     */
    private Add pitchAdd;

    /** White noise for texture. */
    private WhiteNoise noise;

    /** Gates the noise: noise × noiseEnv. */
    private Multiply noiseGate;

    /** Noise amplitude envelope — follows amp decay. */
    private EnvelopeDAHDSR noiseEnv;

    /** Mixes body sine (ch0) and noise (ch1). */
    private MixerMono bodyMixer;

    /** Amp envelope — controls overall output level and decay. */
    private EnvelopeDAHDSR ampEnv;

    /** Final multiplier: bodyMixer × ampEnv. */
    private Multiply ampMul;

    // ---- Patch state --------------------------------------------------------

    private double currentPitch     = 200.0;
    private double currentSweep     = 0.5;
    private double currentDecay     = 0.2;
    private double currentToneNoise = 1.0;

    /** Maximum sweep ratio above base pitch. */
    private static final double MAX_SWEEP_RATIO = 4.0;

    // =========================================================================
    // DrumVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        bodyOsc   = new SineOscillator();
        pitchEnv  = new EnvelopeDAHDSR();
        pitchAdd  = new Add();
        noise     = new WhiteNoise();
        noiseGate = new Multiply();
        noiseEnv  = new EnvelopeDAHDSR();
        bodyMixer = new MixerMono(2);
        ampEnv    = new EnvelopeDAHDSR();
        ampMul    = new Multiply();

        synth.add(bodyOsc);
        synth.add(pitchEnv);
        synth.add(pitchAdd);
        synth.add(noise);
        synth.add(noiseGate);
        synth.add(noiseEnv);
        synth.add(bodyMixer);
        synth.add(ampEnv);
        synth.add(ampMul);

        // Pitch: Add(basePitch, pitchSweepEnv) → bodyOsc.frequency
        pitchEnv.output.connect(0, pitchAdd.inputB, 0);
        pitchAdd.output.connect(bodyOsc.frequency);

        // Noise gating
        noise.output.connect(0, noiseGate.inputA, 0);
        noiseEnv.output.connect(0, noiseGate.inputB, 0);

        // Mix
        bodyOsc.output.connect(0, bodyMixer.input, 0);
        noiseGate.output.connect(0, bodyMixer.input, 1);

        // Amp stage
        bodyMixer.output.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        ampEnv.setupAutoDisable(ampEnv);

        // Pitch envelope: fast sweep
        pitchEnv.attack.set(0.001);
        pitchEnv.hold.set(0.0);
        pitchEnv.sustain.set(0.0);
        pitchEnv.release.set(0.01);

        // Noise envelope defaults
        noiseEnv.attack.set(0.001);
        noiseEnv.hold.set(0.0);
        noiseEnv.sustain.set(0.0);
        noiseEnv.release.set(0.01);

        // Amp envelope
        ampEnv.attack.set(0.002);
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
        noiseEnv.amplitude.set(velocity);

        // Pitch sweep: env decays from sweepRange → 0
        // pitchAdd = basePitch + env → starts at basePitch + sweepRange, decays to basePitch
        double sweepRange = currentPitch * (MAX_SWEEP_RATIO - 1.0) * currentSweep;
        pitchEnv.amplitude.set(sweepRange);
        pitchAdd.inputA.set(currentPitch);

        ampEnv.input.on();
        pitchEnv.input.on();
        noiseEnv.input.on();
    }

    @Override
    public void applyPatch(DrumPatch patch) {
        currentPitch     = patch.pitch;
        currentSweep     = patch.snap; // snap repurposed as sweep amount
        currentDecay     = patch.decay;
        currentToneNoise = patch.toneNoise;

        pitchAdd.inputA.set(currentPitch);
        double sweepRange = currentPitch * (MAX_SWEEP_RATIO - 1.0) * currentSweep;
        pitchEnv.amplitude.set(sweepRange);
        pitchEnv.decay.set(Math.min(currentDecay * 0.5, 0.1));

        ampEnv.decay.set(currentDecay);
        noiseEnv.decay.set(currentDecay * 0.5);

        // Tone/noise mix
        bodyMixer.gain.set(0, currentToneNoise);
        bodyMixer.gain.set(1, 1.0 - currentToneNoise);
    }

    @Override
    public UnitOutputPort getOutput() {
        return ampMul.output;
    }
}
