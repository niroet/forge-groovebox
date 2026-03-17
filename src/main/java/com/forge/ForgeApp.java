package com.forge;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.effects.EffectsChain;
import com.forge.audio.engine.AudioEngine;
import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.SequencerListener;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.audio.synth.SubtractiveSynthVoice;
import com.forge.audio.synth.SynthVoice;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;
import com.forge.model.SynthPatch;
import com.forge.ui.panels.DrumPanel;
import com.forge.ui.panels.SynthPanel;
import com.forge.ui.theme.CrtOverlay;
import com.forge.ui.theme.ForgeColors;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FORGE.EXE -- 3-panel DOOM shell with live audio engine.
 *
 * Layout:
 *   StackPane (root)
 *   +-- BorderPane (main)
 *   |   +-- Top:    title bar + menu bar
 *   |   +-- Center: HBox (left synth | center visualizer+drums | right VEGA)
 *   |   +-- Bottom: status bar
 *   +-- CrtOverlay (scanlines + vignette, mouse-transparent)
 */
public class ForgeApp extends Application {

    private static final int WIDTH  = 1280;
    private static final int HEIGHT = 780;

    // ---- Drag state for custom title bar ------------------------------------
    private double dragOffsetX, dragOffsetY;

    // ---- Audio graph --------------------------------------------------------
    private AudioEngine audioEngine;
    private DrumEngine drumEngine;
    private SynthVoice[] synthVoices;
    private VoiceAllocator voiceAllocator;
    private SequencerClock clock;
    private StepSequencer sequencer;
    private EffectsChain effectsChain;
    private SynthPatch globalPatch;
    private ClockDriver clockDriver;

    // ---- UI panels ----------------------------------------------------------
    private SynthPanel synthPanel;
    private DrumPanel drumPanel;

    // ---- Step tracking ------------------------------------------------------
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private AnimationTimer stepAnimator;

    // ---- Keyboard tracking --------------------------------------------------
    private final Set<KeyCode> heldKeys = new HashSet<>();
    private final Map<KeyCode, Integer> activeKeyNotes = new HashMap<>();
    private int octaveShift = 0;

    // MIDI note mapping
    private static final int[] ASDF_NOTES = {60, 62, 64, 65, 67, 69, 71, 72, 74};
    private static final KeyCode[] ASDF_KEYS = {
        KeyCode.A, KeyCode.S, KeyCode.D, KeyCode.F,
        KeyCode.G, KeyCode.H, KeyCode.J, KeyCode.K, KeyCode.L
    };
    private static final int[] ZXCV_NOTES = {48, 50, 52, 53, 55, 57, 59};
    private static final KeyCode[] ZXCV_KEYS = {
        KeyCode.Z, KeyCode.X, KeyCode.C, KeyCode.V,
        KeyCode.B, KeyCode.N, KeyCode.M
    };

    // =========================================================================
    // ClockDriver inner class -- ticked by JSyn audio thread
    // =========================================================================

    private static class ClockDriver extends com.jsyn.unitgen.UnitGenerator {
        private final SequencerClock clock;

        ClockDriver(SequencerClock clock) {
            this.clock = clock;
        }

        @Override
        public void generate(int start, int limit) {
            for (int i = start; i < limit; i++) {
                clock.tick();
            }
        }
    }

    // =========================================================================
    // JavaFX lifecycle
    // =========================================================================

    @Override
    public void start(Stage stage) {
        // Build audio graph first
        buildAudioGraph();

        BorderPane main = new BorderPane();
        main.setStyle("-fx-background-color: #080808;");

        // Top section: title bar + menu bar
        VBox topSection = new VBox();
        topSection.getChildren().addAll(buildTitleBar(stage), buildMenuBar());
        main.setTop(topSection);

        // Center: 3 panels (synth | visualizer+drums | VEGA)
        main.setCenter(buildCenterPanels());

        // Bottom: status bar
        main.setBottom(buildStatusBar());

        // CRT overlay on top of everything
        CrtOverlay crt = new CrtOverlay();

        StackPane root = new StackPane(main, crt);
        root.setStyle("-fx-background-color: #080808;");

        crt.prefWidthProperty().bind(root.widthProperty());
        crt.prefHeightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.web("#080808"));

        // Load CSS
        String css = getClass().getResource("/css/forge-theme.css") != null
            ? getClass().getResource("/css/forge-theme.css").toExternalForm()
            : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        // Wire keyboard
        hookKeys(scene);

        // Build initial drum beat data BEFORE wiring (so grid syncs correctly)
        buildDefaultBeat();

        // Wire audio to UI panels
        wireAudioToUI();

        // Start step position animator
        startStepAnimator();

        stage.setTitle("FORGE.EXE \u2014 Sound Terminal v2.016");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (stepAnimator != null) {
            stepAnimator.stop();
        }
        if (clock != null) {
            clock.stop();
        }
        if (audioEngine != null) {
            audioEngine.stop();
        }
    }

    // =========================================================================
    // Audio setup
    // =========================================================================

    private void buildAudioGraph() {
        audioEngine = new AudioEngine();
        audioEngine.start();

        // 8 synth voices
        synthVoices = new SynthVoice[8];
        for (int i = 0; i < 8; i++) {
            synthVoices[i] = new SubtractiveSynthVoice();
            synthVoices[i].init(audioEngine.getSynth());
            synthVoices[i].getOutput().connect(0, audioEngine.getMasterMixer().input, i);
        }
        voiceAllocator = new VoiceAllocator(synthVoices);

        // Global synth patch
        globalPatch = new SynthPatch();
        for (SynthVoice v : synthVoices) {
            v.applyPatch(globalPatch);
        }

        // Drum engine (uses mixer channels 4-7)
        drumEngine = new DrumEngine(audioEngine);

        // Apply reasonable default drum patches
        DrumPatch kickPatch = new DrumPatch();
        kickPatch.pitch = 55.0;
        kickPatch.decay = 0.45;
        kickPatch.snap = 0.6;
        kickPatch.drive = 0.3;
        drumEngine.applyPatch(DrumTrack.KICK, kickPatch);

        DrumPatch snarePatch = new DrumPatch();
        snarePatch.pitch = 220.0;
        snarePatch.decay = 0.18;
        snarePatch.toneNoise = 0.4;
        snarePatch.snap = 0.7;
        drumEngine.applyPatch(DrumTrack.SNARE, snarePatch);

        DrumPatch hatPatch = new DrumPatch();
        hatPatch.pitch = 8000.0;
        hatPatch.decay = 0.06;
        hatPatch.toneNoise = 0.0;
        drumEngine.applyPatch(DrumTrack.HAT, hatPatch);

        DrumPatch percPatch = new DrumPatch();
        percPatch.pitch = 800.0;
        percPatch.decay = 0.12;
        percPatch.toneNoise = 0.6;
        percPatch.snap = 0.4;
        drumEngine.applyPatch(DrumTrack.PERC, percPatch);

        // Sequencer clock at 128 BPM
        clock = new SequencerClock();
        clock.setSampleRate(AudioEngine.SAMPLE_RATE);
        clock.setBpm(128.0);
        clock.setStepsPerBar(16);

        // Step sequencer
        sequencer = new StepSequencer(drumEngine, voiceAllocator);

        // Clock listener for step updates
        clock.setListener(new SequencerListener() {
            @Override
            public void onStep(int stepIndex) {
                sequencer.onStep(stepIndex);
                currentStep.set(stepIndex);
            }

            @Override
            public void onBarEnd(int barNumber) {
                sequencer.onBarEnd(barNumber);
            }
        });

        // Clock driver: ticked by JSyn audio thread
        clockDriver = new ClockDriver(clock);
        audioEngine.getSynth().add(clockDriver);
        clockDriver.start();

        // Effects chain -- skip wiring into signal path for now (mixer -> lineOut direct)
        // Effects chain is created for UI control but not inserted into audio path
        effectsChain = new EffectsChain(audioEngine.getSynth());
    }

    private void wireAudioToUI() {
        synthPanel.wire(synthVoices, globalPatch, effectsChain);
        drumPanel.wire(clock, sequencer);
    }

    private void buildDefaultBeat() {
        // Pre-load pattern A with a basic 4-on-floor beat.
        // Called BEFORE wireAudioToUI() so the grid displays the beat when synced.
        com.forge.model.Pattern pat = drumPanel.getActivePattern();

        // Kick: steps 0, 4, 8, 12
        for (int s : new int[]{0, 4, 8, 12}) {
            pat.drumSteps[DrumTrack.KICK.ordinal()][s].active = true;
            pat.drumSteps[DrumTrack.KICK.ordinal()][s].velocity = 0.9;
        }

        // Snare: steps 4, 12
        for (int s : new int[]{4, 12}) {
            pat.drumSteps[DrumTrack.SNARE.ordinal()][s].active = true;
            pat.drumSteps[DrumTrack.SNARE.ordinal()][s].velocity = 0.85;
        }

        // Hat: every even step
        for (int s = 0; s < 16; s += 2) {
            pat.drumSteps[DrumTrack.HAT.ordinal()][s].active = true;
            pat.drumSteps[DrumTrack.HAT.ordinal()][s].velocity = 0.7;
        }
    }

    // =========================================================================
    // Step position animator
    // =========================================================================

    private void startStepAnimator() {
        stepAnimator = new AnimationTimer() {
            private int lastStep = -1;

            @Override
            public void handle(long now) {
                int step = currentStep.get();
                if (step != lastStep) {
                    lastStep = step;
                    drumPanel.setPlayingStep(step);
                }
            }
        };
        stepAnimator.start();
    }

    // =========================================================================
    // Keyboard handling
    // =========================================================================

    private void hookKeys(Scene scene) {
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();

            if (heldKeys.contains(code)) return;
            heldKeys.add(code);

            if (code == KeyCode.SPACE) {
                drumPanel.togglePlayStop();
                return;
            }

            if (code == KeyCode.Q) {
                releaseAllHeldNotes();
                octaveShift -= 12;
                return;
            }
            if (code == KeyCode.W) {
                releaseAllHeldNotes();
                octaveShift += 12;
                return;
            }

            // ASDF row
            for (int i = 0; i < ASDF_KEYS.length; i++) {
                if (code == ASDF_KEYS[i]) {
                    int note = ASDF_NOTES[i] + octaveShift;
                    activeKeyNotes.put(code, note);
                    voiceAllocator.allocate(note, 0.8);
                    return;
                }
            }

            // ZXCV row
            for (int i = 0; i < ZXCV_KEYS.length; i++) {
                if (code == ZXCV_KEYS[i]) {
                    int note = ZXCV_NOTES[i] + octaveShift;
                    activeKeyNotes.put(code, note);
                    voiceAllocator.allocate(note, 0.8);
                    return;
                }
            }
        });

        scene.setOnKeyReleased(event -> {
            KeyCode code = event.getCode();
            heldKeys.remove(code);

            Integer note = activeKeyNotes.remove(code);
            if (note != null) {
                voiceAllocator.releaseNote(note);
            }
        });
    }

    private void releaseAllHeldNotes() {
        for (Integer note : activeKeyNotes.values()) {
            voiceAllocator.releaseNote(note);
        }
        activeKeyNotes.clear();
    }

    // =========================================================================
    // Title bar
    // =========================================================================

    private HBox buildTitleBar(Stage stage) {
        HBox bar = new HBox();
        bar.setStyle(
            "-fx-background-color: linear-gradient(to right, #1a0000, #330000, #1a0000);" +
            "-fx-padding: 3 6 3 8;" +
            "-fx-border-color: transparent transparent #441100 transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("\u2B21 FORGE.EXE \u2014 Sound Terminal v2.016");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-effect: dropshadow(gaussian, #ff6600, 6, 0.5, 0, 0);");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnMin   = makeWinBtn("_",  false);
        Button btnMax   = makeWinBtn("\u25A1", false);
        Button btnClose = makeWinBtn("\u00D7", true);

        btnClose.setOnAction(e -> Platform.exit());

        bar.getChildren().addAll(title, spacer, btnMin, btnMax, btnClose);

        bar.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        return bar;
    }

    private Button makeWinBtn(String text, boolean isClose) {
        Button btn = new Button(text);
        String base =
            "-fx-background-color: linear-gradient(to bottom, #3a3a3a, #1a1a1a);" +
            "-fx-border-color: #555555 #1a1a1a #1a1a1a #555555;" +
            "-fx-border-width: 1px;" +
            "-fx-text-fill: #cccccc;" +
            "-fx-font-family: Monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 5 0 5;" +
            "-fx-min-width: 16px;" +
            "-fx-min-height: 14px;" +
            "-fx-max-height: 14px;";
        btn.setStyle(base);

        String hoverStyle = isClose
            ? base + "-fx-background-color: #660000; -fx-text-fill: #ff4444;"
            : base + "-fx-background-color: #441100; -fx-text-fill: #ff6600;";

        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    // =========================================================================
    // Menu bar
    // =========================================================================

    private HBox buildMenuBar() {
        HBox bar = new HBox(0);
        bar.setStyle(
            "-fx-background-color: #0d0d0d;" +
            "-fx-border-color: transparent transparent #222222 transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 0 4 0 4;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        String[] menus = {
            "Protocol", "Edit", "Synth.Array", "Drum.Seq",
            "VEGA", "Export", "Diagnostics"
        };

        for (String m : menus) {
            Label lbl = new Label(m);
            lbl.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
            lbl.setTextFill(Color.web("#aaaaaa"));
            lbl.setPadding(new Insets(2, 8, 2, 8));
            lbl.setStyle("-fx-cursor: hand;");
            lbl.setOnMouseEntered(e -> {
                lbl.setTextFill(Color.web("#ff8800"));
                lbl.setStyle("-fx-cursor: hand; -fx-background-color: #1a0800;");
            });
            lbl.setOnMouseExited(e -> {
                lbl.setTextFill(Color.web("#aaaaaa"));
                lbl.setStyle("-fx-cursor: hand; -fx-background-color: transparent;");
            });
            bar.getChildren().add(lbl);
        }

        return bar;
    }

    // =========================================================================
    // Center -- 3 panels
    // =========================================================================

    private HBox buildCenterPanels() {
        HBox center = new HBox(0);

        // LEFT: Synth panel
        synthPanel = new SynthPanel();

        // CENTER: Visualizer placeholder + Drum panel
        VBox centerColumn = new VBox(0);
        centerColumn.setStyle("-fx-background-color: #0a0a0a;");

        // Visualizer placeholder (top portion)
        VBox visPlaceholder = new VBox();
        visPlaceholder.setAlignment(Pos.CENTER);
        visPlaceholder.setStyle("-fx-background-color: #0a0a0a; -fx-border-color: transparent transparent #1a1a1a transparent; -fx-border-width: 0 0 1 0;");
        VBox.setVgrow(visPlaceholder, Priority.ALWAYS);

        Label visLabel = new Label("VISUALIZER");
        visLabel.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
        visLabel.setTextFill(Color.web("#2a2a2a"));
        visPlaceholder.getChildren().add(visLabel);

        // Drum panel (bottom portion)
        drumPanel = new DrumPanel();

        centerColumn.getChildren().addAll(visPlaceholder, drumPanel);
        HBox.setHgrow(centerColumn, Priority.ALWAYS);

        // RIGHT: VEGA terminal placeholder
        VBox vegaPanel = buildPanel("VEGA TERMINAL", 220, -1, ForgeColors.VEGA_CYAN);

        center.getChildren().addAll(synthPanel, centerColumn, vegaPanel);
        return center;
    }

    private VBox buildPanel(String name, double prefW, double prefH, Color accentColor) {
        VBox panel = new VBox();
        panel.setStyle(
            "-fx-background-color: #0a0a0a;" +
            "-fx-border-color: #222222;" +
            "-fx-border-width: 0 1 0 0;"
        );

        if (prefW > 0) {
            panel.setPrefWidth(prefW);
            panel.setMinWidth(prefW);
            panel.setMaxWidth(prefW);
        }
        if (prefH > 0) {
            panel.setPrefHeight(prefH);
        }

        HBox header = new HBox();
        header.setStyle(
            "-fx-background-color: #0d0d0d;" +
            "-fx-border-color: transparent transparent #1a1a1a transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 3 8 3 8;"
        );
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        nameLabel.setTextFill(accentColor.deriveColor(0, 1, 0.5, 0.7));

        Region dot = new Region();
        dot.setPrefSize(4, 4);
        dot.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 2;",
            ForgeColors.hex(accentColor)
        ));
        HBox.setMargin(dot, new Insets(0, 6, 0, 0));

        header.getChildren().addAll(dot, nameLabel);

        VBox content = new VBox();
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setAlignment(Pos.CENTER);

        Label placeholder = new Label(name);
        placeholder.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
        placeholder.setTextFill(Color.web("#2a2a2a"));

        content.getChildren().add(placeholder);

        panel.getChildren().addAll(header, content);
        VBox.setVgrow(panel, Priority.ALWAYS);

        return panel;
    }

    // =========================================================================
    // Status bar
    // =========================================================================

    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.setStyle(
            "-fx-background-color: #111111;" +
            "-fx-border-color: #222222 transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-padding: 3 10 3 10;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        Label left = makeStatusLabel(
            "\u25C7 VEGA:ONLINE  \u25CF AUDIO:ACTIVE  VERSE[4/8]  Dm"
        );
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label right = makeStatusLabel(
            "CPU:23%  |  5.8ms  |  44.1kHz  |  MIDI:CH1"
        );

        bar.getChildren().addAll(left, spacer, right);
        return bar;
    }

    private Label makeStatusLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));
        l.setTextFill(Color.web("#666666"));
        return l;
    }

    // =========================================================================

    public static void main(String[] args) {
        launch(args);
    }
}
