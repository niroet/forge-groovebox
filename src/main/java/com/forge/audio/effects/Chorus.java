package com.forge.audio.effects;

import com.forge.audio.engine.AudioEngine;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.UnitFilter;

/**
 * Chorus effect using a LFO-modulated delay line.
 *
 * <p>A single delay line is modulated by a sine LFO, sweeping the delay time
 * between a base delay and base+depth range. This produces the characteristic
 * pitch wobble of a chorus/flanger. A dry/wet mixer blends the result.
 *
 * <p>Signal chain:
 * <pre>
 *   input ──┬──→ [ModulatedDelay] ─→ [2ch DryWetMixer] ─→ output
 *           └──────────────────────────────────────────↗
 * </pre>
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code rate}  — LFO frequency in Hz, 0.1–5.0, default 0.5</li>
 *   <li>{@code depth} — modulation depth, 0.0–1.0, default 0.5</li>
 *   <li>{@code mix}   — dry/wet blend, 0.0–1.0, default 0.5</li>
 * </ul>
 */
public class Chorus implements Effect {

    private static final int    SAMPLE_RATE = AudioEngine.SAMPLE_RATE;
    /** Base delay in samples (5ms). */
    private static final int    BASE_DELAY_SAMPLES = (int) (SAMPLE_RATE * 0.005);
    /** Maximum extra modulation range in samples (25ms). */
    private static final int    MAX_MOD_SAMPLES    = (int) (SAMPLE_RATE * 0.025);
    /** Buffer large enough for base + full modulation range. */
    private static final int    BUFFER_SAMPLES     = BASE_DELAY_SAMPLES + MAX_MOD_SAMPLES + 64;

    // ---- Parameters ---------------------------------------------------------
    private double rate    = 0.5;
    private double depth   = 0.5;
    private double mix     = 0.5;
    private boolean enabled = true;

    // ---- JSyn units ---------------------------------------------------------
    private ChorusDelay chorusDelay;
    private MixerMono   dryWetMixer;

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        chorusDelay = new ChorusDelay(BUFFER_SAMPLES, SAMPLE_RATE);
        dryWetMixer = new MixerMono(2);

        synth.add(chorusDelay);
        synth.add(dryWetMixer);

        chorusDelay.dryOutput.connect(0, dryWetMixer.input, 0);
        chorusDelay.output.connect(0, dryWetMixer.input, 1);

        chorusDelay.rateParam  = rate;
        chorusDelay.depthParam = depth;
        applyGains();
    }

    @Override
    public UnitInputPort getInput() {
        return chorusDelay.input;
    }

    @Override
    public UnitOutputPort getOutput() {
        return dryWetMixer.output;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        applyGains();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setParam(String name, double value) {
        switch (name) {
            case "rate" -> {
                rate = Math.max(0.1, Math.min(5.0, value));
                if (chorusDelay != null) chorusDelay.rateParam = rate;
            }
            case "depth" -> {
                depth = Math.max(0.0, Math.min(1.0, value));
                if (chorusDelay != null) chorusDelay.depthParam = depth;
            }
            case "mix" -> {
                mix = Math.max(0.0, Math.min(1.0, value));
                applyGains();
            }
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        }
    }

    @Override
    public double getParam(String name) {
        return switch (name) {
            case "rate"  -> rate;
            case "depth" -> depth;
            case "mix"   -> mix;
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        };
    }

    // =========================================================================

    private void applyGains() {
        if (dryWetMixer == null) return;
        if (enabled) {
            dryWetMixer.gain.set(0, 1.0 - mix);
            dryWetMixer.gain.set(1, mix);
        } else {
            dryWetMixer.gain.set(0, 1.0);
            dryWetMixer.gain.set(1, 0.0);
        }
    }

    // =========================================================================
    // Inner: LFO-modulated delay line
    // =========================================================================

    /**
     * A circular-buffer delay line whose read pointer is modulated by a sine LFO.
     *
     * <p>The LFO phase is advanced per-sample. The delay time oscillates between
     * {@code BASE_DELAY_SAMPLES} and {@code BASE_DELAY_SAMPLES + depth * MAX_MOD_SAMPLES}.
     * The dry input is also exposed on {@code dryOutput} for the mixer.
     */
    static class ChorusDelay extends UnitFilter {

        final UnitOutputPort dryOutput;

        volatile double rateParam;
        volatile double depthParam;

        private final float[] buffer;
        private final int     bufSize;
        private int           writePos = 0;
        private double        lfoPhase = 0.0;
        private final double  sampleRate;

        ChorusDelay(int bufSize, int sampleRate) {
            buffer          = new float[bufSize];
            this.bufSize    = bufSize;
            this.sampleRate = sampleRate;
            dryOutput       = new UnitOutputPort("dryOutput");
            addPort(dryOutput);
        }

        @Override
        public void generate(int start, int limit) {
            double[] ins     = input.getValues();
            double[] outs    = output.getValues();
            double[] dryOuts = dryOutput.getValues();
            double   rate    = rateParam;
            double   depth   = depthParam;
            double   phaseInc = (2.0 * Math.PI * rate) / sampleRate;

            for (int i = start; i < limit; i++) {
                double in = ins[i];
                dryOuts[i] = in;

                // LFO: sine oscillating 0..1
                double lfoVal = 0.5 + 0.5 * Math.sin(lfoPhase);
                lfoPhase += phaseInc;
                if (lfoPhase > 2.0 * Math.PI) lfoPhase -= 2.0 * Math.PI;

                int modSamples = BASE_DELAY_SAMPLES + (int) (depth * MAX_MOD_SAMPLES * lfoVal);
                int readPos = (writePos - modSamples + bufSize) % bufSize;

                buffer[writePos] = (float) in;
                writePos = (writePos + 1) % bufSize;

                outs[i] = buffer[readPos];
            }
        }
    }
}
