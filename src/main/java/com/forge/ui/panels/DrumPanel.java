package com.forge.ui.panels;

import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.ui.controls.StepButton;
import com.forge.ui.theme.ForgeColors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Drum step-sequencer grid + transport bar.
 *
 * <p>4 drum tracks (KICK, SNARE, HAT, PERC) x 16 steps with clickable
 * StepButtons, per-track mute/solo, pattern selector, and a transport
 * bar with play/stop/arm, BPM, and swing controls.
 */
public class DrumPanel extends VBox {

    // ---- Audio references ---------------------------------------------------
    private SequencerClock clock;
    private StepSequencer sequencer;
    private Pattern pattern;

    // ---- Grid ---------------------------------------------------------------
    private final StepButton[][] stepButtons = new StepButton[4][16];
    private static final String[] TRACK_NAMES = {"KICK", "SNARE", "HAT", "PERC"};
    private static final Color[] TRACK_COLORS = {
        ForgeColors.ARGENT_RED, ForgeColors.ARGENT_ORANGE,
        ForgeColors.ARGENT_YELLOW, ForgeColors.ARGENT_ORANGE
    };

    // ---- Transport ----------------------------------------------------------
    private Label playBtn;
    private Label stopBtn;
    private Label armBtn;
    private TextField bpmField;
    private volatile boolean playing = false;
    private int currentPlayingStep = -1;

    // ---- Mute/Solo state ----------------------------------------------------
    private final boolean[] trackMuted = new boolean[4];
    private final boolean[] trackSoloed = new boolean[4];
    private final Label[] muteLabels = new Label[4];
    private final Label[] soloLabels = new Label[4];

    // ---- Pattern selector ---------------------------------------------------
    private int activePatternIdx = 0;
    private Pattern[] patterns;
    private Label[] patternLabels;

    // =========================================================================
    // Constructor
    // =========================================================================

    public DrumPanel() {
        super(4);
        setPadding(new Insets(6));
        setStyle("-fx-background-color: #0a0a0a;");

        // Initialize 4 patterns
        patterns = new Pattern[4];
        for (int i = 0; i < 4; i++) {
            patterns[i] = new Pattern();
        }
        pattern = patterns[0];

        getChildren().addAll(
            buildHeader(),
            buildDrumGrid(),
            buildTransportBar()
        );
    }

    // =========================================================================
    // Wiring
    // =========================================================================

    /**
     * Wire this panel to the audio engine. Sets up the initial pattern and
     * connects step buttons to the pattern data.
     */
    public void wire(SequencerClock clock, StepSequencer sequencer) {
        this.clock = clock;
        this.sequencer = sequencer;
        this.sequencer.setPattern(pattern);
        syncGridToPattern();
    }

    /**
     * Get the currently active pattern (needed by ForgeApp to init the sequencer).
     */
    public Pattern getActivePattern() {
        return pattern;
    }

    // =========================================================================
    // Step position update (called from AnimationTimer)
    // =========================================================================

    /**
     * Highlight the given step column. Call from the JavaFX thread.
     * Pass -1 to clear all highlights.
     */
    public void setPlayingStep(int step) {
        if (step == currentPlayingStep) return;

        // Clear previous
        if (currentPlayingStep >= 0 && currentPlayingStep < 16) {
            for (int t = 0; t < 4; t++) {
                StepButton btn = stepButtons[t][currentPlayingStep];
                btn.setState(btn.isActive() ? StepButton.ButtonState.ON : StepButton.ButtonState.OFF);
            }
        }

        currentPlayingStep = step;

        // Set new
        if (step >= 0 && step < 16) {
            for (int t = 0; t < 4; t++) {
                StepButton btn = stepButtons[t][step];
                if (btn.isActive()) {
                    btn.setState(StepButton.ButtonState.PLAYING);
                }
            }
        }
    }

    // =========================================================================
    // Header with pattern selector
    // =========================================================================

    private HBox buildHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 0, 4, 0));

        Label title = new Label("\u25C6 DRUM PROTOCOL \u2014 16 STEPS");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        title.setTextFill(ForgeColors.ARGENT_RED);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Pattern selector
        HBox patSel = new HBox(2);
        patSel.setAlignment(Pos.CENTER_RIGHT);
        String[] patNames = {"PAT A", "PAT B", "PAT C", "PAT D"};
        patternLabels = new Label[4];
        for (int i = 0; i < 4; i++) {
            Label pBtn = new Label(patNames[i]);
            pBtn.setFont(Font.font("Monospace", FontWeight.BOLD, 8));
            pBtn.setPadding(new Insets(2, 4, 2, 4));
            pBtn.setStyle("-fx-cursor: hand; -fx-background-radius: 2;");
            patternLabels[i] = pBtn;

            final int idx = i;
            pBtn.setOnMouseClicked(e -> switchPattern(idx));
            patSel.getChildren().add(pBtn);
        }
        updatePatternButtons();

        header.getChildren().addAll(title, spacer, patSel);
        return header;
    }

    // =========================================================================
    // Drum Grid
    // =========================================================================

    private GridPane buildDrumGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setPadding(new Insets(2));

        for (int t = 0; t < 4; t++) {
            // Track label
            Label trackLabel = new Label(TRACK_NAMES[t]);
            trackLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            trackLabel.setTextFill(TRACK_COLORS[t]);
            trackLabel.setPrefWidth(40);
            trackLabel.setMinWidth(40);
            grid.add(trackLabel, 0, t);

            // Step buttons
            for (int s = 0; s < 16; s++) {
                StepButton btn = new StepButton();
                btn.setTrackColor(TRACK_COLORS[t]);
                btn.setPrefSize(26, 22);
                btn.setMinSize(26, 22);
                stepButtons[t][s] = btn;

                // Wire click to pattern data
                final int track = t;
                final int step = s;
                btn.activeProperty().addListener((obs, o, n) -> {
                    if (pattern != null) {
                        pattern.drumSteps[track][step].active = n;
                        if (n) {
                            pattern.drumSteps[track][step].velocity = 0.8;
                        }
                    }
                });

                grid.add(btn, s + 1, t);
            }

            // Mute button
            Label mBtn = new Label("M");
            mBtn.setFont(Font.font("Monospace", FontWeight.BOLD, 8));
            mBtn.setTextFill(Color.web("#555555"));
            mBtn.setPadding(new Insets(2, 4, 2, 4));
            mBtn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2;");
            muteLabels[t] = mBtn;
            final int trackIdx = t;
            mBtn.setOnMouseClicked(e -> {
                trackMuted[trackIdx] = !trackMuted[trackIdx];
                updateMuteSolo(trackIdx);
                if (sequencer != null) {
                    sequencer.setTrackMuted(trackIdx, trackMuted[trackIdx]);
                }
            });
            grid.add(mBtn, 17, t);

            // Solo button
            Label sBtn = new Label("S");
            sBtn.setFont(Font.font("Monospace", FontWeight.BOLD, 8));
            sBtn.setTextFill(Color.web("#555555"));
            sBtn.setPadding(new Insets(2, 4, 2, 4));
            sBtn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2;");
            soloLabels[t] = sBtn;
            sBtn.setOnMouseClicked(e -> {
                trackSoloed[trackIdx] = !trackSoloed[trackIdx];
                applySoloState();
                updateMuteSolo(trackIdx);
            });
            grid.add(sBtn, 18, t);
        }

        return grid;
    }

    // =========================================================================
    // Transport Bar
    // =========================================================================

    private HBox buildTransportBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 4, 4, 4));
        bar.setStyle("-fx-background-color: #0d0d0d; -fx-border-color: #1a1a1a transparent transparent transparent; -fx-border-width: 1 0 0 0;");

        // Play button
        playBtn = makeTransportBtn("\u25B6 RIP");
        playBtn.setOnMouseClicked(e -> startPlayback());

        // Stop button
        stopBtn = makeTransportBtn("\u23F9 HALT");
        stopBtn.setOnMouseClicked(e -> stopPlayback());

        // Arm button
        armBtn = makeTransportBtn("\u25CF ARM");

        // BPM
        Label bpmLabel = new Label("BPM:");
        bpmLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        bpmLabel.setTextFill(Color.web("#888888"));

        bpmField = new TextField("128");
        bpmField.setPrefWidth(50);
        bpmField.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));
        bpmField.setStyle("-fx-background-color: #111111; -fx-text-fill: " +
            ForgeColors.hex(ForgeColors.ARGENT_ORANGE) +
            "; -fx-border-color: #333333; -fx-border-width: 1; -fx-font-family: Monospace;");

        bpmField.setOnAction(e -> applyBpm());
        bpmField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) applyBpm();
        });

        // Swing label (placeholder - just a display for now)
        Label swingLabel = new Label("SWING: 50%");
        swingLabel.setFont(Font.font("Monospace", FontWeight.NORMAL, 9));
        swingLabel.setTextFill(Color.web("#666666"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(playBtn, stopBtn, armBtn, bpmLabel, bpmField, swingLabel, spacer);
        return bar;
    }

    private Label makeTransportBtn(String text) {
        Label btn = new Label(text);
        btn.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        btn.setTextFill(Color.web("#aaaaaa"));
        btn.setPadding(new Insets(4, 10, 4, 10));
        btn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-border-color: #333333; " +
            "-fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-cursor: hand; -fx-background-color: #2a1500; -fx-border-color: " +
            ForgeColors.hex(ForgeColors.ARGENT_ORANGE) +
            "; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;"));
        btn.setOnMouseExited(e -> {
            if (playing && btn == playBtn) {
                btn.setStyle("-fx-cursor: hand; -fx-background-color: #220000; -fx-border-color: " +
                    ForgeColors.hex(ForgeColors.ARGENT_RED) +
                    "; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");
            } else {
                btn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-border-color: #333333; " +
                    "-fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");
            }
        });

        return btn;
    }

    // =========================================================================
    // Transport actions
    // =========================================================================

    public void startPlayback() {
        if (clock == null) return;
        playing = true;
        clock.play();
        playBtn.setTextFill(ForgeColors.ARGENT_RED);
        playBtn.setStyle("-fx-cursor: hand; -fx-background-color: #220000; -fx-border-color: " +
            ForgeColors.hex(ForgeColors.ARGENT_RED) +
            "; -fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");
    }

    public void stopPlayback() {
        if (clock == null) return;
        playing = false;
        clock.stop();
        setPlayingStep(-1);
        playBtn.setTextFill(Color.web("#aaaaaa"));
        playBtn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-border-color: #333333; " +
            "-fx-border-width: 1; -fx-background-radius: 3; -fx-border-radius: 3;");
    }

    public void togglePlayStop() {
        if (playing) stopPlayback();
        else startPlayback();
    }

    public boolean isPlaying() {
        return playing;
    }

    // =========================================================================
    // Pattern switching
    // =========================================================================

    private void switchPattern(int idx) {
        activePatternIdx = idx;
        pattern = patterns[idx];
        if (sequencer != null) {
            sequencer.setPattern(pattern);
        }
        syncGridToPattern();
        updatePatternButtons();
    }

    private void updatePatternButtons() {
        for (int i = 0; i < 4; i++) {
            if (patternLabels == null) return;
            if (i == activePatternIdx) {
                patternLabels[i].setTextFill(Color.WHITE);
                patternLabels[i].setStyle("-fx-cursor: hand; -fx-background-color: " +
                    ForgeColors.hex(ForgeColors.ARGENT_RED) + "; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
            } else {
                patternLabels[i].setTextFill(Color.web("#555555"));
                patternLabels[i].setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
            }
        }
    }

    // =========================================================================
    // Grid sync
    // =========================================================================

    private void syncGridToPattern() {
        for (int t = 0; t < 4; t++) {
            for (int s = 0; s < 16; s++) {
                stepButtons[t][s].setActive(pattern.drumSteps[t][s].active);
            }
        }
    }

    // =========================================================================
    // Mute/Solo
    // =========================================================================

    private void updateMuteSolo(int trackIdx) {
        if (trackMuted[trackIdx]) {
            muteLabels[trackIdx].setTextFill(ForgeColors.ARGENT_RED);
            muteLabels[trackIdx].setStyle("-fx-cursor: hand; -fx-background-color: #2a0000; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
        } else {
            muteLabels[trackIdx].setTextFill(Color.web("#555555"));
            muteLabels[trackIdx].setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
        }
        if (trackSoloed[trackIdx]) {
            soloLabels[trackIdx].setTextFill(ForgeColors.ARGENT_AMBER);
            soloLabels[trackIdx].setStyle("-fx-cursor: hand; -fx-background-color: #1a1400; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
        } else {
            soloLabels[trackIdx].setTextFill(Color.web("#555555"));
            soloLabels[trackIdx].setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2; -fx-padding: 2 4 2 4;");
        }
    }

    private void applySoloState() {
        boolean anySolo = false;
        for (boolean s : trackSoloed) if (s) { anySolo = true; break; }

        if (sequencer != null) {
            for (int t = 0; t < 4; t++) {
                if (anySolo) {
                    // When solo is active, mute all non-soloed tracks
                    sequencer.setTrackMuted(t, !trackSoloed[t]);
                } else {
                    // Revert to manual mute state
                    sequencer.setTrackMuted(t, trackMuted[t]);
                }
            }
        }
    }

    // =========================================================================
    // BPM
    // =========================================================================

    private void applyBpm() {
        if (clock == null) return;
        try {
            double bpm = Double.parseDouble(bpmField.getText().trim());
            bpm = Math.max(30, Math.min(300, bpm));
            clock.setBpm(bpm);
            bpmField.setText(String.valueOf((int) bpm));
        } catch (NumberFormatException ex) {
            bpmField.setText(String.valueOf((int) clock.getBpm()));
        }
    }
}
