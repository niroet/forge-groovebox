package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.MixerMono;
import com.jsyn.unitgen.RoomReverb;

/**
 * Reverb effect backed by JSyn's {@link RoomReverb}.
 *
 * <p>RoomReverb is a plate-style reverb implemented as a JSyn {@code Circuit}. It provides
 * {@code time} (decay), {@code damping}, {@code diffusion}, and {@code multiTap} parameters.
 * We map our four public parameters onto these.
 *
 * <p>Signal chain:
 * <pre>
 *   input ──┬──→ [RoomReverb] ─→ [2ch DryWetMixer] ─→ output
 *           └───────────────────────────────────────↗
 * </pre>
 *
 * <p>Parameters:
 * <ul>
 *   <li>{@code roomSize} — controls reverb decay time, 0.0–1.0, default 0.5</li>
 *   <li>{@code damping}  — high-frequency damping, 0.0–1.0, default 0.5</li>
 *   <li>{@code mix}      — dry/wet blend, 0.0–1.0, default 0.3</li>
 *   <li>{@code width}    — diffusion amount, 0.0–1.0, default 0.5</li>
 * </ul>
 */
public class FreeverbReverb implements Effect {

    // JSyn RoomReverb time range: approximately 0.1 to 10.0 seconds
    private static final double TIME_MIN  = 0.1;
    private static final double TIME_MAX  = 10.0;

    // ---- Parameters ---------------------------------------------------------
    private double roomSize = 0.5;
    private double damping  = 0.5;
    private double mix      = 0.3;
    private double width    = 0.5;
    private boolean enabled = true;

    // ---- JSyn units ---------------------------------------------------------
    private RoomReverb roomReverb;
    private MixerMono  dryWetMixer;

    // =========================================================================

    @Override
    public void init(Synthesizer synth) {
        roomReverb  = new RoomReverb();
        dryWetMixer = new MixerMono(2);

        synth.add(roomReverb);
        synth.add(dryWetMixer);

        // Wire: reverb output → wet channel (ch1)
        roomReverb.output.connect(0, dryWetMixer.input, 1);

        // For dry path we need a tap on the input. RoomReverb's input port is the entry
        // point but we can't also connect it to the dry channel of the mixer (an input
        // port can only receive from one source). Instead we insert a pass-through unit
        // that fans the signal to both the reverb input and the dry mixer channel.
        // We use a DryTap UnitFilter (like Distortion's approach).
        DryTap dryTap = new DryTap();
        synth.add(dryTap);

        // Wire: dryTap.output → reverb input, dryTap.dryOutput → mixer ch0
        dryTap.output.connect(roomReverb.input);
        dryTap.dryOutput.connect(0, dryWetMixer.input, 0);

        // Store dryTap as our true input proxy
        this.inputProxy = dryTap;

        applyParams();
        applyGains();
    }

    // The actual input port is on dryTap, not roomReverb
    private DryTap inputProxy;

    @Override
    public UnitInputPort getInput() {
        return inputProxy.input;
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
            case "roomSize" -> {
                roomSize = Math.max(0.0, Math.min(1.0, value));
                applyParams();
            }
            case "damping" -> {
                damping = Math.max(0.0, Math.min(1.0, value));
                applyParams();
            }
            case "mix" -> {
                mix = Math.max(0.0, Math.min(1.0, value));
                applyGains();
            }
            case "width" -> {
                width = Math.max(0.0, Math.min(1.0, value));
                applyParams();
            }
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        }
    }

    @Override
    public double getParam(String name) {
        return switch (name) {
            case "roomSize" -> roomSize;
            case "damping"  -> damping;
            case "mix"      -> mix;
            case "width"    -> width;
            default -> throw new IllegalArgumentException("Unknown param: " + name);
        };
    }

    // =========================================================================

    private void applyParams() {
        if (roomReverb == null) return;
        // Map roomSize 0-1 → time TIME_MIN–TIME_MAX
        double time = TIME_MIN + roomSize * (TIME_MAX - TIME_MIN);
        roomReverb.time.set(time);
        roomReverb.damping.set(damping);
        roomReverb.diffusion.set(width);
        // multiTap: use the same as diffusion for extra density
        roomReverb.multiTap.set(width * 0.5);
    }

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
    // Inner: simple pass-through that also exposes a dry tap
    // =========================================================================

    /**
     * A UnitFilter that passes input to output and also mirrors input on a
     * secondary {@code dryOutput} port.
     */
    static class DryTap extends com.jsyn.unitgen.UnitFilter {

        final UnitOutputPort dryOutput;

        DryTap() {
            dryOutput = new UnitOutputPort("dryOutput");
            addPort(dryOutput);
        }

        @Override
        public void generate(int start, int limit) {
            double[] ins     = input.getValues();
            double[] outs    = output.getValues();
            double[] dryOuts = dryOutput.getValues();
            for (int i = start; i < limit; i++) {
                outs[i]    = ins[i];
                dryOuts[i] = ins[i];
            }
        }
    }
}
