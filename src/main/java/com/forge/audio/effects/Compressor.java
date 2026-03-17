package com.forge.audio.effects;

import com.forge.audio.engine.AudioEngine;
import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.UnitFilter;

/**
 * Simple feed-forward RMS compressor.
 *
 * <p>Algorithm per sample:
 * <ol>
 *   <li>Compute RMS envelope over a sliding ~10ms window</li>
 *   <li>Convert to dB; if above threshold compute gain reduction</li>
 *   <li>Smooth gain reduction via attack/release coefficients</li>
 *   <li>Apply smoothed gain and makeup gain to the signal</li>
 * </ol>
 *
 * <p>When disabled, the signal passes through unchanged.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code threshold}  — threshold in dB, -60 to 0, default -20</li>
 *   <li>{@code ratio}      — compression ratio, 1–20, default 4</li>
 *   <li>{@code attack}     — attack time in ms, 1–100, default 10</li>
 *   <li>{@code release}    — release time in ms, 10–500, default 100</li>
 *   <li>{@code makeupGain} — makeup gain in dB, 0–20, default 0</li>
 * </ul>
 */
public class Compressor implements Effect {

    private static final int SAMPLE_RATE = AudioEngine.SAMPLE_RATE;

    // ---- Parameters ---------------------------------------------------------
    private double threshold  = -20.0;
    private double ratio      = 4.0;
    private double attackMs   = 10.0;
    private double releaseMs  = 100.0;
    private double makeupGain = 0.0;
    private boolean enabled   = true;

    // ---- JSyn unit ----------------------------------------------------------
    private CompressorFilter compressorFilter;

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        compressorFilter = new CompressorFilter(SAMPLE_RATE);
        synth.add(compressorFilter);
        syncParams();
    }

    @Override
    public UnitInputPort getInput() {
        return compressorFilter.input;
    }

    @Override
    public UnitOutputPort getOutput() {
        return compressorFilter.output;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (compressorFilter != null) compressorFilter.enabledParam = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setParam(String name, double value) {
        switch (name) {
            case "threshold"  -> threshold  = Math.max(-60.0, Math.min(0.0, value));
            case "ratio"      -> ratio      = Math.max(1.0, Math.min(20.0, value));
            case "attack"     -> attackMs   = Math.max(1.0, Math.min(100.0, value));
            case "release"    -> releaseMs  = Math.max(10.0, Math.min(500.0, value));
            case "makeupGain" -> makeupGain = Math.max(0.0, Math.min(20.0, value));
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        }
        syncParams();
    }

    @Override
    public double getParam(String name) {
        return switch (name) {
            case "threshold"  -> threshold;
            case "ratio"      -> ratio;
            case "attack"     -> attackMs;
            case "release"    -> releaseMs;
            case "makeupGain" -> makeupGain;
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        };
    }

    // =========================================================================

    private void syncParams() {
        if (compressorFilter == null) return;
        compressorFilter.thresholdDb   = threshold;
        compressorFilter.ratioParam    = ratio;
        compressorFilter.attackCoeff   = Math.exp(-1.0 / (attackMs  * 0.001 * SAMPLE_RATE));
        compressorFilter.releaseCoeff  = Math.exp(-1.0 / (releaseMs * 0.001 * SAMPLE_RATE));
        compressorFilter.makeupLinear  = dbToLinear(makeupGain);
        compressorFilter.enabledParam  = enabled;
    }

    private static double dbToLinear(double db) {
        return Math.pow(10.0, db / 20.0);
    }

    // =========================================================================
    // Inner: compressor as a JSyn UnitFilter
    // =========================================================================

    static class CompressorFilter extends UnitFilter {

        volatile double  thresholdDb   = -20.0;
        volatile double  ratioParam    = 4.0;
        volatile double  attackCoeff   = 0.99;
        volatile double  releaseCoeff  = 0.999;
        volatile double  makeupLinear  = 1.0;
        volatile boolean enabledParam  = true;

        private final int    sampleRate;
        /** RMS envelope: running sum of squared samples. */
        private double       rmsSumSq   = 0.0;
        private final int    rmsWindow;
        private final float[] rmsBuffer;
        private int           rmsPos    = 0;
        /** Smoothed gain reduction (linear). */
        private double       gainSmooth = 1.0;

        CompressorFilter(int sampleRate) {
            this.sampleRate = sampleRate;
            // ~10ms RMS window
            rmsWindow = Math.max(1, sampleRate / 100);
            rmsBuffer = new float[rmsWindow];
        }

        @Override
        public void generate(int start, int limit) {
            double[] ins  = input.getValues();
            double[] outs = output.getValues();

            if (!enabledParam) {
                // Bypass
                System.arraycopy(ins, start, outs, start, limit - start);
                return;
            }

            double thrDb    = thresholdDb;
            double ratio    = ratioParam;
            double atk      = attackCoeff;
            double rel      = releaseCoeff;
            double makeup   = makeupLinear;

            for (int i = start; i < limit; i++) {
                double in = ins[i];

                // --- RMS envelope ---
                double old = rmsBuffer[rmsPos];
                rmsBuffer[rmsPos] = (float) in;
                rmsPos = (rmsPos + 1) % rmsWindow;
                rmsSumSq = rmsSumSq - old * old + in * in;
                double rms = Math.sqrt(Math.max(0.0, rmsSumSq) / rmsWindow);

                // --- Gain computation ---
                double targetGain;
                if (rms < 1e-10) {
                    targetGain = 1.0;
                } else {
                    double levelDb = 20.0 * Math.log10(rms);
                    if (levelDb > thrDb) {
                        double reduction = (levelDb - thrDb) * (1.0 - 1.0 / ratio);
                        targetGain = Math.pow(10.0, -reduction / 20.0);
                    } else {
                        targetGain = 1.0;
                    }
                }

                // --- Attack / release smoothing ---
                if (targetGain < gainSmooth) {
                    gainSmooth = atk * gainSmooth + (1.0 - atk) * targetGain;
                } else {
                    gainSmooth = rel * gainSmooth + (1.0 - rel) * targetGain;
                }

                outs[i] = in * gainSmooth * makeup;
            }
        }
    }
}
