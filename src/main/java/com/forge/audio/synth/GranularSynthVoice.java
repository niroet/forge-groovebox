package com.forge.audio.synth;

import com.forge.model.FilterType;
import com.forge.model.SynthPatch;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.EnvelopeDAHDSR;
import com.jsyn.unitgen.FilterStateVariable;
import com.jsyn.unitgen.Multiply;
import com.jsyn.unitgen.UnitGenerator;

import java.util.Random;

/**
 * A granular synthesis voice for the FORGE Groovebox.
 *
 * <p>Signal chain:
 * <pre>
 *  [GranularOsc UnitGenerator] ──→ [FilterStateVariable] ──→ [Multiply / amp env] ──→ output
 * </pre>
 *
 * <p>The internal {@link GranularOsc} maintains a 2-second ring-buffer source and
 * continuously spawns short grains from it according to the current density, size,
 * and scatter parameters. When {@link #freeze()} is called the source buffer is
 * snapshotted and grains read from the frozen copy until {@link #unfreeze()} is called.
 */
public class GranularSynthVoice implements SynthVoice {

    // =========================================================================
    // Inner class: custom granular oscillator UnitGenerator
    // =========================================================================

    /**
     * A JSyn {@link UnitGenerator} that implements the granular synthesis engine.
     *
     * <p>Ports:
     * <ul>
     *   <li>{@code frequency}  – current note frequency in Hz (used for saw wave generation)</li>
     *   <li>{@code amplitude}  – unused directly (amplitude is baked into grain envelope)</li>
     *   <li>{@code output}     – audio output</li>
     * </ul>
     */
    static class GranularOsc extends UnitGenerator {

        public final UnitInputPort  frequency;
        public final UnitInputPort  amplitude;
        public final UnitOutputPort output;

        // -- Source buffer (2 s at 44 100 Hz) ----------------------------------
        static final int SOURCE_BUFFER_LEN = 88_200;

        private final float[] sourceBuffer = new float[SOURCE_BUFFER_LEN];
        /** Frozen snapshot — only non-null while freeze is active. */
        private volatile float[] frozenBuffer = null;

        /** Write head for the ring-buffer oscillator fill. */
        private int writeHead = 0;

        /** Simple saw-wave phase accumulator for filling the source buffer (0.0–1.0). */
        private double sawPhase = 0.0;

        // -- Grain pool --------------------------------------------------------
        private final GrainPool grainPool = new GrainPool();
        private final Random    rng       = new Random();

        // -- Grain-spawn parameters (all written from the control thread) ------

        /**
         * Grain density in grains/second.  Volatile so that control-thread writes are
         * visible inside {@link #generate} without requiring synchronisation.
         */
        volatile double density      = 20.0;

        /** Grain size in milliseconds. */
        volatile double grainSizeMs  = 50.0;

        /**
         * Position scatter — half-width of the random window around {@code basePosition}
         * (0.0–1.0 of the source buffer length).
         */
        volatile double posScatter   = 0.1;

        /**
         * Pitch scatter — maximum random deviation from the note's playback rate
         * as a fraction of that rate (0.0–1.0).
         */
        volatile double pitchScatter = 0.0;

        /** Base read position in the source buffer (0.0–1.0). */
        volatile double basePosition = 0.5;

        // -- Spawn accumulator -------------------------------------------------
        private double spawnAccumulator = 0.0;

        // -- Base frequency for computing playback rate ------------------------
        private static final double BASE_FREQUENCY = 440.0;

        // -- Constructor -------------------------------------------------------

        GranularOsc() {
            frequency = new UnitInputPort("Frequency", 440.0);
            amplitude = new UnitInputPort("Amplitude", 1.0);
            output    = new UnitOutputPort("Output");

            addPort(frequency);
            addPort(amplitude);
            addPort(output);
        }

        // ---- Freeze / unfreeze -----------------------------------------------

        /**
         * Capture a snapshot of the current source buffer. Subsequent grains read
         * from this snapshot until {@link #unfreeze()} is called.
         */
        void freeze() {
            float[] snap = new float[SOURCE_BUFFER_LEN];
            System.arraycopy(sourceBuffer, 0, snap, 0, SOURCE_BUFFER_LEN);
            frozenBuffer = snap;
        }

        /** Resume live oscillator-fill mode. */
        void unfreeze() {
            frozenBuffer = null;
        }

        boolean isFrozen() {
            return frozenBuffer != null;
        }

        // ---- Audio callback --------------------------------------------------

        @Override
        public void generate(int start, int limit) {
            double[] freqValues = frequency.getValues();
            double[] outValues  = output.getValues();
            double   frameRate  = getSynthesisEngine().getFrameRate();

            // Decide which buffer grains will read from this block
            float[] readBuf = frozenBuffer;
            if (readBuf == null) readBuf = sourceBuffer;

            for (int i = start; i < limit; i++) {
                double freq = freqValues[i];

                // 1. Fill source buffer with a saw wave (unless frozen)
                if (frozenBuffer == null) {
                    double increment = freq / frameRate;
                    sawPhase += increment;
                    if (sawPhase >= 1.0) sawPhase -= 1.0;
                    // Saw: map [0,1) → [−1, +1)
                    sourceBuffer[writeHead] = (float) (2.0 * sawPhase - 1.0);
                    writeHead = (writeHead + 1) % SOURCE_BUFFER_LEN;
                }

                // 2. Grain spawning
                spawnAccumulator += density / frameRate;
                if (spawnAccumulator >= 1.0) {
                    spawnAccumulator -= 1.0;

                    double scatter = posScatter;
                    double pos     = basePosition + (rng.nextDouble() * 2.0 - 1.0) * scatter;
                    pos = Math.max(0.0, Math.min(1.0, pos));

                    double nominalRate  = freq / BASE_FREQUENCY;
                    double pitchDev     = (rng.nextDouble() * 2.0 - 1.0) * pitchScatter;
                    double rate         = nominalRate * (1.0 + pitchDev);

                    double amp          = (density > 0.0) ? 1.0 / Math.sqrt(density) : 1.0;

                    int grainSamples = Math.max(1, (int) (grainSizeMs * frameRate / 1000.0));

                    grainPool.activate((float) pos, (float) rate, (float) amp, grainSamples);
                }

                // 3. Sum all active grains
                outValues[i] = grainPool.processSample(readBuf);
            }
        }
    }

    // =========================================================================
    // Signal-chain fields
    // =========================================================================

    private GranularOsc         granularOsc;
    private FilterStateVariable filter;
    private EnvelopeDAHDSR      ampEnv;
    private Multiply            ampMul;

    // ---- State ---------------------------------------------------------------

    private double currentFrequencyHz = 440.0;
    private SynthPatch currentPatch;
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
        granularOsc = new GranularOsc();
        filter      = new FilterStateVariable();
        ampEnv      = new EnvelopeDAHDSR();
        ampMul      = new Multiply();

        synth.add(granularOsc);
        synth.add(filter);
        synth.add(ampEnv);
        synth.add(ampMul);

        // Wire signal chain:
        //   granularOsc.output → filter.input
        //   filter.lowPass     → ampMul.inputA
        //   ampEnv.output      → ampMul.inputB
        granularOsc.output.connect(filter.input);
        filter.lowPass.connect(0, ampMul.inputA, 0);
        ampEnv.output.connect(0, ampMul.inputB, 0);

        ampEnv.setupAutoDisable(ampEnv);

        currentPatch = new SynthPatch();
        applyPatch(currentPatch);
    }

    @Override
    public void noteOn(int midiNote, double velocity) {
        currentFrequencyHz = midiToHz(midiNote);
        granularOsc.frequency.set(currentFrequencyHz);
        ampEnv.amplitude.set(velocity);
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

        // Granular parameters
        granularOsc.grainSizeMs  = patch.granularSize;
        granularOsc.density      = Math.max(1.0, patch.granularDensity);
        granularOsc.posScatter   = patch.granularScatter;
        granularOsc.pitchScatter = patch.granularScatter * 0.5; // derived from scatter

        // Re-apply current note frequency
        granularOsc.frequency.set(currentFrequencyHz);

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
    // Granular-specific controls
    // =========================================================================

    /**
     * Freeze the source buffer. Grains continue to play back from the captured
     * snapshot until {@link #unfreeze()} is called.
     */
    public void freeze() {
        granularOsc.freeze();
    }

    /** Resume live oscillator-fill mode. */
    public void unfreeze() {
        granularOsc.unfreeze();
    }

    /** @return {@code true} if the source buffer is currently frozen. */
    public boolean isFrozen() {
        return granularOsc.isFrozen();
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
