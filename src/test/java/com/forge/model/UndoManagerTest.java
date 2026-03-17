package com.forge.model;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class UndoManagerTest {

    private UndoManager undoManager;

    @BeforeEach
    void setUp() {
        undoManager = new UndoManager();
    }

    // -------------------------------------------------------------------------
    // Basic state checks on empty manager
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("New manager has no undo or redo available")
    void emptyManagerState() {
        assertFalse(undoManager.canUndo());
        assertFalse(undoManager.canRedo());
        assertEquals(0, undoManager.historySize());
        assertEquals(-1, undoManager.getCurrentIndex());
    }

    @Test
    @DisplayName("undo and redo return null on empty manager")
    void undoRedoReturnNullWhenEmpty() {
        assertNull(undoManager.undo());
        assertNull(undoManager.redo());
    }

    // -------------------------------------------------------------------------
    // Push and basic undo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Pushing one state enables no undo (need >1 state to undo)")
    void singlePushNoUndo() {
        ProjectState s = stateWithBpm(120);
        undoManager.push(s);

        assertFalse(undoManager.canUndo(), "Only 1 state pushed — canUndo must be false");
        assertFalse(undoManager.canRedo());
        assertEquals(1, undoManager.historySize());
        assertEquals(0, undoManager.getCurrentIndex());
    }

    @Test
    @DisplayName("Push 3 states: undo → 130, undo → 120")
    void undoThroughThreeStates() {
        undoManager.push(stateWithBpm(120));
        undoManager.push(stateWithBpm(130));
        undoManager.push(stateWithBpm(140));

        assertTrue(undoManager.canUndo());
        assertFalse(undoManager.canRedo());

        ProjectState at130 = undoManager.undo();
        assertNotNull(at130);
        assertEquals(130.0, at130.bpm, 0.001, "After first undo, BPM should be 130");
        assertTrue(undoManager.canUndo());
        assertTrue(undoManager.canRedo());

        ProjectState at120 = undoManager.undo();
        assertNotNull(at120);
        assertEquals(120.0, at120.bpm, 0.001, "After second undo, BPM should be 120");
        assertFalse(undoManager.canUndo());
        assertTrue(undoManager.canRedo());
    }

    // -------------------------------------------------------------------------
    // Redo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Redo after two undos restores BPM 130")
    void redoAfterUndos() {
        undoManager.push(stateWithBpm(120));
        undoManager.push(stateWithBpm(130));
        undoManager.push(stateWithBpm(140));

        undoManager.undo(); // → 130
        undoManager.undo(); // → 120

        ProjectState redone = undoManager.redo();
        assertNotNull(redone);
        assertEquals(130.0, redone.bpm, 0.001, "Redo should restore BPM 130");
        assertTrue(undoManager.canUndo());
        assertTrue(undoManager.canRedo());
    }

    // -------------------------------------------------------------------------
    // Redo history cleared on new push
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Pushing a new state after undo clears redo history")
    void pushClearsRedoHistory() {
        undoManager.push(stateWithBpm(120));
        undoManager.push(stateWithBpm(130));
        undoManager.push(stateWithBpm(140));

        undoManager.undo(); // → 130
        undoManager.undo(); // → 120

        // Now push a new state — this should clear 130 and 140 from redo
        undoManager.push(stateWithBpm(150));

        assertFalse(undoManager.canRedo(),
            "Redo history must be cleared after pushing a new state");
        assertEquals(2, undoManager.historySize(),
            "History should have 2 entries: BPM=120 and BPM=150");

        // Verify the current state is 150
        // (undo once to see the previous one)
        ProjectState prev = undoManager.undo();
        assertNotNull(prev);
        assertEquals(120.0, prev.bpm, 0.001,
            "After undoing from 150, should get BPM=120");
    }

    // -------------------------------------------------------------------------
    // MAX_HISTORY cap
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("History is capped at MAX_HISTORY entries")
    void historyCapped() {
        for (int i = 0; i < UndoManager.MAX_HISTORY + 10; i++) {
            undoManager.push(stateWithBpm(100 + i));
        }

        assertTrue(undoManager.historySize() <= UndoManager.MAX_HISTORY,
            "History size must not exceed MAX_HISTORY=" + UndoManager.MAX_HISTORY
            + ", got " + undoManager.historySize());
    }

    // -------------------------------------------------------------------------
    // Clear
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("clear() resets the manager to empty state")
    void clearResetsManager() {
        undoManager.push(stateWithBpm(120));
        undoManager.push(stateWithBpm(130));
        undoManager.clear();

        assertEquals(0, undoManager.historySize());
        assertEquals(-1, undoManager.getCurrentIndex());
        assertFalse(undoManager.canUndo());
        assertFalse(undoManager.canRedo());
        assertNull(undoManager.undo());
        assertNull(undoManager.redo());
    }

    // -------------------------------------------------------------------------
    // Deep-copy isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Snapshots are independent: mutating state after push does not corrupt history")
    void snapshotIsDeepCopy() {
        ProjectState state = stateWithBpm(100);
        undoManager.push(state);

        // Now mutate the original state object
        state.bpm = 999.0;

        // Push a second time to be able to undo
        undoManager.push(stateWithBpm(200));
        ProjectState restored = undoManager.undo();

        assertNotNull(restored);
        assertEquals(100.0, restored.bpm, 0.001,
            "Snapshot must be a deep copy; original mutation must not affect history");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ProjectState stateWithBpm(double bpm) {
        ProjectState s = new ProjectState();
        s.bpm = bpm;
        return s;
    }
}
