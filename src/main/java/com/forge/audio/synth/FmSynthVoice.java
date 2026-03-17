package com.forge.audio.synth;

import com.forge.model.FilterType;
import com.forge.model.SynthPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Add;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.SineOscillator;

/**
 * A single FM synthesis voice.
 *
 * <p>Signal chain:
 * <pre>
 *  [Modulator Osc] ──→ [× fmDepth] ──→ [+ carrier base freq] ──→ [Carrier Osc]
 *       ↑ (fmFeedback)                                                    │
 *                                                                          ↓
 *                                              [FilterStateVariable] ──→ [× ampEnv] ──→ output
 * </pre>
 *
 * <p>The modulator's output is also fed back into its own frequency input, scaled by
 * {@code fmFeedback}, to produce the classic single-operator feedback FM sound.
 */
public class FmSynthVoice implements SynthVoice {

    // ---- Signal-chain units --------------------------------------------------

    /** The carrier oscillator — produces the audible output tone. */
    private SineOscillator carrier;

    /** The modulator oscillator — its output perturbs the carrier's frequency. */
    private SineOscillator modulator;

    /**
     * Scales the modulator output by {@code fmDepth} before adding to the carrier frequency.
     * inputA = modulator.output, inputB = fmDepth constant (set via set()).
     */
    private Multiply depthScaler;

    /**
     * Scales the modulator output by {@code fmFeedback} and feeds back into the modulator
     * frequency input.
     */
    private Multiply feedbackScaler;

    /**
     * Adds the depth-scaled modulator output to the carrier's base frequency.
     * inputA = carrier base frequency (constant), inputB = depthScaler.output.
     */
    private Add freqAdder;

    /** State-variable filter applied after the carrier. */
    private FilterStateVariable filter;

    /** DAHDSR amplitude envelope; gates the final output level. */
    private EnvelopeDAHDSR ampEnv;

    /**
     * Multiplies the filtered carrier signal by the amp envelope output to produce the
     * final gated audio. inputA = filter output, inputB = ampEnv.output.
     */
    private Multiply ampMul;

    // ---- Current patch state -------------------------------------------------

    /** Carrier base frequency for the active note (Hz). */
    private double currentFrequencyHz = 440.0;

    /** Current fm ratio (modulator freq = carrier freq × fmRatio). */
    private double currentFmRatio = 2.0;

    /** Patch reference, kept to allow re-application without re-triggering envelopes. */
    private SynthPatch currentPatch;

    /**
     * True from noteOn until the amp envelope finishes its release phase.
     * Mirrored from {@link SubtractiveSynthVoice} — the gate command is queued
     * asynchronously so {@code ampEnv.isEnabled()} may still be false immediately after noteOn.
     */
    private volatile boolean gateOpen = false;

    // ---- MIDI → Hz conversion ------------------------------------------------

    private static double midiToHz(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    // =========================================================================
    // SynthVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        // -- Create units ------------------------------------------------------
        carrier        = new SineOscillator();
        modulator      = new SineOscillator();
        depthScaler    = new Multiply();
        feedbackScaler = new Multiply();
        freqAdder      = new Add();
        filter         = new FilterStateVariable();
        ampEnv         = new EnvelopeDAHDSR();
        ampMul         = new Multiply();

        // -- Register with synthesizer ----------------------------------------
        synth.add(carrier);
        synth.add(modulator);
        synth.add(depthScaler);
        synth.add(feedbackScaler);
        synth.add(freqAdder);
        synth.add(filter);
        synth.add(ampEnv);
        synth.add(ampMul);

        // -- Wire signal chain -------------------------------------------------
        //
        // Modulator output → depthScaler (inputA)
        modulator.output.connect(0, depthScaler.inputA, 0);
        // depthScaler.output → freqAdder (inputB);  freqAdder.inputA is set as a constant
        depthScaler.output.connect(0, freqAdder.inputB, 0);
        // freqAdder.output → carrier frequency input
        freqAdder.output.connect(0, carrier.frequency, 0);

        // Feedback path: modulator.output → feedbackScaler (inputA)
        modulator.output.connect(0, feedbackScaler.inputA, 0);
        // feedbackScaler.output → modulator frequency input (additive feedback)
        feedbackScaler.output.connect(0, modulator.frequency, 0);

        // Carrier → filter → ampMul
        carrier.output.connect(filter.input);
        filter.lowPass.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        // Auto-disable envelope so isActive() transitions to false after release
        ampEnv.setupAutoDisable(ampEnv);

        // -- Apply default patch -----------------------------------------------
        currentPatch = new SynthPatch();
        applyPatch(currentPatch);
    }

    @Override
    public void noteOn(int midiNote, double velocity) {
        currentFrequencyHz = midiToHz(midiNote);

        // Set carrier base frequency via the adder's constant input
        freqAdder.inputA.set(currentFrequencyHz);

        // Set modulator base frequency; feedback will modulate around this value
        double modFreq = currentFrequencyHz * currentFmRatio;
        modulator.frequency.set(modFreq);

        // Carrier amplitude (the modulator drives FM, not amplitude)
        carrier.amplitude.set(1.0);

        // Scale amp envelope peak by velocity
        ampEnv.amplitude.set(velocity);

        // Mark gate open BEFORE triggering the envelope
        gateOpen = true;

        ampEnv.input.on();
    }

    @Override
    public void noteOff() {
        gateOpen = false;
        ampEnv.input.off();
    }

    @Override
    public void applyPatch(SynthPatch patch) {
        this.currentPatch   = patch;
        this.currentFmRatio = patch.fmRatio;

        // -- FM parameters ----------------------------------------------------
        // depthScaler.inputB is the fmDepth scalar
        depthScaler.inputB.set(patch.fmDepth);
        // feedbackScaler.inputB is the fmFeedback scalar
        feedbackScaler.inputB.set(patch.fmFeedback);

        // Update modulator frequency relative to current note
        double modFreq = currentFrequencyHz * currentFmRatio;
        modulator.frequency.set(modFreq);

        // Re-apply carrier base frequency
        freqAdder.inputA.set(currentFrequencyHz);

        // -- Filter -----------------------------------------------------------
        applyFilterType(patch);
        filter.frequency.set(patch.filterCutoff);
        filter.resonance.set(patch.filterResonance);

        // -- Amp envelope -----------------------------------------------------
        ampEnv.attack.set(patch.ampAttack);
        ampEnv.decay.set(patch.ampDecay);
        ampEnv.sustain.set(patch.ampSustain);
        ampEnv.release.set(patch.ampRelease);
    }

    @Override
    public boolean isActive() {
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
     * Re-wire the filter output to the amp stage according to the requested filter type.
     * FilterStateVariable exposes lowPass, bandPass, and highPass simultaneously.
     */
    private void applyFilterType(SynthPatch patch) {
        filter.lowPass.disconnect(ampMul.inputA);
        filter.bandPass.disconnect(ampMul.inputA);
        filter.highPass.disconnect(ampMul.inputA);

        UnitOutputPort chosenPort = switch (patch.filterType) {
            case LOW_PASS  -> filter.lowPass;
            case HIGH_PASS -> filter.highPass;
            case BAND_PASS -> filter.bandPass;
            case NOTCH     -> filter.lowPass; // approximate with LP
        };
        chosenPort.connect(0, ampMul.inputA, 0);
    }
}
