package com.forge.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectState {
    public double bpm;
    public double swing;
    public String rootNote;
    public ScaleType scaleType;
    public SynthPatch globalSynthPatch;
    public DrumPatch[] drumPatches;     // 4 elements
    public Pattern[] patterns;          // 16 elements
    public List<Section> sections;
    public Map<EffectType, EffectParams> masterFx;
    public int activePatternIndex;
    public int activeSectionIndex;

    public ProjectState() {
        this.bpm = 128.0;
        this.swing = 50.0;
        this.rootNote = "C";
        this.scaleType = ScaleType.MINOR;
        this.globalSynthPatch = new SynthPatch();

        // Default drum patches: kick, snare, hat, perc
        this.drumPatches = new DrumPatch[4];

        // KICK
        DrumPatch kick = new DrumPatch();
        kick.pitch = 60.0;
        kick.decay = 0.3;
        kick.toneNoise = 1.0;
        this.drumPatches[0] = kick;

        // SNARE
        DrumPatch snare = new DrumPatch();
        snare.pitch = 200.0;
        snare.decay = 0.15;
        snare.toneNoise = 0.5;
        this.drumPatches[1] = snare;

        // HAT
        DrumPatch hat = new DrumPatch();
        hat.pitch = 8000.0;
        hat.decay = 0.05;
        hat.toneNoise = 0.0;
        this.drumPatches[2] = hat;

        // PERC
        DrumPatch perc = new DrumPatch();
        perc.pitch = 400.0;
        perc.decay = 0.1;
        perc.toneNoise = 0.7;
        this.drumPatches[3] = perc;

        // 16 patterns
        this.patterns = new Pattern[16];
        for (int i = 0; i < 16; i++) {
            this.patterns[i] = new Pattern();
            this.patterns[i].name = "Pattern " + (i + 1);
        }

        this.sections = new ArrayList<>();
        this.masterFx = new HashMap<>();
        this.activePatternIndex = 0;
        this.activeSectionIndex = -1;
    }
}
