package com.forge.audio.synth;

/**
 * Manages a fixed pool of {@link SynthVoice} instances, assigning them to MIDI notes
 * on demand and reclaiming them when notes are released.
 *
 * <p>Voice stealing priority (when all voices are busy):
 * <ol>
 *   <li>First free (inactive) voice</li>
 *   <li>Oldest voice whose envelope is in the release phase</li>
 *   <li>Oldest voice overall (longest running)</li>
 * </ol>
 */
public class VoiceAllocator {

    private static final int FREE = -1;

    private final SynthVoice[] voices;
    private final int[]  voiceNotes;       // MIDI note each voice is playing; FREE = available
    private final long[] voiceStartTimes;  // System.nanoTime() of the last noteOn for each voice

    /**
     * Construct a VoiceAllocator over the provided pre-initialised voice array.
     *
     * @param voices pool of voices; must not be {@code null} or empty
     */
    public VoiceAllocator(SynthVoice[] voices) {
        if (voices == null || voices.length == 0) {
            throw new IllegalArgumentException("voices must be non-null and non-empty");
        }
        this.voices = voices;
        this.voiceNotes      = new int[voices.length];
        this.voiceStartTimes = new long[voices.length];

        for (int i = 0; i < voices.length; i++) {
            voiceNotes[i]      = FREE;
            voiceStartTimes[i] = 0L;
        }
    }

    /**
     * Allocate a voice for the given MIDI note, trigger its noteOn, and return it.
     *
     * <p>If the same MIDI note is already playing it is first silenced, then re-triggered.
     *
     * @param midiNote MIDI note number (0–127)
     * @param velocity note velocity (0.0–1.0)
     * @return the voice that was allocated; never {@code null}
     */
    public SynthVoice allocate(int midiNote, double velocity) {
        // If the same note is already playing, re-use that voice (retrigger)
        int existingIdx = findVoicePlayingNote(midiNote);
        if (existingIdx >= 0) {
            voices[existingIdx].noteOn(midiNote, velocity);
            voiceStartTimes[existingIdx] = System.nanoTime();
            return voices[existingIdx];
        }

        int idx = chooseFreeVoice();

        // If we're stealing an active voice, silence it first
        if (voiceNotes[idx] != FREE) {
            voices[idx].noteOff();
        }

        voiceNotes[idx]      = midiNote;
        voiceStartTimes[idx] = System.nanoTime();
        voices[idx].noteOn(midiNote, velocity);
        return voices[idx];
    }

    /**
     * Release the voice currently playing {@code midiNote}, if any.
     *
     * @param midiNote MIDI note number (0–127)
     */
    public void releaseNote(int midiNote) {
        int idx = findVoicePlayingNote(midiNote);
        if (idx >= 0) {
            voices[idx].noteOff();
            voiceNotes[idx] = FREE;
        }
    }

    /** Immediately release all active voices. */
    public void releaseAll() {
        for (int i = 0; i < voices.length; i++) {
            if (voiceNotes[i] != FREE) {
                voices[i].noteOff();
                voiceNotes[i] = FREE;
            }
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Choose the best voice index to assign to a new note.
     *
     * <p>Priority:
     * <ol>
     *   <li>A voice marked free (note = {@link #FREE})</li>
     *   <li>A voice that is no longer active (envelope has gone idle) — pick oldest</li>
     *   <li>Any voice — pick the oldest (lowest startTime)</li>
     * </ol>
     */
    private int chooseFreeVoice() {
        // Pass 1: genuinely free slot
        for (int i = 0; i < voices.length; i++) {
            if (voiceNotes[i] == FREE) {
                return i;
            }
        }

        // Pass 2: voices that have finished their release phase (isActive() == false)
        int oldestReleasedIdx  = -1;
        long oldestReleasedTime = Long.MAX_VALUE;
        for (int i = 0; i < voices.length; i++) {
            if (!voices[i].isActive() && voiceStartTimes[i] < oldestReleasedTime) {
                oldestReleasedTime = voiceStartTimes[i];
                oldestReleasedIdx  = i;
            }
        }
        if (oldestReleasedIdx >= 0) {
            // Mark it free so the caller doesn't try to noteOff it again
            voiceNotes[oldestReleasedIdx] = FREE;
            return oldestReleasedIdx;
        }

        // Pass 3: steal the oldest (longest running) active voice
        int   oldestIdx  = 0;
        long  oldestTime = voiceStartTimes[0];
        for (int i = 1; i < voices.length; i++) {
            if (voiceStartTimes[i] < oldestTime) {
                oldestTime = voiceStartTimes[i];
                oldestIdx  = i;
            }
        }
        return oldestIdx;
    }

    /** Returns the index of the voice playing {@code midiNote}, or -1 if none. */
    private int findVoicePlayingNote(int midiNote) {
        for (int i = 0; i < voices.length; i++) {
            if (voiceNotes[i] == midiNote) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // Package-private accessors (for testing)
    // =========================================================================

    /** Returns the MIDI note assigned to voice at {@code index}, or {@link #FREE} (-1). */
    int getVoiceNote(int index) {
        return voiceNotes[index];
    }
}
