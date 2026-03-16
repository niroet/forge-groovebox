package com.forge.model;

import com.google.gson.Gson;

public class SynthPatch {

    // Engine
    public EngineType engineType;

    // Oscillator A
    public WaveShape oscAShape;
    public double oscADetune;   // semitones
    public double oscALevel;    // 0-1

    // Oscillator B
    public WaveShape oscBShape;
    public double oscBDetune;
    public double oscBLevel;

    // Filter
    public FilterType filterType;
    public double filterCutoff;    // Hz
    public double filterResonance; // 0-1

    // Amp envelope (ADSR)
    public double ampAttack;
    public double ampDecay;
    public double ampSustain;
    public double ampRelease;

    // Filter envelope (ADSR)
    public double filterAttack;
    public double filterDecay;
    public double filterSustain;
    public double filterRelease;
    public double filterEnvAmount; // -1 to 1

    // FM params
    public double fmRatio;
    public double fmDepth;
    public double fmFeedback;

    // Wavetable
    public double wavetableMorph; // 0-1

    // Granular
    public double granularSize;    // ms
    public double granularDensity; // grains/sec
    public double granularScatter; // 0-1

    public SynthPatch() {
        engineType = EngineType.SUBTRACTIVE;

        oscAShape = WaveShape.SAW;
        oscADetune = 0.0;
        oscALevel = 1.0;

        oscBShape = WaveShape.SAW;
        oscBDetune = 0.0;
        oscBLevel = 0.0;

        filterType = FilterType.LOW_PASS;
        filterCutoff = 8000.0;
        filterResonance = 0.3;

        ampAttack = 0.01;
        ampDecay = 0.1;
        ampSustain = 0.7;
        ampRelease = 0.3;

        filterAttack = 0.01;
        filterDecay = 0.2;
        filterSustain = 0.5;
        filterRelease = 0.3;
        filterEnvAmount = 0.5;

        fmRatio = 2.0;
        fmDepth = 0.5;
        fmFeedback = 0.0;

        wavetableMorph = 0.0;

        granularSize = 50.0;
        granularDensity = 20.0;
        granularScatter = 0.1;
    }

    /** Deep copy via Gson serialization. */
    public SynthPatch copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), SynthPatch.class);
    }
}
