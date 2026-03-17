package com.forge.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Save and load {@link ProjectState} as pretty-printed JSON using Gson.
 *
 * <p>The auto-save file is named {@code .forge.autosave} and is written to a
 * given project directory.  Auto-save failures are logged but never thrown to
 * the caller, so they cannot crash the audio thread.
 */
public class ProjectPersistence {

    private static final Logger LOG = Logger.getLogger(ProjectPersistence.class.getName());

    /** File name used by {@link #autoSave} and {@link #hasAutoSave}. */
    public static final String AUTOSAVE_FILENAME = ".forge.autosave";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProjectPersistence() { /* static utility class */ }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Serialise {@code state} to JSON and write it to {@code file}.
     *
     * @param state the project state to save
     * @param file  destination file (parent directories are created if needed)
     * @throws IOException if writing fails
     */
    public static void save(ProjectState state, File file) throws IOException {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), GSON.toJson(state));
        LOG.info("[Project] Saved to: " + file.getAbsolutePath());
    }

    /**
     * Read and deserialise a {@link ProjectState} from {@code file}.
     *
     * @param file source JSON file
     * @return the loaded project state
     * @throws IOException if reading or parsing fails
     */
    public static ProjectState load(File file) throws IOException {
        String json = Files.readString(file.toPath());
        ProjectState state = GSON.fromJson(json, ProjectState.class);
        LOG.info("[Project] Loaded from: " + file.getAbsolutePath());
        return state;
    }

    /**
     * Write an auto-save snapshot to {@code <projectDir>/.forge.autosave}.
     * Errors are caught and logged rather than propagated.
     *
     * @param state      the project state to snapshot
     * @param projectDir directory that will contain the auto-save file
     */
    public static void autoSave(ProjectState state, File projectDir) {
        try {
            save(state, new File(projectDir, AUTOSAVE_FILENAME));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "[Project] Auto-save failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns {@code true} if an auto-save file exists in {@code projectDir}.
     *
     * @param projectDir directory to check
     */
    public static boolean hasAutoSave(File projectDir) {
        return new File(projectDir, AUTOSAVE_FILENAME).exists();
    }

    /**
     * Load the auto-save file from {@code projectDir}, or {@code null} if none exists.
     *
     * @param projectDir directory containing the auto-save file
     * @return loaded state, or {@code null} if the file does not exist
     * @throws IOException if the file exists but cannot be read/parsed
     */
    public static ProjectState loadAutoSave(File projectDir) throws IOException {
        File f = new File(projectDir, AUTOSAVE_FILENAME);
        if (!f.exists()) return null;
        return load(f);
    }
}
