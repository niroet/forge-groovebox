package com.forge.audio.sequencer;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.engine.AudioEngine;
import com.forge.audio.synth.SubtractiveSynthVoice;
import com.forge.audio.synth.SynthVoice;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.ProjectState;
import com.forge.model.Section;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectionManagerTest {

    private AudioEngine audioEngine;
    private StepSequencer sequencer;
    private ProjectState state;
    private SectionManager manager;

    @BeforeEach
    void setUp() {
        audioEngine = new AudioEngine();
        audioEngine.start();

        DrumEngine drumEngine = new DrumEngine(audioEngine);

        SynthVoice[] voices = new SubtractiveSynthVoice[2];
        for (int i = 0; i < voices.length; i++) {
            SubtractiveSynthVoice v = new SubtractiveSynthVoice();
            v.init(audioEngine.getSynth());
            v.getOutput().connect(0, audioEngine.getMasterMixer().input, i);
            voices[i] = v;
        }
        VoiceAllocator allocator = new VoiceAllocator(voices);

        sequencer = new StepSequencer(drumEngine, allocator);
        state     = new ProjectState();
        manager   = new SectionManager(state, sequencer);
    }

    @AfterEach
    void tearDown() {
        if (audioEngine != null && audioEngine.isRunning()) {
            audioEngine.stop();
        }
    }

    // -------------------------------------------------------------------------
    // createSectionAddsToState
    // -------------------------------------------------------------------------

    /**
     * Creating a section must add it to {@code state.sections} and populate
     * its fields correctly.
     */
    @Test
    void createSectionAddsToState() {
        assertTrue(state.sections.isEmpty(), "Sections list should start empty");

        Section verse = manager.createSection("VERSE", 1, 8);

        assertEquals(1, state.sections.size(), "One section should be in state after createSection");
        assertSame(verse, state.sections.get(0), "Returned section should be the same object in state");
        assertEquals("VERSE", verse.name);
        assertEquals(1,       verse.patternIndex);
        assertEquals(8,       verse.barLength);
    }

    // -------------------------------------------------------------------------
    // queueSectionSwitchesOnBarEnd
    // -------------------------------------------------------------------------

    /**
     * Queue a second section while the first is active; the switch happens on
     * the very next bar boundary (not necessarily at the end of barLength).
     * This is the "queue on next bar" live-performance contract.
     */
    @Test
    void queueSectionSwitchesOnBarEnd() {
        // Create two sections
        Section intro = manager.createSection("INTRO", 0, 4); // plays 4 bars
        Section verse = manager.createSection("VERSE", 1, 4); // plays 4 bars

        // Activate INTRO: queue it then fire one onBarEnd so it switches in.
        // (No active section yet, so condition 3 fires immediately.)
        manager.queueSectionByIndex(0);
        manager.onBarEnd();

        assertSame(intro, manager.getActiveSection(), "INTRO should now be active");
        assertEquals(0,   manager.getCurrentBar(),   "Bar counter should reset to 0 on switch");

        // Queue VERSE — should switch on the very next bar boundary.
        manager.queueSectionByIndex(1);
        assertSame(verse, manager.getQueuedSection(), "VERSE should be queued");

        // Next onBarEnd: currentBar → 1 < barLength(4), condition 2 skips;
        // condition 3 fires (queuedSection set, no fill) → switch to VERSE.
        manager.onBarEnd();
        assertSame(verse, manager.getActiveSection(), "VERSE should be active after the next bar end");
        assertEquals(0,   manager.getCurrentBar(),   "Bar counter should reset on section switch");
        assertNull(manager.getQueuedSection(),        "Queued section should be cleared after switch");
    }

    // -------------------------------------------------------------------------
    // sectionLoopsWhenNoQueuedSection
    // -------------------------------------------------------------------------

    /**
     * When a section reaches its barLength with nothing queued, it should loop
     * (currentBar resets to 0) and remain the active section.
     */
    @Test
    void sectionLoopsWhenNoQueuedSection() {
        Section intro = manager.createSection("INTRO", 0, 2);

        // Activate INTRO
        manager.queueSectionByIndex(0);
        manager.onBarEnd(); // switches in → currentBar=0
        assertSame(intro, manager.getActiveSection());

        // Bar 1
        manager.onBarEnd(); // currentBar → 1, barLength=2 → not reached yet
        assertEquals(1, manager.getCurrentBar(), "Should be on bar 1");

        // Bar 2 — barLength reached, nothing queued → loop
        manager.onBarEnd(); // currentBar → 2 == barLength → reset to 0
        assertSame(intro, manager.getActiveSection(), "INTRO should still be active after loop");
        assertEquals(0,   manager.getCurrentBar(),   "Bar counter should reset to 0 on loop");
    }

    // -------------------------------------------------------------------------
    // queueSectionByIndexOutOfBoundsDoesNothing
    // -------------------------------------------------------------------------

    /**
     * An out-of-bounds index must not throw and must leave the queued section unchanged.
     */
    @Test
    void queueSectionByIndexOutOfBoundsDoesNothing() {
        manager.createSection("INTRO", 0, 4);

        assertDoesNotThrow(() -> manager.queueSectionByIndex(-1));
        assertDoesNotThrow(() -> manager.queueSectionByIndex(99));
        assertNull(manager.getQueuedSection(), "No section should be queued for invalid indices");
    }
}
