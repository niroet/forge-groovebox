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
 * Kick drum voice.
 *
 * <p>Signal chain:
 * <pre>
 *  pitchEnv ─→ Add(pitchBase + sweep) ─→ SineOsc.frequency
 *
 *  [SineOsc] ──────────────────────────┐
 *                                       ├─ [2ch Mixer] ─→ [Multiply/ampEnv] ─→ output
 *  [WhiteNoise] ─→ [Multiply/clickGate]─┘
 * </pre>
 *
 * <p>The sine oscillator pitch is swept from a high starting frequency (START_PITCH) down
 * to the configured final pitch via a fast-decaying pitch envelope. The envelope amplitude
 * is set to the sweep range (START_PITCH - currentPitch), and an Add unit sums this with
 * the constant base frequency so that as the envelope decays to zero the frequency lands
 * on currentPitch.
 *
 * <p>A noise burst provides the attack click transient. Soft saturation is applied via
 * {@code Math.tanh} in the velocity/amplitude computation.
 *
 * <p>Params read from {@link DrumPatch}: {@code pitch}, {@code clickLevel},
 * {@code decay}, {@code drive}.
 */
public class KickVoice implements DrumVoice {

    // ---- Signal chain units -------------------------------------------------

    /** Body oscillator — sine wave. */
    private SineOscillator bodyOsc;

    /**
     * Pitch sweep envelope — decays from sweepRange to 0.
     * Combined with pitchAdd (base + sweep) to give final oscillator frequency.
     */
    private EnvelopeDAHDSR pitchEnv;

    /**
     * Sums constant base pitch (inputA.set) and pitch envelope output (inputB).
     * Output is wired to bodyOsc.frequency.
     */
    private Add pitchAdd;

    /** White noise source for the attack click. */
    private WhiteNoise clickNoise;

    /** Gates the click noise: noise × clickEnv. */
    private Multiply clickGate;

    /** Very short envelope for the click burst (~2ms). */
    private EnvelopeDAHDSR clickEnv;

    /** Mixes body sine (ch0) and click noise (ch1). */
    private MixerMono bodyMixer;

    /** Amp envelope — controls overall output level and decay. */
    private EnvelopeDAHDSR ampEnv;

    /** Final output multiplier: bodyMixer × ampEnv. */
    private Multiply ampMul;

    // ---- Patch state --------------------------------------------------------

    private double currentPitch = 55.0;
    private double currentDrive = 0.0;

    /** Starting frequency for the pitch sweep (above final pitch). */
    private static final double START_PITCH = 200.0;

    /** Duration of the pitch sweep in seconds. */
    private static final double PITCH_DECAY = 0.04;

    /** Duration of the click noise burst in seconds. */
    private static final double CLICK_DECAY = 0.002;

    // =========================================================================
    // DrumVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        bodyOsc    = new SineOscillator();
        pitchEnv   = new EnvelopeDAHDSR();
        pitchAdd   = new Add();
        clickNoise = new WhiteNoise();
        clickGate  = new Multiply();
        clickEnv   = new EnvelopeDAHDSR();
        bodyMixer  = new MixerMono(2);
        ampEnv     = new EnvelopeDAHDSR();
        ampMul     = new Multiply();

        synth.add(bodyOsc);
        synth.add(pitchEnv);
        synth.add(pitchAdd);
        synth.add(clickNoise);
        synth.add(clickGate);
        synth.add(clickEnv);
        synth.add(bodyMixer);
        synth.add(ampEnv);
        synth.add(ampMul);

        // Pitch: Add(basePitch, pitchSweepEnv) → bodyOsc.frequency
        // pitchAdd.inputA is set to base pitch each trigger (constant during note)
        // pitchAdd.inputB receives pitchEnv output (decays from sweepRange to 0)
        pitchEnv.output.connect(0, pitchAdd.inputB, 0);
        pitchAdd.output.connect(bodyOsc.frequency);

        // Click: noise × clickEnv
        clickNoise.output.connect(0, clickGate.inputA, 0);
        clickEnv.output.connect(0, clickGate.inputB, 0);

        // Mix: channel 0 = body sine, channel 1 = click noise
        bodyOsc.output.connect(0, bodyMixer.input, 0);
        clickGate.output.connect(0, bodyMixer.input, 1);

        // Amp stage: bodyMixer × ampEnv
        bodyMixer.output.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        ampEnv.setupAutoDisable(ampEnv);

        // Pitch envelope: fast attack, short decay, no sustain → single sweep
        pitchEnv.attack.set(0.001);
        pitchEnv.hold.set(0.0);
        pitchEnv.decay.set(PITCH_DECAY);
        pitchEnv.sustain.set(0.0);
        pitchEnv.release.set(0.01);

        // Click envelope: 2ms burst
        clickEnv.attack.set(0.0005);
        clickEnv.hold.set(0.0);
        clickEnv.decay.set(CLICK_DECAY);
        clickEnv.sustain.set(0.0);
        clickEnv.release.set(0.001);

        // Amp envelope defaults
        ampEnv.attack.set(0.002);
        ampEnv.hold.set(0.0);
        ampEnv.sustain.set(0.0);
        ampEnv.release.set(0.05);

        bodyOsc.amplitude.set(1.0);
        clickNoise.amplitude.set(1.0);

        applyPatch(new DrumPatch());
    }

    @Override
    public void trigger(double velocity) {
        // Optional soft saturation via tanh
        double amp = velocity;
        if (currentDrive > 0.0) {
            amp = Math.tanh(velocity * (1.0 + currentDrive * 4.0));
        }
        ampEnv.amplitude.set(amp);
        clickEnv.amplitude.set(amp);

        // Pitch sweep: envelope decays from sweepRange → 0
        // pitchAdd = basePitch + env → starts at basePitch + sweepRange = START_PITCH,
        // decays to basePitch + 0 = currentPitch.
        double sweepRange = Math.max(0.0, START_PITCH - currentPitch);
        pitchEnv.amplitude.set(sweepRange);
        pitchAdd.inputA.set(currentPitch);

        ampEnv.input.on();
        pitchEnv.input.on();
        clickEnv.input.on();
    }

    @Override
    public void applyPatch(DrumPatch patch) {
        currentPitch = patch.pitch;
        currentDrive = patch.drive;

        ampEnv.decay.set(patch.decay);

        // Click level controls noise channel gain in body mixer
        bodyMixer.gain.set(0, 1.0);
        bodyMixer.gain.set(1, patch.clickLevel);

        // Pre-load base pitch into Add
        pitchAdd.inputA.set(currentPitch);
        double sweepRange = Math.max(0.0, START_PITCH - currentPitch);
        pitchEnv.amplitude.set(sweepRange);
    }

    @Override
    public UnitOutputPort getOutput() {
        return ampMul.output;
    }
}
