package com.forge.vega;

import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.EffectParams;
import com.forge.model.EffectType;
import com.forge.model.Pattern;
import com.forge.model.ProjectState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Produces a compact text snapshot of the current groovebox state for injection
 * into VEGA's system prompt.
 *
 * <p>Example output:
 * <pre>
 * Engine: SUBTRACTIVE | BPM: 128 | Key: C MINOR | Swing: 50%
 * Pattern: A | Playing: yes | Step: 4/16
 * OSC-A: SAW, detune 0ct, level 0.8 | OSC-B: SQUARE, detune 0ct, level 0.5
 * Filter: LP, cutoff 8000Hz, reso 0.0
 * Drums: KICK[X---X---X---X---] SNARE[----X-------X---] HAT[X-X-X-X-X-X-X-X-] PERC[----------------]
 * FX: DIST(off) DELAY(off) REVERB(off) CHORUS(off) COMP(off) EQ(off)
 * Muted: none
 * </pre>
 */
public class GrooveboxState {

    private GrooveboxState() {}

    /**
     * Describe the groovebox in a compact single-screen block of text.
     *
     * @param state        the project data model
     * @param playing      whether the sequencer clock is currently running
     * @param currentStep  the currently-playing step (0-based), or -1 if stopped
     * @return a multi-line description string
     */
    public static String describe(ProjectState state, boolean playing, int currentStep) {
        if (state == null) {
            return "(no project loaded)";
        }

        StringBuilder sb = new StringBuilder();

        // -- Line 1: global params ------------------------------------------------
        String engine = state.globalSynthPatch != null
                ? state.globalSynthPatch.engineType.name()
                : "?";
        String key = state.rootNote + " " + state.scaleType.name().replace("_", " ");
        sb.append(String.format(
                "Engine: %s | BPM: %s | Key: %s | Swing: %s%%\n",
                engine,
                formatBpm(state.bpm),
                key,
                (int) state.swing
        ));

        // -- Line 2: pattern + transport ------------------------------------------
        String patName = patternLabel(state.activePatternIndex);
        String playingStr = playing ? "yes" : "no";
        int stepDisplay = (currentStep < 0) ? 0 : currentStep + 1;
        int patLen = (state.patterns != null && state.patterns[state.activePatternIndex] != null)
                ? state.patterns[state.activePatternIndex].synthStepCount
                : 16;
        sb.append(String.format(
                "Pattern: %s | Playing: %s | Step: %d/%d\n",
                patName, playingStr, stepDisplay, patLen
        ));

        // -- Line 3: oscillators --------------------------------------------------
        if (state.globalSynthPatch != null) {
            var p = state.globalSynthPatch;
            sb.append(String.format(
                    "OSC-A: %s, detune %sct, level %.1f | OSC-B: %s, detune %sct, level %.1f\n",
                    p.oscAShape.name(),
                    formatDetune(p.oscADetune),
                    p.oscALevel,
                    p.oscBShape.name(),
                    formatDetune(p.oscBDetune),
                    p.oscBLevel
            ));

            // -- Line 4: filter ---------------------------------------------------
            String fType = filterAbbrev(p.filterType.name());
            sb.append(String.format(
                    "Filter: %s, cutoff %sHz, reso %.1f\n",
                    fType,
                    (int) p.filterCutoff,
                    p.filterResonance
            ));
        }

        // -- Line 5: drum patterns ------------------------------------------------
        Pattern pat = (state.patterns != null) ? state.patterns[state.activePatternIndex] : null;
        if (pat != null) {
            sb.append("Drums:");
            for (DrumTrack track : DrumTrack.values()) {
                int t = track.ordinal();
                sb.append(" ").append(track.name()).append("[");
                DrumStep[] steps = pat.drumSteps[t];
                int len = pat.drumStepCounts[t];
                for (int s = 0; s < len; s++) {
                    sb.append(steps[s].active ? 'X' : '-');
                }
                sb.append("]");
            }
            sb.append("\n");
        }

        // -- Line 6: effects ------------------------------------------------------
        sb.append("FX:");
        Map<EffectType, EffectParams> fx = state.masterFx;
        for (EffectType et : EffectType.values()) {
            EffectParams ep = (fx != null) ? fx.get(et) : null;
            boolean on = (ep != null) && ep.enabled;
            sb.append(" ").append(fxAbbrev(et.name())).append("(").append(on ? "on" : "off").append(")");
        }
        sb.append("\n");

        // -- Line 7: muted tracks -------------------------------------------------
        // Note: mute state lives in StepSequencer, not ProjectState. We include a
        // placeholder here; VegaAgent passes actual mutes via VegaTools.
        sb.append("Muted: none");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String formatBpm(double bpm) {
        if (bpm == Math.floor(bpm)) {
            return String.valueOf((int) bpm);
        }
        return String.format("%.1f", bpm);
    }

    private static String formatDetune(double cents) {
        if (cents == 0.0) return "0";
        if (cents == Math.floor(cents)) return String.valueOf((int) cents);
        return String.format("%.1f", cents);
    }

    private static String patternLabel(int index) {
        // Map 0-25 to A-Z, then A2, B2, etc.
        if (index < 26) {
            return String.valueOf((char) ('A' + index));
        }
        return String.valueOf((char) ('A' + (index % 26))) + (index / 26 + 1);
    }

    private static String filterAbbrev(String filterType) {
        return switch (filterType) {
            case "LOW_PASS"  -> "LP";
            case "HIGH_PASS" -> "HP";
            case "BAND_PASS" -> "BP";
            case "NOTCH"     -> "NT";
            default          -> filterType;
        };
    }

    private static String fxAbbrev(String effectType) {
        return switch (effectType) {
            case "DISTORTION" -> "DIST";
            case "DELAY"      -> "DELAY";
            case "REVERB"     -> "REVERB";
            case "CHORUS"     -> "CHORUS";
            case "COMPRESSOR" -> "COMP";
            case "EQ"         -> "EQ";
            default           -> effectType;
        };
    }
}
