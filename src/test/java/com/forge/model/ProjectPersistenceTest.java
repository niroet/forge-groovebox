package com.forge.model;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectPersistenceTest {

    private Path tempDir;
    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDir  = Files.createTempDirectory("forge_persist_test_");
        tempFile = Files.createTempFile(tempDir, "project_", ".json").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up
        if (tempFile != null) tempFile.delete();
        if (tempDir != null)  Files.deleteIfExists(tempDir);
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Save then load preserves BPM=140")
    void roundTripBpm() throws IOException {
        ProjectState state = new ProjectState();
        state.bpm = 140.0;

        ProjectPersistence.save(state, tempFile);
        ProjectState loaded = ProjectPersistence.load(tempFile);

        assertNotNull(loaded, "Loaded state must not be null");
        assertEquals(140.0, loaded.bpm, 0.001, "BPM must survive round-trip");
    }

    @Test
    @DisplayName("Save then load preserves kick pattern steps 0,4,8,12")
    void roundTripKickPattern() throws IOException {
        ProjectState state = new ProjectState();
        state.bpm = 140.0;

        // Activate kick on steps 0, 4, 8, 12 in pattern 0
        Pattern pat = state.patterns[0];
        int kickIdx = DrumTrack.KICK.ordinal();
        for (int s : new int[]{0, 4, 8, 12}) {
            pat.drumSteps[kickIdx][s].active   = true;
            pat.drumSteps[kickIdx][s].velocity = 0.9;
        }

        ProjectPersistence.save(state, tempFile);
        ProjectState loaded = ProjectPersistence.load(tempFile);

        Pattern loadedPat = loaded.patterns[0];
        for (int s : new int[]{0, 4, 8, 12}) {
            assertTrue(loadedPat.drumSteps[kickIdx][s].active,
                "Kick step " + s + " should be active after load");
            assertEquals(0.9, loadedPat.drumSteps[kickIdx][s].velocity, 0.001,
                "Kick step " + s + " velocity mismatch");
        }
        // Steps that should be inactive
        for (int s : new int[]{1, 2, 3, 5, 6, 7}) {
            assertFalse(loadedPat.drumSteps[kickIdx][s].active,
                "Kick step " + s + " should be inactive after load");
        }
    }

    @Test
    @DisplayName("Save creates a non-empty JSON file")
    void saveCreatesFile() throws IOException {
        ProjectPersistence.save(new ProjectState(), tempFile);

        assertTrue(tempFile.exists(), "File must exist after save");
        assertTrue(tempFile.length() > 0, "Saved file must be non-empty");
        String content = Files.readString(tempFile.toPath());
        assertTrue(content.contains("bpm"), "JSON must contain the 'bpm' field");
    }

    @Test
    @DisplayName("Loaded state has correct default values when saved from fresh ProjectState")
    void defaultValuesPreserved() throws IOException {
        ProjectState fresh = new ProjectState();
        ProjectPersistence.save(fresh, tempFile);
        ProjectState loaded = ProjectPersistence.load(tempFile);

        assertEquals(fresh.bpm,       loaded.bpm,       0.001);
        assertEquals(fresh.rootNote,  loaded.rootNote);
        assertNotNull(loaded.patterns, "Patterns array must be present");
        assertEquals(16, loaded.patterns.length, "Must have 16 patterns");
    }

    // -------------------------------------------------------------------------
    // Auto-save tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("hasAutoSave returns false when no auto-save file exists")
    void noAutoSave() {
        assertFalse(ProjectPersistence.hasAutoSave(tempDir.toFile()));
    }

    @Test
    @DisplayName("autoSave creates the .forge.autosave file in the project directory")
    void autoSaveCreatesFile() {
        ProjectState state = new ProjectState();
        ProjectPersistence.autoSave(state, tempDir.toFile());

        assertTrue(ProjectPersistence.hasAutoSave(tempDir.toFile()),
            "hasAutoSave must be true after autoSave");

        File autoFile = new File(tempDir.toFile(), ProjectPersistence.AUTOSAVE_FILENAME);
        assertTrue(autoFile.exists(), "Auto-save file must exist");
        assertTrue(autoFile.length() > 0, "Auto-save file must be non-empty");
        autoFile.delete();
    }

    @Test
    @DisplayName("loadAutoSave returns null when no auto-save file exists")
    void loadAutoSaveReturnsNullWhenMissing() throws IOException {
        assertNull(ProjectPersistence.loadAutoSave(tempDir.toFile()),
            "loadAutoSave must return null when no file exists");
    }

    @Test
    @DisplayName("autoSave then loadAutoSave round-trips BPM correctly")
    void autoSaveRoundTrip() throws IOException {
        ProjectState state = new ProjectState();
        state.bpm = 155.0;

        ProjectPersistence.autoSave(state, tempDir.toFile());
        ProjectState loaded = ProjectPersistence.loadAutoSave(tempDir.toFile());

        assertNotNull(loaded);
        assertEquals(155.0, loaded.bpm, 0.001);

        // Clean up auto-save
        new File(tempDir.toFile(), ProjectPersistence.AUTOSAVE_FILENAME).delete();
    }
}
