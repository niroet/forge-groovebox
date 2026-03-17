package com.forge.ui.panels;

import com.forge.audio.effects.Effect;
import com.forge.audio.effects.EffectType;
import com.forge.audio.effects.EffectsChain;
import com.forge.audio.synth.SynthVoice;
import com.forge.model.FilterType;
import com.forge.model.SynthPatch;
import com.forge.model.WaveShape;
import com.forge.ui.controls.ForgeDropdown;
import com.forge.ui.controls.ForgeFader;
import com.forge.ui.controls.ForgeKnob;
import com.forge.ui.controls.WaveformDisplay;
import com.forge.ui.theme.ForgeColors;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Synth parameter panel occupying the left 240px of the FORGE shell.
 *
 * <p>Contains: engine tabs, oscillator section, filter section,
 * amp envelope, and FX chain grid. All controls are wired to the
 * shared SynthPatch and pushed to all voices via applyPatch().
 */
public class SynthPanel extends VBox {

    private static final double PANEL_WIDTH = 240;

    // ---- Audio references (set via wire()) ----------------------------------
    private SynthVoice[] voices;
    private SynthPatch patch;
    private EffectsChain effectsChain;

    // ---- Oscillator controls ------------------------------------------------
    private WaveformDisplay waveDisplayA;
    private WaveformDisplay waveDisplayB;
    private ForgeDropdown<WaveShape> waveDropA;
    private ForgeDropdown<WaveShape> waveDropB;
    private ForgeKnob pitchKnobA, shapeKnobA, levelKnobA;
    private ForgeKnob pitchKnobB, shapeKnobB, levelKnobB;

    // ---- Filter controls ----------------------------------------------------
    private Label[] filterTypeLabels;
    private FilterType activeFilterType = FilterType.LOW_PASS;
    private ForgeKnob cutoffKnob, resoKnob;

    // ---- Envelope controls --------------------------------------------------
    private ForgeFader attackFader, decayFader, sustainFader, releaseFader;

    // ---- FX controls --------------------------------------------------------
    private ForgeKnob[] fxKnobs;
    private Region[] fxDots;
    private boolean[] fxEnabled;

    // =========================================================================
    // Constructor
    // =========================================================================

    public SynthPanel() {
        super(0);
        setPrefWidth(PANEL_WIDTH);
        setMinWidth(PANEL_WIDTH);
        setMaxWidth(PANEL_WIDTH);
        setStyle("-fx-background-color: #0a0a0a; -fx-border-color: #222222; -fx-border-width: 0 1 0 0;");

        VBox content = new VBox(0);
        content.setPadding(new Insets(0));

        content.getChildren().addAll(
            buildEngineTabs(),
            buildOscillatorSection(),
            buildFilterSection(),
            buildEnvelopeSection(),
            buildFxSection()
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background: #0a0a0a; -fx-background-color: #0a0a0a; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().add(scroll);
    }

    // =========================================================================
    // Wiring to audio engine
    // =========================================================================

    /**
     * Wire this panel to the audio engine components. Must be called after construction.
     */
    public void wire(SynthVoice[] voices, SynthPatch patch, EffectsChain effectsChain) {
        this.voices = voices;
        this.patch = patch;
        this.effectsChain = effectsChain;

        // Initialize controls from patch state
        waveDropA.setValue(patch.oscAShape);
        waveDropB.setValue(patch.oscBShape);
        waveDisplayA.setWaveShape(patch.oscAShape);
        waveDisplayB.setWaveShape(patch.oscBShape);

        pitchKnobA.setValue(patch.oscADetune);
        levelKnobA.setValue(patch.oscALevel);
        pitchKnobB.setValue(patch.oscBDetune);
        levelKnobB.setValue(patch.oscBLevel);

        cutoffKnob.setValue(patch.filterCutoff);
        resoKnob.setValue(patch.filterResonance);
        updateFilterTypeButtons(patch.filterType);

        attackFader.setValue(normalize(patch.ampAttack, 0.001, 2.0));
        decayFader.setValue(normalize(patch.ampDecay, 0.001, 2.0));
        sustainFader.setValue(patch.ampSustain);
        releaseFader.setValue(normalize(patch.ampRelease, 0.001, 3.0));

        // Wire oscillator controls
        wireOscillatorListeners();
        wireFilterListeners();
        wireEnvelopeListeners();
        wireFxListeners();
    }

    // =========================================================================
    // Engine Tabs
    // =========================================================================

    private HBox buildEngineTabs() {
        HBox tabs = new HBox(1);
        tabs.setPadding(new Insets(4, 4, 4, 4));
        tabs.setStyle("-fx-background-color: #0d0d0d; -fx-border-color: transparent transparent #1a1a1a transparent; -fx-border-width: 0 0 1 0;");

        String[] names = {"SUB", "FM", "WAVE", "GRAIN"};
        for (int i = 0; i < names.length; i++) {
            Label tab = new Label(names[i]);
            tab.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            tab.setPadding(new Insets(3, 8, 3, 8));
            tab.setAlignment(Pos.CENTER);

            if (i == 0) {
                tab.setTextFill(Color.WHITE);
                tab.setStyle("-fx-background-color: " + ForgeColors.hex(ForgeColors.ARGENT_RED) + ";" +
                    "-fx-padding: 3 8 3 8; -fx-cursor: hand; -fx-background-radius: 2;");
            } else {
                tab.setTextFill(Color.web("#555555"));
                tab.setStyle("-fx-background-color: #1a1a1a; -fx-padding: 3 8 3 8; -fx-cursor: hand; -fx-background-radius: 2;");
            }
            tabs.getChildren().add(tab);
        }
        return tabs;
    }

    // =========================================================================
    // Oscillator Section
    // =========================================================================

    private VBox buildOscillatorSection() {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 6, 6, 6));

        Label header = makeSectionHeader("\u25C6 ARGENT OSCILLATORS", ForgeColors.ARGENT_RED);
        section.getChildren().add(header);

        HBox oscPair = new HBox(4);
        oscPair.getChildren().addAll(buildOscPanel("OSC-A", true), buildOscPanel("OSC-B", false));
        section.getChildren().add(oscPair);

        return section;
    }

    private VBox buildOscPanel(String name, boolean isA) {
        VBox panel = new VBox(3);
        panel.setPadding(new Insets(4));
        Color accent = isA ? ForgeColors.ARGENT_RED : ForgeColors.ARGENT_ORANGE;
        panel.setStyle("-fx-background-color: #0d0d0d; -fx-border-color: " + ForgeColors.hex(accent) +
            " #1a1a1a #1a1a1a #1a1a1a; -fx-border-width: 2 1 1 1;");
        panel.setPrefWidth(110);

        Label label = new Label(name);
        label.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
        label.setTextFill(accent);

        WaveformDisplay wd = new WaveformDisplay(100, 24);
        if (isA) waveDisplayA = wd; else waveDisplayB = wd;

        ForgeDropdown<WaveShape> drop = new ForgeDropdown<>(
            FXCollections.observableArrayList(WaveShape.values()));
        drop.setValue(WaveShape.SAW);
        drop.setPrefWidth(100);
        drop.setStyle(drop.getStyle() + "-fx-font-size: 9px; -fx-pref-height: 20;");
        if (isA) waveDropA = drop; else waveDropB = drop;

        // Knobs row
        ForgeKnob pitchK = new ForgeKnob("PITCH", -100, 100, 0);
        pitchK.setAccentColor(accent);
        ForgeKnob shapeK = new ForgeKnob("DETUNE", 0, 1, 0);
        shapeK.setAccentColor(accent);
        ForgeKnob levelK = new ForgeKnob("LEVEL", 0, 1, isA ? 1.0 : 0.0);
        levelK.setAccentColor(accent);

        if (isA) { pitchKnobA = pitchK; shapeKnobA = shapeK; levelKnobA = levelK; }
        else     { pitchKnobB = pitchK; shapeKnobB = shapeK; levelKnobB = levelK; }

        HBox knobs = new HBox(2, pitchK, shapeK, levelK);
        knobs.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(label, wd, drop, knobs);
        return panel;
    }

    // =========================================================================
    // Filter Section
    // =========================================================================

    private VBox buildFilterSection() {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 6, 6, 6));
        section.setStyle("-fx-border-color: " + ForgeColors.hex(ForgeColors.ARGENT_RED) +
            " transparent transparent transparent; -fx-border-width: 0 0 0 2;");

        Label header = makeSectionHeader("\u25C6 ARGENT FILTER", ForgeColors.ARGENT_RED);
        section.getChildren().add(header);

        // Filter type toggles
        HBox typeRow = new HBox(2);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        String[] types = {"LP", "HP", "BP", "NOTCH"};
        FilterType[] filterTypes = {FilterType.LOW_PASS, FilterType.HIGH_PASS, FilterType.BAND_PASS, FilterType.NOTCH};
        filterTypeLabels = new Label[4];

        for (int i = 0; i < 4; i++) {
            Label btn = new Label(types[i]);
            btn.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            btn.setPadding(new Insets(2, 6, 2, 6));
            btn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2;");
            btn.setTextFill(Color.web("#555555"));
            filterTypeLabels[i] = btn;

            final int idx = i;
            btn.setOnMouseClicked(e -> {
                activeFilterType = filterTypes[idx];
                updateFilterTypeButtons(activeFilterType);
                if (patch != null) {
                    patch.filterType = activeFilterType;
                    pushPatch();
                }
            });
            typeRow.getChildren().add(btn);
        }
        updateFilterTypeButtons(FilterType.LOW_PASS);

        // Knobs
        cutoffKnob = new ForgeKnob("CUTOFF", 20, 20000, 8000);
        cutoffKnob.setAccentColor(ForgeColors.ARGENT_RED);
        resoKnob = new ForgeKnob("RESO", 0, 1, 0.3);
        resoKnob.setAccentColor(ForgeColors.ARGENT_RED);

        HBox knobs = new HBox(8, cutoffKnob, resoKnob);
        knobs.setAlignment(Pos.CENTER);

        section.getChildren().addAll(typeRow, knobs);
        return section;
    }

    private void updateFilterTypeButtons(FilterType active) {
        this.activeFilterType = active;
        FilterType[] filterTypes = {FilterType.LOW_PASS, FilterType.HIGH_PASS, FilterType.BAND_PASS, FilterType.NOTCH};
        for (int i = 0; i < 4; i++) {
            if (filterTypeLabels == null) return;
            Label btn = filterTypeLabels[i];
            if (filterTypes[i] == active) {
                btn.setTextFill(Color.WHITE);
                btn.setStyle("-fx-cursor: hand; -fx-background-color: " + ForgeColors.hex(ForgeColors.ARGENT_RED) +
                    "; -fx-background-radius: 2; -fx-padding: 2 6 2 6;");
            } else {
                btn.setTextFill(Color.web("#555555"));
                btn.setStyle("-fx-cursor: hand; -fx-background-color: #1a1a1a; -fx-background-radius: 2; -fx-padding: 2 6 2 6;");
            }
        }
    }

    // =========================================================================
    // Envelope Section
    // =========================================================================

    private VBox buildEnvelopeSection() {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 6, 6, 6));

        Label header = makeSectionHeader("\u25C6 AMP ENVELOPE", ForgeColors.ARGENT_AMBER);
        section.getChildren().add(header);

        attackFader  = new ForgeFader();
        decayFader   = new ForgeFader();
        sustainFader = new ForgeFader();
        releaseFader = new ForgeFader();

        attackFader.setPrefSize(20, 70);
        decayFader.setPrefSize(20, 70);
        sustainFader.setPrefSize(20, 70);
        releaseFader.setPrefSize(20, 70);

        HBox faders = new HBox(6);
        faders.setAlignment(Pos.CENTER);

        String[] labels = {"A", "D", "S", "R"};
        Color[] colors = {ForgeColors.ARGENT_RED, ForgeColors.ARGENT_ORANGE, ForgeColors.ARGENT_AMBER, ForgeColors.ARGENT_RED};
        ForgeFader[] faderArr = {attackFader, decayFader, sustainFader, releaseFader};

        for (int i = 0; i < 4; i++) {
            VBox col = new VBox(2);
            col.setAlignment(Pos.CENTER);
            Label lbl = new Label(labels[i]);
            lbl.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            lbl.setTextFill(colors[i]);
            col.getChildren().addAll(faderArr[i], lbl);
            faders.getChildren().add(col);
        }

        section.getChildren().add(faders);
        return section;
    }

    // =========================================================================
    // FX Section
    // =========================================================================

    private VBox buildFxSection() {
        VBox section = new VBox(4);
        section.setPadding(new Insets(6, 6, 6, 6));

        Label header = makeSectionHeader("\u25C6 FX CHAIN", ForgeColors.ARGENT_RED);
        section.getChildren().add(header);

        String[] fxNames = {"DIST", "DELAY", "REVERB", "CHORUS", "COMP", "EQ"};
        String[] fxParams = {"drive", "time", "roomSize", "rate", "threshold", "lowGain"};
        double[] fxMins = {1.0, 10.0, 0.0, 0.1, -60.0, -12.0};
        double[] fxMaxs = {20.0, 2000.0, 1.0, 5.0, 0.0, 12.0};
        double[] fxDefs = {1.0, 250.0, 0.5, 0.5, -20.0, 0.0};

        fxKnobs = new ForgeKnob[6];
        fxDots = new Region[6];
        fxEnabled = new boolean[]{true, true, true, true, true, true};

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setAlignment(Pos.CENTER);

        for (int i = 0; i < 6; i++) {
            VBox fxBox = new VBox(1);
            fxBox.setAlignment(Pos.CENTER);
            fxBox.setPadding(new Insets(3));
            fxBox.setStyle("-fx-background-color: #0d0d0d; -fx-border-color: #1a1a1a; -fx-border-width: 1;");
            fxBox.setPrefWidth(70);

            // Name + active dot
            HBox nameRow = new HBox(3);
            nameRow.setAlignment(Pos.CENTER_LEFT);

            Region dot = new Region();
            dot.setPrefSize(5, 5);
            dot.setStyle("-fx-background-color: " + ForgeColors.hex(ForgeColors.ARGENT_RED) + "; -fx-background-radius: 3;");
            fxDots[i] = dot;

            Label name = new Label(fxNames[i]);
            name.setFont(Font.font("Monospace", FontWeight.BOLD, 8));
            name.setTextFill(Color.web("#888888"));
            nameRow.getChildren().addAll(dot, name);

            ForgeKnob knob = new ForgeKnob(fxParams[i], fxMins[i], fxMaxs[i], fxDefs[i]);
            knob.setAccentColor(ForgeColors.ARGENT_ORANGE);
            fxKnobs[i] = knob;

            fxBox.getChildren().addAll(nameRow, knob);

            // Click to toggle FX on/off
            final int idx = i;
            fxBox.setOnMouseClicked(e -> {
                // Only toggle if not clicking the knob
                if (e.getTarget() instanceof javafx.scene.canvas.Canvas) return;
                fxEnabled[idx] = !fxEnabled[idx];
                updateFxDot(idx);
                if (effectsChain != null) {
                    EffectType et = EffectType.values()[idx];
                    effectsChain.getEffect(et).setEnabled(fxEnabled[idx]);
                }
            });

            grid.add(fxBox, i % 3, i / 3);
        }

        section.getChildren().add(grid);
        return section;
    }

    private void updateFxDot(int idx) {
        if (fxEnabled[idx]) {
            fxDots[idx].setStyle("-fx-background-color: " + ForgeColors.hex(ForgeColors.ARGENT_RED) + "; -fx-background-radius: 3;");
        } else {
            fxDots[idx].setStyle("-fx-background-color: #333333; -fx-background-radius: 3;");
        }
    }

    // =========================================================================
    // Listener wiring
    // =========================================================================

    private void wireOscillatorListeners() {
        waveDropA.valueProperty().addListener((obs, o, n) -> {
            if (n == null || patch == null) return;
            patch.oscAShape = n;
            waveDisplayA.setWaveShape(n);
            pushPatch();
        });
        waveDropB.valueProperty().addListener((obs, o, n) -> {
            if (n == null || patch == null) return;
            patch.oscBShape = n;
            waveDisplayB.setWaveShape(n);
            pushPatch();
        });

        pitchKnobA.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            // Map cents to semitones: -100 cents to +100 cents = -1 to +1 semitones
            patch.oscADetune = n.doubleValue() / 100.0;
            pushPatch();
        });
        pitchKnobB.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.oscBDetune = n.doubleValue() / 100.0;
            pushPatch();
        });

        levelKnobA.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.oscALevel = n.doubleValue();
            pushPatch();
        });
        levelKnobB.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.oscBLevel = n.doubleValue();
            pushPatch();
        });
    }

    private void wireFilterListeners() {
        cutoffKnob.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.filterCutoff = n.doubleValue();
            pushPatch();
        });
        resoKnob.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.filterResonance = n.doubleValue();
            pushPatch();
        });
    }

    private void wireEnvelopeListeners() {
        attackFader.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.ampAttack = denormalize(n.doubleValue(), 0.001, 2.0);
            pushPatch();
        });
        decayFader.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.ampDecay = denormalize(n.doubleValue(), 0.001, 2.0);
            pushPatch();
        });
        sustainFader.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.ampSustain = n.doubleValue();
            pushPatch();
        });
        releaseFader.valueProperty().addListener((obs, o, n) -> {
            if (patch == null) return;
            patch.ampRelease = denormalize(n.doubleValue(), 0.001, 3.0);
            pushPatch();
        });
    }

    private void wireFxListeners() {
        EffectType[] types = EffectType.values();
        String[] paramNames = {"drive", "time", "roomSize", "rate", "threshold", "lowGain"};

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            fxKnobs[i].valueProperty().addListener((obs, o, n) -> {
                if (effectsChain == null) return;
                Effect fx = effectsChain.getEffect(types[idx]);
                try {
                    fx.setParam(paramNames[idx], n.doubleValue());
                } catch (IllegalArgumentException ex) {
                    // Parameter not supported — ignore silently for now
                    System.err.println("FX param error: " + ex.getMessage());
                }
            });
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void pushPatch() {
        if (voices == null || patch == null) return;
        for (SynthVoice v : voices) {
            v.applyPatch(patch);
        }
    }

    private Label makeSectionHeader(String text, Color color) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        lbl.setTextFill(color);
        lbl.setPadding(new Insets(2, 0, 2, 0));
        return lbl;
    }

    /** Normalize a value from [min,max] to [0,1]. */
    private static double normalize(double value, double min, double max) {
        return Math.max(0, Math.min(1, (value - min) / (max - min)));
    }

    /** Denormalize a [0,1] value to [min,max]. */
    private static double denormalize(double norm, double min, double max) {
        return min + norm * (max - min);
    }
}
