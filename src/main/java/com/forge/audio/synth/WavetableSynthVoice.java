package com.forge.audio.synth;

import com.forge.model.SynthPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.UnitGenerator;

/**
 * A wavetable synthesis voice for the FORGE Groovebox.
 *
 * <p>Signal chain:
 * <pre>
 *  [WavetableOsc] ──→ [FilterStateVariable] ──→ [Multiply / amp env] ──→ output
 * </pre>
 *
 * <p>The {@code morph} parameter (0.0–1.0) selects a position in the {@link WavetableBank}.
 * Values between table boundaries crossfade linearly between the two adjacent tables.
 *
 * <p>The internal {@link WavetableOsc} is a custom {@link UnitGenerator} that maintains
 * a phase accumulator and reads from the active wavetable pair using linear interpolation.
 */
public class WavetableSynthVoice implements SynthVoice {

    // =========================================================================
    // Inner class: custom wavetable oscillator UnitGenerator
    // =========================================================================

    /**
     * A JSyn {@link UnitGenerator} that reads audio from a {@link WavetableBank} with
     * per-sample linear interpolation and table crossfading.
     *
     * <p>Ports:
     * <ul>
     *   <li>{@code frequency} – oscillator frequency in Hz</li>
     *   <li>{@code amplitude} – output amplitude (0.0–1.0)</li>
     *   <li>{@code output}    – audio output</li>
     * </ul>
     *
     * <p>The wavetable and morph position are set directly via {@link #setMorph(double)} and
     * {@link #setBank(WavetableBank)} because they need to update atomically (no audio-rate
     * modulation is required for these parameters).
     */
    static class WavetableOsc extends UnitGenerator {

        public final UnitInputPort  frequency;
        public final UnitInputPort  amplitude;
        public final UnitOutputPort output;

        // Phase accumulator in the range [0, TABLE_SIZE)
        private double phase = 0.0;

        // Wavetable state — written from audio thread via setMorph/setBank
        private volatile WavetableBank bank;
        private volatile float[] tableA;   // lower table for the current morph position
        private volatile float[] tableB;   // upper table (tableA when at exact integer position)
        private volatile double  morphFrac; // blend factor 0.0 = fully tableA, 1.0 = fully tableB

        WavetableOsc() {
            frequency = new UnitInputPort("Frequency", 440.0);
            amplitude = new UnitInputPort("Amplitude", 1.0);
            output    = new UnitOutputPort("Output");

            addPort(frequency);
            addPort(amplitude);
            addPort(output);
        }

        /** Update the active bank and morph position. Call from any thread. */
        void setBank(WavetableBank newBank) {
            bank = newBank;
            // Re-apply morph to pick the correct tables from the new bank
            applyMorphToBank(morphFrac, newBank);
        }

        /**
         * Set the morph position in [0.0, 1.0].
         * 0.0 = first table, 1.0 = last table in the current bank.
         */
        void setMorph(double morph) {
            if (bank == null) return;
            applyMorphToBank(morph, bank);
        }

        private void applyMorphToBank(double morph, WavetableBank b) {
            if (b == null || b.getTableCount() == 0) return;

            int count     = b.getTableCount();
            double scaled = Math.max(0.0, Math.min(1.0, morph)) * (count - 1); // [0, count-1]
            int    idxA   = (int) scaled;
            int    idxB   = Math.min(idxA + 1, count - 1);

            tableA   = b.getTable(idxA);
            tableB   = b.getTable(idxB);
            morphFrac = scaled - idxA; // fractional part, 0.0 .. <1.0
        }

        @Override
        public void generate(int start, int limit) {
            double[] freqValues = frequency.getValues();
            double[] ampValues  = amplitude.getValues();
            double[] outValues  = output.getValues();

            // Capture volatile fields into locals for the inner loop
            float[] tA  = tableA;
            float[] tB  = tableB;
            double  mFr = morphFrac;

            if (tA == null || tB == null) {
                // Bank not yet initialised — output silence
                for (int i = start; i < limit; i++) {
                    outValues[i] = 0.0;
                }
                return;
            }

            final int tableSize = WavetableBank.TABLE_SIZE;

            for (int i = start; i < limit; i++) {
                // Phase increment: how far to step through the table per sample
                double freq      = freqValues[i];
                double increment = tableSize * freq / getSynthesisEngine().getFrameRate();

                // Advance and wrap phase
                phase += increment;
                while (phase >= tableSize) phase -= tableSize;
                while (phase <  0.0)       phase += tableSize;

                // Linear interpolation within the table
                int    idx0  = (int) phase;
                int    idx1  = (idx0 + 1) & (tableSize - 1); // wrap with bitmask (tableSize is 256)
                double frac  = phase - idx0;

                double sampleA = tA[idx0] + frac * (tA[idx1] - tA[idx0]);
                double sampleB = tB[idx0] + frac * (tB[idx1] - tB[idx0]);

                // Crossfade between the two tables according to morph fraction
                double sample = sampleA + mFr * (sampleB - sampleA);

                outValues[i] = sample * ampValues[i];
            }
        }
    }

    // =========================================================================
    // Signal-chain fields
    // =========================================================================

    private WavetableOsc            wavetableOsc;
    private FilterStateVariable     filter;
    private EnvelopeDAHDSR          ampEnv;
    private Multiply                ampMul;

    // ---- State ---------------------------------------------------------------

    private final WavetableBank bank = new WavetableBank();

    private double currentFrequencyHz = 440.0;
    private SynthPatch currentPatch;

    /**
     * True from noteOn until the amp envelope finishes its release phase.
     * Set synchronously so {@link #isActive()} returns true immediately after noteOn.
     */
    private volatile boolean gateOpen = false;

    // ---- MIDI → Hz -----------------------------------------------------------

    private static double midiToHz(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    // =========================================================================
    // SynthVoice implementation
    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        wavetableOsc = new WavetableOsc();
        filter       = new FilterStateVariable();
        ampEnv       = new EnvelopeDAHDSR();
        ampMul       = new Multiply();

        synth.add(wavetableOsc);
        synth.add(filter);
        synth.add(ampEnv);
        synth.add(ampMul);

        // Wire signal chain:
        //   wavetableOsc.output → filter.input
        //   filter.lowPass      → ampMul.inputA
        //   ampEnv.output       → ampMul.inputB
        wavetableOsc.output.connect(filter.input);
        filter.lowPass.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        // Auto-disable amp envelope so isActive() transitions to false after release
        ampEnv.setupAutoDisable(ampEnv);

        // Provide the bank to the oscillator and set a default morph
        wavetableOsc.setBank(bank);

        // Apply default patch
        currentPatch = new SynthPatch();
        applyPatch(currentPatch);
    }

    @Override
    public void noteOn(int midiNote, double velocity) {
        currentFrequencyHz = midiToHz(midiNote);

        wavetableOsc.frequency.set(currentFrequencyHz);
        wavetableOsc.amplitude.set(1.0);

        ampEnv.amplitude.set(velocity);

        // Mark gate open BEFORE triggering the envelope so isActive() is true immediately
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
        this.currentPatch = patch;

        // Morph: map 0.0–1.0 to position in the wavetable bank
        wavetableOsc.setMorph(patch.wavetableMorph);

        // Re-apply current note frequency (detune not applicable here — one oscillator)
        wavetableOsc.frequency.set(currentFrequencyHz);

        // Filter
        applyFilterType(patch);
        filter.frequency.set(patch.filterCutoff);
        filter.resonance.set(patch.filterResonance);

        // Amp envelope
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

    private void applyFilterType(SynthPatch patch) {
        filter.lowPass.disconnect(ampMul.inputA);
        filter.bandPass.disconnect(ampMul.inputA);
        filter.highPass.disconnect(ampMul.inputA);

        UnitOutputPort chosen = switch (patch.filterType) {
            case LOW_PASS  -> filter.lowPass;
            case HIGH_PASS -> filter.highPass;
            case BAND_PASS -> filter.bandPass;
            case NOTCH     -> filter.lowPass;
        };
        chosen.connect(0, ampMul.inputA, 0);
    }
}
