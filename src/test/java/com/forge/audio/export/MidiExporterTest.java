package com.forge.audio.export;

import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import org.junit.jupiter.api.*;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MidiExporterTest {

    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("forge_midi_test_", ".mid").toFile();
        tempFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null) tempFile.delete();
    }

    // -------------------------------------------------------------------------
    // Core export tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Export creates a non-empty MIDI file")
    void exportCreatesFile() throws Exception {
        Pattern pattern = new Pattern();
        MidiExporter.exportPattern(pattern, 120.0, tempFile);

        assertTrue(tempFile.exists(), "MIDI file was not created");
        assertTrue(tempFile.length() > 0, "MIDI file is empty");
    }

    @Test
    @DisplayName("Kick on steps 0,4,8,12 produces exactly 4 NOTE_ON events on channel 10")
    void kickPatternProduces4NoteOns() throws Exception {
        Pattern pattern = new Pattern();
        for (int s : new int[]{0, 4, 8, 12}) {
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].active = true;
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].velocity = 0.9;
        }

        MidiExporter.exportPattern(pattern, 128.0, tempFile);

        // Read back the file
        Sequence seq = MidiSystem.getSequence(tempFile);
        List<ShortMessage> kickNoteOns = new ArrayList<>();

        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiMessage msg = track.get(i).getMessage();
                if (msg instanceof ShortMessage sm) {
                    if (sm.getCommand() == ShortMessage.NOTE_ON
                            && sm.getChannel() == 9                  // channel 10 (0-indexed)
                            && sm.getData1() == 36                   // GM kick note
                            && sm.getData2() > 0) {
                        kickNoteOns.add(sm);
                    }
                }
            }
        }

        assertEquals(4, kickNoteOns.size(),
            "Expected 4 kick NOTE_ON events but found " + kickNoteOns.size());
    }

    @Test
    @DisplayName("Exported MIDI file has correct tempo")
    void correctTempo() throws Exception {
        double bpm = 140.0;
        Pattern pattern = new Pattern();
        MidiExporter.exportPattern(pattern, bpm, tempFile);

        Sequence seq = MidiSystem.getSequence(tempFile);

        // Find the tempo meta-event (type 0x51)
        int foundTempo = -1;
        outer:
        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage msg = event.getMessage();
                if (msg instanceof MetaMessage mm && mm.getType() == 0x51) {
                    byte[] data = mm.getData();
                    foundTempo = ((data[0] & 0xFF) << 16)
                               | ((data[1] & 0xFF) <<  8)
                               |  (data[2] & 0xFF);
                    break outer;
                }
            }
        }

        int expectedMicros = (int) Math.round(60_000_000.0 / bpm);
        assertNotEquals(-1, foundTempo, "No tempo meta-event found in exported MIDI");
        assertEquals(expectedMicros, foundTempo,
            "Tempo meta-event does not match BPM=" + bpm);
    }

    @Test
    @DisplayName("Exported file is a valid Type 1 SMF with multiple tracks")
    void isType1Smf() throws Exception {
        Pattern pattern = new Pattern();
        pattern.drumSteps[DrumTrack.KICK.ordinal()][0].active = true;
        pattern.synthSteps[0].active = true;

        MidiExporter.exportPattern(pattern, 120.0, tempFile);

        Sequence seq = MidiSystem.getSequence(tempFile);

        // Type 1 = multiple tracks; PPQN should match our constant
        assertEquals(MidiExporter.PPQN, seq.getResolution(),
            "PPQN resolution mismatch");

        // Should have at least 2 tracks: tempo + at least one content track
        assertTrue(seq.getTracks().length >= 2,
            "Expected at least 2 tracks in Type 1 MIDI file, got " + seq.getTracks().length);
    }

    @Test
    @DisplayName("All four drum tracks contain NOTE_ON events when all steps active")
    void allDrumTracksPopulated() throws Exception {
        Pattern pattern = new Pattern();
        // Activate step 0 for every drum track
        for (DrumTrack track : DrumTrack.values()) {
            pattern.drumSteps[track.ordinal()][0].active = true;
            pattern.drumSteps[track.ordinal()][0].velocity = 0.8;
        }

        MidiExporter.exportPattern(pattern, 120.0, tempFile);
        Sequence seq = MidiSystem.getSequence(tempFile);

        int[] gmNotes = {36, 38, 42, 47}; // KICK, SNARE, HAT, PERC
        for (int gmNote : gmNotes) {
            boolean found = false;
            outer:
            for (Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiMessage msg = track.get(i).getMessage();
                    if (msg instanceof ShortMessage sm
                            && sm.getCommand() == ShortMessage.NOTE_ON
                            && sm.getChannel() == 9
                            && sm.getData1() == gmNote
                            && sm.getData2() > 0) {
                        found = true;
                        break outer;
                    }
                }
            }
            assertTrue(found, "No NOTE_ON found for GM drum note " + gmNote);
        }
    }
}
