package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.FilterHighShelf;
import com.jsyn.unitgen.FilterLowShelf;
import com.jsyn.unitgen.FilterPeakingEQ;

/**
 * 3-band parametric EQ using JSyn's built-in biquad filter units.
 *
 * <p>Signal chain:
 * <pre>
 *   input ─→ [FilterLowShelf] ─→ [FilterPeakingEQ] ─→ [FilterHighShelf] ─→ output
 * </pre>
 *
 * <p>When disabled, all gains are set to 0 dB (unity), passing signal through.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code lowGain}  — low-shelf gain in dB, -12 to +12, default 0</li>
 *   <li>{@code midGain}  — mid-peak gain in dB, -12 to +12, default 0</li>
 *   <li>{@code midFreq}  — mid-peak centre frequency in Hz, 100–10000, default 1000</li>
 *   <li>{@code highGain} — high-shelf gain in dB, -12 to +12, default 0</li>
 * </ul>
 */
public class ParametricEQ implements Effect {

    private static final double DEFAULT_LOW_FREQ  = 200.0;
    private static final double DEFAULT_MID_FREQ  = 1000.0;
    private static final double DEFAULT_HIGH_FREQ = 5000.0;
    private static final double DEFAULT_Q         = 1.0;

    // ---- Parameters ---------------------------------------------------------
    private double lowGain  = 0.0;
    private double midGain  = 0.0;
    private double midFreq  = DEFAULT_MID_FREQ;
    private double highGain = 0.0;
    private boolean enabled = true;

    // ---- JSyn units ---------------------------------------------------------
    private FilterLowShelf  lowShelf;
    private FilterPeakingEQ midPeak;
    private FilterHighShelf highShelf;

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        lowShelf  = new FilterLowShelf();
        midPeak   = new FilterPeakingEQ();
        highShelf = new FilterHighShelf();

        synth.add(lowShelf);
        synth.add(midPeak);
        synth.add(highShelf);

        // Wire in series: lowShelf → midPeak → highShelf
        lowShelf.output.connect(midPeak.input);
        midPeak.output.connect(highShelf.input);

        // Set fixed frequencies for shelf bands
        lowShelf.frequency.set(DEFAULT_LOW_FREQ);
        highShelf.frequency.set(DEFAULT_HIGH_FREQ);

        // Set Q for mid-peak
        midPeak.frequency.set(DEFAULT_MID_FREQ);
        midPeak.Q.set(DEFAULT_Q);

        applyParams();
    }

    @Override
    public UnitInputPort getInput() {
        return lowShelf.input;
    }

    @Override
    public UnitOutputPort getOutput() {
        return highShelf.output;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        applyParams();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setParam(String name, double value) {
        switch (name) {
            case "lowGain"  -> lowGain  = Math.max(-12.0, Math.min(12.0, value));
            case "midGain"  -> midGain  = Math.max(-12.0, Math.min(12.0, value));
            case "midFreq"  -> midFreq  = Math.max(100.0, Math.min(10000.0, value));
            case "highGain" -> highGain = Math.max(-12.0, Math.min(12.0, value));
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        }
        applyParams();
    }

    @Override
    public double getParam(String name) {
        return switch (name) {
            case "lowGain"  -> lowGain;
            case "midGain"  -> midGain;
            case "midFreq"  -> midFreq;
            case "highGain" -> highGain;
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        };
    }

    // =========================================================================

    private void applyParams() {
        if (lowShelf == null) return;
        if (enabled) {
            // FilterBiquadShelf uses a gain port that accepts dB values
            lowShelf.gain.set(lowGain);
            midPeak.gain.set(midGain);
            midPeak.frequency.set(midFreq);
            highShelf.gain.set(highGain);
        } else {
            // Unity gain (0 dB) on all bands = transparent bypass
            lowShelf.gain.set(0.0);
            midPeak.gain.set(0.0);
            highShelf.gain.set(0.0);
        }
    }
}
