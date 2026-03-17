package com.forge;

import com.forge.audio.engine.AudioEngine;
import com.forge.audio.drums.DrumEngine;
import com.forge.audio.synth.SubtractiveSynthVoice;
import com.forge.audio.synth.SynthVoice;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.model.DrumPatch;
import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ForgeDemo extends Application {

    // ---- Audio graph --------------------------------------------------------
    private AudioEngine audioEngine;
    private DrumEngine drumEngine;
    private VoiceAllocator voiceAllocator;
    private SequencerClock clock;
    private StepSequencer sequencer;
    private ClockDriver clockDriver;

    // ---- UI state -----------------------------------------------------------
    private final AtomicInteger currentStep = new AtomicInteger(0);
    private volatile boolean playing = false;
    private int octaveShift = 0; // semitone offset from base mapping

    // ---- Key tracking (avoid retrigger on key held) -------------------------
    private final Set<KeyCode> heldKeys = new HashSet<>();
    // Track which MIDI note each key actually triggered (so noteOff uses the right note even after octave shift)
    private final java.util.Map<KeyCode, Integer> activeKeyNotes = new java.util.HashMap<>();

    // ---- UI nodes -----------------------------------------------------------
    private Text stepText;
    private Text playText;
    private Text octaveText;

    // ---- MIDI note mapping: ASDF GH JK L and ZXCV BN M ---------------------
    // ASDF row: C4 D4 E4 F4 G4 A4 B4 C5 D5
    private static final int[] ASDF_NOTES = {60, 62, 64, 65, 67, 69, 71, 72, 74};
    private static final KeyCode[] ASDF_KEYS = {
        KeyCode.A, KeyCode.S, KeyCode.D, KeyCode.F,
        KeyCode.G, KeyCode.H, KeyCode.J, KeyCode.K, KeyCode.L
    };

    // ZXCV row: C3 D3 E3 F3 G3 A3 B3
    private static final int[] ZXCV_NOTES = {48, 50, 52, 53, 55, 57, 59};
    private static final KeyCode[] ZXCV_KEYS = {
        KeyCode.Z, KeyCode.X, KeyCode.C, KeyCode.V,
        KeyCode.B, KeyCode.N, KeyCode.M
    };

    // =========================================================================
    // ClockDriver inner class
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
    // JavaFX entry point
    // =========================================================================

    @Override
    public void start(Stage stage) {
        buildAudioGraph();
        buildBeat();
        startBeat();

        Scene scene = buildUI(stage);
        hookKeys(scene);

        stage.setTitle("FORGE.EXE — DEMO");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
    }

    // =========================================================================
    // Audio setup
    // =========================================================================

    private void buildAudioGraph() {
        audioEngine = new AudioEngine();
        audioEngine.start();

        // -- 8 synth voices --------------------------------------------------
        SynthVoice[] voices = new SynthVoice[8];
        for (int i = 0; i < 8; i++) {
            voices[i] = new SubtractiveSynthVoice();
            voices[i].init(audioEngine.getSynth());
            // Connect voice output to master mixer channels 0-7
            voices[i].getOutput().connect(0, audioEngine.getMasterMixer().input, i);
        }
        voiceAllocator = new VoiceAllocator(voices);

        // -- Drum engine (uses mixer channels 4-7 internally) ----------------
        drumEngine = new DrumEngine(audioEngine);

        // -- Sequencer clock at 128 BPM -------------------------------------
        clock = new SequencerClock();
        clock.setSampleRate(AudioEngine.SAMPLE_RATE);
        clock.setBpm(128.0);
        clock.setStepsPerBar(16);

        // -- Step sequencer --------------------------------------------------
        sequencer = new StepSequencer(drumEngine, voiceAllocator);

        // Listen for step changes to update the UI
        clock.setListener(new com.forge.audio.sequencer.SequencerListener() {
            @Override
            public void onStep(int stepIndex) {
                sequencer.onStep(stepIndex);
                currentStep.set(stepIndex);
                Platform.runLater(() -> updateStepDisplay(stepIndex));
            }

            @Override
            public void onBarEnd(int barNumber) {
                sequencer.onBarEnd(barNumber);
            }
        });

        // -- Clock driver: ticked by JSyn audio thread -----------------------
        clockDriver = new ClockDriver(clock);
        audioEngine.getSynth().add(clockDriver);
        clockDriver.start();
    }

    private void buildBeat() {
        Pattern pattern = new Pattern();

        // Kick: steps 0, 4, 8, 12
        int[] kickSteps = {0, 4, 8, 12};
        for (int s : kickSteps) {
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].active = true;
            pattern.drumSteps[DrumTrack.KICK.ordinal()][s].velocity = 0.9;
        }

        // Snare: steps 4, 12
        int[] snareSteps = {4, 12};
        for (int s : snareSteps) {
            pattern.drumSteps[DrumTrack.SNARE.ordinal()][s].active = true;
            pattern.drumSteps[DrumTrack.SNARE.ordinal()][s].velocity = 0.85;
        }

        // Hat: every even step (0, 2, 4, 6, 8, 10, 12, 14)
        for (int s = 0; s < 16; s += 2) {
            pattern.drumSteps[DrumTrack.HAT.ordinal()][s].active = true;
            pattern.drumSteps[DrumTrack.HAT.ordinal()][s].velocity = 0.7;
        }

        // Apply default drum patches for reasonable sounds
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

        sequencer.setPattern(pattern);
    }

    private void startBeat() {
        playing = true;
        clock.play();
    }

    // =========================================================================
    // UI
    // =========================================================================

    private Scene buildUI(Stage stage) {
        // Title
        Text title = new Text("FORGE.EXE \u2014 DEMO");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 28));
        title.setFill(Color.web("#FF6600"));

        // Help row
        Text help = new Text("Space: Play/Stop  |  ASDF/GH/JKL: notes (C4\u2013D5)  |  ZXCVBNM: lower octave  |  Q/W: Octave \u2193/\u2191");
        help.setFont(Font.font("Monospace", FontWeight.NORMAL, 13));
        help.setFill(Color.web("#AAAAAA"));

        // Status row
        playText = new Text("[\u25B6 PLAYING]");
        playText.setFont(Font.font("Monospace", FontWeight.BOLD, 16));
        playText.setFill(Color.web("#00FF88"));

        Text bpmLabel = new Text("  BPM: 128  |  Steps: 16");
        bpmLabel.setFont(Font.font("Monospace", FontWeight.NORMAL, 16));
        bpmLabel.setFill(Color.web("#888888"));

        HBox statusRow = new HBox(10, playText, bpmLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // Step indicator
        stepText = new Text(buildStepBar(0));
        stepText.setFont(Font.font("Monospace", FontWeight.NORMAL, 14));
        stepText.setFill(Color.web("#FF6600"));

        Text stepLabel = new Text("STEP INDICATOR:");
        stepLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
        stepLabel.setFill(Color.web("#666666"));

        // Octave display
        octaveText = new Text("Octave shift: 0  (base: C4/C3)");
        octaveText.setFont(Font.font("Monospace", FontWeight.NORMAL, 13));
        octaveText.setFill(Color.web("#88AAFF"));

        // Key hint grid
        Text keyHint1 = new Text("  Upper row:  A=C4  S=D4  D=E4  F=F4  G=G4  H=A4  J=B4  K=C5  L=D5");
        Text keyHint2 = new Text("  Lower row:  Z=C3  X=D3  C=E3  V=F3  B=G3  N=A3  M=B3");
        for (Text t : new Text[]{keyHint1, keyHint2}) {
            t.setFont(Font.font("Monospace", FontWeight.NORMAL, 12));
            t.setFill(Color.web("#555555"));
        }

        VBox root = new VBox(18,
            title,
            help,
            statusRow,
            stepLabel,
            stepText,
            octaveText,
            keyHint1,
            keyHint2
        );
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #0A0A0A;");

        return new Scene(root, 720, 360, Color.web("#0A0A0A"));
    }

    private String buildStepBar(int active) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i == active) {
                sb.append("[\u25A0]");
            } else if (i % 4 == 0) {
                sb.append("[ ]");
            } else {
                sb.append("[\u00B7]");
            }
            if (i < 15) sb.append(" ");
        }
        return sb.toString();
    }

    private void updateStepDisplay(int step) {
        stepText.setText(buildStepBar(step));
    }

    // =========================================================================
    // Keyboard handling
    // =========================================================================

    private void hookKeys(Scene scene) {
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();

            // Avoid retrigger on held keys
            if (heldKeys.contains(code)) return;
            heldKeys.add(code);

            if (code == KeyCode.SPACE) {
                togglePlayStop();
                return;
            }

            if (code == KeyCode.Q) {
                // Release all currently held notes before shifting
                releaseAllHeldNotes();
                octaveShift -= 12;
                updateOctaveDisplay();
                return;
            }
            if (code == KeyCode.W) {
                releaseAllHeldNotes();
                octaveShift += 12;
                updateOctaveDisplay();
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

            // Look up the actual MIDI note this key triggered (not current octave)
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

    private void togglePlayStop() {
        if (playing) {
            clock.stop();
            playing = false;
            Platform.runLater(() -> {
                playText.setText("[\u25A0 STOPPED]");
                playText.setFill(Color.web("#FF4444"));
                stepText.setText(buildStepBar(-1));
            });
        } else {
            playing = true;
            clock.play();
            Platform.runLater(() -> {
                playText.setText("[\u25B6 PLAYING]");
                playText.setFill(Color.web("#00FF88"));
            });
        }
    }

    private void updateOctaveDisplay() {
        int semitones = octaveShift;
        String dir = semitones >= 0 ? "+" : "";
        Platform.runLater(() ->
            octaveText.setText("Octave shift: " + dir + semitones + "  (base: C4/C3)")
        );
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    private void shutdown() {
        clock.stop();
        audioEngine.stop();
    }

    @Override
    public void stop() {
        shutdown();
    }

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args) {
        launch(args);
    }
}
