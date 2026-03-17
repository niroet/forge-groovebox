package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.UnitFilter;
import com.jsyn.unitgen.MixerMono;

/**
 * Tanh waveshaper distortion effect.
 *
 * <p>Signal chain:
 * <pre>
 *   input ──┬──→ [TanhShaper(drive)] ─→ [2ch DryWetMixer] ─→ output
 *           └──────────────────────────────────────────────↗  (dry ch0, wet ch1)
 * </pre>
 *
 * <p>The waveshaper computes {@code tanh(input * drive)}. Mix blends dry (unprocessed)
 * and wet (distorted) signals. When disabled, wet gain is set to 0 and dry gain to 1,
 * producing a transparent bypass.
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code drive} — waveshaper drive amount, 1.0–20.0, default 1.0</li>
 *   <li>{@code mix}   — dry/wet blend, 0.0–1.0, default 1.0</li>
 * </ul>
 */
public class Distortion implements Effect {

    // ---- Parameters ---------------------------------------------------------
    private double drive = 1.0;
    private double mix   = 1.0;
    private boolean enabled = true;

    // ---- JSyn units ---------------------------------------------------------
    private TanhShaper shaper;
    private MixerMono  dryWetMixer; // ch0 = dry, ch1 = wet

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        shaper      = new TanhShaper();
        dryWetMixer = new MixerMono(2);

        synth.add(shaper);
        synth.add(dryWetMixer);

        // Wire: shaper output → mixer ch1 (wet)
        shaper.output.connect(0, dryWetMixer.input, 1);

        // Dry signal (shaper.input passes through unchanged)
        // We wire the input into both the shaper AND the dry mixer channel.
        // JSyn allows one output to fan out to multiple inputs, but an input
        // can only receive from one source. We route input → shaper, and
        // shaper feeds both the wet path and we access the dry signal via the
        // shaper's own UnitFilter.input port (which we also connect to mixer ch0).
        // Actually, we need to feed input directly into mixer ch0 for the dry path.
        // Since UnitFilter.input is UnitInputPort we must connect it once.
        // Solution: use shaper's input as THE input, then also connect shaper.input → dryWetMixer ch0.
        // JSyn does NOT support fan-out on input ports, but we can connect from shaper's
        // upstream output into both shaper.input and dryWetMixer ch0 — that is done
        // at chain-wiring time. However, within init() we only know shaper.input is our
        // entry point. The dry path will be wired by the caller after they connect their
        // signal to shaper.input by also connecting the same source to dryWetMixer ch0.
        //
        // Simpler pattern used by other voices: implement bypass by controlling the
        // mixer channel gains rather than by reconnecting ports.
        // The dry signal path requires access to the raw input. We solve this by
        // connecting shaper.input to dryWetMixer ch0 HERE so that any upstream signal
        // that connects to shaper.input automatically flows into both paths.
        // JSyn supports multiple listeners on an output, but an input can only feed one
        // destination. The canonical approach is to use a PassThrough for the dry leg.
        //
        // We implement this with a second UnitFilter subclass (DryTap) that simply
        // passes input to output, giving us a tappable dry node.
        //
        // Actually the cleanest approach: treat shaper.input as the real input port.
        // Implement the dry path by connecting shaper's input INSIDE the generate()
        // override: read inputs[0] (the raw sample), produce both the tanh-shaped
        // sample AND write the raw sample to a secondary output.
        // But UnitFilter only has one output port by default.
        //
        // FINAL DECISION: embed dry-tap logic inside TanhShaper itself.
        // TanhShaper will expose a second output port "dryOutput" that simply mirrors
        // the input. This is cleanest and avoids any port wiring complications.
        shaper.dryOutput.connect(0, dryWetMixer.input, 0);

        applyGains();
    }

    @Override
    public UnitInputPort getInput() {
        return shaper.input;
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
            case "drive" -> {
                drive = Math.max(1.0, Math.min(20.0, value));
                shaper.driveParam = drive;
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
            case "drive" -> drive;
            case "mix"   -> mix;
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        };
    }

    // =========================================================================
    // Private
    // =========================================================================

    private void applyGains() {
        if (dryWetMixer == null) return;
        if (enabled) {
            // ch0 = dry * (1 - mix), ch1 = wet * mix
            dryWetMixer.gain.set(0, 1.0 - mix);
            dryWetMixer.gain.set(1, mix);
        } else {
            // Bypass: full dry, no wet
            dryWetMixer.gain.set(0, 1.0);
            dryWetMixer.gain.set(1, 0.0);
        }
    }

    // =========================================================================
    // Inner class: TanhShaper UnitGenerator
    // =========================================================================

    /**
     * Custom JSyn UnitFilter that applies {@code tanh(input * drive)} to each sample
     * and also mirrors the raw input on a secondary {@code dryOutput} port.
     */
    static class TanhShaper extends UnitFilter {

        /** Secondary output port carrying the unprocessed (dry) signal. */
        final UnitOutputPort dryOutput;

        /** Drive amount — applied per sample. Written by the outer class. */
        volatile double driveParam = 1.0;

        TanhShaper() {
            // UnitFilter already declares: input (UnitInputPort), output (UnitOutputPort)
            dryOutput = new UnitOutputPort("dryOutput");
            addPort(dryOutput);
        }

        @Override
        public void generate(int start, int limit) {
            double[] inputs  = input.getValues();
            double[] outputs = output.getValues();
            double[] dryOuts = dryOutput.getValues();
            double d = driveParam;
            for (int i = start; i < limit; i++) {
                double in = inputs[i];
                dryOuts[i]  = in;
                outputs[i]  = Math.tanh(in * d);
            }
        }
    }
}
