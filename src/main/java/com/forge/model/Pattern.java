package com.forge.model;

import com.google.gson.Gson;

public class Pattern {
    public String name;
    public int synthStepCount;
    public SynthStep[] synthSteps;
    public int[] drumStepCounts;      // 4 elements
    public DrumStep[][] drumSteps;    // 4 tracks × N steps
    public SynthPatch synthPatch;     // optional
    public Double bpmOverride;        // optional

    public Pattern() {
        this.name = "Pattern";
        this.synthStepCount = 16;
        this.synthSteps = new SynthStep[16];
        for (int i = 0; i < 16; i++) {
            this.synthSteps[i] = new SynthStep();
        }

        this.drumStepCounts = new int[]{16, 16, 16, 16};
        this.drumSteps = new DrumStep[4][];
        for (int t = 0; t < 4; t++) {
            this.drumSteps[t] = new DrumStep[16];
            for (int s = 0; s < 16; s++) {
                this.drumSteps[t][s] = new DrumStep();
            }
        }

        this.synthPatch = null;
        this.bpmOverride = null;
    }

    /** Deep copy via Gson serialization. */
    public Pattern copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), Pattern.class);
    }
}
