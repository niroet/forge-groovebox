package com.forge.audio.drums;

import com.forge.model.DrumPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.SquareOscillatorBL;

/**
 * Hi-hat voice.
 *
 * <p>Signal chain:
 * <pre>
 *  [Sq1..Sq6] ─→ [6ch Mixer] ─→ [FilterStateVariable (high-pass)] ─→ [Multiply/ampEnv] ─→ output
 * </pre>
 *
 * <p>Six detuned square oscillators create the classic metallic cluster sound.
 * The cluster passes through a resonant high-pass filter whose cutoff is controlled by
 * {@link DrumPatch#toneNoise} (used here as "tone" / filter brightness).
 * The amp envelope decay is very short for closed hat ({@link DrumPatch#open} = false)
 * and medium-length for open hat ({@link DrumPatch#open} = true).
 *
 * <p>Params read from {@link DrumPatch}: {@code pitch} (scales all 6 frequencies),
 * {@code toneNoise} (high-pass filter cutoff), {@code decay}, {@code open}.
 */
public class HatVoice implements DrumVoice {

    // ---- Six detuned square oscillators -------------------------------------
    // Base ratios drawn from classic TR-808/TR-909 metallic clusters
    private static final double[] BASE_RATIOS = { 302.0, 375.0, 438.0, 523.0, 575.0, 627.0 };

    private SquareOscillatorBL[] oscs;

    // ---- Downstream units ---------------------------------------------------

    /** Mixes the six square oscillators. */
    private MixerMono oscMixer;

    /** Resonant high-pass filter shaping the metallic tone. */
    private FilterStateVariable hpFilter;

    /** Amp envelope — fast for closed hat, medium for open hat. */
    private EnvelopeDAHDSR ampEnv;

    /** Final multiplier: filter output × ampEnv. */
    private Multiply ampMul;

    // ---- Patch state --------------------------------------------------------

    private double  currentPitch = 1.0;   // frequency scale multiplier
    private double  currentTone  = 8000.0; // filter cutoff Hz
    private double  currentDecay = 0.05;   // closed hat default
    private boolean currentOpen  = false;

    /** Closed hat decay (seconds). */
    private static final double CLOSED_DECAY = 0.05;

    /** Open hat decay (seconds). */
    private static final double OPEN_DECAY = 0.4;

    // =========================================================================
    // DrumVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        oscs     = new SquareOscillatorBL[6];
        oscMixer = new MixerMono(6);
        hpFilter = new FilterStateVariable();
        ampEnv   = new EnvelopeDAHDSR();
        ampMul   = new Multiply();

        for (int i = 0; i < 6; i++) {
            oscs[i] = new SquareOscillatorBL();
            synth.add(oscs[i]);
            oscs[i].amplitude.set(1.0 / 6.0); // equal mix, prevent clipping
            oscs[i].output.connect(0, oscMixer.input, i);
        }

        synth.add(oscMixer);
        synth.add(hpFilter);
        synth.add(ampEnv);
        synth.add(ampMul);

        // oscMixer → high-pass filter → ampMul
        oscMixer.output.connect(hpFilter.input);
        hpFilter.resonance.set(1.8);
        hpFilter.highPass.connect(0, ampMul.inputA, 0);

        // Amp envelope → ampMul
        ampEnv.output.connect(0, ampMul.inputB, 0);

        ampEnv.setupAutoDisable(ampEnv);

        // Envelope defaults
        ampEnv.attack.set(0.001);
        ampEnv.hold.set(0.0);
        ampEnv.sustain.set(0.0);
        ampEnv.release.set(0.01);

        applyPatch(new DrumPatch());
    }

    @Override
    public void trigger(double velocity) {
        ampEnv.amplitude.set(velocity);
        ampEnv.input.on();
    }

    @Override
    public void applyPatch(DrumPatch patch) {
        currentPitch = patch.pitch;
        currentOpen  = patch.open;
        currentDecay = patch.decay;

        // pitch scales all 6 oscillator frequencies
        // patch.pitch is treated as a frequency scale factor relative to base ratios;
        // if patch.pitch is ~60Hz (default DrumPatch) we normalize it as a multiplier
        // against a nominal 60Hz "centre". Values above 60 raise pitch, below lower it.
        double pitchScale = patch.pitch / 60.0;
        for (int i = 0; i < 6; i++) {
            oscs[i].frequency.set(BASE_RATIOS[i] * pitchScale);
        }

        // toneNoise used as filter cutoff brightness: 1.0 = bright (high cutoff)
        // Map 0–1 to a reasonable HP cutoff range: 2000–12000 Hz
        double filterCutoff = 2000.0 + patch.toneNoise * 10000.0;
        hpFilter.frequency.set(filterCutoff);
        currentTone = filterCutoff;

        // Decay: if open flag set, use longer decay; otherwise use patch.decay (clamped short)
        if (currentOpen) {
            ampEnv.decay.set(Math.max(OPEN_DECAY, currentDecay));
        } else {
            ampEnv.decay.set(Math.min(CLOSED_DECAY * 2.0, currentDecay));
        }
    }

    @Override
    public UnitOutputPort getOutput() {
        return ampMul.output;
    }
}
