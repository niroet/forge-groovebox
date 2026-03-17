package com.forge.audio.effects;

import com.forge.audio.engine.AudioEngine;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.UnitFilter;

/**
 * Mono delay effect with feedback.
 *
 * <p>Implements a circular-buffer delay line:
 * <pre>
 *   out    = dry * (1 - mix) + delayed * mix
 *   delayed = buffer[readPos]
 *   buffer[writePos] = input + delayed * feedback
 * </pre>
 *
 * <p>Signal chain:
 * <pre>
 *   input ──┬──→ [DelayLine] ─→ [2ch DryWetMixer] ─→ output
 *           └────────────────────────────────────────↗
 * </pre>
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code time}     — delay time in ms, 10–2000, default 250</li>
 *   <li>{@code feedback} — feedback amount, 0.0–0.95, default 0.3</li>
 *   <li>{@code mix}      — dry/wet blend, 0.0–1.0, default 0.3</li>
 * </ul>
 */
public class StereoDelay implements Effect {

    private static final int    SAMPLE_RATE   = AudioEngine.SAMPLE_RATE;
    private static final int    MAX_DELAY_MS  = 2000;
    private static final int    MAX_SAMPLES   = SAMPLE_RATE * MAX_DELAY_MS / 1000;

    // ---- Parameters ---------------------------------------------------------
    private double timeMs    = 250.0;
    private double feedback  = 0.3;
    private double mix       = 0.3;
    private boolean enabled  = true;

    // ---- JSyn units ---------------------------------------------------------
    private DelayLine  delayLine;
    private MixerMono  dryWetMixer;

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        delayLine   = new DelayLine(MAX_SAMPLES);
        dryWetMixer = new MixerMono(2);

        synth.add(delayLine);
        synth.add(dryWetMixer);

        // Dry: delayLine.dryOutput → mixer ch0
        delayLine.dryOutput.connect(0, dryWetMixer.input, 0);
        // Wet: delayLine output → mixer ch1
        delayLine.output.connect(0, dryWetMixer.input, 1);

        // Apply initial params
        delayLine.delaySamples = (int) (SAMPLE_RATE * timeMs / 1000.0);
        delayLine.feedbackParam = feedback;
        applyGains();
    }

    @Override
    public UnitInputPort getInput() {
        return delayLine.input;
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
            case "time" -> {
                timeMs = Math.max(10.0, Math.min(MAX_DELAY_MS, value));
                if (delayLine != null) {
                    delayLine.delaySamples = (int) (SAMPLE_RATE * timeMs / 1000.0);
                }
            }
            case "feedback" -> {
                feedback = Math.max(0.0, Math.min(0.95, value));
                if (delayLine != null) delayLine.feedbackParam = feedback;
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
            case "time"     -> timeMs;
            case "feedback" -> feedback;
            case "mix"      -> mix;
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
    // Inner: circular-buffer delay line
    // =========================================================================

    /**
     * Circular-buffer delay line with feedback.
     *
     * <p>Reads a delayed sample, mixes it back with the input via {@code feedbackParam},
     * and writes to the buffer. The raw input is mirrored on {@code dryOutput} for the
     * dry/wet mixer.
     */
    static class DelayLine extends UnitFilter {

        final UnitOutputPort dryOutput;

        volatile int    delaySamples;
        volatile double feedbackParam;

        private final float[] buffer;
        private final int     bufferSize;
        private int           writePos = 0;

        DelayLine(int maxSamples) {
            buffer     = new float[maxSamples];
            bufferSize = maxSamples;
            dryOutput  = new UnitOutputPort("dryOutput");
            addPort(dryOutput);
        }

        @Override
        public void generate(int start, int limit) {
            double[] inputs  = input.getValues();
            double[] outputs = output.getValues();
            double[] dryOuts = dryOutput.getValues();
            int delay    = Math.max(1, Math.min(delaySamples, bufferSize - 1));
            double fb    = feedbackParam;

            for (int i = start; i < limit; i++) {
                double in = inputs[i];
                dryOuts[i] = in;

                int readPos = (writePos - delay + bufferSize) % bufferSize;
                double delayed = buffer[readPos];

                double written = in + delayed * fb;
                // Soft-clip the feedback path to prevent runaway
                written = Math.tanh(written);
                buffer[writePos] = (float) written;
                writePos = (writePos + 1) % bufferSize;

                outputs[i] = delayed;
            }
        }
    }
}
