package com.forge.ui.panels;

import com.forge.model.Pattern;
import com.forge.model.SynthStep;
import com.forge.ui.theme.ForgeColors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * 16-step synth melody sequencer grid.
 *
 * <p>Each cell shows the MIDI note name when that step is active.  The
 * currently <em>selected</em> step is highlighted with a bright amber border
 * so note-entry keys write directly into the pattern rather than playing live.
 *
 * <p>Interaction:
 * <ul>
 *   <li>Left-click a step  → select it (or deselect if already selected)
 *   <li>Right-click / Shift+click → toggle slide on the step
 *   <li>While a step is selected, calling {@link #enterNote(int)} writes that
 *       MIDI note into the step and activates it.
 *   <li>{@link #deleteSelectedStep()} clears the selected step.
 *   <li>{@link #moveSelection(int)} moves selection left / right (pass ±1).
 * </ul>
 */
public class SynthSequenceGrid extends VBox {

    // ---- Model ---------------------------------------------------------------
    private Pattern pattern;

    // ---- Selection state -----------------------------------------------------
    private int selectedStep = -1;          // -1 = none selected
    private int playingStep  = -1;          // current sequencer position

    // ---- Cell nodes ----------------------------------------------------------
    private static final int STEPS = 16;
    private final StepCell[] cells = new StepCell[STEPS];

    // ---- Listener ------------------------------------------------------------
    /** Called when the user selects / deselects a step cell. */
    public interface SelectionListener {
        void onSelectionChanged(int stepIndex);  // -1 = deselected
    }
    private SelectionListener selectionListener;

    // =========================================================================
    // Constructor
    // =========================================================================

    public SynthSequenceGrid() {
        super(0);
        setStyle("-fx-background-color: #080808;");
        setPadding(new Insets(4, 6, 4, 6));

        getChildren().addAll(buildHeader(), buildGrid());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /** Attach this grid to a pattern (call after construction or on pattern switch). */
    public void setPattern(Pattern p) {
        this.pattern = p;
        refreshAll();
    }

    public Pattern getPattern() { return pattern; }

    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }

    /** @return the index of the currently selected step, or -1 if none. */
    public int getSelectedStep() { return selectedStep; }

    /** Enter a MIDI note into the selected step and activate it. No-op if nothing selected. */
    public void enterNote(int midiNote) {
        if (selectedStep < 0 || pattern == null) return;
        SynthStep step = pattern.synthSteps[selectedStep];
        step.midiNote = midiNote;
        step.active   = true;
        cells[selectedStep].refresh();
    }

    /** Deactivate the selected step. No-op if nothing selected. */
    public void deleteSelectedStep() {
        if (selectedStep < 0 || pattern == null) return;
        pattern.synthSteps[selectedStep].active = false;
        cells[selectedStep].refresh();
    }

    /** Move selection by delta steps (±1). Wraps around. */
    public void moveSelection(int delta) {
        if (selectedStep < 0) {
            setSelectedStep(0);
        } else {
            setSelectedStep(Math.floorMod(selectedStep + delta, STEPS));
        }
    }

    /** Deselect any step (used when clicking elsewhere in the app). */
    public void clearSelection() {
        setSelectedStep(-1);
    }

    /** Highlight the current playback position (call from AnimationTimer). */
    public void setPlayingStep(int step) {
        if (step == playingStep) return;
        int prev = playingStep;
        playingStep = step;
        if (prev >= 0 && prev < STEPS) cells[prev].refresh();
        if (step >= 0 && step < STEPS) cells[step].refresh();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void setSelectedStep(int idx) {
        int prev = selectedStep;
        selectedStep = idx;
        if (prev >= 0 && prev < STEPS) cells[prev].refresh();
        if (idx >= 0 && idx < STEPS)   cells[idx].refresh();
        if (selectionListener != null)  selectionListener.onSelectionChanged(idx);
    }

    private void refreshAll() {
        for (StepCell c : cells) {
            if (c != null) c.refresh();
        }
    }

    // =========================================================================
    // UI builders
    // =========================================================================

    private HBox buildHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 3, 0));

        javafx.scene.control.Label title = new javafx.scene.control.Label("\u25C6 SYNTH.SEQ \u2014 16 STEPS");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
        title.setTextFill(ForgeColors.ARGENT_AMBER);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        javafx.scene.control.Label hint = new javafx.scene.control.Label("CLICK STEP \u2192 PRESS NOTE KEY");
        hint.setFont(Font.font("Monospace", FontWeight.NORMAL, 8));
        hint.setTextFill(Color.web("#444444"));

        header.getChildren().addAll(title, spacer, hint);
        return header;
    }

    private HBox buildGrid() {
        HBox row = new HBox(2);
        row.setAlignment(Pos.CENTER_LEFT);

        for (int i = 0; i < STEPS; i++) {
            StepCell cell = new StepCell(i);
            cells[i] = cell;
            row.getChildren().add(cell);
            HBox.setHgrow(cell, Priority.ALWAYS);
        }
        return row;
    }

    // =========================================================================
    // StepCell inner class
    // =========================================================================

    private class StepCell extends Region {

        private final int index;
        private final Canvas canvas;

        // beat-group tint every 4 steps
        private static final Color[] GROUP_BG = {
            Color.web("#0e0e0e"), Color.web("#0b0b0b"),
            Color.web("#0e0e0e"), Color.web("#0b0b0b")
        };

        StepCell(int index) {
            this.index = index;
            this.canvas = new Canvas();
            getChildren().add(canvas);

            // canvas tracks region size
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());
            canvas.widthProperty() .addListener((o, a, b) -> refresh());
            canvas.heightProperty().addListener((o, a, b) -> refresh());

            setPrefHeight(28);
            setMinHeight(28);
            setMaxHeight(28);
            setPrefWidth(38);
            setMinWidth(30);

            // Click handling
            setOnMouseClicked(e -> {
                if (e.isShiftDown() || e.getButton() == MouseButton.SECONDARY) {
                    // Toggle slide
                    if (pattern != null) {
                        SynthStep step = pattern.synthSteps[index];
                        step.slide = !step.slide;
                        refresh();
                    }
                } else {
                    // Select / deselect
                    if (selectedStep == index) {
                        clearSelection();
                    } else {
                        setSelectedStep(index);
                    }
                }
            });
        }

        void refresh() {
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            if (w <= 0 || h <= 0) return;

            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, w, h);

            SynthStep step = (pattern != null) ? pattern.synthSteps[index] : null;
            boolean isSelected = (index == selectedStep);
            boolean isPlaying  = (index == playingStep);
            boolean isActive   = (step != null) && step.active;

            // ---- background ----
            Color bgColor;
            if (isPlaying && isActive) {
                bgColor = Color.web("#221500");
            } else if (isPlaying) {
                bgColor = Color.web("#181008");
            } else if (isActive) {
                bgColor = Color.web("#1a1200");
            } else {
                bgColor = GROUP_BG[(index / 4) % 4];
            }
            gc.setFill(bgColor);
            gc.fillRoundRect(0, 0, w, h, 3, 3);

            // ---- border ----
            Color borderColor;
            if (isSelected) {
                borderColor = ForgeColors.ARGENT_AMBER;
            } else if (isPlaying) {
                borderColor = Color.web("#886600");
            } else if (isActive) {
                borderColor = Color.web("#442200");
            } else {
                borderColor = Color.web("#1e1e1e");
            }
            gc.setStroke(borderColor);
            gc.setLineWidth(isSelected ? 1.5 : 1.0);
            gc.strokeRoundRect(0.5, 0.5, w - 1, h - 1, 3, 3);

            if (step == null) return;

            // ---- gate length bar ----
            if (isActive) {
                double barH    = 3.0;
                double barMaxW = w - 4;
                double barW    = barMaxW * step.gateLength;
                double barY    = h - barH - 2;

                Color gateColor = isPlaying
                    ? ForgeColors.ARGENT_YELLOW
                    : ForgeColors.ARGENT_AMBER.deriveColor(0, 1, 0.55, 1);
                gc.setFill(gateColor);
                gc.fillRoundRect(2, barY, barW, barH, 1.5, 1.5);
            }

            // ---- note name text ----
            if (isActive) {
                String noteName = midiToNoteName(step.midiNote);
                gc.setFont(Font.font("Monospace", FontWeight.BOLD, 8.5));
                gc.setFill(isPlaying ? ForgeColors.ARGENT_YELLOW : ForgeColors.ARGENT_AMBER);
                // center text
                javafx.scene.text.Text measure = new Text(noteName);
                measure.setFont(gc.getFont());
                double tw = measure.getBoundsInLocal().getWidth();
                double th = measure.getBoundsInLocal().getHeight();
                gc.fillText(noteName, (w - tw) / 2.0, (h - 5 + th / 2.0) / 2.0 + 1);
            }

            // ---- slide arrow ----
            // Draw a small right-arrow connector at the right edge when slide is on
            if (isActive && step.slide && index < STEPS - 1) {
                gc.setStroke(ForgeColors.VEGA_CYAN.deriveColor(0, 1, 0.7, 1));
                gc.setLineWidth(1.5);
                double arrowY = h / 2.0 - 1;
                gc.strokeLine(w - 4, arrowY, w, arrowY);
                gc.strokeLine(w - 3, arrowY - 2, w, arrowY);
                gc.strokeLine(w - 3, arrowY + 2, w, arrowY);
            }

            // ---- selected step indicator dot (bottom center) ----
            if (isSelected) {
                gc.setFill(ForgeColors.ARGENT_AMBER);
                gc.fillOval(w / 2.0 - 2, h - 5, 4, 4);
            }
        }
    }

    // =========================================================================
    // MIDI note → name
    // =========================================================================

    private static final String[] NOTE_NAMES = {
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    };

    static String midiToNoteName(int midi) {
        int octave = (midi / 12) - 1;
        String name = NOTE_NAMES[midi % 12];
        return name + octave;
    }
}
