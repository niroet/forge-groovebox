package com.forge.audio.export;

import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.model.SynthStep;

import javax.sound.midi.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * Exports a FORGE {@link Pattern} to a Standard MIDI File (SMF Type 1).
 *
 * <p>Track layout:
 * <ul>
 *   <li>Track 0 (tempo track): contains only the tempo meta-event</li>
 *   <li>Track 1 (synth track): note-on/off events for {@code pattern.synthSteps} on MIDI channel 1</li>
 *   <li>Tracks 2–5 (drum tracks): one track per {@link DrumTrack} on MIDI channel 10 (GM drums)</li>
 * </ul>
 *
 * <p>Resolution: 96 PPQN (pulses per quarter note).
 */
public class MidiExporter {

    private static final Logger LOG = Logger.getLogger(MidiExporter.class.getName());

    /** MIDI resolution in pulses per quarter note. */
    public static final int PPQN = 96;

    /** Pulses per 16th note step (1 beat = 4 steps → PPQN / 4). */
    private static final int PULSES_PER_STEP = PPQN / 4;

    /** GM drum note numbers. */
    private static final int GM_KICK  = 36;
    private static final int GM_SNARE = 38;
    private static final int GM_HAT   = 42;
    private static final int GM_PERC  = 47;

    /** MIDI channel for synth (0-indexed = channel 1). */
    private static final int SYNTH_CHANNEL = 0;

    /** MIDI channel for drums (0-indexed = channel 10). */
    private static final int DRUM_CHANNEL = 9;

    /** Default note-on velocity when not specified. */
    private static final int DEFAULT_VELOCITY = 100;

    private MidiExporter() { /* static utility class */ }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Export a pattern as a Standard MIDI File.
     *
     * @param pattern    the pattern to export
     * @param bpm        tempo in BPM
     * @param outputFile destination MIDI file
     * @throws Exception if encoding or writing fails
     */
    public static void exportPattern(Pattern pattern, double bpm, File outputFile) throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, PPQN);

        // --- Tempo track ---
        Track tempoTrack = sequence.createTrack();
        addTempoEvent(tempoTrack, bpm);

        // --- Synth track (channel 1) ---
        Track synthTrack = sequence.createTrack();
        addSynthNotes(synthTrack, pattern);

        // --- Drum tracks (one per DrumTrack, channel 10) ---
        for (DrumTrack dt : DrumTrack.values()) {
            Track drumTrack = sequence.createTrack();
            addDrumNotes(drumTrack, pattern, dt);
        }

        outputFile.getParentFile().mkdirs();
        MidiSystem.write(sequence, 1, outputFile);

        LOG.info("[MIDI] Exported pattern '" + pattern.name + "' to: " + outputFile.getAbsolutePath());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** Add a tempo meta-event at tick 0. */
    private static void addTempoEvent(Track track, double bpm) throws InvalidMidiDataException {
        int microsecondsPerBeat = (int) Math.round(60_000_000.0 / bpm);
        byte[] tempoData = new byte[]{
            (byte) ((microsecondsPerBeat >> 16) & 0xFF),
            (byte) ((microsecondsPerBeat >>  8) & 0xFF),
            (byte) ( microsecondsPerBeat        & 0xFF)
        };
        MetaMessage tempoMsg = new MetaMessage(0x51, tempoData, 3);
        track.add(new MidiEvent(tempoMsg, 0));

        // End-of-track
        addEndOfTrack(track, 0);
    }

    /** Add note-on / note-off pairs for synth steps on channel 1. */
    private static void addSynthNotes(Track track, Pattern pattern) throws InvalidMidiDataException {
        int stepCount = Math.min(pattern.synthSteps.length, pattern.synthStepCount);
        int lastTick  = 0;

        for (int s = 0; s < stepCount; s++) {
            SynthStep step = pattern.synthSteps[s];
            if (step == null || !step.active) continue;

            long onTick  = (long) s * PULSES_PER_STEP;
            double gate  = step.gateLength > 0 ? step.gateLength : 0.5;
            long offTick = onTick + (long) Math.max(1, Math.round(PULSES_PER_STEP * gate * 2));
            int vel      = (int) Math.round(step.velocity * 127.0);
            int note     = step.midiNote;

            ShortMessage noteOn = new ShortMessage();
            noteOn.setMessage(ShortMessage.NOTE_ON, SYNTH_CHANNEL, note, vel);
            track.add(new MidiEvent(noteOn, onTick));

            ShortMessage noteOff = new ShortMessage();
            noteOff.setMessage(ShortMessage.NOTE_OFF, SYNTH_CHANNEL, note, 0);
            track.add(new MidiEvent(noteOff, offTick));

            lastTick = (int) Math.max(lastTick, offTick);
        }

        addEndOfTrack(track, lastTick);
    }

    /** Add note-on / note-off pairs for a single drum track on MIDI channel 10. */
    private static void addDrumNotes(Track track, Pattern pattern, DrumTrack drumTrack)
            throws InvalidMidiDataException {
        int trackIdx  = drumTrack.ordinal();
        DrumStep[] steps = pattern.drumSteps[trackIdx];
        int stepCount = Math.min(steps.length, pattern.drumStepCounts[trackIdx]);
        int gmNote    = gmNoteFor(drumTrack);
        int lastTick  = 0;

        for (int s = 0; s < stepCount; s++) {
            DrumStep step = steps[s];
            if (step == null || !step.active) continue;

            long onTick  = (long) s * PULSES_PER_STEP;
            long offTick = onTick + PULSES_PER_STEP / 2; // short gate for drums
            int vel      = (int) Math.round(step.velocity * 127.0);

            ShortMessage noteOn = new ShortMessage();
            noteOn.setMessage(ShortMessage.NOTE_ON, DRUM_CHANNEL, gmNote, vel);
            track.add(new MidiEvent(noteOn, onTick));

            ShortMessage noteOff = new ShortMessage();
            noteOff.setMessage(ShortMessage.NOTE_OFF, DRUM_CHANNEL, gmNote, 0);
            track.add(new MidiEvent(noteOff, offTick));

            lastTick = (int) Math.max(lastTick, offTick);
        }

        addEndOfTrack(track, lastTick);
    }

    /** Add an end-of-track meta event at or after the given tick. */
    private static void addEndOfTrack(Track track, long atTick) throws InvalidMidiDataException {
        MetaMessage eot = new MetaMessage(0x2F, new byte[0], 0);
        track.add(new MidiEvent(eot, atTick));
    }

    /** Map a {@link DrumTrack} to its GM drum note number. */
    private static int gmNoteFor(DrumTrack track) {
        return switch (track) {
            case KICK  -> GM_KICK;
            case SNARE -> GM_SNARE;
            case HAT   -> GM_HAT;
            case PERC  -> GM_PERC;
        };
    }
}
