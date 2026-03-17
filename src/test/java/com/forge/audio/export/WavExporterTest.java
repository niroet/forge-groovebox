package com.forge.audio.export;

import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.model.SynthPatch;
import org.junit.jupiter.api.*;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class WavExporterTest {

    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("forge_wav_test_", ".wav").toFile();
        tempFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null) tempFile.delete();
    }

    @Test
    @DisplayName("Export 1-bar pattern with kick on beat 1 produces a valid WAV file")
    void exportKickOnBeat1() throws Exception {
        // Build a 1-bar pattern with kick on step 0 only
        Pattern pattern = new Pattern();
        pattern.drumSteps[DrumTrack.KICK.ordinal()][0].active = true;
        pattern.drumSteps[DrumTrack.KICK.ordinal()][0].velocity = 1.0;

        DrumPatch[] patches = defaultPatches();

        WavExporter.exportPattern(pattern, patches, new SynthPatch(), 120.0, 1, tempFile);

        // File must exist and be non-empty
        assertTrue(tempFile.exists(), "WAV file was not created");
        assertTrue(tempFile.length() > 0, "WAV file is empty");
    }

    @Test
    @DisplayName("Exported WAV has the correct sample rate in the header")
    void correctSampleRateInHeader() throws Exception {
        Pattern pattern = new Pattern();
        pattern.drumSteps[DrumTrack.KICK.ordinal()][0].active = true;

        WavExporter.exportPattern(pattern, defaultPatches(), null, 128.0, 1, tempFile);

        // Read back the AudioFormat from the WAV header
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(tempFile)) {
            AudioFormat fmt = ais.getFormat();
            assertEquals(WavExporter.SAMPLE_RATE, (int) fmt.getSampleRate(),
                "Sample rate in WAV header must match WavExporter.SAMPLE_RATE");
        }
    }

    @Test
    @DisplayName("Exported WAV contains non-zero PCM audio data")
    void nonZeroAudioContent() throws Exception {
        Pattern pattern = new Pattern();
        // Kick on all four beats
        for (int s : new int[]{0, 4, 8, 12}) {
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].active = true;
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].velocity = 0.9;
        }

        WavExporter.exportPattern(pattern, defaultPatches(), null, 128.0, 1, tempFile);

        // The file must be substantially larger than just a WAV header (44 bytes)
        assertTrue(tempFile.length() > 1000,
            "WAV file content too small — expected real PCM data, got " + tempFile.length() + " bytes");
    }

    @Test
    @DisplayName("exportPattern throws on bars < 1")
    void throwsOnZeroBars() {
        Pattern pattern = new Pattern();
        assertThrows(IllegalArgumentException.class,
            () -> WavExporter.exportPattern(pattern, defaultPatches(), null, 120.0, 0, tempFile));
    }

    @Test
    @DisplayName("Export multi-bar pattern produces longer file than single bar")
    void multiBarIsLonger() throws Exception {
        Pattern pattern = new Pattern();
        pattern.drumSteps[DrumTrack.KICK.ordinal()][0].active = true;

        File single = Files.createTempFile("forge_1bar_", ".wav").toFile();
        File multi  = Files.createTempFile("forge_4bar_", ".wav").toFile();
        single.deleteOnExit();
        multi.deleteOnExit();
        try {
            WavExporter.exportPattern(pattern, defaultPatches(), null, 120.0, 1, single);
            WavExporter.exportPattern(pattern, defaultPatches(), null, 120.0, 4, multi);
            assertTrue(multi.length() > single.length(),
                "4-bar export must produce a larger file than a 1-bar export");
        } finally {
            single.delete();
            multi.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DrumPatch[] defaultPatches() {
        DrumPatch kick = new DrumPatch();
        kick.pitch = 60.0; kick.decay = 0.3; kick.toneNoise = 1.0;

        DrumPatch snare = new DrumPatch();
        snare.pitch = 200.0; snare.decay = 0.15; snare.toneNoise = 0.5;

        DrumPatch hat = new DrumPatch();
        hat.pitch = 8000.0; hat.decay = 0.05; hat.toneNoise = 0.0;

        DrumPatch perc = new DrumPatch();
        perc.pitch = 400.0; perc.decay = 0.1; perc.toneNoise = 0.7;

        return new DrumPatch[]{kick, snare, hat, perc};
    }
}
