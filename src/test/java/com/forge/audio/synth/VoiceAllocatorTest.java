package com.forge.audio.synth;

import com.forge.audio.engine.AudioEngine;
import com.forge.model.SynthPatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceAllocatorTest {

    private static final int VOICE_COUNT = 8;

    private AudioEngine    engine;
    private SynthVoice[]   voices;
    private VoiceAllocator allocator;

    @BeforeEach
    void setUp() {
        engine = new AudioEngine();
        engine.start();

        voices = new SubtractiveSynthVoice[VOICE_COUNT];
        for (int i = 0; i < VOICE_COUNT; i++) {
            SubtractiveSynthVoice v = new SubtractiveSynthVoice();
            v.init(engine.getSynth());
            // Connect to master mixer so the render graph is live and auto-disable fires.
            v.getOutput().connect(0, engine.getMasterMixer().input, i % engine.getMasterMixer().input.getNumParts());
            voices[i] = v;
        }
        allocator = new VoiceAllocator(voices);
    }

    @AfterEach
    void tearDown() {
        if (engine != null && engine.isRunning()) {
            engine.stop();
        }
    }

    @Test
    void allocatesVoiceOnNoteOn() {
        SynthVoice allocated = allocator.allocate(60, 0.8);
        assertNotNull(allocated, "allocate() must return a non-null voice");
        assertTrue(allocated.isActive(), "allocated voice must be active");
    }

    @Test
    void releasesCorrectVoice() {
        // Allocate two notes
        allocator.allocate(60, 0.8);
        allocator.allocate(64, 0.8);

        // Release note 60 — find which voice has note 60
        int voiceIdxBefore = -1;
        for (int i = 0; i < VOICE_COUNT; i++) {
            if (allocator.getVoiceNote(i) == 60) {
                voiceIdxBefore = i;
                break;
            }
        }
        assertNotEquals(-1, voiceIdxBefore, "note 60 must be assigned to a voice");

        allocator.releaseNote(60);

        // After releaseNote, the voiceNote slot must be cleared to FREE (-1)
        assertEquals(-1, allocator.getVoiceNote(voiceIdxBefore),
                "voice slot must be freed after releaseNote");
    }

    @Test
    void stealsOldestWhenFull() {
        // Fill all 8 voices
        for (int note = 60; note < 60 + VOICE_COUNT; note++) {
            allocator.allocate(note, 0.8);
        }

        // All 8 voices should be active
        int activeCount = 0;
        for (SynthVoice v : voices) {
            if (v.isActive()) activeCount++;
        }
        assertEquals(VOICE_COUNT, activeCount, "all voices must be active after filling pool");

        // Allocate a 9th note — this must steal a voice without throwing
        SynthVoice stolen = allocator.allocate(68, 0.8);
        assertNotNull(stolen, "must get a voice even when pool is full (voice stealing)");
        assertTrue(stolen.isActive(), "stolen/re-triggered voice must be active");

        // The note 68 must now be tracked somewhere in the allocator
        boolean note68Tracked = false;
        for (int i = 0; i < VOICE_COUNT; i++) {
            if (allocator.getVoiceNote(i) == 68) {
                note68Tracked = true;
                break;
            }
        }
        assertTrue(note68Tracked, "note 68 must be tracked after stealing a voice");
    }

    @Test
    void releaseAllSilencesEverything() {
        for (int note = 60; note < 64; note++) {
            allocator.allocate(note, 0.8);
        }

        allocator.releaseAll();

        // All voice note slots must be FREE
        for (int i = 0; i < VOICE_COUNT; i++) {
            assertEquals(-1, allocator.getVoiceNote(i),
                    "all voice slots must be FREE after releaseAll");
        }
    }

    @Test
    void retriggerSameNoteReusesVoice() {
        // Allocate note 60 twice — should reuse the same voice, not waste two slots
        SynthVoice first  = allocator.allocate(60, 0.8);
        SynthVoice second = allocator.allocate(60, 0.6);
        assertSame(first, second, "retriggering the same note must reuse the same voice");
    }

    @Test
    void constructorRejectsNullVoices() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceAllocator(null));
    }

    @Test
    void constructorRejectsEmptyVoices() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceAllocator(new SynthVoice[0]));
    }

    @Test
    void applyPatchToAllVoices() {
        SynthPatch patch = new SynthPatch();
        for (SynthVoice v : voices) {
            assertDoesNotThrow(() -> v.applyPatch(patch),
                    "applyPatch must not throw on any voice in the pool");
        }
    }

    @Test
    void replaceVoicesSwapsPool() {
        // Allocate a note on the original pool
        allocator.allocate(60, 0.8);

        // Build a fresh pool of FmSynthVoice
        SynthVoice[] newVoices = new SynthVoice[VOICE_COUNT];
        for (int i = 0; i < VOICE_COUNT; i++) {
            FmSynthVoice v = new FmSynthVoice();
            v.init(engine.getSynth());
            newVoices[i] = v;
        }

        allocator.releaseAll();
        allocator.replaceVoices(newVoices);

        // All slots must be free after replacement
        for (int i = 0; i < VOICE_COUNT; i++) {
            assertEquals(-1, allocator.getVoiceNote(i), "all slots must be FREE after replaceVoices");
        }

        // Allocation on new pool must work
        SynthVoice allocated = allocator.allocate(62, 0.7);
        assertNotNull(allocated, "must allocate from new voice pool");
        assertSame(allocated, newVoices[0], "allocated voice must be from new pool");
    }

    @Test
    void replaceVoicesRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> allocator.replaceVoices(null));
    }

    @Test
    void getVoicesReturnsCurrentPool() {
        SynthVoice[] poolRef = allocator.getVoices();
        assertSame(voices, poolRef, "getVoices must return the current pool reference");
    }
}
