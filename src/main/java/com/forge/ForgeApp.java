package com.forge;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.effects.EffectsChain;
import com.forge.audio.engine.AudioEngine;
import com.forge.audio.export.MidiExporter;
import com.forge.audio.export.WavExporter;
import com.forge.audio.sequencer.SectionManager;
import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.SequencerListener;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.audio.synth.FmSynthVoice;
import com.forge.audio.synth.GranularSynthVoice;
import com.forge.audio.synth.SubtractiveSynthVoice;
import com.forge.audio.synth.SynthVoice;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.audio.synth.WavetableSynthVoice;
import com.forge.midi.MidiInputHandler;
import com.forge.model.DrumPatch;
import com.forge.model.DrumTrack;
import com.forge.model.EngineType;
import com.forge.model.ProjectPersistence;
import com.forge.model.ProjectState;
import com.forge.model.SynthPatch;
import com.forge.model.UndoManager;
import com.forge.ui.panels.DrumPanel;
import com.forge.ui.panels.SynthPanel;
import com.forge.ui.panels.SynthSequenceGrid;
import com.forge.ui.panels.VegaPanel;
import com.forge.ui.theme.CrtOverlay;
import com.forge.ui.theme.ForgeColors;
import com.forge.ui.visualizer.VisualizerPanel;
import com.forge.model.VisualizerMode;
import com.forge.vega.VegaAgent;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
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
    private SectionManager sectionManager;

    // ---- Project model ------------------------------------------------------
    private ProjectState projectState;

    // ---- MIDI input ---------------------------------------------------------
    private MidiInputHandler midiInputHandler;

    // ---- Undo/redo ----------------------------------------------------------
    private final UndoManager undoManager = new UndoManager();

    // ---- Stage reference (needed for FileChooser) ---------------------------
    private Stage primaryStage;

    // ---- VEGA AI ------------------------------------------------------------
    private VegaAgent vegaAgent;

    // ---- UI panels ----------------------------------------------------------
    private SynthPanel synthPanel;
    private DrumPanel drumPanel;
    private SynthSequenceGrid synthGrid;
    private VisualizerPanel visualizerPanel;
    private VegaPanel vegaPanel;

    // ---- Step tracking ------------------------------------------------------
    private final AtomicInteger currentStep = new AtomicInteger(-1);
    private AnimationTimer stepAnimator;

    // ---- Status bar live labels ---------------------------------------------
    private Label statusLeft;
    private Label statusRight;
    private AnimationTimer statusAnimator;

    // ---- CRT overlay reference (for toggle) ---------------------------------
    private CrtOverlay crtOverlay;

    // ---- Current engine type ------------------------------------------------
    private EngineType currentEngine = EngineType.SUBTRACTIVE;

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
        primaryStage = stage;

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
        crtOverlay = new CrtOverlay();

        StackPane root = new StackPane(main, crtOverlay);
        root.setStyle("-fx-background-color: #080808;");

        crtOverlay.prefWidthProperty().bind(root.widthProperty());
        crtOverlay.prefHeightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.web("#080808"));

        // Load CSS
        String css = getClass().getResource("/css/forge-theme.css") != null
            ? getClass().getResource("/css/forge-theme.css").toExternalForm()
            : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        // Wire keyboard (includes Ctrl+Z / Ctrl+Shift+Z for undo/redo)
        hookKeys(scene);

        // Build initial drum beat data BEFORE wiring (so grid syncs correctly)
        buildDefaultBeat();

        // Push initial state into undo history
        undoManager.push(projectState);

        // Wire audio to UI panels
        wireAudioToUI();

        // Start step position animator
        startStepAnimator();

        stage.setTitle("FORGE.EXE \u2014 Sound Terminal v2.016");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(660);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (midiInputHandler != null) {
            midiInputHandler.stop();
        }
        if (stepAnimator != null) {
            stepAnimator.stop();
        }
        if (statusAnimator != null) {
            statusAnimator.stop();
        }
        if (visualizerPanel != null) {
            visualizerPanel.stop();
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
        // Project state — single source of truth for all model data
        projectState = new ProjectState();

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

        // Section manager — live arrangement
        sectionManager = new SectionManager(projectState, sequencer);

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
                sectionManager.onBarEnd();
            }
        });

        // Clock driver: ticked by JSyn audio thread
        clockDriver = new ClockDriver(clock);
        audioEngine.getSynth().add(clockDriver);
        clockDriver.start();

        // Effects chain — owned by AudioEngine, wired into the signal path:
        //   masterMixer → effectsChain → lineOut
        effectsChain = audioEngine.getEffectsChain();

        // VEGA AI agent — receives the live audio components and project state
        projectState.bpm = 128.0;
        vegaAgent = new VegaAgent(
                projectState, drumEngine, voiceAllocator, clock, sequencer, effectsChain);

        // MIDI input — optional, silently skipped if no device available
        midiInputHandler = new MidiInputHandler(voiceAllocator, drumEngine);
        midiInputHandler.start();
    }

    private void wireAudioToUI() {
        synthPanel.wire(synthVoices, globalPatch, effectsChain);
        synthPanel.setOnEngineSwitch(this::handleEngineSwitch);
        drumPanel.wire(clock, sequencer);
        // Wire synth grid to the same pattern the drum panel uses
        synthGrid.setPattern(drumPanel.getActivePattern());
        // Wire AnalysisBus to visualizer panel and start the animation timer
        visualizerPanel.setAnalysisBus(audioEngine.getAnalysisBus());
        visualizerPanel.start();
        // Start live status bar updater at ~4fps
        startStatusAnimator();
    }

    // =========================================================================
    // Engine switching
    // =========================================================================

    /**
     * Switch to a new synth engine type. Releases all active notes, creates fresh voices,
     * disconnects old voices from the mixer, connects new ones, then replaces the pool.
     */
    private void handleEngineSwitch(EngineType engine) {
        if (engine == currentEngine) return;
        currentEngine = engine;

        // 1. Release all active notes immediately
        voiceAllocator.releaseAll();

        // 2. Capture old voices before replacement
        SynthVoice[] oldVoices = voiceAllocator.getVoices();

        // 3. Brief delay on background thread to let release envelopes decay,
        //    then disconnect old voices and swap in new ones.
        new Thread(() -> {
            try {
                Thread.sleep(80); // ~80ms for quick release clicks
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            // Disconnect old voices from mixer
            for (int i = 0; i < oldVoices.length; i++) {
                try {
                    oldVoices[i].getOutput().disconnect(0, audioEngine.getMasterMixer().input, i);
                } catch (Exception ex) {
                    // Best-effort disconnect — ignore if already disconnected
                }
            }

            // Create new voices
            SynthVoice[] newVoices = createVoices(engine);

            // Connect new voices to mixer
            for (int i = 0; i < newVoices.length; i++) {
                newVoices[i].getOutput().connect(0, audioEngine.getMasterMixer().input, i);
            }

            // Apply current patch
            for (SynthVoice v : newVoices) {
                v.applyPatch(globalPatch);
            }

            // Replace the voice pool
            voiceAllocator.replaceVoices(newVoices);

            // Update synth panel voices reference on FX thread
            Platform.runLater(() -> {
                synthVoices = newVoices;
                synthPanel.updateVoices(newVoices);
                System.out.println("[FORGE] Engine switched to: " + engine);
            });
        }, "forge-engine-switch").start();
    }

    /** Create 8 new voices of the given engine type, initialised with the JSyn synth. */
    private SynthVoice[] createVoices(EngineType engine) {
        SynthVoice[] voices = new SynthVoice[8];
        for (int i = 0; i < 8; i++) {
            voices[i] = switch (engine) {
                case SUBTRACTIVE -> new SubtractiveSynthVoice();
                case FM          -> new FmSynthVoice();
                case WAVETABLE   -> new WavetableSynthVoice();
                case GRANULAR    -> new GranularSynthVoice();
            };
            voices[i].init(audioEngine.getSynth());
        }
        return voices;
    }

    private void wireVegaPanel() {
        // Sync divine mode: when the avatar toggles, update the agent
        if (vegaPanel.divineModeProperty() != null) {
            vegaPanel.divineModeProperty().addListener((obs, oldVal, newVal) ->
                    vegaAgent.setDivineMode(newVal));
        }

        // Wire user messages → VegaAgent
        vegaPanel.setOnMessageSent(text -> {
            vegaPanel.addSlayerMessage(text);
            vegaPanel.clearInput();
            vegaPanel.setThinking(true);

            vegaAgent.setDivineMode(vegaPanel.isDivineMode());
            vegaAgent.sendMessage(text, response ->
                javafx.application.Platform.runLater(() -> {
                    vegaPanel.setThinking(false);
                    vegaPanel.addVegaMessage(response);
                })
            );
        });

        // Welcome message shown after the scene is fully laid out
        javafx.application.Platform.runLater(() ->
            vegaPanel.addVegaMessage(
                "Systems online. Audio engine initialized.\n" +
                "\u251C\u2500 Synth: 8 voices, SUBTRACTIVE\n" +
                "\u251C\u2500 Drums: 4 tracks, synthesis\n" +
                "\u251C\u2500 Sequencer: 128 BPM\n" +
                "\u2514\u2500 Effects: 6 modules ready\n" +
                "\u26A1 Awaiting your command, Slayer."
            )
        );
    }

    private void buildDefaultBeat() {
        // Pre-load pattern A with a punchy 4-on-floor beat in D MINOR @ 128 BPM.
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

        // Perc: steps 2, 10 (ghosted off-beat accent)
        for (int s : new int[]{2, 10}) {
            pat.drumSteps[DrumTrack.PERC.ordinal()][s].active = true;
            pat.drumSteps[DrumTrack.PERC.ordinal()][s].velocity = 0.65;
        }

        // Set project key to D MINOR
        projectState.rootNote = "D";
        projectState.scaleType = com.forge.model.ScaleType.MINOR;
        projectState.bpm = 128.0;

        // Better default synth patch: SAW + SQUARE, LP filter, punchy envelope
        globalPatch.oscAShape = com.forge.model.WaveShape.SAW;
        globalPatch.oscBShape = com.forge.model.WaveShape.SQUARE;
        globalPatch.oscALevel = 0.8;
        globalPatch.oscBLevel = 0.4;
        globalPatch.filterCutoff = 2000.0;
        globalPatch.filterResonance = 0.1;
        globalPatch.ampAttack  = 0.01;
        globalPatch.ampDecay   = 0.3;
        globalPatch.ampSustain = 0.7;
        globalPatch.ampRelease = 0.3;
        // Apply updated patch to existing voices
        for (SynthVoice v : synthVoices) {
            v.applyPatch(globalPatch);
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
                    synthGrid.setPlayingStep(step);
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

            // Guard against key-repeat firing multiple allocations
            if (heldKeys.contains(code)) return;
            heldKeys.add(code);

            // ---- Global transport -------------------------------------------
            if (code == KeyCode.SPACE) {
                drumPanel.togglePlayStop();
                return;
            }

            // ---- CRT toggle (Ctrl+E for Effects/CRT overlay) ----------------
            if (event.isControlDown() && code == KeyCode.E) {
                if (crtOverlay != null) {
                    crtOverlay.setEnabled(!crtOverlay.isEnabled());
                }
                return;
            }

            // ---- Undo / Redo ------------------------------------------------
            if (event.isControlDown() && code == KeyCode.Z) {
                if (event.isShiftDown()) {
                    // Ctrl+Shift+Z → redo
                    ProjectState redone = undoManager.redo();
                    if (redone != null) {
                        applyProjectState(redone);
                        System.out.println("[FORGE] Redo → BPM=" + redone.bpm);
                    }
                } else {
                    // Ctrl+Z → undo
                    ProjectState undone = undoManager.undo();
                    if (undone != null) {
                        applyProjectState(undone);
                        System.out.println("[FORGE] Undo → BPM=" + undone.bpm);
                    }
                }
                return;
            }

            // ---- F key: fill active (hold) ----------------------------------
            if (code == KeyCode.F && !event.isControlDown()) {
                sequencer.setFillActive(true);
                System.out.println("[FORGE] fill: ON");
                return;
            }

            // ---- Tab: toggle grid focus (placeholder) -----------------------
            if (code == KeyCode.TAB) {
                event.consume();   // prevent focus traversal
                System.out.println("[FORGE] TAB: grid focus toggled");
                return;
            }

            // ---- Octave shift -----------------------------------------------
            if (code == KeyCode.Q) {
                releaseAllHeldNotes();
                octaveShift -= 12;
                System.out.println("[FORGE] octave: " + (octaveShift / 12));
                return;
            }
            if (code == KeyCode.W) {
                releaseAllHeldNotes();
                octaveShift += 12;
                System.out.println("[FORGE] octave: " + (octaveShift / 12));
                return;
            }

            // ---- Ctrl+1-5: drum/synth mute ----------------------------------
            if (event.isControlDown()) {
                switch (code) {
                    case DIGIT1 -> { drumPanel.muteTrack(0); return; }
                    case DIGIT2 -> { drumPanel.muteTrack(1); return; }
                    case DIGIT3 -> { drumPanel.muteTrack(2); return; }
                    case DIGIT4 -> { drumPanel.muteTrack(3); return; }
                    case DIGIT5 -> { sequencer.setTrackMuted(4, !sequencer.isSynthMuted()); return; }
                    default -> { /* fall through */ }
                }
            }

            // ---- 1-9: queue section by index --------------------------------
            if (!event.isControlDown()) {
                switch (code) {
                    case DIGIT1 -> { sectionManager.queueSectionByIndex(0); return; }
                    case DIGIT2 -> { sectionManager.queueSectionByIndex(1); return; }
                    case DIGIT3 -> { sectionManager.queueSectionByIndex(2); return; }
                    case DIGIT4 -> { sectionManager.queueSectionByIndex(3); return; }
                    case DIGIT5 -> { sectionManager.queueSectionByIndex(4); return; }
                    case DIGIT6 -> { sectionManager.queueSectionByIndex(5); return; }
                    case DIGIT7 -> { sectionManager.queueSectionByIndex(6); return; }
                    case DIGIT8 -> { sectionManager.queueSectionByIndex(7); return; }
                    case DIGIT9 -> { sectionManager.queueSectionByIndex(8); return; }
                    default -> { /* fall through */ }
                }
            }

            // ---- F1-F6: visualizer mode switch ------------------------------
            switch (code) {
                case F1 -> { visualizerPanel.setMode(VisualizerMode.SPECTRUM);     return; }
                case F2 -> { visualizerPanel.setMode(VisualizerMode.OSCILLOSCOPE); return; }
                case F3 -> { visualizerPanel.setMode(VisualizerMode.SPECTROGRAM);  return; }
                case F4 -> { visualizerPanel.setMode(VisualizerMode.TERRAIN);      return; }
                case F5 -> { visualizerPanel.setMode(VisualizerMode.PARTICLES);    return; }
                case F6 -> { visualizerPanel.setMode(VisualizerMode.VEGA_EYE);     return; }
                default -> { /* fall through */ }
            }

            // ---- Synth grid: arrow navigation + step editing ----------------
            if (synthGrid != null && synthGrid.getSelectedStep() >= 0) {
                if (code == KeyCode.LEFT) {
                    synthGrid.moveSelection(-1);
                    return;
                }
                if (code == KeyCode.RIGHT) {
                    synthGrid.moveSelection(1);
                    return;
                }
                if (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
                    synthGrid.deleteSelectedStep();
                    return;
                }
                // Note keys → step record
                Integer stepNote = resolveNoteKey(code);
                if (stepNote != null) {
                    synthGrid.enterNote(stepNote);
                    return;
                }
            }

            // ---- Live keyboard play -----------------------------------------
            Integer liveNote = resolveNoteKey(code);
            if (liveNote != null) {
                activeKeyNotes.put(code, liveNote);
                voiceAllocator.allocate(liveNote, 0.8);
            }
        });

        scene.setOnKeyReleased(event -> {
            KeyCode code = event.getCode();
            heldKeys.remove(code);

            // Fill key released
            if (code == KeyCode.F && !event.isControlDown()) {
                sequencer.setFillActive(false);
                System.out.println("[FORGE] fill: OFF");
                return;
            }

            // Release live note (only if not in step-record mode for that key)
            Integer note = activeKeyNotes.remove(code);
            if (note != null) {
                voiceAllocator.releaseNote(note);
            }
        });
    }

    /**
     * Map a key code to a MIDI note number using the two keyboard rows,
     * respecting the current octave shift.  Returns null if the key is
     * not a note key.
     */
    private Integer resolveNoteKey(KeyCode code) {
        for (int i = 0; i < ASDF_KEYS.length; i++) {
            if (code == ASDF_KEYS[i]) return ASDF_NOTES[i] + octaveShift;
        }
        for (int i = 0; i < ZXCV_KEYS.length; i++) {
            if (code == ZXCV_KEYS[i]) return ZXCV_NOTES[i] + octaveShift;
        }
        return null;
    }

    private void releaseAllHeldNotes() {
        for (Integer note : activeKeyNotes.values()) {
            voiceAllocator.releaseNote(note);
        }
        activeKeyNotes.clear();
    }

    /**
     * Apply a restored project state (from undo/redo or load) to the live engine.
     * Only syncs the BPM for now; full pattern sync would require additional wiring.
     */
    private void applyProjectState(ProjectState state) {
        projectState = state;
        clock.setBpm(state.bpm);
    }

    // =========================================================================
    // Export helpers
    // =========================================================================

    private void doExportWav() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Pattern as WAV");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("WAV Audio", "*.wav"));
        chooser.setInitialFileName("forge_pattern.wav");
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        new Thread(() -> {
            try {
                com.forge.model.Pattern pat = drumPanel.getActivePattern();
                WavExporter.exportPattern(
                    pat, projectState.drumPatches,
                    projectState.globalSynthPatch,
                    projectState.bpm, 1, file);
                Platform.runLater(() -> showInfo("WAV Export", "Exported to:\n" + file.getAbsolutePath()));
            } catch (Exception ex) {
                Platform.runLater(() -> showError("WAV Export Failed", ex.getMessage()));
            }
        }, "forge-wav-export").start();
    }

    private void doExportMidi() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Pattern as MIDI");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("MIDI File", "*.mid"));
        chooser.setInitialFileName("forge_pattern.mid");
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try {
            com.forge.model.Pattern pat = drumPanel.getActivePattern();
            MidiExporter.exportPattern(pat, projectState.bpm, file);
            showInfo("MIDI Export", "Exported to:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            showError("MIDI Export Failed", ex.getMessage());
        }
    }

    private void doSaveProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("FORGE Project", "*.forge"));
        chooser.setInitialFileName("project.forge");
        File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try {
            ProjectPersistence.save(projectState, file);
            showInfo("Save Project", "Saved to:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            showError("Save Failed", ex.getMessage());
        }
    }

    private void doLoadProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Project");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("FORGE Project", "*.forge"));
        File file = chooser.showOpenDialog(primaryStage);
        if (file == null) return;

        try {
            ProjectState loaded = ProjectPersistence.load(file);
            applyProjectState(loaded);
            undoManager.push(loaded);
            showInfo("Load Project", "Loaded from:\n" + file.getAbsolutePath());
        } catch (Exception ex) {
            showError("Load Failed", ex.getMessage());
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "Unknown error");
        alert.showAndWait();
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

        // Menu items: label → action (null = placeholder/future)
        Map<String, Runnable> menuActions = new java.util.LinkedHashMap<>();
        menuActions.put("Protocol",   () -> doSaveProject());       // Protocol → Save
        menuActions.put("Open",       () -> doLoadProject());
        menuActions.put("Synth.Array", null);
        menuActions.put("Drum.Seq",   null);
        menuActions.put("VEGA",       null);
        menuActions.put("Exp.WAV",    () -> doExportWav());
        menuActions.put("Exp.MIDI",   () -> doExportMidi());
        menuActions.put("Diagnostics",null);

        for (Map.Entry<String, Runnable> entry : menuActions.entrySet()) {
            String m = entry.getKey();
            Runnable action = entry.getValue();

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
            if (action != null) {
                lbl.setOnMouseClicked(e -> action.run());
            }
            bar.getChildren().add(lbl);
        }

        return bar;
    }

    // =========================================================================
    // Center -- 3 panels
    // =========================================================================

    private HBox buildCenterPanels() {
        HBox center = new HBox(0);

        // LEFT: Synth panel — border on right edge for visual separation
        synthPanel = new SynthPanel();
        synthPanel.setStyle(synthPanel.getStyle() + " -fx-border-color: transparent #1a1a1a transparent transparent; -fx-border-width: 0 1 0 0;");

        // CENTER: Visualizer placeholder + Drum panel
        VBox centerColumn = new VBox(0);
        centerColumn.setStyle("-fx-background-color: #0a0a0a;");

        // Visualizer panel (top portion)
        visualizerPanel = new VisualizerPanel();
        VBox.setVgrow(visualizerPanel, Priority.ALWAYS);

        // Synth sequence grid (compact strip above drum panel)
        synthGrid = new SynthSequenceGrid();
        // Deselect synth step when clicking elsewhere
        visualizerPanel.setOnMouseClicked(e -> synthGrid.clearSelection());

        // Separator line
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #1a1a1a;");

        // Drum panel (bottom portion)
        drumPanel = new DrumPanel();

        centerColumn.getChildren().addAll(visualizerPanel, synthGrid, sep, drumPanel);
        HBox.setHgrow(centerColumn, Priority.ALWAYS);

        // RIGHT: VEGA terminal panel — border on left edge for visual separation
        vegaPanel = new VegaPanel();
        vegaPanel.setStyle(vegaPanel.getStyle() != null
            ? vegaPanel.getStyle() + " -fx-border-color: transparent transparent transparent #1a1a1a; -fx-border-width: 0 0 0 1;"
            : "-fx-border-color: transparent transparent transparent #1a1a1a; -fx-border-width: 0 0 0 1;");
        wireVegaPanel();

        // Enable caching on static panel headers/labels for GPU performance
        synthPanel.setCache(true);
        synthPanel.setCacheHint(javafx.scene.CacheHint.SPEED);

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

        statusLeft  = makeStatusLabel("\u25C7 VEGA:--  \u25CF AUDIO:--");
        statusRight = makeStatusLabel("CPU:OK  |  5.8ms  |  44.1kHz");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(statusLeft, spacer, statusRight);
        return bar;
    }

    /** Start the 4fps status bar update timer. */
    private void startStatusAnimator() {
        // Fixed latency: 256 samples / 44100 Hz ≈ 5.8 ms
        final String latency    = String.format("%.1fms", 1000.0 * AudioEngine.BUFFER_SIZE / AudioEngine.SAMPLE_RATE);
        final String sampleRate = "44.1kHz";

        statusAnimator = new AnimationTimer() {
            private long lastUpdate = 0L;

            @Override
            public void handle(long now) {
                // Update at ~4 fps (every 250ms)
                if (now - lastUpdate < 250_000_000L) return;
                lastUpdate = now;

                // VEGA status
                String vegaStatus = (System.getenv("ANTHROPIC_API_KEY") != null &&
                                     !System.getenv("ANTHROPIC_API_KEY").isBlank())
                        ? "ONLINE" : "NO KEY";

                // Audio status
                String audioStatus = (audioEngine != null && audioEngine.isRunning()) ? "ACTIVE" : "IDLE";

                // Current section / pattern
                String section = buildSectionDisplay();

                // Key from project state
                String key = buildKeyDisplay();

                statusLeft.setText(
                    "\u25C7 VEGA:" + vegaStatus +
                    "  \u25CF AUDIO:" + audioStatus +
                    "  " + section +
                    "  " + key
                );
                statusRight.setText(
                    "CPU:OK  |  " + latency + "  |  " + sampleRate
                );
            }
        };
        statusAnimator.start();
    }

    private String buildSectionDisplay() {
        if (sectionManager == null || projectState == null) return "PATTERN A";
        // Try to show current section + bar
        com.forge.model.Section active = sectionManager.getActiveSection();
        if (active != null) {
            int barInSection = sectionManager.getCurrentBar() + 1; // 1-based for display
            int totalBars    = active.barLength;
            return active.name.toUpperCase() + " [" + barInSection + "/" + totalBars + "]";
        }
        // Fall back to pattern name
        int patIdx = projectState.activePatternIndex;
        char patLetter = (char)('A' + Math.max(0, Math.min(25, patIdx)));
        return "PATTERN " + patLetter;
    }

    private String buildKeyDisplay() {
        if (projectState == null) return "Cm";
        String root = projectState.rootNote != null ? projectState.rootNote : "C";
        com.forge.model.ScaleType scale = projectState.scaleType;
        if (scale == null) scale = com.forge.model.ScaleType.MINOR;
        String scaleAbbrev = switch (scale) {
            case MAJOR            -> "MAJ";
            case MINOR            -> "MIN";
            case DORIAN           -> "DOR";
            case PHRYGIAN         -> "PHR";
            case LYDIAN           -> "LYD";
            case MIXOLYDIAN       -> "MIX";
            case AEOLIAN          -> "AEO";
            case LOCRIAN          -> "LOC";
            case PENTATONIC_MAJOR -> "PMAJ";
            case PENTATONIC_MINOR -> "PMIN";
            case BLUES            -> "BLU";
            case CHROMATIC        -> "CHR";
        };
        return root + " " + scaleAbbrev;
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
