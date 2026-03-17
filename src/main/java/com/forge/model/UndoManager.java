package com.forge.model;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot-based undo/redo manager for {@link ProjectState}.
 *
 * <p>Each snapshot is the full Gson-serialised JSON of the project state.
 * The history is capped at {@link #MAX_HISTORY} entries to bound memory use.
 *
 * <p>Undo/redo behaviour:
 * <ul>
 *   <li>{@link #push} adds the current state and trims any redo history.</li>
 *   <li>{@link #undo} moves back one step and returns the state at that position.</li>
 *   <li>{@link #redo} moves forward one step and returns the state at that position.</li>
 *   <li>Both return {@code null} when there is nothing to undo/redo.</li>
 * </ul>
 */
public class UndoManager {

    /** Maximum number of history snapshots retained. */
    public static final int MAX_HISTORY = 50;

    private final Gson gson = new Gson();

    /** Ring of JSON snapshots. Index 0 = oldest. */
    private final List<String> history = new ArrayList<>();

    /** Points to the currently active snapshot (the state the user is viewing). */
    private int currentIndex = -1;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Push a new state snapshot onto the history.
     *
     * <p>Any redo states beyond {@code currentIndex} are discarded. If the
     * history then exceeds {@link #MAX_HISTORY} entries, the oldest is removed.
     *
     * @param state project state to snapshot
     */
    public void push(ProjectState state) {
        String json = gson.toJson(state);

        // Discard redo history
        while (history.size() > currentIndex + 1) {
            history.remove(history.size() - 1);
        }

        history.add(json);

        // Trim oldest if we exceed the cap
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
            // currentIndex already points at the last entry; after removing
            // the head it is still correct (size - 1).
        }

        currentIndex = history.size() - 1;
    }

    /**
     * Move back one step in history and return the restored state.
     *
     * @return the previous {@link ProjectState}, or {@code null} if already at the oldest entry
     */
    public ProjectState undo() {
        if (!canUndo()) return null;
        currentIndex--;
        return gson.fromJson(history.get(currentIndex), ProjectState.class);
    }

    /**
     * Move forward one step in history and return the restored state.
     *
     * @return the next {@link ProjectState}, or {@code null} if already at the newest entry
     */
    public ProjectState redo() {
        if (!canRedo()) return null;
        currentIndex++;
        return gson.fromJson(history.get(currentIndex), ProjectState.class);
    }

    /** Returns {@code true} if there is at least one state to undo to. */
    public boolean canUndo() {
        return currentIndex > 0;
    }

    /** Returns {@code true} if there is at least one state to redo. */
    public boolean canRedo() {
        return currentIndex < history.size() - 1;
    }

    /**
     * Returns the number of snapshots currently in the history.
     * Useful for tests and diagnostics.
     */
    public int historySize() {
        return history.size();
    }

    /** Returns the index of the currently active snapshot (0-based), or -1 if empty. */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /** Clear all history. */
    public void clear() {
        history.clear();
        currentIndex = -1;
    }
}
