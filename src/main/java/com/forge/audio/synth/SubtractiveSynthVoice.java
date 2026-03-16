package com.forge.audio.synth;

import com.forge.model.FilterType;
import com.forge.model.SynthPatch;
import com.forge.model.WaveShape;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.PulseOscillatorBL;
import com.jsyn.unitgen.SawtoothOscillatorBL;
import com.jsyn.unitgen.SineOscillator;
import com.jsyn.unitgen.SquareOscillatorBL;
import com.jsyn.unitgen.TriangleOscillator;
import com.jsyn.unitgen.UnitOscillator;

/**
 * A two-oscillator subtractive synth voice with a state-variable filter and DAHDSR envelopes.
 *
 * <p>Signal chain:
 * <pre>
 *  [OSC-A] ─┐
 *            ├─ [2-ch Mixer] ─→ [FilterStateVariable] ─→ [Multiply/Amp env] ─→ output
 *  [OSC-B] ─┘
 * </pre>
 *
 * <p>Both oscillators can independently be set to SAW, SQUARE, PULSE, TRIANGLE, or SINE.
 * Waveform switching is done by maintaining one oscillator instance per waveform type per
 * slot, and connecting only the active one's output into the mixer.
 */
public class SubtractiveSynthVoice implements SynthVoice {

    // ---- Signal-chain units --------------------------------------------------

    // Oscillator A — one instance per waveform
    private SawtoothOscillatorBL oscA_Saw;
    private SquareOscillatorBL   oscA_Square;
    private PulseOscillatorBL    oscA_Pulse;
    private TriangleOscillator   oscA_Triangle;
    private SineOscillator       oscA_Sine;

    // Oscillator B — one instance per waveform
    private SawtoothOscillatorBL oscB_Saw;
    private SquareOscillatorBL   oscB_Square;
    private PulseOscillatorBL    oscB_Pulse;
    private TriangleOscillator   oscB_Triangle;
    private SineOscillator       oscB_Sine;

    // Currently active oscillator instances (pointers into the above)
    private UnitOscillator activeOscA;
    private UnitOscillator activeOscB;

    // Mixer combining osc A and B before the filter
    private MixerMono oscMixer; // 2-channel

    // Filter
    private FilterStateVariable filter;

    // Amp envelope — gates the output amplitude
    private EnvelopeDAHDSR ampEnv;

    // Filter envelope — modulates filter cutoff
    private EnvelopeDAHDSR filterEnv;

    // Final output stage: oscMixer → filter → ampMul (amplified by ampEnv)
    // We use Multiply to apply the amp envelope output as an amplitude scaler.
    private Multiply ampMul;

    // ---- Current patch state -------------------------------------------------

    private WaveShape currentOscAShape = WaveShape.SAW;
    private WaveShape currentOscBShape = WaveShape.SAW;

    // Base frequency for the current note (Hz), used when re-applying detune
    private double currentFrequencyHz = 440.0;

    // Patch reference kept so applyPatch can be called without re-triggering envelopes
    private SynthPatch currentPatch;

    /**
     * True from noteOn until the amp envelope finishes its release phase and becomes idle.
     * We combine this with {@code ampEnv.isEnabled()} because the gate command is queued
     * asynchronously and {@code isEnabled()} may still be false immediately after noteOn.
     */
    private volatile boolean gateOpen = false;

    // ---- MIDI → Hz conversion ------------------------------------------------

    private static double midiToHz(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    /** Convert semitone detune to a frequency multiplier via 2^(semitones/12). */
    private static double detuneMultiplier(double semitones) {
        return Math.pow(2.0, semitones / 12.0);
    }

    // =========================================================================
    // SynthVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        // -- Create all oscillators -------------------------------------------
        oscA_Saw      = new SawtoothOscillatorBL();
        oscA_Square   = new SquareOscillatorBL();
        oscA_Pulse    = new PulseOscillatorBL();
        oscA_Triangle = new TriangleOscillator();
        oscA_Sine     = new SineOscillator();

        oscB_Saw      = new SawtoothOscillatorBL();
        oscB_Square   = new SquareOscillatorBL();
        oscB_Pulse    = new PulseOscillatorBL();
        oscB_Triangle = new TriangleOscillator();
        oscB_Sine     = new SineOscillator();

        // -- Create downstream units ------------------------------------------
        oscMixer  = new MixerMono(2);
        filter    = new FilterStateVariable();
        ampEnv    = new EnvelopeDAHDSR();
        filterEnv = new EnvelopeDAHDSR();
        ampMul    = new Multiply();

        // -- Register everything with the synthesizer -------------------------
        synth.add(oscA_Saw);
        synth.add(oscA_Square);
        synth.add(oscA_Pulse);
        synth.add(oscA_Triangle);
        synth.add(oscA_Sine);

        synth.add(oscB_Saw);
        synth.add(oscB_Square);
        synth.add(oscB_Pulse);
        synth.add(oscB_Triangle);
        synth.add(oscB_Sine);

        synth.add(oscMixer);
        synth.add(filter);
        synth.add(ampEnv);
        synth.add(filterEnv);
        synth.add(ampMul);

        // -- Wire the fixed parts of the signal chain -------------------------
        // oscMixer ch0 = osc A, ch1 = osc B (connected below via connectOscToSlot)
        // oscMixer → filter → ampMul (osc side)
        oscMixer.output.connect(filter.input);
        // Filter envelope modulates filter cutoff
        filterEnv.output.connect(filter.frequency);
        // Filter output → ampMul input[0] (audio)
        filter.lowPass.connect(0, ampMul.inputA, 0);
        // Amp envelope → ampMul input[1] (amplitude)
        ampEnv.output.connect(0, ampMul.inputB, 0);

        // Auto-disable amp envelope when it reaches IDLE so voice can be reclaimed
        ampEnv.setupAutoDisable(ampEnv);

        // -- Connect default (SAW) oscillators --------------------------------
        activeOscA = oscA_Saw;
        activeOscB = oscB_Saw;
        activeOscA.output.connect(0, oscMixer.input, 0);
        activeOscB.output.connect(0, oscMixer.input, 1);

        // -- Apply a default patch to set reasonable initial values -----------
        currentPatch = new SynthPatch();
        applyPatch(currentPatch);
    }

    @Override
    public void noteOn(int midiNote, double velocity) {
        currentFrequencyHz = midiToHz(midiNote);

        // Set oscillator frequencies with detune
        double freqA = currentFrequencyHz * detuneMultiplier(currentPatch.oscADetune);
        double freqB = currentFrequencyHz * detuneMultiplier(currentPatch.oscBDetune);
        activeOscA.frequency.set(freqA);
        activeOscA.amplitude.set(currentPatch.oscALevel);
        activeOscB.frequency.set(freqB);
        activeOscB.amplitude.set(currentPatch.oscBLevel);

        // Scale amp envelope amplitude by velocity
        ampEnv.amplitude.set(velocity);

        // Mark gate open BEFORE triggering the envelope so isActive() returns true immediately
        gateOpen = true;

        // Trigger both envelopes
        ampEnv.input.on();
        filterEnv.input.on();
    }

    @Override
    public void noteOff() {
        // Clear gate first so polyphony tracking sees this voice as releasing
        gateOpen = false;
        ampEnv.input.off();
        filterEnv.input.off();
    }

    @Override
    public void applyPatch(SynthPatch patch) {
        this.currentPatch = patch;

        // -- Oscillator waveforms ---------------------------------------------
        if (patch.oscAShape != currentOscAShape) {
            switchOscillator(true, patch.oscAShape);
            currentOscAShape = patch.oscAShape;
        }
        if (patch.oscBShape != currentOscBShape) {
            switchOscillator(false, patch.oscBShape);
            currentOscBShape = patch.oscBShape;
        }

        // -- Oscillator levels ------------------------------------------------
        activeOscA.amplitude.set(patch.oscALevel);
        activeOscB.amplitude.set(patch.oscBLevel);

        // -- Oscillator frequencies (re-apply detune to current note) ---------
        double freqA = currentFrequencyHz * detuneMultiplier(patch.oscADetune);
        double freqB = currentFrequencyHz * detuneMultiplier(patch.oscBDetune);
        activeOscA.frequency.set(freqA);
        activeOscB.frequency.set(freqB);

        // -- Filter -----------------------------------------------------------
        applyFilterType(patch);
        filter.frequency.set(patch.filterCutoff);
        filter.resonance.set(patch.filterResonance);

        // -- Amp envelope -----------------------------------------------------
        ampEnv.attack.set(patch.ampAttack);
        ampEnv.decay.set(patch.ampDecay);
        ampEnv.sustain.set(patch.ampSustain);
        ampEnv.release.set(patch.ampRelease);

        // -- Filter envelope --------------------------------------------------
        filterEnv.attack.set(patch.filterAttack);
        filterEnv.decay.set(patch.filterDecay);
        filterEnv.sustain.set(patch.filterSustain);
        filterEnv.release.set(patch.filterRelease);
        // Scale filter envelope depth by envAmount; amplitude drives the cutoff modulation range
        filterEnv.amplitude.set(patch.filterCutoff * patch.filterEnvAmount);
    }

    @Override
    public boolean isActive() {
        // gateOpen is set synchronously in noteOn and cleared in noteOff.
        // ampEnv.isEnabled() is true while the envelope is running (attack → release),
        // and becomes false once the release phase completes (auto-disabled).
        // We OR them so isActive() is true from the instant of noteOn through end of release.
        return gateOpen || ampEnv.isEnabled();
    }

    @Override
    public UnitOutputPort getOutput() {
        return ampMul.output;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Switch the oscillator in slot A or B to the given waveform shape.
     * Disconnects the current oscillator from the mixer and connects the new one.
     *
     * @param isSlotA {@code true} for slot A, {@code false} for slot B
     * @param shape   the new waveform
     */
    private void switchOscillator(boolean isSlotA, WaveShape shape) {
        UnitOscillator current = isSlotA ? activeOscA : activeOscB;
        int mixerChannel = isSlotA ? 0 : 1;

        // Disconnect the currently active oscillator
        current.output.disconnect(0, oscMixer.input, mixerChannel);

        // Select the new oscillator instance
        UnitOscillator next = isSlotA ? oscForShapeA(shape) : oscForShapeB(shape);

        // Carry over frequency and amplitude
        next.frequency.set(current.frequency.get(0));
        next.amplitude.set(current.amplitude.get(0));

        // Connect new oscillator to mixer
        next.output.connect(0, oscMixer.input, mixerChannel);

        if (isSlotA) {
            activeOscA = next;
        } else {
            activeOscB = next;
        }
    }

    private UnitOscillator oscForShapeA(WaveShape shape) {
        return switch (shape) {
            case SAW      -> oscA_Saw;
            case SQUARE   -> oscA_Square;
            case PULSE    -> oscA_Pulse;
            case TRIANGLE -> oscA_Triangle;
            case SINE     -> oscA_Sine;
        };
    }

    private UnitOscillator oscForShapeB(WaveShape shape) {
        return switch (shape) {
            case SAW      -> oscB_Saw;
            case SQUARE   -> oscB_Square;
            case PULSE    -> oscB_Pulse;
            case TRIANGLE -> oscB_Triangle;
            case SINE     -> oscB_Sine;
        };
    }

    /**
     * Re-wire the filter output to the amp stage according to the requested filter type.
     * FilterStateVariable exposes lowPass, bandPass, and highPass simultaneously;
     * we just choose which port to route to the amp multiplier.
     */
    private void applyFilterType(SynthPatch patch) {
        // Disconnect all filter outputs from ampMul first
        filter.lowPass.disconnect(ampMul.inputA);
        filter.bandPass.disconnect(ampMul.inputA);
        filter.highPass.disconnect(ampMul.inputA);

        // Connect the desired output
        UnitOutputPort chosenPort = switch (patch.filterType) {
            case LOW_PASS  -> filter.lowPass;
            case HIGH_PASS -> filter.highPass;
            case BAND_PASS -> filter.bandPass;
            case NOTCH     -> filter.lowPass; // SVF notch = LP + HP; approximate with LP for now
        };
        chosenPort.connect(0, ampMul.inputA, 0);
    }
}
