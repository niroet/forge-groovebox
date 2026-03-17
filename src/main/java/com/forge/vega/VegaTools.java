package com.forge.vega;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.effects.EffectsChain;
import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.DrumPatch;
import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.EffectParams;
import com.forge.model.EffectType;
import com.forge.model.EngineType;
import com.forge.model.FilterType;
import com.forge.model.Pattern;
import com.forge.model.ProjectState;
import com.forge.model.SynthStep;
import com.forge.model.WaveShape;

import dev.langchain4j.agent.tool.Tool;

import java.util.HashMap;

/**
 * VEGA tool implementations that Claude can call via LangChain4j's {@code @Tool} mechanism.
 *
 * <p>Each method mutates the live audio state ({@link ProjectState} + audio components)
 * so changes are audible immediately.
 */
public class VegaTools {

    private final ProjectState   state;
    private final DrumEngine     drumEngine;
    private final VoiceAllocator voiceAllocator;
    private final SequencerClock clock;
    private final StepSequencer  sequencer;
    private final EffectsChain   effectsChain;

    public VegaTools(
            ProjectState   state,
            DrumEngine     drumEngine,
            VoiceAllocator voiceAllocator,
            SequencerClock clock,
            StepSequencer  sequencer,
            EffectsChain   effectsChain) {
        this.state          = state;
        this.drumEngine     = drumEngine;
        this.voiceAllocator = voiceAllocator;
        this.clock          = clock;
        this.sequencer      = sequencer;
        this.effectsChain   = effectsChain;
    }

    // =========================================================================
    // Synth engine
    // =========================================================================

    @Tool("Set the synthesis engine type. Valid values: SUBTRACTIVE, FM, WAVETABLE, GRANULAR")
    public String setEngine(String engineType) {
        EngineType et;
        try {
            et = EngineType.valueOf(engineType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown engine type: " + engineType + ". Use SUBTRACTIVE, FM, WAVETABLE, or GRANULAR.";
        }
        state.globalSynthPatch.engineType = et;
        return "Engine set to " + et.name();
    }

    @Tool("Set oscillator A parameters. waveShape: SINE, SAW, SQUARE, PULSE, or TRIANGLE. detuneCents: semitone offset. level: 0.0-1.0")
    public String setOscA(String waveShape, double detuneCents, double level) {
        WaveShape ws;
        try {
            ws = WaveShape.valueOf(waveShape.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown wave shape: " + waveShape + ". Use SINE, SAW, SQUARE, PULSE, or TRIANGLE.";
        }
        state.globalSynthPatch.oscAShape  = ws;
        state.globalSynthPatch.oscADetune = detuneCents;
        state.globalSynthPatch.oscALevel  = clamp01(level);
        return String.format("OSC-A: %s, detune %.1fct, level %.2f", ws.name(), detuneCents, level);
    }

    @Tool("Set oscillator B parameters. waveShape: SINE, SAW, SQUARE, PULSE, or TRIANGLE. detuneCents: semitone offset. level: 0.0-1.0")
    public String setOscB(String waveShape, double detuneCents, double level) {
        WaveShape ws;
        try {
            ws = WaveShape.valueOf(waveShape.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown wave shape: " + waveShape + ". Use SINE, SAW, SQUARE, PULSE, or TRIANGLE.";
        }
        state.globalSynthPatch.oscBShape  = ws;
        state.globalSynthPatch.oscBDetune = detuneCents;
        state.globalSynthPatch.oscBLevel  = clamp01(level);
        return String.format("OSC-B: %s, detune %.1fct, level %.2f", ws.name(), detuneCents, level);
    }

    @Tool("Set filter parameters. filterType: LOW_PASS, HIGH_PASS, BAND_PASS, or NOTCH. cutoffHz: filter cutoff frequency in Hz. resonance: 0.0-1.0")
    public String setFilter(String filterType, double cutoffHz, double resonance) {
        FilterType ft;
        try {
            ft = FilterType.valueOf(filterType.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return "Unknown filter type: " + filterType + ". Use LOW_PASS, HIGH_PASS, BAND_PASS, or NOTCH.";
        }
        state.globalSynthPatch.filterType       = ft;
        state.globalSynthPatch.filterCutoff     = Math.max(20.0, Math.min(20000.0, cutoffHz));
        state.globalSynthPatch.filterResonance  = clamp01(resonance);
        return String.format("Filter: %s, cutoff %.0fHz, reso %.2f", ft.name(), cutoffHz, resonance);
    }

    @Tool("Set amplitude envelope (ADSR). All values are in seconds. sustain is a level (0.0-1.0)")
    public String setEnvelope(double attack, double decay, double sustain, double release) {
        state.globalSynthPatch.ampAttack  = Math.max(0.001, attack);
        state.globalSynthPatch.ampDecay   = Math.max(0.001, decay);
        state.globalSynthPatch.ampSustain = clamp01(sustain);
        state.globalSynthPatch.ampRelease = Math.max(0.001, release);
        return String.format("Envelope: A=%.3fs D=%.3fs S=%.2f R=%.3fs",
                attack, decay, sustain, release);
    }

    // =========================================================================
    // Transport
    // =========================================================================

    @Tool("Set the sequencer tempo in BPM (20-300)")
    public String setBPM(double bpm) {
        double clamped = Math.max(20.0, Math.min(300.0, bpm));
        if (clock != null) clock.setBpm(clamped);
        state.bpm = clamped;
        return "BPM: " + formatBpm(clamped);
    }

    @Tool("Set swing amount. 50 = straight (no swing), 75 = maximum swing (triplet feel)")
    public String setSwing(double percent) {
        double clamped = Math.max(50.0, Math.min(75.0, percent));
        if (clock != null) clock.setSwing(clamped);
        state.swing = clamped;
        return "Swing: " + (int) clamped + "%";
    }

    // =========================================================================
    // Drum patterns
    // =========================================================================

    @Tool("Set a 16-step drum pattern. track: KICK, SNARE, HAT, or PERC. steps: 16-character string of X (active) and - (silent), e.g. X---X---X---X---")
    public String setDrumPattern(String track, String steps) {
        DrumTrack dt;
        try {
            dt = DrumTrack.valueOf(track.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown drum track: " + track + ". Use KICK, SNARE, HAT, or PERC.";
        }
        if (steps == null || steps.isEmpty()) {
            return "Steps string is empty.";
        }

        Pattern p = state.patterns[state.activePatternIndex];
        int len = Math.min(steps.length(), 16);
        for (int i = 0; i < len; i++) {
            char c = steps.charAt(i);
            DrumStep ds = p.drumSteps[dt.ordinal()][i];
            ds.active = (c == 'X' || c == 'x');
            if (ds.active) {
                ds.velocity = 0.8;
            }
        }
        // Zero out any remaining steps if the provided string is shorter
        for (int i = len; i < 16; i++) {
            p.drumSteps[dt.ordinal()][i].active = false;
        }
        return dt.name() + " pattern set: " + steps.substring(0, len);
    }

    @Tool("Configure a drum voice patch. track: KICK, SNARE, HAT, or PERC. pitch: base frequency in Hz. decay: envelope decay in seconds. toneNoise: 0.0=noise, 1.0=pure tone")
    public String setDrumPatch(String track, double pitch, double decay, double toneNoise) {
        DrumTrack dt;
        try {
            dt = DrumTrack.valueOf(track.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown drum track: " + track + ". Use KICK, SNARE, HAT, or PERC.";
        }
        DrumPatch patch = state.drumPatches[dt.ordinal()];
        patch.pitch     = Math.max(20.0, pitch);
        patch.decay     = Math.max(0.01, decay);
        patch.toneNoise = clamp01(toneNoise);
        if (drumEngine != null) drumEngine.applyPatch(dt, patch);
        return String.format("%s patch: pitch=%.0fHz, decay=%.2fs, toneNoise=%.2f",
                dt.name(), pitch, decay, toneNoise);
    }

    // =========================================================================
    // Effects
    // =========================================================================

    @Tool("Enable or disable an effect. effectType: DISTORTION, DELAY, REVERB, CHORUS, COMPRESSOR, or EQ")
    public String toggleEffect(String effectType, boolean enabled) {
        EffectType et;
        try {
            et = EffectType.valueOf(effectType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown effect: " + effectType + ". Use DISTORTION, DELAY, REVERB, CHORUS, COMPRESSOR, or EQ.";
        }

        // Update model
        state.masterFx.computeIfAbsent(et, k -> new EffectParams()).enabled = enabled;

        // Update live audio chain (EffectsChain uses its own EffectType enum)
        if (effectsChain != null) {
            com.forge.audio.effects.EffectType audioEt =
                    com.forge.audio.effects.EffectType.valueOf(et.name());
            effectsChain.getEffect(audioEt).setEnabled(enabled);
        }

        return et.name() + ": " + (enabled ? "on" : "off");
    }

    @Tool("Set a parameter on an effect. effectType: DISTORTION, DELAY, REVERB, CHORUS, COMPRESSOR, or EQ. paramName and value are effect-specific")
    public String setEffectParam(String effectType, String paramName, double value) {
        EffectType et;
        try {
            et = EffectType.valueOf(effectType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Unknown effect: " + effectType + ". Use DISTORTION, DELAY, REVERB, CHORUS, COMPRESSOR, or EQ.";
        }

        // Update model
        EffectParams ep = state.masterFx.computeIfAbsent(et, k -> new EffectParams());
        ep.params.put(paramName, value);

        // Update live audio chain (EffectsChain uses its own EffectType enum)
        if (effectsChain != null) {
            try {
                com.forge.audio.effects.EffectType audioEt =
                        com.forge.audio.effects.EffectType.valueOf(et.name());
                effectsChain.getEffect(audioEt).setParam(paramName, value);
            } catch (IllegalArgumentException e) {
                return "Unknown param '" + paramName + "' for effect " + et.name() + ": " + e.getMessage();
            }
        }

        return String.format("%s.%s = %.3f", et.name(), paramName, value);
    }

    // =========================================================================
    // Track mutes
    // =========================================================================

    @Tool("Mute or unmute a track. track: KICK, SNARE, HAT, PERC, or SYNTH")
    public String setTrackMute(String track, boolean muted) {
        String upper = track.toUpperCase();
        int trackIndex;
        try {
            if (upper.equals("SYNTH")) {
                trackIndex = 4;
            } else {
                trackIndex = DrumTrack.valueOf(upper).ordinal();
            }
        } catch (IllegalArgumentException e) {
            return "Unknown track: " + track + ". Use KICK, SNARE, HAT, PERC, or SYNTH.";
        }
        if (sequencer != null) sequencer.setTrackMuted(trackIndex, muted);
        return upper + ": " + (muted ? "muted" : "unmuted");
    }

    // =========================================================================
    // Synth note sequence
    // =========================================================================

    @Tool("Set the synth step sequence. notes: comma-separated MIDI note numbers (0-127). Use 0 for a rest. Example: '60,62,0,65,67,0,0,0'")
    public String setSynthPattern(String notes) {
        if (notes == null || notes.isBlank()) {
            return "Notes string is empty.";
        }
        String[] parts = notes.split(",");
        Pattern p = state.patterns[state.activePatternIndex];

        int count = Math.min(parts.length, 16);
        for (int i = 0; i < count; i++) {
            String part = parts[i].trim();
            int note;
            try {
                note = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return "Invalid MIDI note at position " + i + ": '" + part + "'";
            }
            SynthStep ss = p.synthSteps[i];
            if (note == 0) {
                ss.active = false;
            } else {
                ss.active   = true;
                ss.midiNote = Math.max(0, Math.min(127, note));
                ss.velocity = 0.8;
            }
        }
        // Clear remaining steps if fewer notes provided
        for (int i = count; i < 16; i++) {
            p.synthSteps[i].active = false;
        }
        return "Synth pattern set: " + count + " steps";
    }

    // =========================================================================
    // Root note / scale
    // =========================================================================

    @Tool("Set the musical key and scale. rootNote: C, C#, D, D#, E, F, F#, G, G#, A, A#, B. scale: MAJOR, MINOR, DORIAN, PHRYGIAN, etc.")
    public String setKeyAndScale(String rootNote, String scale) {
        com.forge.model.ScaleType scaleType;
        try {
            scaleType = com.forge.model.ScaleType.valueOf(scale.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return "Unknown scale: " + scale + ". Use MAJOR, MINOR, DORIAN, PHRYGIAN, LYDIAN, MIXOLYDIAN, AEOLIAN, LOCRIAN, PENTATONIC_MAJOR, PENTATONIC_MINOR, BLUES, or CHROMATIC.";
        }
        state.rootNote = rootNote;
        state.scaleType = scaleType;
        return "Key: " + rootNote + " " + scaleType.name();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String formatBpm(double bpm) {
        if (bpm == Math.floor(bpm)) return String.valueOf((int) bpm);
        return String.format("%.1f", bpm);
    }
}
