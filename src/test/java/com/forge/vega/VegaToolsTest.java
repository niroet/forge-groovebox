package com.forge.vega;

import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;
import com.forge.model.EffectType;
import com.forge.model.EffectParams;
import com.forge.model.EngineType;
import com.forge.model.FilterType;
import com.forge.model.Pattern;
import com.forge.model.ProjectState;
import com.forge.model.WaveShape;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VegaTools}.
 *
 * <p>Audio components (DrumEngine, VoiceAllocator, SequencerClock, StepSequencer,
 * EffectsChain) require a live JSyn synthesizer and cannot be constructed in a
 * headless test without spinning up audio hardware.  We pass {@code null} for those
 * and only exercise methods that operate purely on {@link ProjectState}.
 */
class VegaToolsTest {

    private ProjectState state;
    private VegaTools    tools;

    @BeforeEach
    void setUp() {
        state = new ProjectState();
        // Pass null for audio components — only state-only tool methods are tested here.
        tools = new VegaTools(state, null, null, null, null, null);
    }

    // =========================================================================
    // BPM
    // =========================================================================

    @Test
    void setBpmChangesState() {
        // SequencerClock is null so we stub out the call — the tools guard on clock being null
        // by calling clock.setBpm only when clock != null (see implementation note).
        // This test verifies the state side-effect.
        tools = new VegaTools(state, null, null, new StubClock(), null, null);

        String result = tools.setBPM(140.0);

        assertEquals(140.0, state.bpm, 0.001, "state.bpm should be updated");
        assertTrue(result.contains("140"), "Result should mention BPM value");
    }

    @Test
    void setBpmClampsToRange() {
        tools = new VegaTools(state, null, null, new StubClock(), null, null);

        tools.setBPM(5.0);   // below min
        assertEquals(20.0, state.bpm, 0.001, "BPM should be clamped to 20");

        tools.setBPM(999.0); // above max
        assertEquals(300.0, state.bpm, 0.001, "BPM should be clamped to 300");
    }

    // =========================================================================
    // Drum patterns
    // =========================================================================

    @Test
    void setDrumPatternParsesCorrectly() {
        String result = tools.setDrumPattern("KICK", "X---X---X---X---");

        assertNotNull(result);
        assertTrue(result.contains("KICK"), "Result should confirm the track");

        Pattern p = state.patterns[0];
        int t = DrumTrack.KICK.ordinal();

        // Steps 0, 4, 8, 12 should be active
        assertTrue(p.drumSteps[t][0].active,  "Step 0 should be active");
        assertFalse(p.drumSteps[t][1].active, "Step 1 should be inactive");
        assertFalse(p.drumSteps[t][2].active, "Step 2 should be inactive");
        assertFalse(p.drumSteps[t][3].active, "Step 3 should be inactive");
        assertTrue(p.drumSteps[t][4].active,  "Step 4 should be active");
        assertFalse(p.drumSteps[t][5].active, "Step 5 should be inactive");
        assertTrue(p.drumSteps[t][8].active,  "Step 8 should be active");
        assertTrue(p.drumSteps[t][12].active, "Step 12 should be active");
    }

    @Test
    void setDrumPatternAllDashes() {
        // First set something active
        tools.setDrumPattern("SNARE", "X-X-X-X-X-X-X-X-");
        // Now clear it
        String result = tools.setDrumPattern("SNARE", "----------------");
        assertNotNull(result);

        Pattern p = state.patterns[0];
        int t = DrumTrack.SNARE.ordinal();
        for (int i = 0; i < 16; i++) {
            assertFalse(p.drumSteps[t][i].active, "All steps should be inactive after all-dash pattern");
        }
    }

    @Test
    void setDrumPatternUnknownTrack() {
        String result = tools.setDrumPattern("BASS", "X---X---X---X---");
        assertNotNull(result);
        assertTrue(result.toLowerCase().contains("unknown"), "Should report unknown track");
    }

    @Test
    void setDrumPatternCaseInsensitive() {
        String result = tools.setDrumPattern("kick", "X---------------");
        assertFalse(result.toLowerCase().contains("unknown"), "Should accept lowercase track name");
        Pattern p = state.patterns[0];
        assertTrue(p.drumSteps[DrumTrack.KICK.ordinal()][0].active);
    }

    // =========================================================================
    // Oscillators
    // =========================================================================

    @Test
    void setOscAUpdatesState() {
        tools.setOscA("SAW", 5.0, 0.7);
        assertEquals(WaveShape.SAW, state.globalSynthPatch.oscAShape);
        assertEquals(5.0, state.globalSynthPatch.oscADetune, 0.001);
        assertEquals(0.7, state.globalSynthPatch.oscALevel, 0.001);
    }

    @Test
    void setOscBUpdatesState() {
        tools.setOscB("SQUARE", -7.0, 0.5);
        assertEquals(WaveShape.SQUARE, state.globalSynthPatch.oscBShape);
        assertEquals(-7.0, state.globalSynthPatch.oscBDetune, 0.001);
        assertEquals(0.5, state.globalSynthPatch.oscBLevel, 0.001);
    }

    @Test
    void setOscUnknownShape() {
        String result = tools.setOscA("BANJO", 0, 0.5);
        assertTrue(result.toLowerCase().contains("unknown"), "Should report unknown wave shape");
    }

    // =========================================================================
    // Filter
    // =========================================================================

    @Test
    void setFilterUpdatesState() {
        tools.setFilter("HIGH_PASS", 2000.0, 0.5);
        assertEquals(FilterType.HIGH_PASS, state.globalSynthPatch.filterType);
        assertEquals(2000.0, state.globalSynthPatch.filterCutoff, 0.1);
        assertEquals(0.5, state.globalSynthPatch.filterResonance, 0.001);
    }

    @Test
    void setFilterClampsCutoff() {
        tools.setFilter("LOW_PASS", -100.0, 0.0);
        assertEquals(20.0, state.globalSynthPatch.filterCutoff, 0.1, "Cutoff should clamp to 20Hz minimum");

        tools.setFilter("LOW_PASS", 99999.0, 0.0);
        assertEquals(20000.0, state.globalSynthPatch.filterCutoff, 0.1, "Cutoff should clamp to 20kHz maximum");
    }

    // =========================================================================
    // Envelope
    // =========================================================================

    @Test
    void setEnvelopeUpdatesState() {
        tools.setEnvelope(0.01, 0.2, 0.6, 0.5);
        assertEquals(0.01, state.globalSynthPatch.ampAttack,  0.0001);
        assertEquals(0.2,  state.globalSynthPatch.ampDecay,   0.0001);
        assertEquals(0.6,  state.globalSynthPatch.ampSustain, 0.0001);
        assertEquals(0.5,  state.globalSynthPatch.ampRelease, 0.0001);
    }

    // =========================================================================
    // Engine type
    // =========================================================================

    @Test
    void setEngineUpdatesState() {
        tools.setEngine("FM");
        assertEquals(EngineType.FM, state.globalSynthPatch.engineType);
    }

    @Test
    void setEngineUnknownType() {
        String result = tools.setEngine("FIZZBUZZ");
        assertTrue(result.toLowerCase().contains("unknown"), "Should report unknown engine type");
    }

    // =========================================================================
    // Synth pattern
    // =========================================================================

    @Test
    void setSynthPatternSetsNotes() {
        tools.setSynthPattern("60,62,0,65");
        Pattern p = state.patterns[0];
        assertTrue(p.synthSteps[0].active);
        assertEquals(60, p.synthSteps[0].midiNote);
        assertTrue(p.synthSteps[1].active);
        assertEquals(62, p.synthSteps[1].midiNote);
        assertFalse(p.synthSteps[2].active, "Note 0 should be a rest");
        assertTrue(p.synthSteps[3].active);
        assertEquals(65, p.synthSteps[3].midiNote);
    }

    // =========================================================================
    // Key + scale
    // =========================================================================

    @Test
    void setKeyAndScaleUpdatesState() {
        tools.setKeyAndScale("D", "MINOR");
        assertEquals("D", state.rootNote);
        assertEquals(com.forge.model.ScaleType.MINOR, state.scaleType);
    }

    // =========================================================================
    // Swing
    // =========================================================================

    @Test
    void setSwingUpdatesState() {
        tools = new VegaTools(state, null, null, new StubClock(), null, null);
        tools.setSwing(65.0);
        assertEquals(65.0, state.swing, 0.001);
    }

    @Test
    void setSwingClamps() {
        tools = new VegaTools(state, null, null, new StubClock(), null, null);
        tools.setSwing(30.0); // below 50
        assertEquals(50.0, state.swing, 0.001);
        tools.setSwing(90.0); // above 75
        assertEquals(75.0, state.swing, 0.001);
    }

    // =========================================================================
    // Stub helpers
    // =========================================================================

    /**
     * Minimal stub for {@link com.forge.audio.sequencer.SequencerClock} so we don't
     * need a real audio engine.
     */
    private static class StubClock extends com.forge.audio.sequencer.SequencerClock {
        @Override public void setBpm(double bpm)     { /* no-op */ }
        @Override public void setSwing(double swing) { /* no-op */ }
    }
}
